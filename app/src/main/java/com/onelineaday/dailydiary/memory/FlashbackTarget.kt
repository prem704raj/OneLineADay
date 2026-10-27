package com.onelineaday.dailydiary.memory

import java.time.LocalDate

/**
 * Historical query target computed by [LeapDayFlashbackEngine].
 *
 * @property queryDate The exact calendar date to query in the database.
 * @property yearOffset How many years in the past this target represents (1..5).
 * @property isLeapDayObservation Whether this target observes a historical Feb 29 on Feb 28 in a non-leap year.
 * @property originalDate The actual historical date of the event (equal to queryDate or the historical Feb 29).
 */
data class FlashbackTarget(
    val queryDate: LocalDate,
    val yearOffset: Int,
    val isLeapDayObservation: Boolean = false,
    val originalDate: LocalDate = queryDate
)
