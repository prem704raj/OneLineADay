package com.onelineaday.dailydiary.video

import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import java.time.LocalDate
import java.time.YearMonth

data class ReelItem(
    val date: LocalDate,
    val photoUri: String,
    val mood: Mood,
    val lineSnippet: String,
    val durationMillis: Long = 1000L
)

object MonthlyReelEngine {

    /**
     * Extracts and organizes photos from a specified month into a 1-second-per-day reel timeline.
     */
    fun buildMonthlyReel(
        entries: List<JournalEntry>,
        yearMonth: YearMonth,
        secondsPerDay: Float = 1.0f
    ): List<ReelItem> {
        val duration = (secondsPerDay * 1000f).toLong().coerceAtLeast(400L)

        return entries
            .filter { it.date.year == yearMonth.year && it.date.month == yearMonth.month }
            .sortedBy { it.date }
            .mapNotNull { entry ->
                val primaryPhoto = entry.photoUri ?: entry.mediaUris.firstOrNull()
                if (primaryPhoto != null) {
                    val snippet = if (entry.content.length > 50) {
                        entry.content.take(47) + "…"
                    } else {
                        entry.content
                    }
                    ReelItem(
                        date = entry.date,
                        photoUri = primaryPhoto,
                        mood = entry.mood,
                        lineSnippet = snippet,
                        durationMillis = duration
                    )
                } else {
                    null
                }
            }
    }
}

