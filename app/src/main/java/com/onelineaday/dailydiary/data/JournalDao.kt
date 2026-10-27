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

    @Query("SELECT * FROM journals ORDER BY createdAt ASC")
    suspend fun getAllJournalsOnce(): List<Journal>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournal(journal: Journal)
    

    @Query("SELECT * FROM journals WHERE id = :id")
    suspend fun getJournalById(id: String): Journal?

    @Query("SELECT COUNT(*) FROM journals")
    suspend fun getJournalCountOnce(): Int
    
    // --- Journal Entry Queries ---
    
    @Query("SELECT * FROM journal_entries WHERE journalId = :journalId ORDER BY date DESC")
    fun getAllEntries(journalId: String = "default"): Flow<List<JournalEntry>>
    
    @Query("SELECT * FROM journal_entries WHERE date = :date AND journalId = :journalId LIMIT 1")
    suspend fun getEntryByDate(date: LocalDate, journalId: String = "default"): JournalEntry?

    @Query("SELECT * FROM journal_entries WHERE date = :date AND journalId = :journalId LIMIT 1")
    suspend fun getEntryByDateOnce(date: LocalDate, journalId: String = "default"): JournalEntry?
    
    @Query("SELECT * FROM journal_entries WHERE date = :date AND journalId = :journalId LIMIT 1")
    fun getEntryByDateFlow(date: LocalDate, journalId: String = "default"): Flow<JournalEntry?>
    
    @Query("""
        SELECT * FROM journal_entries 
        WHERE journalId = :journalId 
          AND date IN (:dates) 
        ORDER BY date DESC
    """)
    fun getEntriesForDates(dates: List<LocalDate>, journalId: String = "default"): Flow<List<JournalEntry>>

    @Query("""
        SELECT * FROM journal_entries 
        WHERE journalId = :journalId 
          AND date IN (:dates) 
        ORDER BY date DESC
    """)
    suspend fun getEntriesForDatesOnce(dates: List<LocalDate>, journalId: String = "default"): List<JournalEntry>
    

    @Query("SELECT COUNT(*) FROM journal_entries WHERE journalId = :journalId")
    fun getTotalEntryCount(journalId: String = "default"): Flow<Int>

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun getTotalEntryCountOnce(): Int
    
    @Upsert
    suspend fun saveEntry(entry: JournalEntry)
    

    @Delete
    suspend fun deleteEntry(entry: JournalEntry)
    
    @Query("DELETE FROM journal_entries WHERE date = :date AND journalId = :journalId")
    suspend fun deleteEntryByDate(date: LocalDate, journalId: String = "default")
    
    @Query("""
        SELECT * FROM journal_entries 
        WHERE journalId = :journalId 
          AND (
            content LIKE '%' || :query || '%'
            OR tags LIKE '%' || :query || '%'
          )
        ORDER BY date DESC
    """)
    fun searchEntries(query: String, journalId: String = "default"): Flow<List<JournalEntry>>
    
    // Stats queries

    @Query("SELECT mood, COUNT(*) as count FROM journal_entries WHERE journalId = :journalId GROUP BY mood ORDER BY count DESC")
    fun getMoodDistribution(journalId: String = "default"): Flow<List<MoodCount>>

    // Backup & Restore queries
    @Query("SELECT * FROM journal_entries ORDER BY journalId ASC, date ASC")
    suspend fun getAllEntriesForBackup(): List<JournalEntry>

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAllEntriesForRestore()

    @Query("DELETE FROM journals")
    suspend fun deleteAllJournalsForRestore()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJournalsForRestore(journals: List<Journal>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntriesForRestore(entries: List<JournalEntry>)

    // --- Streak Freeze Queries ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreakFreeze(freeze: StreakFreeze)

    @Query("SELECT * FROM streak_freezes WHERE journalId = :journalId ORDER BY date DESC")
    fun getStreakFreezes(journalId: String = "default"): Flow<List<StreakFreeze>>

    @Query("SELECT * FROM streak_freezes WHERE journalId = :journalId ORDER BY date DESC")
    suspend fun getStreakFreezesOnce(journalId: String = "default"): List<StreakFreeze>

    @Query("DELETE FROM streak_freezes WHERE date = :date AND journalId = :journalId")
    suspend fun deleteStreakFreeze(date: LocalDate, journalId: String = "default")

    @Query("SELECT * FROM streak_freezes ORDER BY journalId ASC, date ASC")
    suspend fun getAllStreakFreezesForBackup(): List<StreakFreeze>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreakFreezesForRestore(freezes: List<StreakFreeze>)
}

data class MoodCount(
    val mood: Mood,
    val count: Int
)