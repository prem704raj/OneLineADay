package com.onelineaday.dailydiary.data

import java.time.LocalDate

object StreakCalculator {

    fun current(
        entryDates: Set<LocalDate>,
        today: LocalDate,
        frozenDates: Set<LocalDate> = emptySet()
    ): Int {
        var cursor = today

        if (cursor !in entryDates && cursor !in frozenDates) {
            cursor = cursor.minusDays(1)
        }

        var count = 0

        while (true) {
            when {
                cursor in entryDates -> {
                    count++
                    cursor = cursor.minusDays(1)
                }

                cursor in frozenDates -> {
                    // Preserve continuity but do not count a missed day towards streak
                    cursor = cursor.minusDays(1)
                }

                else -> return count
            }
        }
    }

    fun longest(entryDates: Set<LocalDate>): Int {
        val dates = entryDates.sorted()

        if (dates.isEmpty()) {
            return 0
        }

        var longest = 1
        var current = 1

        for (index in 1 until dates.size) {
            if (dates[index] == dates[index - 1].plusDays(1)) {
                current++
                longest = maxOf(longest, current)
            } else if (dates[index] != dates[index - 1]) {
                current = 1
            }
        }

        return longest
    }
}
