package com.onelineaday.dailydiary.memory

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LeapDayFlashbackEngineTest {

    @Test
    fun `standard date computes five historical targets across prior years`() {
        val today = LocalDate.of(2026, 10, 27)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 5)

        assertEquals(5, targets.size)
        assertEquals(LocalDate.of(2025, 10, 27), targets[0].queryDate)
        assertEquals(1, targets[0].yearOffset)
        assertEquals(LocalDate.of(2024, 10, 27), targets[1].queryDate)
        assertEquals(2, targets[1].yearOffset)
        assertEquals(LocalDate.of(2023, 10, 27), targets[2].queryDate)
        assertEquals(3, targets[2].yearOffset)
        assertEquals(LocalDate.of(2022, 10, 27), targets[3].queryDate)
        assertEquals(4, targets[3].yearOffset)
        assertEquals(LocalDate.of(2021, 10, 27), targets[4].queryDate)
        assertEquals(5, targets[4].yearOffset)

        assertTrue(targets.none { it.isLeapDayObservation })
    }

    @Test
    fun `leap year detection accurately flags leap and common years`() {
        assertTrue(LeapDayFlashbackEngine.isLeapYear(2020))
        assertTrue(LeapDayFlashbackEngine.isLeapYear(2024))
        assertTrue(LeapDayFlashbackEngine.isLeapYear(2028))
        assertTrue(LeapDayFlashbackEngine.isLeapYear(2000))
        assertTrue(LeapDayFlashbackEngine.isLeapYear(1600))
        assertTrue(LeapDayFlashbackEngine.isLeapYear(2400))

        assertFalse(LeapDayFlashbackEngine.isLeapYear(2021))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(2022))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(2023))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(2025))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(2026))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(1700))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(1800))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(1900))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(2100))
    }

    @Test
    fun `non-leap year Feb 28 targets Feb 28 and observes historical Feb 29 for leap years`() {
        // 2025 is a common year. Prior leap years: 2024 (1 yr ago), 2020 (5 yrs ago)
        val today = LocalDate.of(2025, 2, 28)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 5)

        // 5 standard Feb 28 targets + 2 leap day observation targets (2024, 2020) = 7 targets
        assertEquals(7, targets.size)

        val leapObservations = targets.filter { it.isLeapDayObservation }
        assertEquals(2, leapObservations.size)

        // Check 2024 leap observation
        val leap2024 = leapObservations.find { it.queryDate.year == 2024 }
        assertNotNull(leap2024)
        assertEquals(LocalDate.of(2024, 2, 29), leap2024?.queryDate)
        assertEquals(1, leap2024?.yearOffset)
        assertTrue(leap2024?.isLeapDayObservation == true)

        // Check 2020 leap observation
        val leap2020 = leapObservations.find { it.queryDate.year == 2020 }
        assertNotNull(leap2020)
        assertEquals(LocalDate.of(2020, 2, 29), leap2020?.queryDate)
        assertEquals(5, leap2020?.yearOffset)
        assertTrue(leap2020?.isLeapDayObservation == true)
    }

    @Test
    fun `leap year Feb 29 targets historical Feb 29 in leap years and Feb 28 anchor in common years`() {
        // Today is 2024-02-29 (leap year)
        val today = LocalDate.of(2024, 2, 29)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 5)

        assertEquals(5, targets.size)
        // 2023: common -> Feb 28 anchor
        assertEquals(LocalDate.of(2023, 2, 28), targets[0].queryDate)
        // 2022: common -> Feb 28 anchor
        assertEquals(LocalDate.of(2022, 2, 28), targets[1].queryDate)
        // 2021: common -> Feb 28 anchor
        assertEquals(LocalDate.of(2021, 2, 28), targets[2].queryDate)
        // 2020: leap -> exact Feb 29
        assertEquals(LocalDate.of(2020, 2, 29), targets[3].queryDate)
        // 2019: common -> Feb 28 anchor
        assertEquals(LocalDate.of(2019, 2, 28), targets[4].queryDate)

        assertTrue(targets.none { it.isLeapDayObservation })
    }

    @Test
    fun `custom history depth computes requested number of offsets`() {
        val today = LocalDate.of(2026, 5, 10)
        val targets3 = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 3)
        assertEquals(3, targets3.size)
        assertEquals(listOf(1, 2, 3), targets3.map { it.yearOffset })

        val targets10 = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 10)
        assertEquals(10, targets10.size)
        assertEquals((1..10).toList(), targets10.map { it.yearOffset })
    }
}
