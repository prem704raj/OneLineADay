package com.onelineaday.dailydiary.memory

import com.onelineaday.dailydiary.data.JournalDao
import com.onelineaday.dailydiary.data.JournalEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Repository orchestrating historical flashback target computation,
 * Room indexed database queries, and correlation into rich FlashbackMemory objects.
 */
class FlashbackRepository(
    private val journalDao: JournalDao
) {
    /**
     * Continuous reactive Flow emitting historical memories for [date] across [historyYears].
     */
    fun getFlashbacksFlow(
        date: LocalDate = LocalDate.now(),
        journalId: String = "default",
        historyYears: Int = 5,
        chronologicalAscending: Boolean = false
    ): Flow<List<FlashbackMemory>> {
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(date, historyYears)
        val dates = targets.map { it.queryDate }

        return journalDao.getEntriesForDates(dates, journalId).map { entries ->
            correlateTargetsWithEntries(targets, entries, chronologicalAscending)
        }
    }

    /**
     * One-shot asynchronous retrieval for background workers or notification triggers.
     */
    suspend fun getFlashbacksOnce(
        date: LocalDate = LocalDate.now(),
        journalId: String = "default",
        historyYears: Int = 5,
        chronologicalAscending: Boolean = false
    ): List<FlashbackMemory> {
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(date, historyYears)
        val dates = targets.map { it.queryDate }
        val entries = journalDao.getEntriesForDatesOnce(dates, journalId)
        return correlateTargetsWithEntries(targets, entries, chronologicalAscending)
    }

    private fun correlateTargetsWithEntries(
        targets: List<FlashbackTarget>,
        entries: List<JournalEntry>,
        chronologicalAscending: Boolean
    ): List<FlashbackMemory> {
        val entryMap = entries.associateBy { it.date }
        val memories = mutableListOf<FlashbackMemory>()

        for (target in targets) {
            val entry = entryMap[target.queryDate]
            if (entry != null) {
                memories.add(
                    FlashbackMemory(
                        entry = entry,
                        yearOffset = target.yearOffset,
                        targetDate = target.queryDate,
                        isLeapDayObservation = target.isLeapDayObservation
                    )
                )
            }
        }

        return if (chronologicalAscending) {
            memories.sortedBy { it.entry.date }
        } else {
            memories.sortedByDescending { it.entry.date }
        }
    }
}
