package com.onelineaday.dailydiary.capsule

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class TimeCapsule(
    val id: String = UUID.randomUUID().toString(),
    val journalId: String = "default",
    val title: String,
    val messageToFutureSelf: String,
    val sealedDate: LocalDate,
    val unlockDate: LocalDate,
    val isRevealed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isReadyToUnlock(currentDate: LocalDate = LocalDate.now()): Boolean {
        return !currentDate.isBefore(unlockDate)
    }

    fun daysRemaining(currentDate: LocalDate = LocalDate.now()): Long {
        return ChronoUnit.DAYS.between(currentDate, unlockDate).coerceAtLeast(0)
    }
}

