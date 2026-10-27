package com.onelineaday.dailydiary.e2e.tier1

import com.onelineaday.dailydiary.e2e.engine.LeapDayFlashbackEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tier 1: Core Feature Coverage for On This Day 2.0 & Leap Day Observation Engine (F6, F7).
 */
class Tier1LeapDayFlashbackFeatureTest {

    @Test
    fun `standard date computes five historical targets across prior years`() {
        val today = LocalDate.of(2026, 10, 27)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 5)

        assertEquals(5, targets.size)
        assertEquals(LocalDate.of(2025, 10, 27), targets[0].queryDate)
        assertEquals(LocalDate.of(2024, 10, 27), targets[1].queryDate)
        assertEquals(LocalDate.of(2023, 10, 27), targets[2].queryDate)
        assertEquals(LocalDate.of(2022, 10, 27), targets[3].queryDate)
        assertEquals(LocalDate.of(2021, 10, 27), targets[4].queryDate)

        assertTrue(targets.none { it.isLeapDayObservation })
    }

    @Test
    fun `leap year detection accurately flags leap and common years`() {
        assertTrue(LeapDayFlashbackEngine.isLeapYear(2020))
        assertTrue(LeapDayFlashbackEngine.isLeapYear(2024))
        assertTrue(LeapDayFlashbackEngine.isLeapYear(2028))

        assertFalse(LeapDayFlashbackEngine.isLeapYear(2021))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(2022))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(2023))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(2025))
        assertFalse(LeapDayFlashbackEngine.isLeapYear(2026))
    }

    @Test
    fun `leap day query on february 29 matches prior leap year february 29`() {
        val today = LocalDate.of(2024, 2, 29) // 2024 is leap
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 5)

        // Target for 2020 (4 years prior, leap year)
        val target2020 = targets.first { it.queryDate.year == 2020 }
        assertEquals(LocalDate.of(2020, 2, 29), target2020.queryDate)

        // Target for 2023 (1 year prior, non-leap year)
        val target2023 = targets.first { it.queryDate.year == 2023 }
        assertEquals(LocalDate.of(2023, 2, 28), target2023.queryDate)
    }

    @Test
    fun `february 28 in non-leap year observes historical february 29 with leap badge`() {
        val today = LocalDate.of(2025, 2, 28) // 2025 is non-leap
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 5)

        // 2024 was leap year, so there should be a leap observation for Feb 29, 2024
        val leapObservation = targets.firstOrNull { it.isLeapDayObservation }
        assertTrue("Feb 28 in non-leap year must observe Feb 29 of prior leap year", leapObservation != null)
        assertEquals(LocalDate.of(2024, 2, 29), leapObservation!!.queryDate)
        assertTrue(leapObservation.isLeapDayObservation)

        // Standard Feb 28, 2024 must also be checked
        val standard2024 = targets.firstOrNull { it.queryDate == LocalDate.of(2024, 2, 28) && !it.isLeapDayObservation }
        assertTrue("Standard Feb 28 memory must also be included", standard2024 != null)
    }

    @Test
    fun `custom history depth limits targets appropriately`() {
        val today = LocalDate.of(2026, 5, 1)
        val targets3 = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 3)
        assertEquals(3, targets3.size)
        assertEquals(2025, targets3[0].queryDate.year)
        assertEquals(2024, targets3[1].queryDate.year)
        assertEquals(2023, targets3[2].queryDate.year)
    }

    @Test
    fun `year offsets are monotonically increasing`() {
        val today = LocalDate.of(2026, 7, 15)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 5)
        for (i in 0 until targets.size) {
            assertEquals(i + 1, targets[i].yearOffset)
        }
    }
}
