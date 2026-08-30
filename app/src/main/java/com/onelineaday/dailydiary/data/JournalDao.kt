package com.onelineaday.dailydiary.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for journal entries
 */
@Dao
interface JournalDao {
    
    // --- Journal Queries ---
    
    @Query("SELECT * FROM journals ORDER BY createdAt ASC")
    fun getAllJournals(): Flow<List<Journal>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournal(journal: Journal)
    
    @Update
    suspend fun updateJournal(journal: Journal)
    
    @Delete
    suspend fun deleteJournal(journal: Journal)
    
    @Query("SELECT * FROM journals WHERE id = :id")
    suspend fun getJournalById(id: String): Journal?
    
    // --- Journal Entry Queries ---
    
    @Query("SELECT * FROM journal_entries WHERE journalId = :journalId ORDER BY date DESC")
    fun getAllEntries(journalId: String = "default"): Flow<List<JournalEntry>>
    
    @Query("SELECT * FROM journal_entries WHERE date = :date AND journalId = :journalId")
    suspend fun getEntryByDate(date: LocalDate, journalId: String = "default"): JournalEntry?
    
    @Query("SELECT * FROM journal_entries WHERE date = :date AND journalId = :journalId")
    fun getEntryByDateFlow(date: LocalDate, journalId: String = "default"): Flow<JournalEntry?>
    
    @Query("SELECT * FROM journal_entries WHERE date BETWEEN :startDate AND :endDate AND journalId = :journalId ORDER BY date DESC")
    fun getEntriesBetweenDates(startDate: LocalDate, endDate: LocalDate, journalId: String = "default"): Flow<List<JournalEntry>>
    
    @Query("SELECT * FROM journal_entries WHERE mood = :mood AND journalId = :journalId ORDER BY date DESC")
    fun getEntriesByMood(mood: Mood, journalId: String = "default"): Flow<List<JournalEntry>>
    
    @Query("SELECT COUNT(*) FROM journal_entries WHERE journalId = :journalId")
    fun getTotalEntryCount(journalId: String = "default"): Flow<Int>
    
    @Query("SELECT * FROM journal_entries WHERE journalId = :journalId ORDER BY date DESC LIMIT :limit")
    fun getRecentEntries(limit: Int, journalId: String = "default"): Flow<List<JournalEntry>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntry)
    
    @Update
    suspend fun updateEntry(entry: JournalEntry)
    
    @Delete
    suspend fun deleteEntry(entry: JournalEntry)
    
    @Query("DELETE FROM journal_entries WHERE date = :date AND journalId = :journalId")
    suspend fun deleteEntryByDate(date: LocalDate, journalId: String = "default")
    
    @Query("SELECT * FROM journal_entries WHERE content LIKE '%' || :query || '%' AND journalId = :journalId ORDER BY date DESC")
    fun searchEntries(query: String, journalId: String = "default"): Flow<List<JournalEntry>>
    
    // Stats queries
    @Query("""
        SELECT COUNT(DISTINCT date) FROM journal_entries 
        WHERE date >= :startDate AND journalId = :journalId
    """)
    suspend fun getEntryCountSince(startDate: LocalDate, journalId: String = "default"): Int
    
    @Query("SELECT mood, COUNT(*) as count FROM journal_entries WHERE journalId = :journalId GROUP BY mood ORDER BY count DESC")
    fun getMoodDistribution(journalId: String = "default"): Flow<List<MoodCount>>
}

data class MoodCount(
    val mood: Mood,
    val count: Int
)
