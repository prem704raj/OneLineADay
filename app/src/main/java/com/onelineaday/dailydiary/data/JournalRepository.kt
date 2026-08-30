package com.onelineaday.dailydiary.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository for managing journal entries
 */
class JournalRepository(private val journalDao: JournalDao) {
    
    fun getAllJournals(): Flow<List<Journal>> = journalDao.getAllJournals()
    
    suspend fun insertJournal(journal: Journal) = journalDao.insertJournal(journal)
    
    suspend fun updateJournal(journal: Journal) = journalDao.updateJournal(journal)
    
    suspend fun deleteJournal(journal: Journal) = journalDao.deleteJournal(journal)
    
    suspend fun getJournalById(id: String): Journal? = journalDao.getJournalById(id)

    fun getAllEntries(journalId: String = "default"): Flow<List<JournalEntry>> = journalDao.getAllEntries(journalId)
    
    fun getTotalEntryCount(journalId: String = "default"): Flow<Int> = journalDao.getTotalEntryCount(journalId)
    
    fun getMoodDistribution(journalId: String = "default"): Flow<List<MoodCount>> = journalDao.getMoodDistribution(journalId)
    
    fun getEntryByDate(date: LocalDate, journalId: String = "default"): Flow<JournalEntry?> {
        return journalDao.getEntryByDateFlow(date, journalId)
    }
    
    suspend fun getEntryByDateOnce(date: LocalDate, journalId: String = "default"): JournalEntry? {
        return journalDao.getEntryByDate(date, journalId)
    }
    
    fun getEntriesBetweenDates(startDate: LocalDate, endDate: LocalDate, journalId: String = "default"): Flow<List<JournalEntry>> {
        return journalDao.getEntriesBetweenDates(startDate, endDate, journalId)
    }
    
    fun getEntriesByMood(mood: Mood, journalId: String = "default"): Flow<List<JournalEntry>> {
        return journalDao.getEntriesByMood(mood, journalId)
    }
    
    fun getRecentEntries(limit: Int = 30, journalId: String = "default"): Flow<List<JournalEntry>> {
        return journalDao.getRecentEntries(limit, journalId)
    }
    
    fun searchEntries(query: String, journalId: String = "default"): Flow<List<JournalEntry>> {
        return journalDao.searchEntries(query, journalId)
    }
    
    suspend fun saveEntry(entry: JournalEntry) {
        journalDao.insertEntry(entry)
    }
    
    suspend fun updateEntry(entry: JournalEntry) {
        journalDao.updateEntry(entry.copy(updatedAt = System.currentTimeMillis()))
    }
    
    suspend fun deleteEntry(entry: JournalEntry) {
        journalDao.deleteEntry(entry)
    }
    
    suspend fun deleteEntryByDate(date: LocalDate, journalId: String = "default") {
        journalDao.deleteEntryByDate(date, journalId)
    }
    
    suspend fun getEntryCountSince(startDate: LocalDate, journalId: String = "default"): Int {
        return journalDao.getEntryCountSince(startDate, journalId)
    }
    
    /**
     * Calculate current streak - consecutive days with entries
     */
    suspend fun calculateStreak(
        entries: List<JournalEntry>, 
        freezesAvailable: Int = 0,
        onFreezesConsumed: (Int) -> Unit = {}
    ): Int {
        if (entries.isEmpty()) return 0
        
        val sortedDates = entries.map { it.date }.sortedDescending()
        val today = LocalDate.now()
        
        val firstDate = sortedDates.firstOrNull() ?: return 0
        
        var streak = 1
        var currentDate = firstDate
        var remainingFreezes = freezesAvailable
        var freezesConsumed = 0
        
        // Check if there's an entry for today or yesterday. If not, check if we can use freezes.
        val daysSinceFirst = java.time.temporal.ChronoUnit.DAYS.between(firstDate, today).toInt()
        if (daysSinceFirst > 1) {
            val missedDays = daysSinceFirst - 1 // If firstDate is 2 days ago, missed 1 day
            if (remainingFreezes >= missedDays) {
                remainingFreezes -= missedDays
                freezesConsumed += missedDays
                streak += missedDays // Count the frozen days as part of the streak? Or just bridge them? Usually freezes don't add to the count, just bridge. Wait, usually streak freezes keep the number exactly as it was, but for simplicity we can just add them or leave streak alone. Let's just keep the streak count to not include the missed days, or include them so it doesn't drop. Usually it just bridges.
                // Let's NOT add missedDays to streak, just bridge.
            } else {
                return 0
            }
        }
        
        for (i in 1 until sortedDates.size) {
            val nextDate = sortedDates[i]
            val gap = java.time.temporal.ChronoUnit.DAYS.between(nextDate, currentDate).toInt()
            
            if (gap == 1) {
                streak++
                currentDate = nextDate
            } else if (gap > 1) {
                val missedDays = gap - 1
                if (remainingFreezes >= missedDays) {
                    remainingFreezes -= missedDays
                    freezesConsumed += missedDays
                    streak++ // Add the actual entry day
                    currentDate = nextDate
                } else {
                    break
                }
            }
        }
        
        if (freezesConsumed > 0) {
            onFreezesConsumed(freezesConsumed)
        }
        
        return streak
    }
    
    /**
     * Get longest streak ever achieved
     */
    suspend fun calculateLongestStreak(entries: List<JournalEntry>): Int {
        if (entries.isEmpty()) return 0
        
        val sortedDates = entries.map { it.date }.distinct().sorted()
        var maxStreak = 1
        var currentStreak = 1
        
        for (i in 1 until sortedDates.size) {
            if (sortedDates[i] == sortedDates[i-1].plusDays(1)) {
                currentStreak++
                maxStreak = maxOf(maxStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }
        
        return maxStreak
    }
}
