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
import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.ui.components.isVideoMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

/**
 * Colorful themes for shared entry pages
 */
enum class ShareTheme(
    val title: String,
    val emoji: String,
    val bgStartColor: Int,
    val bgEndColor: Int,
    val cardBgColor: Int,
    val textColor: Int,
    val subtitleColor: Int,
    val accentColor: Int,
    val borderColor: Int,
    val watermarkColor: Int
) {
    GOLDEN(
        title = "Warm Amber",
        emoji = "✨",
        bgStartColor = Color.parseColor("#FFFBEB"),
        bgEndColor = Color.parseColor("#FDE68A"),
        cardBgColor = Color.parseColor("#FFFFFF"),
        textColor = Color.parseColor("#451A03"),
        subtitleColor = Color.parseColor("#92400E"),
        accentColor = Color.parseColor("#D97706"),
        borderColor = Color.parseColor("#FCD34D"),
        watermarkColor = Color.parseColor("#B45309")
    ),
    SUNSET(
        title = "Sunset Glow",
        emoji = "🌅",
        bgStartColor = Color.parseColor("#FFF1F2"),
        bgEndColor = Color.parseColor("#FED7AA"),
        cardBgColor = Color.parseColor("#FFFFFF"),
        textColor = Color.parseColor("#431407"),
        subtitleColor = Color.parseColor("#9A3412"),
        accentColor = Color.parseColor("#EA580C"),
        borderColor = Color.parseColor("#FDBA74"),
        watermarkColor = Color.parseColor("#C2410C")
    ),
    OCEAN(
        title = "Ocean Breeze",
        emoji = "🌊",
        bgStartColor = Color.parseColor("#F0F9FF"),
        bgEndColor = Color.parseColor("#BAE6FD"),
        cardBgColor = Color.parseColor("#FFFFFF"),
        textColor = Color.parseColor("#082F49"),
        subtitleColor = Color.parseColor("#075985"),
        accentColor = Color.parseColor("#0284C7"),
        borderColor = Color.parseColor("#7DD3FC"),
        watermarkColor = Color.parseColor("#0369A1")
    ),
    EMERALD(
        title = "Forest Mint",
        emoji = "🌿",
        bgStartColor = Color.parseColor("#F0FDF4"),
        bgEndColor = Color.parseColor("#BBF7D0"),
        cardBgColor = Color.parseColor("#FFFFFF"),
        textColor = Color.parseColor("#052E16"),
        subtitleColor = Color.parseColor("#166534"),
        accentColor = Color.parseColor("#16A34A"),
        borderColor = Color.parseColor("#86EFAC"),
        watermarkColor = Color.parseColor("#15803D")
    ),
    LAVENDER(
        title = "Lavender Dream",
        emoji = "💜",
        bgStartColor = Color.parseColor("#FAF5FF"),
        bgEndColor = Color.parseColor("#E9D5FF"),
        cardBgColor = Color.parseColor("#FFFFFF"),
        textColor = Color.parseColor("#3B0764"),
        subtitleColor = Color.parseColor("#6B21A8"),
        accentColor = Color.parseColor("#9333EA"),
        borderColor = Color.parseColor("#D8B4FE"),
        watermarkColor = Color.parseColor("#7E22CE")
    ),
    ROSE(
        title = "Rose Blush",
        emoji = "🌸",
        bgStartColor = Color.parseColor("#FFF1F2"),
        bgEndColor = Color.parseColor("#FECDD3"),
        cardBgColor = Color.parseColor("#FFFFFF"),
        textColor = Color.parseColor("#4C0519"),
        subtitleColor = Color.parseColor("#9F1239"),
        accentColor = Color.parseColor("#E11D48"),
        borderColor = Color.parseColor("#FDA4AF"),
        watermarkColor = Color.parseColor("#BE123C")
    ),
    MIDNIGHT(
        title = "Midnight Dark",
        emoji = "🌙",
        bgStartColor = Color.parseColor("#0B0F19"),
        bgEndColor = Color.parseColor("#1E293B"),
        cardBgColor = Color.parseColor("#1E293B"),
        textColor = Color.parseColor("#F8FAFC"),
        subtitleColor = Color.parseColor("#94A3B8"),
        accentColor = Color.parseColor("#38BDF8"),
        borderColor = Color.parseColor("#334155"),
        watermarkColor = Color.parseColor("#64748B")
    ),
    MINIMAL_MONOCHROME(
        title = "Minimal Monochrome",
        emoji = "🖋️",
        bgStartColor = Color.parseColor("#F8F9FA"),
        bgEndColor = Color.parseColor("#E9ECEF"),
        cardBgColor = Color.parseColor("#FFFFFF"),
        textColor = Color.parseColor("#18181B"),
        subtitleColor = Color.parseColor("#71717A"),
        accentColor = Color.parseColor("#27272A"),
        borderColor = Color.parseColor("#E4E4E7"),
        watermarkColor = Color.parseColor("#A1A1AA")
    );

    companion object {
        fun forMood(mood: Mood): ShareTheme {
            return when (mood) {
                Mood.HAPPY -> GOLDEN
                Mood.EXCITED -> SUNSET
                Mood.MOTIVATED -> EMERALD
                Mood.LOVED -> ROSE
                Mood.GRATEFUL -> OCEAN
                Mood.SAD -> OCEAN
                Mood.TIRED -> LAVENDER
                Mood.ANGRY -> ROSE
                Mood.ANXIOUS -> LAVENDER
                Mood.NEUTRAL -> SUNSET
            }
        }
    }
}

enum class StoryFormat(val width: Int, val height: Int) {
    VERTICAL_9_16(1080, 1920),
    HORIZONTAL_16_9(1920, 1080),
    DYNAMIC_CARD(2400, 0)
}

object ShareImageHelper {

    suspend fun shareEntryAsImage(
        context: Context,
        entry: JournalEntry,
        theme: ShareTheme = ShareTheme.forMood(entry.mood)
    ) = withContext(Dispatchers.IO) {
        try {
            val bitmap = generateEntryBitmap(context, entry, theme)
            shareBitmap(context, bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to share image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun shareStoryCardAsImage(
        context: Context,
        entry: JournalEntry,
        theme: ShareTheme = ShareTheme.forMood(entry.mood),
        format: StoryFormat = StoryFormat.VERTICAL_9_16,
        yearOffset: Int? = null,
        isLeapDayObservation: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val bitmap = generateStoryCardBitmap(context, entry, theme, format, yearOffset, isLeapDayObservation)
            shareBitmap(context, bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to share story card", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun shareMultiYearFlashbackStoryAsImage(
        context: Context,
        flashbacks: List<com.onelineaday.dailydiary.memory.FlashbackMemory>,
        currentDate: java.time.LocalDate,
        theme: ShareTheme = ShareTheme.GOLDEN,
        format: StoryFormat = StoryFormat.VERTICAL_9_16
    ) = withContext(Dispatchers.IO) {
        try {
            val bitmap = generateMultiYearFlashbackStoryBitmap(context, flashbacks, currentDate, theme, format)
            shareBitmap(context, bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to share flashback story", Toast.LENGTH_SHORT).show()
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

    /**
     * Generates a high-resolution horizontal card with side-by-side images and colourful page styling.
     */
    suspend fun generateEntryBitmap(
        context: Context,
        entry: JournalEntry,
        theme: ShareTheme = ShareTheme.forMood(entry.mood)
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = 2400
        val outerPadding = 60f
        val cardLeft = outerPadding
        val cardTop = outerPadding
        val cardRight = width - outerPadding
        val cardWidth = cardRight - cardLeft
        val cardPaddingX = 70f
        val contentWidth = cardWidth - (cardPaddingX * 2)

        val allMedia = (entry.mediaUris + listOfNotNull(entry.photoUri).filter { it !in entry.mediaUris }).distinct()
        val totalMediaCount = allMedia.size

        // Preload up to 4 images at high resolution concurrently
        val loadedBitmaps: List<Pair<String, Bitmap>> = coroutineScope {
            allMedia.take(4).map { uri ->
                async {
                    val bmp = loadMediaBitmap(context, uri, 1600, 2400)
                    if (bmp != null) uri to bmp else null
                }
            }.awaitAll().filterNotNull()
        }

        val displayCount = loadedBitmaps.size
        val gap = 24f

        // Calculate horizontal side-by-side column width & row height preserving natural aspect ratios
        val colWidth = if (displayCount > 0) {
            (contentWidth - (displayCount - 1) * gap) / displayCount
        } else 0f

        val maxAspect = if (displayCount > 0) {
            loadedBitmaps.maxOfOrNull {
                it.second.height.toFloat() / it.second.width.toFloat()
            } ?: 1.0f
        } else 0f

        val imageRowHeight = if (displayCount > 0) {
            (colWidth * maxAspect).coerceIn(400f, 1500f)
        } else 0f

        val hasContent = entry.content.isNotBlank()
        val textLayout = if (hasContent) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textColor
                textSize = 52f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            StaticLayout.Builder.obtain(
                entry.content.trim(),
                0,
                entry.content.trim().length,
                textPaint,
                contentWidth.toInt()
            ).setLineSpacing(16f, 1.3f).build()
        } else null

        // Calculate card height dynamically
        var cardInnerY = 70f // top card padding
        cardInnerY += 140f   // Date & Mood header
        cardInnerY += 30f    // spacing
        if (textLayout != null) {
            cardInnerY += textLayout.height.toFloat() + 40f
        }
        if (displayCount > 0) {
            cardInnerY += imageRowHeight + 40f
        }
        if (entry.tags.isNotEmpty()) {
            cardInnerY += 60f + 30f
        }
        cardInnerY += 70f    // footer
        cardInnerY += 70f    // bottom card padding

        val cardHeight = cardInnerY
        val totalHeight = (cardHeight + (outerPadding * 2)).toInt()
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardTop + cardHeight)

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Colorful Page Background Gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), totalHeight.toFloat(),
                theme.bgStartColor, theme.bgEndColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)

        // 2. Draw Decorative Ambient Glow Circles
        val glowPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width.toFloat(), 0f, 700f,
                Color.argb(45, 255, 255, 255), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width.toFloat(), 0f, 700f, glowPaint1)

        val glowPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                0f, totalHeight.toFloat(), 700f,
                Color.argb(35, 255, 255, 255), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(0f, totalHeight.toFloat(), 700f, glowPaint2)

        // 3. Draw Inner Page Card
        val cardShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(20, 0, 0, 0)
        }
        canvas.drawRoundRect(RectF(cardRect.left, cardRect.top + 6f, cardRect.right, cardRect.bottom + 6f), 36f, 36f, cardShadowPaint)

        val cardBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.cardBgColor
        }
        canvas.drawRoundRect(cardRect, 36f, 36f, cardBodyPaint)

        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = theme.borderColor
        }
        canvas.drawRoundRect(cardRect, 36f, 36f, cardBorderPaint)

        // 4. Draw Header: Date (Left) and Mood Pill (Right)
        var drawY = cardTop + 70f
        val contentLeft = cardLeft + cardPaddingX

        // Day of Week
        val dayOfWeekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.subtitleColor
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(entry.date.format(DateTimeFormatter.ofPattern("EEEE")), contentLeft, drawY + 34f, dayOfWeekPaint)

        // Full Date
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textColor
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")), contentLeft, drawY + 104f, datePaint)

        // Mood Pill (Top Right)
        val moodText = "${entry.mood.emoji}  ${entry.mood.label}"
        val moodTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentColor
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val moodPillWidth = moodTextPaint.measureText(moodText) + 60f
        val moodPillHeight = 72f
        val moodPillLeft = cardRight - cardPaddingX - moodPillWidth
        val moodPillTop = drawY + 28f
        val moodPillRect = RectF(moodPillLeft, moodPillTop, moodPillLeft + moodPillWidth, moodPillTop + moodPillHeight)

        // Mood Pill Background
        val moodBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(30, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor))
        }
        canvas.drawRoundRect(moodPillRect, 36f, 36f, moodBgPaint)

        val moodBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(80, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor))
        }
        canvas.drawRoundRect(moodPillRect, 36f, 36f, moodBorderPaint)

        val moodTextY = moodPillRect.centerY() - (moodTextPaint.descent() + moodTextPaint.ascent()) / 2
        canvas.drawText(moodText, moodPillLeft + 30f, moodTextY, moodTextPaint)

        drawY += 140f + 30f

        // 5. Draw Entry Text
        if (textLayout != null) {
            canvas.save()
            canvas.translate(contentLeft, drawY)
            textLayout.draw(canvas)
            canvas.restore()
            drawY += textLayout.height.toFloat() + 40f
        }

        // 6. Draw Media: All Images Arranged HORIZONTALLY SIDE-BY-SIDE
        if (displayCount > 0) {
            var imgX = contentLeft
            for (i in 0 until displayCount) {
                val (uri, bmp) = loadedBitmaps[i]
                val destRect = RectF(imgX, drawY, imgX + colWidth, drawY + imageRowHeight)
                val overlayText = if (i == 3 && totalMediaCount > 4) "+${totalMediaCount - 3}" else null
                drawFitBitmap(canvas, bmp, destRect, 24f, uri, theme.borderColor, overlayText)
                imgX += colWidth + gap
            }
            drawY += imageRowHeight + 40f
        }

        // 7. Draw Tags
        if (entry.tags.isNotEmpty()) {
            val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.accentColor
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            var tagX = contentLeft
            for (tag in entry.tags) {
                val tagString = "#$tag"
                val tagWidth = tagTextPaint.measureText(tagString) + 40f
                val tagHeight = 56f
                val tagRect = RectF(tagX, drawY, tagX + tagWidth, drawY + tagHeight)

                val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(25, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor))
                }
                canvas.drawRoundRect(tagRect, 28f, 28f, tagBgPaint)

                val tagY = tagRect.centerY() - (tagTextPaint.descent() + tagTextPaint.ascent()) / 2
                canvas.drawText(tagString, tagX + 20f, tagY, tagTextPaint)
                tagX += tagWidth + 16f
            }
            drawY += 60f + 30f
        }

        // 8. Draw Footer Watermark
        val footerY = cardTop + cardHeight - 50f
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.borderColor
            strokeWidth = 2f
        }
        canvas.drawLine(contentLeft, footerY - 40f, cardRight - cardPaddingX, footerY - 40f, dividerPaint)

        val footerBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.subtitleColor
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("One Line A Day", contentLeft, footerY, footerBrandPaint)

        val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.watermarkColor
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Daily Journal & Memories", cardRight - cardPaddingX, footerY, footerRightPaint)

        bitmap
    }

    /**
     * Generates a fixed 9:16 or 16:9 visual story card for sharing to Instagram, WhatsApp, or other social platforms.
     */
    suspend fun generateStoryCardBitmap(
        context: Context,
        entry: JournalEntry,
        theme: ShareTheme = ShareTheme.forMood(entry.mood),
        format: StoryFormat = StoryFormat.VERTICAL_9_16,
        yearOffset: Int? = null,
        isLeapDayObservation: Boolean = false
    ): Bitmap = withContext(Dispatchers.IO) {
        if (format == StoryFormat.DYNAMIC_CARD) {
            return@withContext generateEntryBitmap(context, entry, theme)
        }

        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                theme.bgStartColor, theme.bgEndColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Card Container
        val outerPadding = 48f
        val cardRect = RectF(outerPadding, outerPadding, width - outerPadding, height - outerPadding)
        val cardCorner = 40f

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.cardBgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, cardPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.borderColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, borderPaint)

        val innerPadding = 56f
        val contentLeft = cardRect.left + innerPadding
        val contentRight = cardRect.right - innerPadding
        val contentWidth = (contentRight - contentLeft).toInt()
        var currentY = cardRect.top + innerPadding

        if (format == StoryFormat.VERTICAL_9_16) {
            // Top Badge if flashback
            if (yearOffset != null || isLeapDayObservation) {
                val badgeText = when {
                    yearOffset != null && isLeapDayObservation -> "✨ $yearOffset YEARS AGO (FEB 29 OBSERVED)"
                    yearOffset != null && yearOffset == 1 -> "1 YEAR AGO TODAY"
                    yearOffset != null -> "$yearOffset YEARS AGO"
                    else -> "✨ LEAP DAY MEMORY"
                }

                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.accentColor
                    alpha = 35
                    style = Paint.Style.FILL
                }
                val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.accentColor
                    textSize = 28f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val textBounds = Rect()
                badgeTextPaint.getTextBounds(badgeText, 0, badgeText.length, textBounds)
                val badgeWidth = textBounds.width() + 40f
                val badgeHeight = 52f
                val badgeRect = RectF(contentLeft, currentY, contentLeft + badgeWidth, currentY + badgeHeight)
                canvas.drawRoundRect(badgeRect, 26f, 26f, badgePaint)
                canvas.drawText(
                    badgeText,
                    badgeRect.centerX() - textBounds.width() / 2f - textBounds.left,
                    badgeRect.centerY() + textBounds.height() / 2f - textBounds.bottom,
                    badgeTextPaint
                )
                currentY += badgeHeight + 24f
            }

            // Day of Week
            val dayOfWeekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.subtitleColor
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val dayOfWeekStr = entry.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            canvas.drawText(dayOfWeekStr, contentLeft, currentY + 30f, dayOfWeekPaint)
            currentY += 40f

            // Full Date + Mood
            val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textColor
                textSize = 48f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val dateStr = entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
            canvas.drawText(dateStr, contentLeft, currentY + 44f, datePaint)

            // Mood pill on the right
            val moodStr = "${entry.mood.emoji} ${entry.mood.label}"
            val moodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textColor
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(moodStr, contentRight, currentY + 44f, moodPaint)
            currentY += 80f

            // Thin divider
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.borderColor
                strokeWidth = 2f
            }
            canvas.drawLine(contentLeft, currentY, contentRight, currentY, divPaint)
            currentY += 40f

            // Media attachment (if any)
            val allMedia = (entry.mediaUris + listOfNotNull(entry.photoUri).filter { it !in entry.mediaUris }).distinct()
            if (allMedia.isNotEmpty()) {
                val mediaHeight = 720f
                val mediaRect = RectF(contentLeft, currentY, contentRight, currentY + mediaHeight)
                val bmp = loadMediaBitmap(context, allMedia[0], contentWidth, mediaHeight.toInt())
                if (bmp != null) {
                    val overlay = if (allMedia.size > 1) "+${allMedia.size - 1}" else null
                    drawFitBitmap(canvas, bmp, mediaRect, 32f, allMedia[0], theme.borderColor, overlay)
                    currentY += mediaHeight + 40f
                }
            }

            // Entry Content
            val contentTextSize = when {
                entry.content.length < 120 -> 44f
                entry.content.length < 300 -> 36f
                else -> 30f
            }
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textColor
                textSize = contentTextSize
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            }
            val textLayout = StaticLayout.Builder.obtain(entry.content, 0, entry.content.length, textPaint, contentWidth)
                .setLineSpacing(12f, 1.3f)
                .build()

            canvas.save()
            canvas.translate(contentLeft, currentY)
            textLayout.draw(canvas)
            canvas.restore()
            currentY += textLayout.height + 40f

            // Tags
            if (entry.tags.isNotEmpty()) {
                val tagStr = entry.tags.joinToString("  ") { "#$it" }
                val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.accentColor
                    textSize = 28f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(tagStr, contentLeft, currentY + 28f, tagPaint)
            }

            // Watermark footer pinned to bottom
            val footerY = cardRect.bottom - 48f
            canvas.drawLine(contentLeft, footerY - 40f, contentRight, footerY - 40f, divPaint)

            val footerBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.subtitleColor
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("One Line A Day", contentLeft, footerY, footerBrandPaint)

            val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.watermarkColor
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("Daily Journal & Memories", contentRight, footerY, footerRightPaint)

        } else if (format == StoryFormat.HORIZONTAL_16_9) {
            val allMedia = (entry.mediaUris + listOfNotNull(entry.photoUri).filter { it !in entry.mediaUris }).distinct()
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.borderColor
                strokeWidth = 2f
            }

            if (allMedia.isNotEmpty()) {
                // Two-column layout: Left = Text/Metadata, Right = Image
                val colGap = 48f
                val leftWidth = (cardRect.width() - innerPadding * 2 - colGap) * 0.58f
                val rightWidth = (cardRect.width() - innerPadding * 2 - colGap) * 0.42f
                val leftRight = contentLeft + leftWidth
                val rightLeft = leftRight + colGap

                // Badge
                if (yearOffset != null || isLeapDayObservation) {
                    val badgeText = when {
                        yearOffset != null && isLeapDayObservation -> "✨ $yearOffset YEARS AGO (FEB 29 OBSERVED)"
                        yearOffset != null && yearOffset == 1 -> "1 YEAR AGO TODAY"
                        yearOffset != null -> "$yearOffset YEARS AGO"
                        else -> "✨ LEAP DAY MEMORY"
                    }
                    val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = theme.accentColor
                        textSize = 26f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText(badgeText, contentLeft, currentY + 24f, badgeTextPaint)
                    currentY += 44f
                }

                // Date
                val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.textColor
                    textSize = 44f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                }
                val dateStr = entry.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
                canvas.drawText(dateStr, contentLeft, currentY + 40f, datePaint)
                currentY += 60f

                // Mood
                val moodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.subtitleColor
                    textSize = 30f
                }
                canvas.drawText("${entry.mood.emoji} ${entry.mood.label}", contentLeft, currentY + 28f, moodPaint)
                currentY += 48f

                canvas.drawLine(contentLeft, currentY, leftRight, currentY, divPaint)
                currentY += 32f

                // Text
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.textColor
                    textSize = 34f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                }
                val textLayout = StaticLayout.Builder.obtain(entry.content, 0, entry.content.length, textPaint, leftWidth.toInt())
                    .setLineSpacing(10f, 1.25f)
                    .build()

                canvas.save()
                canvas.translate(contentLeft, currentY)
                textLayout.draw(canvas)
                canvas.restore()

                // Right column media
                val mediaTop = cardRect.top + innerPadding
                val mediaBottom = cardRect.bottom - innerPadding - 80f
                val mediaRect = RectF(rightLeft, mediaTop, rightLeft + rightWidth, mediaBottom)
                val bmp = loadMediaBitmap(context, allMedia[0], rightWidth.toInt(), (mediaBottom - mediaTop).toInt())
                if (bmp != null) {
                    val overlay = if (allMedia.size > 1) "+${allMedia.size - 1}" else null
                    drawFitBitmap(canvas, bmp, mediaRect, 32f, allMedia[0], theme.borderColor, overlay)
                }

                // Footer
                val footerY = cardRect.bottom - 48f
                val footerBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.subtitleColor
                    textSize = 28f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("One Line A Day", contentLeft, footerY, footerBrandPaint)
                val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.watermarkColor
                    textSize = 24f
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("Daily Journal & Memories", contentRight, footerY, footerRightPaint)

            } else {
                // Centered single card for text
                if (yearOffset != null || isLeapDayObservation) {
                    val badgeText = when {
                        yearOffset != null && isLeapDayObservation -> "✨ $yearOffset YEARS AGO (FEB 29 OBSERVED)"
                        yearOffset != null && yearOffset == 1 -> "1 YEAR AGO TODAY"
                        yearOffset != null -> "$yearOffset YEARS AGO"
                        else -> "✨ LEAP DAY MEMORY"
                    }
                    val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = theme.accentColor
                        textSize = 28f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(badgeText, cardRect.centerX(), currentY + 28f, badgeTextPaint)
                    currentY += 56f
                }

                val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.textColor
                    textSize = 52f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val dateStr = entry.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
                canvas.drawText(dateStr, cardRect.centerX(), currentY + 48f, datePaint)
                currentY += 70f

                val moodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.subtitleColor
                    textSize = 34f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("${entry.mood.emoji} ${entry.mood.label}", cardRect.centerX(), currentY + 30f, moodPaint)
                currentY += 60f

                canvas.drawLine(contentLeft + 200f, currentY, contentRight - 200f, currentY, divPaint)
                currentY += 60f

                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.textColor
                    textSize = 42f
                    typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
                val textLayout = StaticLayout.Builder.obtain(entry.content, 0, entry.content.length, textPaint, (contentWidth * 0.85f).toInt())
                    .setLineSpacing(14f, 1.35f)
                    .build()

                canvas.save()
                canvas.translate(cardRect.centerX() - (contentWidth * 0.85f) / 2f, currentY)
                textLayout.draw(canvas)
                canvas.restore()

                val footerY = cardRect.bottom - 48f
                val footerBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.subtitleColor
                    textSize = 28f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("One Line A Day", contentLeft, footerY, footerBrandPaint)
                val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.watermarkColor
                    textSize = 24f
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText("Daily Journal & Memories", contentRight, footerY, footerRightPaint)
            }
        }

        bitmap
    }

    /**
     * Generates a multi-year comparison flashback story card displaying 1 to 5 years of memories
     * on the same calendar day on a single card.
     */
    suspend fun generateMultiYearFlashbackStoryBitmap(
        context: Context,
        flashbacks: List<com.onelineaday.dailydiary.memory.FlashbackMemory>,
        currentDate: java.time.LocalDate,
        theme: ShareTheme = ShareTheme.GOLDEN,
        format: StoryFormat = StoryFormat.VERTICAL_9_16
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = format.width
        val height = format.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient background
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                theme.bgStartColor, theme.bgEndColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Card Container
        val outerPadding = 48f
        val cardRect = RectF(outerPadding, outerPadding, width - outerPadding, height - outerPadding)
        val cardCorner = 40f

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.cardBgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, cardPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.borderColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, cardCorner, cardCorner, borderPaint)

        val innerPadding = 56f
        val contentLeft = cardRect.left + innerPadding
        val contentRight = cardRect.right - innerPadding
        val contentWidth = (contentRight - contentLeft).toInt()
        var currentY = cardRect.top + innerPadding

        // Header
        val headerSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentColor
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("ON THIS DAY ACROSS THE YEARS", contentLeft, currentY + 26f, headerSubtitlePaint)
        currentY += 44f

        val headerDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textColor
            textSize = 50f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(currentDate.format(DateTimeFormatter.ofPattern("MMMM d")), contentLeft, currentY + 46f, headerDatePaint)
        currentY += 70f

        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.borderColor
            strokeWidth = 2f
        }
        canvas.drawLine(contentLeft, currentY, contentRight, currentY, divPaint)
        currentY += 40f

        // Render each memory row
        val sortedMemories = flashbacks.sortedByDescending { it.entry.date }.take(5)
        val availableHeight = (cardRect.bottom - 100f) - currentY
        val rowHeight = (availableHeight / maxOf(1, sortedMemories.size)).coerceAtMost(320f)

        for (memory in sortedMemories) {
            val rowTop = currentY
            val yearStr = "${memory.entry.date.year} (${memory.formattedTimeAgo})"
            val yearBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.accentColor
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(yearStr, contentLeft, rowTop + 30f, yearBadgePaint)

            // Leap day observation label
            if (memory.isLeapDayObservation) {
                val leapTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#9333EA")
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("✨ Feb 29 Memory", contentLeft + 360f, rowTop + 30f, leapTextPaint)
            }

            // Mood
            val moodTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.subtitleColor
                textSize = 28f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("${memory.entry.mood.emoji} ${memory.entry.mood.label}", contentRight, rowTop + 30f, moodTextPaint)

            // Content excerpt
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textColor
                textSize = 30f
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            }
            val textWidth = if (memory.hasMedia) contentWidth - 140 else contentWidth
            val excerpt = if (memory.entry.content.length > 180) memory.entry.content.take(177) + "..." else memory.entry.content
            val textLayout = StaticLayout.Builder.obtain(excerpt, 0, excerpt.length, textPaint, textWidth)
                .setLineSpacing(8f, 1.2f)
                .setMaxLines(3)
                .build()

            canvas.save()
            canvas.translate(contentLeft, rowTop + 48f)
            textLayout.draw(canvas)
            canvas.restore()

            // Thumbnail if media present
            if (memory.hasMedia) {
                val thumbSize = 110f
                val thumbRect = RectF(contentRight - thumbSize, rowTop + 48f, contentRight, rowTop + 48f + thumbSize)
                val bmp = loadMediaBitmap(context, memory.allMediaUris[0], thumbSize.toInt(), thumbSize.toInt())
                if (bmp != null) {
                    drawFitBitmap(canvas, bmp, thumbRect, 16f, memory.allMediaUris[0], theme.borderColor, null)
                }
            }

            currentY += rowHeight
            if (memory != sortedMemories.last()) {
                val subDivPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.borderColor
                    alpha = 100
                    strokeWidth = 1f
                }
                canvas.drawLine(contentLeft, currentY - 12f, contentRight, currentY - 12f, subDivPaint)
            }
        }

        // Footer
        val footerY = cardRect.bottom - 48f
        canvas.drawLine(contentLeft, footerY - 40f, contentRight, footerY - 40f, divPaint)

        val footerBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.subtitleColor
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("One Line A Day", contentLeft, footerY, footerBrandPaint)

        val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.watermarkColor
            textSize = 24f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Daily Journal & Memories", contentRight, footerY, footerRightPaint)

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
        borderColor: Int,
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
            strokeWidth = 2.5f
            color = borderColor
        }
        canvas.drawRoundRect(imageRect, cornerRadius, cornerRadius, borderPaint)

        // If it's a video, draw a play icon in center
        val isVideo = isVideoMedia(uriString)
        if (isVideo) {
            val playCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(170, 0, 0, 0)
            }
            val centerX = imageRect.centerX()
            val centerY = imageRect.centerY()
            val circleRadius = 50f
            canvas.drawCircle(centerX, centerY, circleRadius, playCirclePaint)

            val playTrianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val trianglePath = Path().apply {
                moveTo(centerX - 15f, centerY - 22f)
                lineTo(centerX + 25f, centerY)
                lineTo(centerX - 15f, centerY + 22f)
                close()
            }
            canvas.drawPath(trianglePath, playTrianglePaint)
        }

        // Draw +N overlay if specified
        if (overlayText != null) {
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(185, 0, 0, 0)
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
