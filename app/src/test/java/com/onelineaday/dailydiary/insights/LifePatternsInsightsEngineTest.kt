package com.onelineaday.dailydiary.insights

import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class LifePatternsInsightsEngineTest {

    private val today = LocalDate.of(2026, 10, 27) // Tuesday

    @Test
    fun testEmptyEntriesReport() {
        val report = LifePatternsInsightsEngine.analyze(emptyList(), today)
        assertEquals(0, report.totalAnalyzedEntries)
        assertNull(report.peakMoodDay)
        assertEquals(0.0, report.thirtyDayConsistencyRate, 0.001)
        assertTrue(report.factualInsights.isNotEmpty())
    }

    @Test
    fun testDayOfWeekGroupingAndPeakMoodDay() {
        // Create 3 Saturday entries (all HAPPY) and 2 Monday entries (both SAD)
        val entries = listOf(
            JournalEntry(date = LocalDate.of(2026, 10, 10), content = "Great Saturday morning walk", mood = Mood.HAPPY),
            JournalEntry(date = LocalDate.of(2026, 10, 17), content = "Wonderful picnic with friends", mood = Mood.EXCITED),
            JournalEntry(date = LocalDate.of(2026, 10, 24), content = "Peaceful evening reading", mood = Mood.GRATEFUL),
            JournalEntry(date = LocalDate.of(2026, 10, 12), content = "Tough Monday meeting", mood = Mood.SAD),
            JournalEntry(date = LocalDate.of(2026, 10, 19), content = "Exhausted after long workday", mood = Mood.TIRED)
        )

        val report = LifePatternsInsightsEngine.analyze(entries, today)

        assertEquals(5, report.totalAnalyzedEntries)
        assertEquals(DayOfWeek.SATURDAY, report.peakMoodDay)

        val saturdayPattern = report.dayPatterns[DayOfWeek.SATURDAY]!!
        assertEquals(3, saturdayPattern.entryCount)
        assertEquals(3, saturdayPattern.positiveCount)
        assertEquals(100.0, saturdayPattern.positivePercentage, 0.001)

        val mondayPattern = report.dayPatterns[DayOfWeek.MONDAY]!!
        assertEquals(2, mondayPattern.entryCount)
        assertEquals(0, mondayPattern.positiveCount)
        assertEquals(0.0, mondayPattern.positivePercentage, 0.001)
    }

    @Test
    fun testMostReflectiveDayIdentifiedByWordCount() {
        // Tuesdays have long entries, Wednesdays have short entries
        val entries = listOf(
            JournalEntry(date = LocalDate.of(2026, 10, 13), content = "This is a remarkably detailed and expressive entry contemplating the passage of time.", mood = Mood.HAPPY),
            JournalEntry(date = LocalDate.of(2026, 10, 20), content = "Another thoughtful journal entry reflecting on personal growth and deep emotional awareness.", mood = Mood.HAPPY),
            JournalEntry(date = LocalDate.of(2026, 10, 14), content = "Good day.", mood = Mood.NEUTRAL),
            JournalEntry(date = LocalDate.of(2026, 10, 21), content = "Busy day.", mood = Mood.NEUTRAL)
        )

        val report = LifePatternsInsightsEngine.analyze(entries, today)
        assertEquals(DayOfWeek.TUESDAY, report.mostReflectiveDay)
    }

    @Test
    fun testTagMoodCorrelationAnalysis() {
        val entries = listOf(
            JournalEntry(date = LocalDate.of(2026, 10, 1), content = "Hiking", mood = Mood.GRATEFUL, tags = listOf("nature", "fitness")),
            JournalEntry(date = LocalDate.of(2026, 10, 2), content = "Walk in forest", mood = Mood.HAPPY, tags = listOf("nature")),
            JournalEntry(date = LocalDate.of(2026, 10, 3), content = "Camping", mood = Mood.EXCITED, tags = listOf("nature"))
        )

        val report = LifePatternsInsightsEngine.analyze(entries, today)
        val natureTag = report.tagAssociations.firstOrNull { it.tag == "nature" }

        assertNotNull(natureTag)
        assertEquals(3, natureTag!!.usageCount)
        assertEquals(100.0, natureTag.positivePercentage, 0.001)
    }
}

