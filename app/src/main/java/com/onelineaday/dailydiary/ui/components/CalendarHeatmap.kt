package com.onelineaday.dailydiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarHeatmap(
    entriesDates: List<LocalDate>,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val startDate = remember(today) { today.minusWeeks(15).with(DayOfWeek.MONDAY) }
    
    val dateMap = remember(entriesDates) {
        entriesDates.groupBy { it }.mapValues { it.value.size }
    }
    
    val totalDays = ChronoUnit.DAYS.between(startDate, today).toInt() + 1
    val dates = remember(startDate, totalDays) {
        (0 until totalDays).map { startDate.plusDays(it.toLong()) }
    }
    
    val weeks = dates.chunked(7)
    
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val level1Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val level2Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val level3Color = MaterialTheme.colorScheme.primary
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Contribution Graph",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Day Labels
            Column(
                modifier = Modifier.padding(end = 8.dp, top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val days = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
                // Just creating spacing to align with the grid
                for (i in 0..6) {
                    val day = DayOfWeek.MONDAY.plus(i.toLong())
                    if (day in days) {
                        Text(
                            text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(14.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
            
            // Heatmap Grid
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                reverseLayout = true // Show most recent on the right
            ) {
                items(weeks.reversed().size) { reversedIndex ->
                    val weekIndex = weeks.size - 1 - reversedIndex
                    val week = weeks[weekIndex]
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Month label placeholder (simplified)
                        val showMonth = week.firstOrNull()?.dayOfMonth ?: 100 <= 7
                        if (showMonth && week.isNotEmpty()) {
                            Text(
                                text = week.first().month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.height(16.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        // Days in week
                        for (i in 0..6) {
                            val dayIndex = i
                            if (dayIndex < week.size) {
                                val date = week[dayIndex]
                                val count = dateMap[date] ?: 0
                                
                                val boxColor = when {
                                    count == 0 -> emptyColor
                                    count == 1 -> level1Color
                                    count == 2 -> level2Color
                                    else -> level3Color
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(boxColor)
                                )
                            } else {
                                Spacer(modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
