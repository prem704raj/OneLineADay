package com.onelineaday.dailydiary.mosaic

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import com.onelineaday.dailydiary.data.Mood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

object YearInPixelsShareHelper {

    suspend fun shareMosaicCard(
        context: Context,
        year: Int,
        entries: Map<LocalDate, Mood>
    ) = withContext(Dispatchers.IO) {
        val bitmap = generateMosaicBitmap(year, entries)
        val cachePath = File(context.cacheDir, "journal_shares").apply { mkdirs() }
        val file = File(cachePath, "mosaic_${year}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        if (contentUri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "image/png"
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Share Year in Pixels"))
            }
        }
    }

    fun generateMosaicBitmap(year: Int, entries: Map<LocalDate, Mood>): Bitmap {
        val width = 2000
        val height = 2400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw elegant modern dark background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.parseColor("#0F172A"), Color.parseColor("#1E293B"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle ambient glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width.toFloat(), 0f, 800f,
                Color.argb(35, 99, 102, 241), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width.toFloat(), 0f, 800f, glowPaint)

        var drawY = 100f
        val paddingX = 80f

        // 2. Header
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Year in Pixels", paddingX, drawY + 60f, titlePaint)

        val yearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#818CF8")
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("$year", width - paddingX, drawY + 60f, yearPaint)
        drawY += 120f

        // Subtitle & stats
        val stats = MoodMosaicEngine.calculateStats(entries)
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val dominantStr = stats.dominantMood?.let { "${it.emoji} ${it.label}" } ?: "None"
        canvas.drawText("${stats.totalLogged} days recorded  •  Dominant Mood: $dominantStr", paddingX, drawY + 30f, subtitlePaint)
        drawY += 70f

        // Divider
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            strokeWidth = 2f
        }
        canvas.drawLine(paddingX, drawY, width - paddingX, drawY, dividerPaint)
        drawY += 50f

        // 3. Draw 12x31 Grid
        val grid = MoodMosaicEngine.buildMosaic(year, entries)
        val gridWidth = width - (paddingX * 2) - 100f // 100f for month labels
        val cellSize = (gridWidth / 31f).coerceAtMost(48f)
        val cellGap = 6f
        val effectiveCell = cellSize - cellGap

        val monthLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val emptyCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
        }
        val emptyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.parseColor("#334155")
        }

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw day header numbers (1..31) every 5 days
        val dayNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        val startX = paddingX + 100f
        for (day in 1..31) {
            if (day == 1 || day % 5 == 0 || day == 31) {
                val cx = startX + (day - 1) * cellSize + (effectiveCell / 2f)
                canvas.drawText("$day", cx, drawY + 22f, dayNumPaint)
            }
        }
        drawY += 40f

        for (m in 0 until 12) {
            val monthName = Month.of(m + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase()
            val rowY = drawY + (m * (cellSize + 8f))

            canvas.drawText(monthName, paddingX, rowY + (effectiveCell * 0.75f), monthLabelPaint)

            for (d in 0 until 31) {
                val cell = grid[m][d]
                val cellLeft = startX + (d * cellSize)
                val cellTop = rowY
                val cellRect = RectF(cellLeft, cellTop, cellLeft + effectiveCell, cellTop + effectiveCell)

                when (cell) {
                    is PixelCell.Logged -> {
                        cellPaint.color = getMoodColorInt(cell.mood)
                        canvas.drawRoundRect(cellRect, 8f, 8f, cellPaint)
                    }
                    is PixelCell.Unlogged -> {
                        canvas.drawRoundRect(cellRect, 8f, 8f, emptyCellPaint)
                        canvas.drawRoundRect(cellRect, 8f, 8f, emptyBorderPaint)
                    }
                    is PixelCell.OutOfRange -> {
                        // Omit drawing for days like Feb 30
                    }
                }
            }
        }
        drawY += 12 * (cellSize + 8f) + 40f

        // 4. Mood Legend Grid
        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val moods = Mood.values()
        val cols = 5
        val legendColW = (width - paddingX * 2) / cols

        for (i in moods.indices) {
            val mood = moods[i]
            val c = i % cols
            val r = i / cols
            val lx = paddingX + c * legendColW
            val ly = drawY + r * 50f

            val dotRect = RectF(lx, ly, lx + 26f, ly + 26f)
            cellPaint.color = getMoodColorInt(mood)
            canvas.drawRoundRect(dotRect, 6f, 6f, cellPaint)

            val count = stats.moodCounts[mood] ?: 0
            canvas.drawText("${mood.emoji} ${mood.label} ($count)", lx + 36f, ly + 22f, legendPaint)
        }

        // 5. Watermark Footer
        val footerY = height - 50f
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("One Line A Day  •  Daily Journal & Memories", paddingX, footerY, brandPaint)

        return bitmap
    }

    private fun getMoodColorInt(mood: Mood): Int = when (mood) {
        Mood.HAPPY -> Color.parseColor("#FFD93D")
        Mood.NEUTRAL -> Color.parseColor("#A8A8A8")
        Mood.SAD -> Color.parseColor("#6C9BCF")
        Mood.ANGRY -> Color.parseColor("#FF6B6B")
        Mood.EXCITED -> Color.parseColor("#FF9F1C")
        Mood.TIRED -> Color.parseColor("#9E9E9E")
        Mood.MOTIVATED -> Color.parseColor("#4CAF50")
        Mood.LOVED -> Color.parseColor("#E91E63")
        Mood.ANXIOUS -> Color.parseColor("#9C27B0")
        Mood.GRATEFUL -> Color.parseColor("#00BCD4")
    }
}

