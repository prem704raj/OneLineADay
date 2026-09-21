package com.onelineaday.dailydiary.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 9, 10)

    @Test
    fun `empty dates produces zero streak and zero longest`() {
        assertEquals(0, StreakCalculator.current(emptySet(), today))
        assertEquals(0, StreakCalculator.longest(emptySet()))
    }

    @Test
    fun `entry on today gives streak of one`() {
        val dates = setOf(today)
        assertEquals(1, StreakCalculator.current(dates, today))
        assertEquals(1, StreakCalculator.longest(dates))
    }

    @Test
    fun `entry on yesterday with no entry today gives streak of one`() {
        val yesterday = today.minusDays(1)
        val dates = setOf(yesterday)
        assertEquals(1, StreakCalculator.current(dates, today))
        assertEquals(1, StreakCalculator.longest(dates))
    }

    @Test
    fun `entry two days ago with no entry today or yesterday gives streak of zero`() {
        val twoDaysAgo = today.minusDays(2)
        val dates = setOf(twoDaysAgo)
        assertEquals(0, StreakCalculator.current(dates, today))
        assertEquals(1, StreakCalculator.longest(dates))
    }

    @Test
    fun `continuous streak ending today`() {
        val dates = setOf(
            today,
            today.minusDays(1),
            today.minusDays(2),
            today.minusDays(3),
            today.minusDays(4)
        )
        assertEquals(5, StreakCalculator.current(dates, today))
        assertEquals(5, StreakCalculator.longest(dates))
    }

    @Test
    fun `continuous streak ending yesterday`() {
        val dates = setOf(
            today.minusDays(1),
            today.minusDays(2),
            today.minusDays(3)
        )
        assertEquals(3, StreakCalculator.current(dates, today))
        assertEquals(3, StreakCalculator.longest(dates))
    }

    @Test
    fun `frozen date bridges gap between entries without increasing streak count`() {
        val dayMinus1 = today.minusDays(1)
        val dayMinus2 = today.minusDays(2)
        val dayMinus3 = today.minusDays(3)

        // Today has an entry, dayMinus2 and dayMinus3 have entries.
        // dayMinus1 was missed but has a freeze applied.
        val entries = setOf(today, dayMinus2, dayMinus3)
        val frozen = setOf(dayMinus1)

        val streak = StreakCalculator.current(
            entryDates = entries,
            today = today,
            frozenDates = frozen
        )

        // 3 entries total (today, day-2, day-3); freeze preserved continuity but didn't add count
        assertEquals(3, streak)
    }

    @Test
    fun `frozen date bridges gap when today is not yet logged`() {
        val dayMinus1 = today.minusDays(1)
        val dayMinus2 = today.minusDays(2)
        val dayMinus3 = today.minusDays(3)

        // No entry today. dayMinus1 is frozen. dayMinus2 and dayMinus3 are logged.
        val entries = setOf(dayMinus2, dayMinus3)
        val frozen = setOf(dayMinus1)

        val streak = StreakCalculator.current(
            entryDates = entries,
            today = today,
            frozenDates = frozen
        )

        assertEquals(2, streak)
    }

    @Test
    fun `longest streak selects maximum across disconnected clusters`() {
        val dates = setOf(
            // Streak of 4
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 1, 4),
            // Gap
            // Streak of 7
            LocalDate.of(2026, 3, 10),
            LocalDate.of(2026, 3, 11),
            LocalDate.of(2026, 3, 12),
            LocalDate.of(2026, 3, 13),
            LocalDate.of(2026, 3, 14),
            LocalDate.of(2026, 3, 15),
            LocalDate.of(2026, 3, 16),
            // Gap
            // Streak of 2
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 2)
        )

        assertEquals(7, StreakCalculator.longest(dates))
    }

    @Test
    fun `unsorted set input does not break streak calculations`() {
        val dates = setOf(
            today.minusDays(2),
            today,
            today.minusDays(1)
        )
        assertEquals(3, StreakCalculator.current(dates, today))
        assertEquals(3, StreakCalculator.longest(dates))
    }
}
