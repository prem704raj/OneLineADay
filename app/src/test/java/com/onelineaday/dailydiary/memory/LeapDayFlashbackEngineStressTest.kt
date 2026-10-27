package com.onelineaday.dailydiary.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Empirical Stress Test Suite for [LeapDayFlashbackEngine].
 *
 * Adversarially challenges:
 * 1. Proleptic Gregorian century transitions (1600, 1700, 1800, 1900, 2000, 2100, 2400).
 * 2. Extended historical depth (1..10 years) and year offset monotonicity.
 * 3. Feb 28 common year observations vs Feb 28 leap year behavior.
 * 4. Leap day to leap day matching (2024 to 2020) and anchor mapping.
 * 5. Boundary dates (New Year, New Year's Eve, Century transitions).
 */
class LeapDayFlashbackEngineStressTest {

    // =========================================================================
    // 1. Gregorian Century Boundary Stress Testing
    // =========================================================================

    @Test
    fun `exhaustive century leap evaluation across 1600 through 2400`() {
        val testCenturies = mapOf(
            1600 to true,   // divisible by 400 -> leap
            1700 to false,  // divisible by 100, not 400 -> common
            1800 to false,  // divisible by 100, not 400 -> common
            1900 to false,  // divisible by 100, not 400 -> common
            2000 to true,   // divisible by 400 -> leap
            2100 to false,  // divisible by 100, not 400 -> common
            2200 to false,  // divisible by 100, not 400 -> common
            2300 to false,  // divisible by 100, not 400 -> common
            2400 to true    // divisible by 400 -> leap
        )

        for ((year, expectedLeap) in testCenturies) {
            val actual = LeapDayFlashbackEngine.isLeapYear(year)
            assertEquals("Gregorian rule failed for century year $year", expectedLeap, actual)
        }
    }

    @Test
    fun `leap day flashback across non-leap century 1900 boundary`() {
        // In 1904 (leap), 4 years ago was 1900 (century common year).
        // Feb 29, 1904 should query 1900-02-28 (common year anchor), NOT 1900-02-29!
        val today = LocalDate.of(1904, 2, 29)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 5)

        assertEquals(5, targets.size)
        val target1900 = targets.find { it.queryDate.year == 1900 }
        assertNotNull("Must contain target for year 1900", target1900)
        assertEquals("1900 is non-leap century; must anchor to Feb 28", LocalDate.of(1900, 2, 28), target1900!!.queryDate)
        assertEquals(4, target1900.yearOffset)
        assertFalse(target1900.isLeapDayObservation)
    }

    @Test
    fun `leap day flashback across leap century 2000 boundary`() {
        // In 2004 (leap), 4 years ago was 2000 (century leap year).
        // Feb 29, 2004 should query 2000-02-29 (true leap match).
        val today = LocalDate.of(2004, 2, 29)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 5)

        assertEquals(5, targets.size)
        val target2000 = targets.find { it.queryDate.year == 2000 }
        assertNotNull("Must contain target for year 2000", target2000)
        assertEquals("2000 is leap century; must match Feb 29", LocalDate.of(2000, 2, 29), target2000!!.queryDate)
        assertEquals(4, target2000.yearOffset)
        assertFalse(target2000.isLeapDayObservation)
    }

    // =========================================================================
    // 2. Historical Depth 1..10 and Monotonicity Stress Testing
    // =========================================================================

    @Test
    fun `extended history depth 1 through 10 generates strictly monotonic year offsets`() {
        val today = LocalDate.of(2026, 7, 15)

        for (depth in 1..10) {
            val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = depth)
            assertEquals("Target count must match requested depth $depth", depth, targets.size)

            for (i in 0 until depth) {
                val target = targets[i]
                val expectedOffset = i + 1
                val expectedYear = today.year - expectedOffset

                assertEquals("Year offset mismatch at index $i", expectedOffset, target.yearOffset)
                assertEquals("Query year mismatch at index $i", expectedYear, target.queryDate.year)
                assertEquals(7, target.queryDate.monthValue)
                assertEquals(15, target.queryDate.dayOfMonth)
            }

            // Monotonic decrease in years
            val years = targets.map { it.queryDate.year }
            for (i in 0 until years.size - 1) {
                assertTrue("Years must strictly decrease monotonically", years[i] > years[i + 1])
            }
        }
    }

    @Test
    fun `extended history depth 10 on common year Feb 28 contains correct leap observations`() {
        // 2026 is a common year.
        // Offsets 1..10:
        // 2025: common (offset 1)
        // 2024: LEAP (offset 2) -> 2024-02-28 AND 2024-02-29 (observed)
        // 2023: common (offset 3)
        // 2022: common (offset 4)
        // 2021: common (offset 5)
        // 2020: LEAP (offset 6) -> 2020-02-28 AND 2020-02-29 (observed)
        // 2019: common (offset 7)
        // 2018: common (offset 8)
        // 2017: common (offset 9)
        // 2016: LEAP (offset 10) -> 2016-02-28 AND 2016-02-29 (observed)
        val today = LocalDate.of(2026, 2, 28)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 10)

        // 10 standard Feb 28 dates + 3 leap year observations (2024, 2020, 2016) = 13 targets total
        assertEquals(13, targets.size)

        val leapObs = targets.filter { it.isLeapDayObservation }
        assertEquals(3, leapObs.size)

        val observedYears = leapObs.map { it.queryDate.year }
        assertEquals(listOf(2024, 2020, 2016), observedYears)

        for (obs in leapObs) {
            assertEquals(2, obs.queryDate.monthValue)
            assertEquals(29, obs.queryDate.dayOfMonth)
            assertEquals(obs.queryDate, obs.originalDate)
            assertTrue(obs.isLeapDayObservation)
        }

        // Offsets must be non-decreasing
        val offsets = targets.map { it.yearOffset }
        for (i in 0 until offsets.size - 1) {
            assertTrue("Offsets must be non-decreasing", offsets[i] <= offsets[i + 1])
        }
    }

    // =========================================================================
    // 3. Feb 28 Leap Year vs Common Year Differentiation
    // =========================================================================

    @Test
    fun `feb 28 in a leap year does NOT observe historical leap days because tomorrow is feb 29`() {
        // In 2024 (a leap year), Feb 28 is NOT the eve of March 1; Feb 29 exists tomorrow!
        // Therefore, Feb 28 should only query Feb 28 of historical years.
        val leapYearFeb28 = LocalDate.of(2024, 2, 28)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(leapYearFeb28, historyYears = 5)

        assertEquals(5, targets.size)
        assertTrue("No targets should be leap day observations on Feb 28 of a leap year",
            targets.none { it.isLeapDayObservation }
        )
        for (target in targets) {
            assertEquals(28, target.queryDate.dayOfMonth)
        }
    }

    // =========================================================================
    // 4. Quadrennial Leap Day to Leap Day Matching
    // =========================================================================

    @Test
    fun `leap day 2024 matches 2020 leap day exactly with zero observation flag`() {
        val leapDay2024 = LocalDate.of(2024, 2, 29)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(leapDay2024, historyYears = 5)

        assertEquals(5, targets.size)

        // 2020 (offset 4) was a leap year
        val target2020 = targets.find { it.yearOffset == 4 }
        assertNotNull(target2020)
        assertEquals(LocalDate.of(2020, 2, 29), target2020!!.queryDate)
        assertFalse("Native Feb 29 to Feb 29 is not an observation mapping", target2020.isLeapDayObservation)

        // Non-leap historical years fallback to Feb 28 anchor
        val commonYearTargets = targets.filter { it.yearOffset != 4 }
        assertEquals(4, commonYearTargets.size)
        for (target in commonYearTargets) {
            assertEquals(28, target.queryDate.dayOfMonth)
            assertFalse(target.isLeapDayObservation)
        }
    }

    // =========================================================================
    // 5. Boundary Calendar Dates Stress Testing
    // =========================================================================

    @Test
    fun `january 1st historical targets compute cleanly across year boundary`() {
        val newYearsDay = LocalDate.of(2026, 1, 1)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(newYearsDay, historyYears = 5)

        assertEquals(5, targets.size)
        for (i in 0 until 5) {
            val expectedYear = 2025 - i
            assertEquals(LocalDate.of(expectedYear, 1, 1), targets[i].queryDate)
            assertEquals(i + 1, targets[i].yearOffset)
        }
    }

    @Test
    fun `december 31st historical targets compute cleanly`() {
        val newYearsEve = LocalDate.of(2025, 12, 31)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(newYearsEve, historyYears = 5)

        assertEquals(5, targets.size)
        for (i in 0 until 5) {
            val expectedYear = 2024 - i
            assertEquals(LocalDate.of(expectedYear, 12, 31), targets[i].queryDate)
            assertEquals(i + 1, targets[i].yearOffset)
        }
    }

    @Test
    fun `historyYears zero produces empty list without error`() {
        val today = LocalDate.of(2026, 6, 1)
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 0)
        assertTrue(targets.isEmpty())
    }
}
