package com.onelineaday.dailydiary.e2e.tier1

import com.onelineaday.dailydiary.data.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Tier 1: Core Feature Coverage for Habit Resilience, Streak Freezes & Grace Window (F19, F20).
 */
class Tier1StreakEngineFeatureTest {

    private val baseDate = LocalDate.of(2026, 10, 27)

    @Test
    fun `empty entry dates results in zero current and zero longest streak`() {
        assertEquals(0, StreakCalculator.current(emptySet(), baseDate))
        assertEquals(0, StreakCalculator.longest(emptySet()))
    }

    @Test
    fun `single entry on today produces current streak of one`() {
        val entries = setOf(baseDate)
        assertEquals(1, StreakCalculator.current(entries, baseDate))
        assertEquals(1, StreakCalculator.longest(entries))
    }

    @Test
    fun `contiguous seven day streak produces current streak of seven`() {
        val entries = (0..6).map { baseDate.minusDays(it.toLong()) }.toSet()
        assertEquals(7, StreakCalculator.current(entries, baseDate))
        assertEquals(7, StreakCalculator.longest(entries))
    }

    @Test
    fun `twenty-four hour grace window preserves streak when yesterday logged but today not yet logged`() {
        // Today is not logged, but yesterday, 2 days ago, and 3 days ago are logged.
        val entries = setOf(
            baseDate.minusDays(1),
            baseDate.minusDays(2),
            baseDate.minusDays(3)
        )
        // 24-hr grace allows user to keep their streak until today ends
        assertEquals(3, StreakCalculator.current(entries, baseDate))
    }

    @Test
    fun `streak freeze bridges single missed day without artificially inflating streak count`() {
        // Logged: Today, Day-2, Day-3. Day-1 was missed but has a freeze applied.
        val entries = setOf(baseDate, baseDate.minusDays(2), baseDate.minusDays(3))
        val frozen = setOf(baseDate.minusDays(1))

        val currentStreak = StreakCalculator.current(
            entryDates = entries,
            today = baseDate,
            frozenDates = frozen
        )

        // 3 actual entries; freeze maintained continuity but did not increment count
        assertEquals(3, currentStreak)
    }

    @Test
    fun `multiple streak freezes bridge multiple separate gap days across month`() {
        // Logged: Days 0, 2, 4, 5
        // Frozen: Days 1, 3
        val entries = setOf(
            baseDate,
            baseDate.minusDays(2),
            baseDate.minusDays(4),
            baseDate.minusDays(5)
        )
        val frozen = setOf(
            baseDate.minusDays(1),
            baseDate.minusDays(3)
        )

        val currentStreak = StreakCalculator.current(
            entryDates = entries,
            today = baseDate,
            frozenDates = frozen
        )

        assertEquals(4, currentStreak)
    }

    @Test
    fun `two day gap without freeze permanently breaks current streak to zero`() {
        // Logged: Day-2, Day-3. Missing both today and yesterday without freeze.
        val entries = setOf(baseDate.minusDays(2), baseDate.minusDays(3))
        assertEquals(0, StreakCalculator.current(entries, baseDate))
        // Historical longest is still preserved
        assertEquals(2, StreakCalculator.longest(entries))
    }

    @Test
    fun `longest streak chooses max across multiple separated streak clusters`() {
        val cluster1 = (20..24).map { baseDate.minusDays(it.toLong()) } // 5 days
        val cluster2 = (10..18).map { baseDate.minusDays(it.toLong()) } // 9 days
        val cluster3 = (0..2).map { baseDate.minusDays(it.toLong()) }   // 3 days

        val allEntries = (cluster1 + cluster2 + cluster3).toSet()
        assertEquals(9, StreakCalculator.longest(allEntries))
        assertEquals(3, StreakCalculator.current(allEntries, baseDate))
    }
}
