package com.onelineaday.dailydiary.data

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreakResilienceStressTest {

    private val baseDate = LocalDate.of(2026, 10, 27) // Tuesday

    @Test
    fun testRestDayBridgesContinuity() {
        // Monday (logged), Sunday (rest day), Saturday (logged), Friday (logged)
        val monday = LocalDate.of(2026, 10, 26)
        val sunday = LocalDate.of(2026, 10, 25)
        val saturday = LocalDate.of(2026, 10, 24)
        val friday = LocalDate.of(2026, 10, 23)

        val entries = setOf(monday, saturday, friday)
        val restDays = setOf(DayOfWeek.SUNDAY)

        val bridgeDates = if (sunday.dayOfWeek in restDays) setOf(sunday) else emptySet()

        val streak = StreakCalculator.current(
            entryDates = entries,
            today = monday,
            frozenDates = bridgeDates
        )

        // 3 actual entries; Sunday did not add to count but bridged Saturday to Monday
        assertEquals(3, streak)
    }

    @Test
    fun testWeekendRestDaysBridgesContinuity() {
        // Monday (logged), Sunday & Sat (rest days), Friday (logged)
        val monday = LocalDate.of(2026, 10, 26)
        val sunday = LocalDate.of(2026, 10, 25)
        val saturday = LocalDate.of(2026, 10, 24)
        val friday = LocalDate.of(2026, 10, 23)

        val entries = setOf(monday, friday)
        val restDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        val bridgeDates = setOf(saturday, sunday)

        val streak = StreakCalculator.current(
            entryDates = entries,
            today = monday,
            frozenDates = bridgeDates
        )

        // Friday and Monday logged (2 entries), weekend bridged
        assertEquals(2, streak)
    }

    @Test
    fun testMultipleConsecutiveFreezesPreserveLongChain() {
        // 10-day streak with 2 freezes in the middle
        val today = LocalDate.of(2026, 6, 15)
        val entries = mutableSetOf<LocalDate>()
        val freezes = mutableSetOf<LocalDate>()

        for (i in 0..9) {
            val d = today.minusDays(i.toLong())
            if (i == 3 || i == 4) {
                freezes.add(d) // 2 consecutive freeze days
            } else {
                entries.add(d) // 8 logged days
            }
        }

        val streak = StreakCalculator.current(
            entryDates = entries,
            today = today,
            frozenDates = freezes
        )

        assertEquals(8, streak)
    }

    @Test
    fun testUnfrozenGapBreaksStreakEvenIfFreezesExistElsewhere() {
        val today = LocalDate.of(2026, 6, 15)
        val entries = setOf(
            today,
            today.minusDays(1),
            // today.minusDays(2) is MISSED AND NOT FROZEN
            today.minusDays(3),
            today.minusDays(4)
        )
        // Freeze exists at day 5, but day 2 was unshielded
        val freezes = setOf(today.minusDays(5))

        val streak = StreakCalculator.current(
            entryDates = entries,
            today = today,
            frozenDates = freezes
        )

        // Only today and day-1 are in the current unbroken streak
        assertEquals(2, streak)
    }

    @Test
    fun testLeapDayBoundaryStreakContinuity() {
        // 2024 is leap year: Feb 28, Feb 29, March 1
        val feb28 = LocalDate.of(2024, 2, 28)
        val feb29 = LocalDate.of(2024, 2, 29)
        val mar1 = LocalDate.of(2024, 3, 1)

        val entries = setOf(feb28, feb29, mar1)

        val streak = StreakCalculator.current(
            entryDates = entries,
            today = mar1
        )

        assertEquals(3, streak)
        assertEquals(3, StreakCalculator.longest(entries))
    }
}

