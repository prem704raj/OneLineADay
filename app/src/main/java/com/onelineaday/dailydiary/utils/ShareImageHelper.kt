package com.onelineaday.dailydiary.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareImageHelper {
    
    fun shareBitmap(context: Context, bitmap: Bitmap) {
        try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_entry.png")
            val fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.close()
            
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            
            if (contentUri != null) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    type = "image/png"
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share your memory"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Failed to share image", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    fun generateEntryBitmap(context: Context, entry: com.onelineaday.dailydiary.data.JournalEntry): Bitmap {
        // Simple fallback: draw text on a canvas
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Background
        canvas.drawColor(android.graphics.Color.WHITE)
        
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 60f
            isAntiAlias = true
        }
        
        val dateText = entry.date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy"))
        canvas.drawText(dateText, 100f, 200f, paint)
        
        paint.textSize = 80f
        canvas.drawText(entry.mood.emoji, 100f, 320f, paint)
        
        paint.textSize = 50f
        
        // Draw content with basic word wrap
        val textPaint = android.text.TextPaint(paint)
        val textLayout = android.text.StaticLayout.Builder.obtain(
            entry.content, 0, entry.content.length, textPaint, width - 200
        ).build()
        
        canvas.save()
        canvas.translate(100f, 450f)
        textLayout.draw(canvas)
        canvas.restore()
        
        return bitmap
    }
}
