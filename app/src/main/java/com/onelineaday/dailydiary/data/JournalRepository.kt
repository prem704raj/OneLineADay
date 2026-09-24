package com.onelineaday.dailydiary.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository for managing journal entries
 */
class JournalRepository(private val journalDao: JournalDao) {
    
    fun getAllJournals(): Flow<List<Journal>> = journalDao.getAllJournals()

    suspend fun getAllJournalsOnce(): List<Journal> = journalDao.getAllJournalsOnce()
    
    suspend fun insertJournal(journal: Journal) = journalDao.insertJournal(journal)
    

    suspend fun getJournalById(id: String): Journal? = journalDao.getJournalById(id)

    suspend fun getAllEntriesForBackup(): List<JournalEntry> =
        journalDao.getAllEntriesForBackup()

    fun getAllEntries(journalId: String = "default"): Flow<List<JournalEntry>> = journalDao.getAllEntries(journalId)
    
    fun getTotalEntryCount(journalId: String = "default"): Flow<Int> = journalDao.getTotalEntryCount(journalId)
    
    fun getMoodDistribution(journalId: String = "default"): Flow<List<MoodCount>> = journalDao.getMoodDistribution(journalId)
    
    fun getEntryByDate(date: LocalDate, journalId: String = "default"): Flow<JournalEntry?> {
        return journalDao.getEntryByDateFlow(date, journalId)
    }
    
    suspend fun getEntryByDateOnce(date: LocalDate, journalId: String = "default"): JournalEntry? {
        return journalDao.getEntryByDateOnce(date, journalId)
    }
    

    fun searchEntries(query: String, journalId: String = "default"): Flow<List<JournalEntry>> {
        return journalDao.searchEntries(query, journalId)
    }
    
    suspend fun saveEntry(entry: JournalEntry) {
        journalDao.saveEntry(entry)
    }
    

    suspend fun deleteEntry(entry: JournalEntry) {
        journalDao.deleteEntry(entry)
    }
    
    suspend fun deleteEntryByDate(date: LocalDate, journalId: String = "default") {
        journalDao.deleteEntryByDate(date, journalId)
    }
    

    /**
     * Calculate current streak - consecutive days with entries (pure function, no mutation)
     */
    fun calculateStreak(
        entries: List<JournalEntry>
    ): Int {
        return calculateCurrentStreak(entries, LocalDate.now())
    }

    fun calculateCurrentStreak(
        entries: List<JournalEntry>,
        today: LocalDate = LocalDate.now(),
        frozenDates: Set<LocalDate> = emptySet()
    ): Int {
        return StreakCalculator.current(
            entryDates = entries.map { it.date }.toSet(),
            today = today,
            frozenDates = frozenDates
        )
    }
    
    /**
     * Get longest streak ever achieved
     */
    fun calculateLongestStreak(entries: List<JournalEntry>): Int {
        return StreakCalculator.longest(entries.map { it.date }.toSet())
    }
}
