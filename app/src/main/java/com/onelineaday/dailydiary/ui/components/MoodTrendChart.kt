package com.onelineaday.dailydiary.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import java.time.LocalDate

@Composable
fun MoodTrendChart(
    entries: List<JournalEntry>,
    modifier: Modifier = Modifier
) {
    // Get last 30 days
    val today = LocalDate.now()
    val thirtyDaysAgo = today.minusDays(30)
    
    val recentEntries = entries
        .filter { !it.date.isBefore(thirtyDaysAgo) }
        .sortedBy { it.date }
        
    if (recentEntries.isEmpty()) {
        Text("Not enough data to show mood trend.", modifier = modifier.padding(16.dp))
        return
    }

    val moodValues = recentEntries.map {
        when (it.mood) {
            Mood.ANGRY, Mood.SAD, Mood.TIRED, Mood.ANXIOUS -> 1f
            Mood.NEUTRAL -> 2f
            Mood.HAPPY, Mood.GRATEFUL -> 3f
            Mood.MOTIVATED -> 4f
            Mood.EXCITED, Mood.LOVED -> 5f
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "30-Day Mood Trend",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val width = size.width
            val height = size.height
            
            val maxMood = 5f
            val minMood = 1f
            
            val dx = width / maxOf(1, moodValues.size - 1).toFloat()
            val dy = height / (maxMood - minMood)
            
            val path = Path()
            
            moodValues.forEachIndexed { index, value ->
                val x = index * dx
                val y = height - ((value - minMood) * dy)
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
                
                drawCircle(
                    color = primaryColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }
            
            drawPath(
                path = path,
                color = primaryColor.copy(alpha = 0.5f),
                style = Stroke(width = 3.dp.toPx())
            )
            
            // Draw baseline guides
            (1..5).forEach {
                val y = height - ((it - minMood) * dy)
                drawLine(
                    color = surfaceColor.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Older", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Recent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
