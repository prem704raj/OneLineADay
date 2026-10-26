package com.onelineaday.dailydiary.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.ui.components.isVideoMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

object ShareImageHelper {

    suspend fun shareEntryAsImage(context: Context, entry: JournalEntry) = withContext(Dispatchers.IO) {
        try {
            val bitmap = generateEntryBitmap(context, entry)
            val cachePath = File(context.cacheDir, "journal_shares").apply { mkdirs() }
            val file = File(cachePath, "entry_${System.currentTimeMillis()}.png")
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
                    context.startActivity(Intent.createChooser(shareIntent, "Share your memory"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to share image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareBitmap(context: Context, bitmap: Bitmap) {
        try {
            val cachePath = File(context.cacheDir, "journal_shares").apply { mkdirs() }
            val file = File(cachePath, "entry_${System.currentTimeMillis()}.png")
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
                context.startActivity(Intent.createChooser(shareIntent, "Share your memory"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to share image", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun generateEntryBitmap(context: Context, entry: JournalEntry): Bitmap = withContext(Dispatchers.IO) {
        val width = 1080
        val paddingX = 80f
        val contentWidth = width - (paddingX * 2) // 920f
        val gap = 24f

        val allMedia = (entry.mediaUris + listOfNotNull(entry.photoUri).filter { it !in entry.mediaUris }).distinct()
        val totalMediaCount = allMedia.size

        // Preload up to 4 images at high resolution concurrently
        val loadedBitmaps: List<Pair<String, Bitmap>> = coroutineScope {
            allMedia.take(4).map { uri ->
                async {
                    val bmp = loadMediaBitmap(context, uri, 1080, 2400)
                    if (bmp != null) uri to bmp else null
                }
            }.awaitAll().filterNotNull()
        }

        val hasContent = entry.content.isNotBlank()
        val textLayout = if (hasContent) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1F2937")
                textSize = 46f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            StaticLayout.Builder.obtain(
                entry.content.trim(),
                0,
                entry.content.trim().length,
                textPaint,
                contentWidth.toInt()
            ).setLineSpacing(14f, 1.2f).build()
        } else null

        // Calculate media total height with natural aspect ratios (0% cropping)
        var mediaBlockHeight = 0f
        if (loadedBitmaps.isNotEmpty()) {
            if (loadedBitmaps.size <= 3) {
                // Stacked full-width layout: each image preserves its full natural aspect ratio
                for ((_, bmp) in loadedBitmaps) {
                    val aspect = bmp.height.toFloat() / bmp.width.toFloat()
                    val h = (contentWidth * aspect).coerceIn(200f, 2400f)
                    mediaBlockHeight += h + gap
                }
            } else {
                // 2-column layout for 4 items
                val colWidth = (contentWidth - 20f) / 2f
                val aspect0 = loadedBitmaps[0].second.height.toFloat() / loadedBitmaps[0].second.width.toFloat()
                val aspect1 = loadedBitmaps[1].second.height.toFloat() / loadedBitmaps[1].second.width.toFloat()
                val row1H = (colWidth * maxOf(aspect0, aspect1)).coerceIn(300f, 1200f)

                val aspect2 = loadedBitmaps[2].second.height.toFloat() / loadedBitmaps[2].second.width.toFloat()
                val aspect3 = loadedBitmaps[3].second.height.toFloat() / loadedBitmaps[3].second.width.toFloat()
                val row2H = (colWidth * maxOf(aspect2, aspect3)).coerceIn(300f, 1200f)

                mediaBlockHeight = row1H + gap + row2H + gap
            }
        }

        // Calculate dynamic canvas height
        var currentY = 100f // top padding
        currentY += 160f // Date + mood header

        if (textLayout != null) {
            currentY += textLayout.height.toFloat() + 48f
        }

        currentY += mediaBlockHeight

        if (entry.tags.isNotEmpty()) {
            currentY += 60f
        }

        currentY += 120f // footer + bottom padding

        val finalHeight = currentY.toInt().coerceAtLeast(1080)
        val bitmap = Bitmap.createBitmap(width, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean white background
        canvas.drawColor(Color.WHITE)

        // Draw date
        var drawY = 100f
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dateText = entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        canvas.drawText(dateText, paddingX, drawY + 40f, datePaint)
        drawY += 75f

        // Draw mood emoji + label
        val moodEmojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 60f
        }
        canvas.drawText(entry.mood.emoji, paddingX, drawY + 54f, moodEmojiPaint)

        val moodLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4B5563")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(entry.mood.label, paddingX + 85f, drawY + 46f, moodLabelPaint)
        drawY += 85f

        // Draw entry text
        if (textLayout != null) {
            canvas.save()
            canvas.translate(paddingX, drawY)
            textLayout.draw(canvas)
            canvas.restore()
            drawY += textLayout.height.toFloat() + 48f
        }

        // Draw media with 0% cropping (full images preserved)
        if (loadedBitmaps.isNotEmpty()) {
            if (loadedBitmaps.size <= 3) {
                // Render each image full-width at its natural aspect ratio
                for ((uri, bmp) in loadedBitmaps) {
                    val aspect = bmp.height.toFloat() / bmp.width.toFloat()
                    val itemHeight = (contentWidth * aspect).coerceIn(200f, 2400f)
                    val rect = RectF(paddingX, drawY, paddingX + contentWidth, drawY + itemHeight)
                    drawFitBitmap(canvas, bmp, rect, 24f, uri)
                    drawY += itemHeight + gap
                }
            } else {
                // 2-column layout for 4 items, using fit so nothing is cropped
                val colWidth = (contentWidth - 20f) / 2f
                val aspect0 = loadedBitmaps[0].second.height.toFloat() / loadedBitmaps[0].second.width.toFloat()
                val aspect1 = loadedBitmaps[1].second.height.toFloat() / loadedBitmaps[1].second.width.toFloat()
                val row1H = (colWidth * maxOf(aspect0, aspect1)).coerceIn(300f, 1200f)

                drawFitBitmap(canvas, loadedBitmaps[0].second, RectF(paddingX, drawY, paddingX + colWidth, drawY + row1H), 20f, loadedBitmaps[0].first)
                drawFitBitmap(canvas, loadedBitmaps[1].second, RectF(paddingX + colWidth + 20f, drawY, paddingX + contentWidth, drawY + row1H), 20f, loadedBitmaps[1].first)
                drawY += row1H + gap

                val aspect2 = loadedBitmaps[2].second.height.toFloat() / loadedBitmaps[2].second.width.toFloat()
                val aspect3 = loadedBitmaps[3].second.height.toFloat() / loadedBitmaps[3].second.width.toFloat()
                val row2H = (colWidth * maxOf(aspect2, aspect3)).coerceIn(300f, 1200f)

                drawFitBitmap(canvas, loadedBitmaps[2].second, RectF(paddingX, drawY, paddingX + colWidth, drawY + row2H), 20f, loadedBitmaps[2].first)
                val overlayText = if (totalMediaCount > 4) "+${totalMediaCount - 3}" else null
                drawFitBitmap(canvas, loadedBitmaps[3].second, RectF(paddingX + colWidth + 20f, drawY, paddingX + contentWidth, drawY + row2H), 20f, loadedBitmaps[3].first, overlayText)
                drawY += row2H + gap
            }
        }

        // Draw tags
        if (entry.tags.isNotEmpty()) {
            val tagsText = entry.tags.joinToString("  ") { "#$it" }
            val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4F46E5")
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(tagsText, paddingX, drawY + 28f, tagPaint)
            drawY += 50f
        }

        // Draw footer watermark
        val footerY = finalHeight - 50f
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 2f
        }
        canvas.drawLine(paddingX, footerY - 35f, paddingX + contentWidth, footerY - 35f, dividerPaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9CA3AF")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("One Line A Day", paddingX, footerY, footerPaint)

        bitmap
    }

    private suspend fun loadMediaBitmap(
        context: Context,
        pathOrUri: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val resolved = if (pathOrUri.startsWith("/")) {
                com.onelineaday.dailydiary.media.MediaStorageManager.resolveMediaFile(context, pathOrUri) ?: File(pathOrUri)
            } else {
                android.net.Uri.parse(pathOrUri)
            }
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(resolved)
                .size(targetWidth, targetHeight)
                .allowHardware(false) // critical for software canvas!
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawFitBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        destRect: RectF,
        cornerRadius: Float,
        uriString: String,
        overlayText: String? = null
    ) {
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()
        if (bitmapWidth <= 0f || bitmapHeight <= 0f) return

        val targetWidth = destRect.width()
        val targetHeight = destRect.height()

        // Scale to fit completely inside destRect with ZERO cropping
        val scale = minOf(targetWidth / bitmapWidth, targetHeight / bitmapHeight)
        val scaledW = bitmapWidth * scale
        val scaledH = bitmapHeight * scale

        val dx = destRect.left + (targetWidth - scaledW) * 0.5f
        val dy = destRect.top + (targetHeight - scaledH) * 0.5f
        val imageRect = RectF(dx, dy, dx + scaledW, dy + scaledH)

        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }

        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(matrix)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }

        canvas.drawRoundRect(imageRect, cornerRadius, cornerRadius, paint)

        // Draw border around the image
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#E5E7EB")
        }
        canvas.drawRoundRect(imageRect, cornerRadius, cornerRadius, borderPaint)

        // If it's a video, draw a play icon in center
        val isVideo = isVideoMedia(uriString)
        if (isVideo) {
            val playCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(160, 0, 0, 0)
            }
            val centerX = imageRect.centerX()
            val centerY = imageRect.centerY()
            val circleRadius = 40f
            canvas.drawCircle(centerX, centerY, circleRadius, playCirclePaint)

            val playTrianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val trianglePath = Path().apply {
                moveTo(centerX - 12f, centerY - 18f)
                lineTo(centerX + 20f, centerY)
                lineTo(centerX - 12f, centerY + 18f)
                close()
            }
            canvas.drawPath(trianglePath, playTrianglePaint)
        }

        // Draw +N overlay if specified
        if (overlayText != null) {
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(175, 0, 0, 0)
            }
            canvas.drawRoundRect(imageRect, cornerRadius, cornerRadius, overlayPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 64f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val yPos = imageRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(overlayText, imageRect.centerX(), yPos, textPaint)
        }
    }
}
