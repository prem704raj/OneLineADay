package com.onelineaday.dailydiary.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

object PdfExportHelper {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 42f
    private const val CONTENT_WIDTH = (PAGE_WIDTH - (MARGIN * 2)).toInt()

    suspend fun generateJournalPdf(
        context: Context,
        entries: List<JournalEntry>,
        currentStreak: Int,
        longestStreak: Int,
        totalEntries: Int
    ): Uri? = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) {
            return@withContext null
        }

        val exportDir = File(context.cacheDir, "journal_exports").apply {
            mkdirs()
        }

        val output = File(
            exportDir,
            "OneLineADay_${System.currentTimeMillis()}.pdf"
        )

        val pdf = PdfDocument()

        try {
            val renderer = JournalPdfRenderer(context = context, pdf = pdf)

            renderer.drawCover(
                entries = entries,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                totalEntries = totalEntries
            )

            entries.sortedByDescending { it.date }.forEach { entry ->
                renderer.drawEntry(entry)
            }

            renderer.finish()

            FileOutputStream(output).use { stream ->
                pdf.writeTo(stream)
                stream.fd.sync()
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                output
            )
        } finally {
            pdf.close()
        }
    }

    private class JournalPdfRenderer(
        private val context: Context,
        private val pdf: PdfDocument
    ) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = MARGIN

        private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 40, 45)
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(50, 50, 55)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 55, 60)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        private val mutedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(105, 105, 115)
            textSize = 10f
        }

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 225, 230)
            strokeWidth = 1f
        }

        fun drawCover(
            entries: List<JournalEntry>,
            currentStreak: Int,
            longestStreak: Int,
            totalEntries: Int
        ) {
            startPage()
            y = 180f

            drawText(
                text = context.getString(R.string.pdf_title),
                paint = titlePaint,
                spacingAfter = 14f
            )

            drawText(
                text = context.getString(R.string.pdf_subtitle),
                paint = bodyPaint,
                spacingAfter = 28f
            )

            val ordered = entries.sortedBy { it.date }
            val range = if (ordered.isNotEmpty()) {
                val first = ordered.first().date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
                val last = ordered.last().date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
                "$first — $last"
            } else {
                ""
            }

            drawText(
                text = range,
                paint = mutedPaint,
                spacingAfter = 28f
            )

            drawText(
                text = context.getString(R.string.pdf_entries, totalEntries),
                paint = headingPaint
            )

            drawText(
                text = context.getString(R.string.pdf_current_streak, currentStreak),
                paint = bodyPaint
            )

            drawText(
                text = context.getString(R.string.pdf_longest_streak, longestStreak),
                paint = bodyPaint
            )

            finishPage()
        }

        fun drawEntry(entry: JournalEntry) {
            ensurePage()
            ensureSpace(155f)

            val date = entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))

            drawText(
                text = date,
                paint = headingPaint,
                spacingAfter = 4f
            )

            drawText(
                text = entry.mood.emoji + " " + entry.mood.name.lowercase().replaceFirstChar { it.uppercase() },
                paint = mutedPaint,
                spacingAfter = 8f
            )

            drawText(
                text = entry.content,
                paint = bodyPaint,
                spacingAfter = 8f
            )

            if (entry.tags.isNotEmpty()) {
                drawText(
                    text = entry.tags.joinToString(" ") { "#$it" },
                    paint = mutedPaint,
                    spacingAfter = 8f
                )
            }

            if (!entry.audioUri.isNullOrBlank()) {
                drawText(
                    text = context.getString(R.string.pdf_voice_attached),
                    paint = mutedPaint,
                    spacingAfter = 6f
                )
            }

            val media = (listOfNotNull(entry.photoUri) + entry.mediaUris).distinct()
            media.forEach { path ->
                if (isLikelyVideo(path)) {
                    drawText(
                        text = context.getString(R.string.pdf_video_attached),
                        paint = mutedPaint,
                        spacingAfter = 6f
                    )
                } else {
                    drawImageIfPossible(path)
                }
            }

            val c = canvas ?: return
            ensureSpace(20f)
            c.drawLine(
                MARGIN,
                y,
                PAGE_WIDTH - MARGIN,
                y,
                linePaint
            )
            y += 18f
        }

        private fun drawText(
            text: String,
            paint: TextPaint,
            spacingAfter: Float = 6f
        ) {
            if (text.isBlank()) {
                return
            }

            val layout = StaticLayout.Builder.obtain(
                text,
                0,
                text.length,
                paint,
                CONTENT_WIDTH
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()

            ensureSpace(layout.height.toFloat() + spacingAfter)

            val currentCanvas = canvas ?: return
            currentCanvas.save()
            currentCanvas.translate(MARGIN, y)
            layout.draw(currentCanvas)
            currentCanvas.restore()

            y += layout.height + spacingAfter
        }

        private fun drawImageIfPossible(path: String) {
            val file = File(path)
            if (!file.exists() || !file.isFile) {
                return
            }

            val bitmap = decodeBoundedBitmap(file) ?: return
            try {
                val maxWidth = CONTENT_WIDTH.toFloat()
                val maxHeight = 220f
                val scale = minOf(
                    maxWidth / bitmap.width,
                    maxHeight / bitmap.height,
                    1f
                )
                val width = bitmap.width * scale
                val height = bitmap.height * scale

                ensureSpace(height + 12f)

                val destination = RectF(
                    MARGIN,
                    y,
                    MARGIN + width,
                    y + height
                )

                canvas?.drawBitmap(bitmap, null, destination, null)
                y += height + 12f
            } finally {
                bitmap.recycle()
            }
        }

        private fun decodeBoundedBitmap(file: File): Bitmap? {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null
            }

            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
            }

            return BitmapFactory.decodeFile(file.absolutePath, options)
        }

        private fun isLikelyVideo(path: String): Boolean {
            val lower = path.lowercase()
            return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".3gp")
        }

        private fun ensurePage() {
            if (page == null) {
                startPage()
            }
        }

        private fun ensureSpace(required: Float) {
            ensurePage()
            if (y + required > PAGE_HEIGHT - MARGIN) {
                finishPage()
                startPage()
            }
        }

        private fun startPage() {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH,
                PAGE_HEIGHT,
                pageNumber
            ).create()

            page = pdf.startPage(info)
            canvas = page?.canvas
            y = MARGIN
        }

        private fun finishPage() {
            page?.let { current ->
                pdf.finishPage(current)
            }
            page = null
            canvas = null
        }

        fun finish() {
            finishPage()
        }
    }
}
