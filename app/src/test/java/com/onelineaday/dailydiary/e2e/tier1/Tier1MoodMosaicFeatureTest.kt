package com.onelineaday.dailydiary.e2e.tier1

import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.mosaic.MoodMosaicEngine
import com.onelineaday.dailydiary.mosaic.PixelCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tier 1: Core Feature Coverage for Year in Pixels 12-Month Mood Mosaic & Stats (F12, F13).
 */
class Tier1MoodMosaicFeatureTest {

    @Test
    fun `mosaic grid dimensions are exactly 12 months by 31 days`() {
        val grid = MoodMosaicEngine.buildMosaic(2026, emptyMap())
        assertEquals(12, grid.size)
        grid.forEach { monthRow ->
            assertEquals(31, monthRow.size)
        }
    }

    @Test
    fun `invalid days like february 30 and 31 are flagged as out of range`() {
        val grid = MoodMosaicEngine.buildMosaic(2026, emptyMap())
        val febRow = grid[1] // Month 2 (February)
        assertTrue("Feb 30 must be OutOfRange", febRow[29] is PixelCell.OutOfRange)
        assertTrue("Feb 31 must be OutOfRange", febRow[30] is PixelCell.OutOfRange)
    }

    @Test
    fun `leap year february 29 is valid while non-leap year february 29 is out of range`() {
        val leapGrid = MoodMosaicEngine.buildMosaic(2024, emptyMap())
        val commonGrid = MoodMosaicEngine.buildMosaic(2025, emptyMap())

        assertTrue("Feb 29 in 2024 must be valid Unlogged day", leapGrid[1][28] is PixelCell.Unlogged)
        assertTrue("Feb 29 in 2025 must be OutOfRange", commonGrid[1][28] is PixelCell.OutOfRange)
    }

    @Test
    fun `logged entries are correctly mapped to corresponding cells`() {
        val date1 = LocalDate.of(2026, 1, 1)
        val date2 = LocalDate.of(2026, 12, 31)
        val entries = mapOf(
            date1 to Mood.EXCITED,
            date2 to Mood.HAPPY
        )

        val grid = MoodMosaicEngine.buildMosaic(2026, entries)

        val cellJan1 = grid[0][0]
        assertTrue(cellJan1 is PixelCell.Logged)
        assertEquals(Mood.EXCITED, (cellJan1 as PixelCell.Logged).mood)

        val cellDec31 = grid[11][30]
        assertTrue(cellDec31 is PixelCell.Logged)
        assertEquals(Mood.HAPPY, (cellDec31 as PixelCell.Logged).mood)
    }

    @Test
    fun `mood distribution calculates exact counts and dominant mood`() {
        val entries = mapOf(
            LocalDate.of(2026, 3, 1) to Mood.HAPPY,
            LocalDate.of(2026, 3, 2) to Mood.HAPPY,
            LocalDate.of(2026, 3, 3) to Mood.GRATEFUL,
            LocalDate.of(2026, 3, 4) to Mood.NEUTRAL
        )

        val stats = MoodMosaicEngine.calculateStats(entries)
        assertEquals(4, stats.totalLogged)
        assertEquals(2, stats.moodCounts[Mood.HAPPY])
        assertEquals(1, stats.moodCounts[Mood.GRATEFUL])
        assertEquals(1, stats.moodCounts[Mood.NEUTRAL])
        assertEquals(0, stats.moodCounts[Mood.SAD])
        assertEquals(0, stats.moodCounts[Mood.ANGRY])

        assertEquals(50.0, stats.moodPercentages[Mood.HAPPY]!!, 0.001)
        assertEquals(Mood.HAPPY, stats.dominantMood)
    }

    @Test
    fun `mood stability score correctly reflects smooth versus volatile transitions`() {
        // Smooth transitions (diff <= 1: EXCITED(5) -> HAPPY(4) -> NEUTRAL(3))
        val smoothEntries = mapOf(
            LocalDate.of(2026, 4, 1) to Mood.EXCITED,
            LocalDate.of(2026, 4, 2) to Mood.HAPPY,
            LocalDate.of(2026, 4, 3) to Mood.NEUTRAL
        )
        val smoothStats = MoodMosaicEngine.calculateStats(smoothEntries)
        assertEquals(1.0, smoothStats.stabilityScore, 0.001)

        // Volatile transitions (diff > 1: EXCITED(5) -> SAD(1) -> EXCITED(5))
        val volatileEntries = mapOf(
            LocalDate.of(2026, 4, 1) to Mood.EXCITED,
            LocalDate.of(2026, 4, 2) to Mood.SAD,
            LocalDate.of(2026, 4, 3) to Mood.EXCITED
        )
        val volatileStats = MoodMosaicEngine.calculateStats(volatileEntries)
        assertEquals(0.0, volatileStats.stabilityScore, 0.001)
    }
}
