package com.onelineaday.dailydiary.data

import android.content.Context
import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StreakManager(
    private val repository: JournalRepository,
    private val context: Context
) {
    companion object {
        const val PREFS_NAME = "streak_prefs"
        const val KEY_REST_DAYS = "scheduled_rest_days"
        const val KEY_MANUAL_FREEZES = "manual_freezes_inventory"
        const val MAX_FREEZE_INVENTORY = 5
        const val DAYS_PER_EARNED_FREEZE = 7
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Retrieves configured rest days (e.g. DayOfWeek.SUNDAY).
     */
    fun getRestDays(): Set<DayOfWeek> {
        val stringSet = prefs.getStringSet(KEY_REST_DAYS, emptySet()) ?: emptySet()
        return stringSet.mapNotNull {
            runCatching { DayOfWeek.valueOf(it) }.getOrNull()
        }.toSet()
    }

    /**
     * Sets configured rest days.
     */
    fun setRestDays(restDays: Set<DayOfWeek>) {
        val stringSet = restDays.map { it.name }.toSet()
        prefs.edit().putStringSet(KEY_REST_DAYS, stringSet).apply()
    }

    /**
     * Computes the available freeze inventory for the given journal.
     * Inventory = Earned (longest streak / 7) + Manual Bonus - Consumed Freezes (capped at MAX_FREEZE_INVENTORY).
     */
    suspend fun getAvailableFreezesCount(journalId: String = "default"): Int {
        val allEntries = repository.getAllEntriesForBackup().filter { it.journalId == journalId }
        val longestStreak = repository.calculateLongestStreak(allEntries)
        val earned = (longestStreak / DAYS_PER_EARNED_FREEZE)
        val manualBonus = prefs.getInt(KEY_MANUAL_FREEZES, 0)
        val consumed = repository.getStreakFreezesOnce(journalId).size

        val available = (earned + manualBonus - consumed).coerceAtLeast(0)
        return minOf(available, MAX_FREEZE_INVENTORY)
    }

    /**
     * Checks if a freeze can and should be applied for a missed date.
     * If yesterday was missed and is not a rest day, and user has available freezes, automatically inserts a freeze.
     */
    suspend fun autoApplyFreezeIfNeeded(
        today: LocalDate = LocalDate.now(),
        journalId: String = "default"
    ): Boolean {
        val yesterday = today.minusDays(1)
        val restDays = getRestDays()

        if (yesterday.dayOfWeek in restDays) {
            return false // Rest days are automatically exempt from breaking streak
        }

        val yesterdayEntry = repository.getEntryByDateOnce(yesterday, journalId)
        if (yesterdayEntry != null) {
            return false // Yesterday was logged, no freeze needed
        }

        val existingFreezes = repository.getStreakFreezesOnce(journalId).map { it.date }.toSet()
        if (yesterday in existingFreezes) {
            return false // Already frozen
        }

        // Check if day before yesterday was logged (a freeze only bridges an existing chain)
        val dayBeforeYesterday = today.minusDays(2)
        val dayBeforeLogged = repository.getEntryByDateOnce(dayBeforeYesterday, journalId) != null ||
                dayBeforeYesterday in existingFreezes ||
                dayBeforeYesterday.dayOfWeek in restDays

        if (!dayBeforeLogged) {
            return false // Streak already broken earlier
        }

        val available = getAvailableFreezesCount(journalId)
        if (available > 0) {
            repository.insertStreakFreeze(
                StreakFreeze(
                    date = yesterday,
                    journalId = journalId,
                    reason = "AUTOMATIC_PRESERVATION"
                )
            )
            return true
        }

        return false
    }

    /**
     * Calculates the effective current streak considering entries, streak freezes, and rest days.
     */
    suspend fun calculateEffectiveStreak(
        today: LocalDate = LocalDate.now(),
        journalId: String = "default"
    ): Int {
        val entries = repository.getAllEntriesForBackup().filter { it.journalId == journalId }
        val entryDates = entries.map { it.date }.toSet()
        val frozenDates = repository.getStreakFreezesOnce(journalId).map { it.date }.toSet()
        val restDays = getRestDays()

        // Rest days also act as non-breaking bridge days
        val restDates = mutableSetOf<LocalDate>()
        // Consider rest days in the last 365 days
        for (i in 0..365) {
            val d = today.minusDays(i.toLong())
            if (d.dayOfWeek in restDays && d !in entryDates) {
                restDates.add(d)
            }
        }

        val allBridges = frozenDates + restDates

        return StreakCalculator.current(
            entryDates = entryDates,
            today = today,
            frozenDates = allBridges
        )
    }
}

