package com.onelineaday.dailydiary.memory

import java.time.LocalDate
import java.time.Year

/**
 * On This Day 2.0 & Leap Day Observation Engine.
 * Strictly implements PROJECT.md §F6 & §F7:
 * - 5-Year Timeline flashback comparison across calendar years (Y-1 down to Y-5).
 * - Leap Day (Feb 29) handling: In non-leap years, maps Feb 29 memories to Feb 28 with a visual leap badge.
 */
object LeapDayFlashbackEngine {

    fun isLeapYear(year: Int): Boolean = Year.isLeap(year.toLong())

    /**
     * Computes the list of historical flashback query targets for a given date across [historyYears].
     */
    fun computeFlashbackTargets(
        currentDate: LocalDate,
        historyYears: Int = 5
    ): List<FlashbackTarget> {
        val results = mutableListOf<FlashbackTarget>()
        val currentMonth = currentDate.monthValue
        val currentDay = currentDate.dayOfMonth

        for (offset in 1..historyYears) {
            val targetYear = currentDate.year - offset
            val targetIsLeap = isLeapYear(targetYear)

            when {
                // Today is Feb 29 in a leap year
                currentMonth == 2 && currentDay == 29 -> {
                    if (targetIsLeap) {
                        val date = LocalDate.of(targetYear, 2, 29)
                        results.add(FlashbackTarget(queryDate = date, yearOffset = offset))
                    } else {
                        // Historical non-leap year had no Feb 29; check Feb 28 as standard anchor
                        val date = LocalDate.of(targetYear, 2, 28)
                        results.add(FlashbackTarget(queryDate = date, yearOffset = offset))
                    }
                }

                // Today is Feb 28 in a non-leap year: check Feb 28 AND observe past leap day Feb 29
                currentMonth == 2 && currentDay == 28 && !isLeapYear(currentDate.year) -> {
                    // Standard Feb 28 memory
                    val date28 = LocalDate.of(targetYear, 2, 28)
                    results.add(FlashbackTarget(queryDate = date28, yearOffset = offset))

                    // If historical year was a leap year, also observe its Feb 29 memory with badge
                    if (targetIsLeap) {
                        val leapDate = LocalDate.of(targetYear, 2, 29)
                        results.add(
                            FlashbackTarget(
                                queryDate = leapDate,
                                yearOffset = offset,
                                isLeapDayObservation = true,
                                originalDate = leapDate
                            )
                        )
                    }
                }

                // Standard calendar day
                else -> {
                    val date = LocalDate.of(targetYear, currentMonth, currentDay)
                    results.add(FlashbackTarget(queryDate = date, yearOffset = offset))
                }
            }
        }

        return results
    }
}
