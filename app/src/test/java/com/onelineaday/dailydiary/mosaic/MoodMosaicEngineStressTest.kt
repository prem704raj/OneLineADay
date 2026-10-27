package com.onelineaday.dailydiary.mosaic

import com.onelineaday.dailydiary.data.Mood
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MoodMosaicEngineStressTest {

    @Test
    fun testMatrixDimensionsAllYears() {
        val testYears = listOf(1996, 2000, 2020, 2024, 2025, 2026, 2100, 2400)
        for (year in testYears) {
            val grid = MoodMosaicEngine.buildMosaic(year, emptyMap())
            assertEquals("Grid must have 12 months for year $year", 12, grid.size)
            for (m in 0 until 12) {
                assertEquals("Month $m must have 31 days for year $year", 31, grid[m].size)
            }
        }
    }

    @Test
    fun testFebruaryDaysInLeapAndNonLeapYears() {
        // 2024 is leap
        val leapGrid = MoodMosaicEngine.buildMosaic(2024, emptyMap())
        assertTrue("Feb 29 in 2024 must be Unlogged", leapGrid[1][28] is PixelCell.Unlogged)
        assertTrue("Feb 30 in 2024 must be OutOfRange", leapGrid[1][29] is PixelCell.OutOfRange)
        assertTrue("Feb 31 in 2024 must be OutOfRange", leapGrid[1][30] is PixelCell.OutOfRange)

        // 2025 is common
        val commonGrid = MoodMosaicEngine.buildMosaic(2025, emptyMap())
        assertTrue("Feb 28 in 2025 must be Unlogged", commonGrid[1][27] is PixelCell.Unlogged)
        assertTrue("Feb 29 in 2025 must be OutOfRange", commonGrid[1][28] is PixelCell.OutOfRange)
        assertTrue("Feb 30 in 2025 must be OutOfRange", commonGrid[1][29] is PixelCell.OutOfRange)
        assertTrue("Feb 31 in 2025 must be OutOfRange", commonGrid[1][30] is PixelCell.OutOfRange)

        // 2000 is leap century
        val leapCenturyGrid = MoodMosaicEngine.buildMosaic(2000, emptyMap())
        assertTrue("Feb 29 in 2000 must be Unlogged", leapCenturyGrid[1][28] is PixelCell.Unlogged)

        // 2100 is non-leap century
        val commonCenturyGrid = MoodMosaicEngine.buildMosaic(2100, emptyMap())
        assertTrue("Feb 29 in 2100 must be OutOfRange", commonCenturyGrid[1][28] is PixelCell.OutOfRange)
    }

    @Test
    fun test30DayMonthsOutOfRange() {
        // April (3), June (5), September (8), November (10) -> indices 3, 5, 8, 10
        val monthsWith30Days = listOf(3, 5, 8, 10)
        val grid = MoodMosaicEngine.buildMosaic(2026, emptyMap())

        for (m in monthsWith30Days) {
            assertTrue("Day 30 (index 29) must be valid Unlogged in month $m", grid[m][29] is PixelCell.Unlogged)
            assertTrue("Day 31 (index 30) must be OutOfRange in month $m", grid[m][30] is PixelCell.OutOfRange)
        }
    }

    @Test
    fun testLoggedEntriesPopulatedCorrectly() {
        val entries = mutableMapOf<LocalDate, Mood>()
        entries[LocalDate.of(2026, 1, 1)] = Mood.HAPPY
        entries[LocalDate.of(2026, 7, 4)] = Mood.EXCITED
        entries[LocalDate.of(2026, 12, 31)] = Mood.GRATEFUL

        val grid = MoodMosaicEngine.buildMosaic(2026, entries)

        val jan1 = grid[0][0]
        assertTrue(jan1 is PixelCell.Logged)
        assertEquals(Mood.HAPPY, (jan1 as PixelCell.Logged).mood)
        assertEquals(LocalDate.of(2026, 1, 1), jan1.date)

        val jul4 = grid[6][3]
        assertTrue(jul4 is PixelCell.Logged)
        assertEquals(Mood.EXCITED, (jul4 as PixelCell.Logged).mood)

        val dec31 = grid[11][30]
        assertTrue(dec31 is PixelCell.Logged)
        assertEquals(Mood.GRATEFUL, (dec31 as PixelCell.Logged).mood)

        // Unlogged day
        val jan2 = grid[0][1]
        assertTrue(jan2 is PixelCell.Unlogged)
    }

    @Test
    fun testMoodStatsEmptyEntries() {
        val stats = MoodMosaicEngine.calculateStats(emptyMap())
        assertEquals(0, stats.totalLogged)
        assertNull(stats.dominantMood)
        assertEquals(1.0, stats.stabilityScore, 0.001)
        Mood.values().forEach { mood ->
            assertEquals(0, stats.moodCounts[mood])
            assertEquals(0.0, stats.moodPercentages[mood]!!, 0.001)
        }
    }

    @Test
    fun testMoodStatsDominantAndPercentages() {
        val entries = mapOf(
            LocalDate.of(2026, 1, 1) to Mood.HAPPY,
            LocalDate.of(2026, 1, 2) to Mood.HAPPY,
            LocalDate.of(2026, 1, 3) to Mood.SAD,
            LocalDate.of(2026, 1, 4) to Mood.ANXIOUS
        )
        val stats = MoodMosaicEngine.calculateStats(entries)
        assertEquals(4, stats.totalLogged)
        assertEquals(Mood.HAPPY, stats.dominantMood)
        assertEquals(2, stats.moodCounts[Mood.HAPPY])
        assertEquals(50.0, stats.moodPercentages[Mood.HAPPY]!!, 0.001)
        assertEquals(25.0, stats.moodPercentages[Mood.SAD]!!, 0.001)
        assertEquals(25.0, stats.moodPercentages[Mood.ANXIOUS]!!, 0.001)
    }

    @Test
    fun testStabilityScoreTransitions() {
        // Consecutive days with difference <= 1
        // EXCITED (5) -> HAPPY (4) -> NEUTRAL (3) -> TIRED (3)
        val smooth = mapOf(
            LocalDate.of(2026, 2, 1) to Mood.EXCITED,
            LocalDate.of(2026, 2, 2) to Mood.HAPPY,
            LocalDate.of(2026, 2, 3) to Mood.NEUTRAL,
            LocalDate.of(2026, 2, 4) to Mood.TIRED
        )
        val smoothStats = MoodMosaicEngine.calculateStats(smooth)
        assertEquals(1.0, smoothStats.stabilityScore, 0.001)

        // All volatile: EXCITED (5) -> SAD (1) -> EXCITED (5)
        val volatile = mapOf(
            LocalDate.of(2026, 2, 1) to Mood.EXCITED,
            LocalDate.of(2026, 2, 2) to Mood.SAD,
            LocalDate.of(2026, 2, 3) to Mood.EXCITED
        )
        val volatileStats = MoodMosaicEngine.calculateStats(volatile)
        assertEquals(0.0, volatileStats.stabilityScore, 0.001)

        // Non-consecutive entries do not count towards transitions
        val nonConsecutive = mapOf(
            LocalDate.of(2026, 2, 1) to Mood.EXCITED,
            LocalDate.of(2026, 2, 5) to Mood.SAD,
            LocalDate.of(2026, 2, 10) to Mood.ANGRY
        )
        val nonConsecutiveStats = MoodMosaicEngine.calculateStats(nonConsecutive)
        assertEquals(1.0, nonConsecutiveStats.stabilityScore, 0.001)
    }
}

