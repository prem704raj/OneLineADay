package com.onelineaday.dailydiary.mosaic

import com.onelineaday.dailydiary.data.Mood
import java.time.LocalDate
import java.time.YearMonth

sealed class PixelCell {
    data class Logged(val date: LocalDate, val mood: Mood) : PixelCell()
    data class Unlogged(val date: LocalDate) : PixelCell()
    object OutOfRange : PixelCell()
}

data class MoodStats(
    val totalLogged: Int,
    val moodCounts: Map<Mood, Int>,
    val moodPercentages: Map<Mood, Double>,
    val dominantMood: Mood?,
    val stabilityScore: Double // 0.0 to 1.0 (proportion of smooth transitions between consecutive logged days)
)

object MoodMosaicEngine {

    fun moodValue(mood: Mood): Int = when (mood) {
        Mood.EXCITED, Mood.LOVED -> 5
        Mood.HAPPY, Mood.GRATEFUL, Mood.MOTIVATED -> 4
        Mood.NEUTRAL, Mood.TIRED -> 3
        Mood.ANXIOUS -> 2
        Mood.SAD, Mood.ANGRY -> 1
    }

    /**
     * Builds a 12x31 grid matrix for the specified year.
     * Dimensions: matrix[monthIndex 0..11][dayIndex 0..30]
     */
    fun buildMosaic(year: Int, entries: Map<LocalDate, Mood>): Array<Array<PixelCell>> {
        val grid = Array(12) { monthIdx ->
            val month = monthIdx + 1
            val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
            Array<PixelCell>(31) { dayIdx ->
                val day = dayIdx + 1
                if (day <= daysInMonth) {
                    val date = LocalDate.of(year, month, day)
                    val mood = entries[date]
                    if (mood != null) PixelCell.Logged(date, mood) else PixelCell.Unlogged(date)
                } else {
                    PixelCell.OutOfRange
                }
            }
        }
        return grid
    }

    /**
     * Calculates mood statistics across a map of entries.
     */
    fun calculateStats(entries: Map<LocalDate, Mood>): MoodStats {
        val total = entries.size
        if (total == 0) {
            return MoodStats(
                totalLogged = 0,
                moodCounts = Mood.values().associateWith { 0 },
                moodPercentages = Mood.values().associateWith { 0.0 },
                dominantMood = null,
                stabilityScore = 1.0
            )
        }

        val counts = Mood.values().associateWith { 0 }.toMutableMap()
        entries.values.forEach { mood ->
            counts[mood] = (counts[mood] ?: 0) + 1
        }

        val percentages = counts.mapValues { (_, count) ->
            (count.toDouble() / total.toDouble()) * 100.0
        }

        val dominant = counts.maxByOrNull { it.value }?.key

        // Compute stability: evaluate consecutive calendar days
        val sortedDates = entries.keys.sorted()
        var consecutiveTransitions = 0
        var smoothTransitions = 0

        for (i in 0 until sortedDates.size - 1) {
            val d1 = sortedDates[i]
            val d2 = sortedDates[i + 1]
            if (d2 == d1.plusDays(1)) {
                consecutiveTransitions++
                val m1 = moodValue(entries.getValue(d1))
                val m2 = moodValue(entries.getValue(d2))
                if (Math.abs(m1 - m2) <= 1) {
                    smoothTransitions++
                }
            }
        }

        val stability = if (consecutiveTransitions == 0) 1.0 else smoothTransitions.toDouble() / consecutiveTransitions.toDouble()

        return MoodStats(
            totalLogged = total,
            moodCounts = counts,
            moodPercentages = percentages,
            dominantMood = dominant,
            stabilityScore = stability
        )
    }
}

