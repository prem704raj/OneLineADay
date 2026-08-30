package com.onelineaday.dailydiary.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.onelineaday.dailydiary.data.JournalEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.VerticalAlignment
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.layout.properties.BorderRadius
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.layout.element.Image
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.events.IEventHandler
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.events.Event

object PdfExportHelper {

    private class FooterEventHandler(
        private val mutedColor: DeviceRgb, 
        private val normalFont: com.itextpdf.kernel.font.PdfFont
    ) : IEventHandler {
        override fun handleEvent(event: Event?) {
            val docEvent = event as PdfDocumentEvent
            val pdfDoc = docEvent.document
            val page = docEvent.page
            val pageNum = pdfDoc.getPageNumber(page)
            
            // Don't add footer to cover page
            if (pageNum == 1) return
            
            val canvas = PdfCanvas(page.newContentStreamBefore(), page.resources, pdfDoc)
            val area = page.pageSize
            
            com.itextpdf.layout.Canvas(canvas, area)
                .add(Paragraph("Page $pageNum")
                    .setFont(normalFont)
                    .setFontSize(9f)
                    .setFontColor(mutedColor)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFixedPosition(area.left, area.bottom + 20f, area.width))
        }
    }

    suspend fun generateJournalPdf(
        context: Context, 
        entries: List<JournalEntry>,
        currentStreak: Int,
        longestStreak: Int,
        totalEntries: Int
    ): Uri? = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext null

        val file = File(context.cacheDir, "OneLineADay_Journal.pdf")
        
        try {
            val writer = PdfWriter(file)
            val pdf = PdfDocument(writer)
            val document = Document(pdf)
            document.setMargins(50f, 50f, 60f, 50f)
            
            // Ultra-Premium Color Palette
            val primaryDark = DeviceRgb(88, 28, 135)    // Deep Purple
            val primary = DeviceRgb(147, 51, 234)       // Vibrant Purple
            val bgAccent = DeviceRgb(250, 245, 255)     // Soft Lavender
            val textDark = DeviceRgb(30, 41, 59)        // Slate 800
            val textMuted = DeviceRgb(100, 116, 139)    // Slate 500
            val borderLight = DeviceRgb(226, 232, 240)  // Slate 200
            
            // Fonts
            val titleFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
            val normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)
            val italicFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE)

            // Add Footer Event
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE, FooterEventHandler(textMuted, normalFont))

            // ==========================================
            // 1. DEDICATED COVER PAGE
            // ==========================================
            val coverTable = Table(1)
                .useAllAvailableWidth()
                .setHeight(600f) // Center content vertically
            
            val coverCell = Cell()
                .setBorder(Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER)
            
            coverCell.add(Paragraph("📝")
                .setFontSize(40f)
                .setMarginBottom(20f))
                
            coverCell.add(Paragraph("ONE LINE A DAY")
                .setFont(titleFont)
                .setFontSize(36f)
                .setFontColor(primaryDark))
                
            coverCell.add(Paragraph("Your Personal Journal")
                .setFont(italicFont)
                .setFontSize(16f)
                .setFontColor(textMuted)
                .setMarginTop(10f))
            
            // Date Range
            val sortedEntries = entries.sortedByDescending { it.date }
            if (sortedEntries.isNotEmpty()) {
                val firstDate = sortedEntries.last().date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
                val lastDate = sortedEntries.first().date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
                
                coverCell.add(Paragraph("$firstDate — $lastDate")
                    .setFont(normalFont)
                    .setFontSize(12f)
                    .setFontColor(primary)
                    .setMarginTop(40f))
            }

            // Stats row on cover
            val coverStatsTable = Table(3).useAllAvailableWidth().setMarginTop(60f)
            listOf(
                Pair("Total Entries", "$totalEntries"),
                Pair("Current Streak", "$currentStreak Days"),
                Pair("Best Streak", "$longestStreak Days")
            ).forEach { (label, value) ->
                coverStatsTable.addCell(Cell()
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER)
                    .add(Paragraph(value).setFont(titleFont).setFontSize(18f).setFontColor(primaryDark))
                    .add(Paragraph(label).setFont(normalFont).setFontSize(10f).setFontColor(textMuted)))
            }
            coverCell.add(coverStatsTable)
            
            coverTable.addCell(coverCell)
            document.add(coverTable)
            
            // Page Break after cover
            document.add(com.itextpdf.layout.element.AreaBreak(com.itextpdf.layout.properties.AreaBreakType.NEXT_PAGE))

            // ==========================================
            // 2. JOURNAL ENTRIES (Grouped by Month)
            // ==========================================
            val dateTimeFormatter = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            
            val entriesByMonth = sortedEntries.groupBy { YearMonth.from(it.date) }
            
            entriesByMonth.forEach { (yearMonth, monthEntries) ->
                // Chapter Header (Month & Year)
                val monthTitle = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                
                document.add(Paragraph(monthTitle)
                    .setFont(titleFont)
                    .setFontSize(22f)
                    .setFontColor(primaryDark)
                    .setMarginTop(20f)
                    .setMarginBottom(5f))
                    
                // Divider line
                val dividerTable = Table(1).useAllAvailableWidth().setMarginBottom(20f)
                dividerTable.addCell(Cell().setBorderTop(SolidBorder(primary, 2f)).setBorderBottom(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER))
                document.add(dividerTable)

                monthEntries.forEach { entry ->
                    // Card Container
                    val cardTable = Table(1)
                        .useAllAvailableWidth()
                        .setMarginBottom(20f)
                        .setBackgroundColor(bgAccent)
                        .setBorderRadius(BorderRadius(12f))
                    
                    val cardCell = Cell()
                        .setBorder(Border.NO_BORDER)
                        .setPadding(20f)
                    
                    // Header row: Date and Time
                    val dateStr = entry.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
                    val timeStr = dateTimeFormatter.format(java.util.Date(entry.updatedAt))
                    
                    val headerLayout = Table(floatArrayOf(1f, 1f)).useAllAvailableWidth()
                    
                    // Left: Date
                    headerLayout.addCell(Cell().setBorder(Border.NO_BORDER)
                        .setTextAlignment(TextAlignment.LEFT)
                        .add(Paragraph(dateStr)
                            .setFont(titleFont)
                            .setFontSize(14f)
                            .setFontColor(primaryDark)))
                            
                    // Right: Time & Mood
                    headerLayout.addCell(Cell().setBorder(Border.NO_BORDER)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .add(Paragraph("${entry.mood.emoji} ${entry.mood.label} • 🕐 $timeStr")
                            .setFont(normalFont)
                            .setFontSize(10f)
                            .setFontColor(textMuted)))
                            
                    cardCell.add(headerLayout)
                    
                    // Subtle separator inside card
                    cardCell.add(Paragraph("").setMarginTop(8f).setMarginBottom(8f)
                        .setBorderBottom(SolidBorder(borderLight, 1f)))
                    
                    // Content
                    cardCell.add(Paragraph(entry.content)
                        .setFont(normalFont)
                        .setFontSize(12f)
                        .setFontColor(textDark)
                        .setMarginBottom(10f))
                    
                    // Photo Attachment (if any)
                    entry.photoUri?.let { photoPath ->
                        try {
                            val photoFile = File(photoPath)
                            if (photoFile.exists()) {
                                val imageData = ImageDataFactory.create(photoPath)
                                val image = Image(imageData)
                                    .setMaxWidth(300f)
                                    .setMaxHeight(250f)
                                    .setMarginTop(10f)
                                    .setBorderRadius(BorderRadius(8f))
                                
                                val photoBox = Table(1)
                                    .setMarginTop(15f)
                                    .setBackgroundColor(ColorConstants.WHITE)
                                    .setBorderRadius(BorderRadius(8f))
                                    .setPadding(10f)
                                    
                                val photoCell = Cell().setBorder(Border.NO_BORDER)
                                photoCell.add(Paragraph("📷 Attached Memory")
                                    .setFont(italicFont)
                                    .setFontSize(9f)
                                    .setFontColor(primary))
                                photoCell.add(image)
                                
                                photoBox.addCell(photoCell)
                                cardCell.add(photoBox)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    cardTable.addCell(cardCell)
                    document.add(cardTable)
                }
            }
            
            document.close()
            
            return@withContext FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
