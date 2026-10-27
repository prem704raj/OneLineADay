package com.onelineaday.dailydiary.insights

import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class DayPattern(
    val dayOfWeek: DayOfWeek,
    val entryCount: Int,
    val positiveCount: Int,
    val positivePercentage: Double,
    val averageWordCount: Int,
    val dominantMood: Mood?
)

data class TagMoodAssociation(
    val tag: String,
    val usageCount: Int,
    val topMood: Mood,
    val positivePercentage: Double
)

data class LifePatternsReport(
    val totalAnalyzedEntries: Int,
    val dayPatterns: Map<DayOfWeek, DayPattern>,
    val peakMoodDay: DayOfWeek?,
    val mostReflectiveDay: DayOfWeek?,
    val tagAssociations: List<TagMoodAssociation>,
    val thirtyDayConsistencyRate: Double,
    val ninetyDayConsistencyRate: Double,
    val factualInsights: List<String>
)

object LifePatternsInsightsEngine {

    private val POSITIVE_MOODS = setOf(
        Mood.EXCITED,
        Mood.HAPPY,
        Mood.GRATEFUL,
        Mood.LOVED,
        Mood.MOTIVATED
    )

    fun analyze(entries: List<JournalEntry>, today: LocalDate = LocalDate.now()): LifePatternsReport {
        if (entries.isEmpty()) {
            return LifePatternsReport(
                totalAnalyzedEntries = 0,
                dayPatterns = DayOfWeek.values().associateWith { day ->
                    DayPattern(day, 0, 0, 0.0, 0, null)
                },
                peakMoodDay = null,
                mostReflectiveDay = null,
                tagAssociations = emptyList(),
                thirtyDayConsistencyRate = 0.0,
                ninetyDayConsistencyRate = 0.0,
                factualInsights = listOf("Start writing daily lines to discover your life patterns and rhythm.")
            )
        }

        // Group entries by DayOfWeek
        val byDayOfWeek = entries.groupBy { it.date.dayOfWeek }
        val dayPatterns = DayOfWeek.values().associateWith { day ->
            val dayEntries = byDayOfWeek[day] ?: emptyList()
            val total = dayEntries.size
            if (total == 0) {
                DayPattern(day, 0, 0, 0.0, 0, null)
            } else {
                val posCount = dayEntries.count { it.mood in POSITIVE_MOODS }
                val posPct = (posCount.toDouble() / total.toDouble()) * 100.0
                val totalWords = dayEntries.sumOf { entry ->
                    entry.content.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                }
                val avgWords = totalWords / total
                val domMood = dayEntries.groupBy { it.mood }.maxByOrNull { it.value.size }?.key
                DayPattern(day, total, posCount, posPct, avgWords, domMood)
            }
        }

        // Find peak mood day (at least 2 entries required for meaningful trend)
        val validDaysWithEntries = dayPatterns.values.filter { it.entryCount >= 2 }
        val peakMoodDay = validDaysWithEntries.maxByOrNull { it.positivePercentage }?.dayOfWeek
            ?: dayPatterns.values.maxByOrNull { it.entryCount }?.dayOfWeek

        val mostReflectiveDay = validDaysWithEntries.maxByOrNull { it.averageWordCount }?.dayOfWeek

        // Tag associations
        val tagEntries = mutableMapOf<String, MutableList<JournalEntry>>()
        for (entry in entries) {
            for (tag in entry.tags) {
                val cleanTag = tag.trim().lowercase(Locale.ROOT)
                if (cleanTag.isNotBlank()) {
                    tagEntries.getOrPut(cleanTag) { mutableListOf() }.add(entry)
                }
            }
        }

        val tagAssociations = tagEntries.mapNotNull { (tag, tEntries) ->
            if (tEntries.size < 2) null
            else {
                val posCount = tEntries.count { it.mood in POSITIVE_MOODS }
                val posPct = (posCount.toDouble() / tEntries.size.toDouble()) * 100.0
                val topMood = tEntries.groupBy { it.mood }.maxByOrNull { it.value.size }?.key ?: Mood.NEUTRAL
                TagMoodAssociation(tag, tEntries.size, topMood, posPct)
            }
        }.sortedByDescending { it.usageCount }

        // Consistency rates
        val entryDates = entries.map { it.date }.toSet()
        val daysIn30 = (0 until 30).count { today.minusDays(it.toLong()) in entryDates }
        val daysIn90 = (0 until 90).count { today.minusDays(it.toLong()) in entryDates }

        val thirtyDayRate = (daysIn30.toDouble() / 30.0) * 100.0
        val ninetyDayRate = (daysIn90.toDouble() / 90.0) * 100.0

        // Factual statements
        val insights = mutableListOf<String>()

        if (peakMoodDay != null) {
            val pattern = dayPatterns[peakMoodDay]
            if (pattern != null && pattern.entryCount > 0) {
                val dayName = peakMoodDay.getDisplayName(TextStyle.FULL, Locale.getDefault())
                val pct = pattern.positivePercentage.toInt()
                insights.add("Your highest mood consistency occurs on ${dayName}s ($pct% positive mood entries).")
            }
        }

        if (mostReflectiveDay != null && mostReflectiveDay != peakMoodDay) {
            val dayName = mostReflectiveDay.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val avgW = dayPatterns[mostReflectiveDay]?.averageWordCount ?: 0
            insights.add("${dayName}s are your most reflective days, averaging $avgW words per entry.")
        }

        val topPositiveTag = tagAssociations.maxByOrNull { it.positivePercentage }
        if (topPositiveTag != null && topPositiveTag.positivePercentage >= 70.0) {
            insights.add("Writing about '${topPositiveTag.tag}' strongly correlates with feeling ${topPositiveTag.topMood.label.lowercase(Locale.getDefault())}.")
        }

        insights.add("You completed $daysIn30 of the last 30 days (${thirtyDayRate.toInt()}% consistency).")

        return LifePatternsReport(
            totalAnalyzedEntries = entries.size,
            dayPatterns = dayPatterns,
            peakMoodDay = peakMoodDay,
            mostReflectiveDay = mostReflectiveDay,
            tagAssociations = tagAssociations,
            thirtyDayConsistencyRate = thirtyDayRate,
            ninetyDayConsistencyRate = ninetyDayRate,
            factualInsights = insights
        )
    }
}

