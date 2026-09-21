package com.onelineaday.dailydiary.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.onelineaday.dailydiary.data.JournalEntry
import kotlinx.coroutines.Dispatchers
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
    
    fun generateEntryBitmap(context: Context, entry: JournalEntry): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background
        canvas.drawColor(Color.WHITE)
        
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 60f
            isAntiAlias = true
        }
        
        val dateText = entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        canvas.drawText(dateText, 100f, 200f, paint)
        
        paint.textSize = 80f
        canvas.drawText(entry.mood.emoji, 100f, 320f, paint)
        
        paint.textSize = 50f
        
        // Draw content with basic word wrap
        val textPaint = TextPaint(paint)
        val textLayout = StaticLayout.Builder.obtain(
            entry.content, 0, entry.content.length, textPaint, width - 200
        ).build()
        
        canvas.save()
        canvas.translate(100f, 450f)
        textLayout.draw(canvas)
        canvas.restore()
        
        return bitmap
    }
}
