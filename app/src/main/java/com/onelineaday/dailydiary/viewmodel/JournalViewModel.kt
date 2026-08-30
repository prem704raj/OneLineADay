package com.onelineaday.dailydiary.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onelineaday.dailydiary.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.io.File
import java.io.FileOutputStream

data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val todayEntry: JournalEntry? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalEntries: Int = 0,
    val moodDistribution: List<MoodCount> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
    val searchQuery: String = "",
    val searchResults: List<JournalEntry> = emptyList(),
    val entrySaved: Boolean = false,  // Flag to trigger form reset
    val freezesAvailable: Int = 0,
    val activeJournalId: String = "default",
    val journals: List<Journal> = emptyList()
)

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = JournalDatabase.getDatabase(application)
    private val repository = JournalRepository(database.journalDao())
    
    private val prefs = application.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()
    
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    
    private val _activeJournalId = MutableStateFlow(prefs.getString("active_journal_id", "default") ?: "default")
    private var dataLoadJob: kotlinx.coroutines.Job? = null
    
    var lastDeletedEntry: JournalEntry? = null
        private set
    
    init {
        viewModelScope.launch {
            _activeJournalId.collect { journalId ->
                prefs.edit().putString("active_journal_id", journalId).apply()
                _uiState.update { it.copy(activeJournalId = journalId) }
                loadData(journalId)
            }
        }
        
        // Load journals list
        viewModelScope.launch {
            repository.getAllJournals().collect { journals ->
                _uiState.update { it.copy(journals = journals) }
            }
        }
    }
    
    fun setActiveJournal(journalId: String) {
        _activeJournalId.value = journalId
    }
    
    fun createJournal(name: String, colorHex: String? = null) {
        viewModelScope.launch {
            val journal = Journal(name = name, colorHex = colorHex)
            repository.insertJournal(journal)
            setActiveJournal(journal.id)
        }
    }
    
    private fun loadData(journalId: String) {
        dataLoadJob?.cancel()
        dataLoadJob = viewModelScope.launch {
            // Load all entries
            launch {
                repository.getAllEntries(journalId).collect { entries ->
                    val freezesAvailable = prefs.getInt("streak_freezes", 0)
                    val streak = repository.calculateStreak(entries, freezesAvailable) { consumed ->
                        val remaining = prefs.getInt("streak_freezes", 0) - consumed
                        prefs.edit().putInt("streak_freezes", maxOf(0, remaining)).apply()
                    }
                    val longestStreak = repository.calculateLongestStreak(entries)
                    
                    _uiState.update { 
                        it.copy(
                            entries = entries,
                            currentStreak = streak,
                            longestStreak = longestStreak,
                            totalEntries = entries.size,
                            isLoading = false,
                            freezesAvailable = prefs.getInt("streak_freezes", 0)
                        )
                    }
                }
            }
            
            // Load today's entry
            launch {
                repository.getEntryByDate(LocalDate.now(), journalId).collect { entry ->
                    _uiState.update { it.copy(todayEntry = entry) }
                }
            }
            
            // Load mood distribution
            launch {
                repository.getMoodDistribution(journalId).collect { distribution ->
                    _uiState.update { it.copy(moodDistribution = distribution) }
                }
            }
        }
    }
    
    fun saveEntry(content: String, mood: Mood, photoUri: String? = null, mediaUris: List<String> = emptyList(), tags: List<String> = emptyList()) {
        viewModelScope.launch {
            val journalId = _activeJournalId.value
            val existingEntry = repository.getEntryByDateOnce(_selectedDate.value, journalId)
            
            // Migrate old photoUri to mediaUris if necessary
            val finalMediaUris = mediaUris.toMutableList()
            if (photoUri != null && !finalMediaUris.contains(photoUri)) {
                finalMediaUris.add(0, photoUri)
            }
            
            val entry = if (existingEntry != null) {
                existingEntry.copy(
                    content = content,
                    mood = mood,
                    photoUri = photoUri,
                    mediaUris = finalMediaUris,
                    tags = tags,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                JournalEntry(
                    date = _selectedDate.value,
                    journalId = journalId,
                    content = content,
                    mood = mood,
                    photoUri = photoUri,
                    mediaUris = finalMediaUris,
                    tags = tags
                )
            }
            
            repository.saveEntry(entry)
            _uiState.update { it.copy(message = "Entry saved!", entrySaved = true) }
        }
    }
    
    fun clearEntrySavedFlag() {
        _uiState.update { it.copy(entrySaved = false) }
    }
    
    fun deleteEntry(entry: JournalEntry) {
        lastDeletedEntry = entry
        viewModelScope.launch {
            repository.deleteEntry(entry)
            _uiState.update { it.copy(message = "Entry deleted (Shake to undo)") }
        }
    }
    
    fun updateEntry(entry: JournalEntry, newContent: String, newMood: Mood, newPhotoUri: String?, newMediaUris: List<String> = emptyList()) {
        viewModelScope.launch {
            val updatedEntry = entry.copy(
                content = newContent,
                mood = newMood,
                photoUri = newPhotoUri,
                mediaUris = newMediaUris,
                updatedAt = System.currentTimeMillis()
            )
            repository.saveEntry(updatedEntry)
            _uiState.update { it.copy(message = "Entry updated!") }
        }
    }
    
    fun togglePin(entry: JournalEntry) {
        viewModelScope.launch {
            val updatedEntry = entry.copy(isPinned = !entry.isPinned)
            repository.saveEntry(updatedEntry)
            _uiState.update { it.copy(message = if (updatedEntry.isPinned) "Entry pinned" else "Entry unpinned") }
        }
    }
    
    fun claimFreeze() {
        val current = prefs.getInt("streak_freezes", 0)
        prefs.edit().putInt("streak_freezes", current + 1).apply()
        _uiState.update { 
            it.copy(
                freezesAvailable = current + 1,
                message = "Streak Freeze claimed!"
            )
        }
        loadData(_activeJournalId.value) // Recalculate streak in case a broken streak can now be repaired
    }
    
    fun deleteEntryByDate(date: LocalDate) {
        viewModelScope.launch {
            val entry = repository.getEntryByDateOnce(date, _activeJournalId.value)
            if (entry != null) {
                lastDeletedEntry = entry
            }
            repository.deleteEntryByDate(date, _activeJournalId.value)
            _uiState.update { it.copy(message = "Entry deleted (Shake to undo)") }
        }
    }
    
    fun undoDelete() {
        val entryToRestore = lastDeletedEntry ?: return
        lastDeletedEntry = null
        viewModelScope.launch {
            repository.saveEntry(entryToRestore)
            _uiState.update { it.copy(message = "Entry restored!") }
            loadData(_activeJournalId.value)
        }
    }
    
    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
        _uiState.update { it.copy(selectedDate = date) }
        
        viewModelScope.launch {
            repository.getEntryByDate(date).collect { entry ->
                _uiState.update { it.copy(todayEntry = entry) }
            }
        }
    }
    
    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        
        viewModelScope.launch {
            repository.searchEntries(query).collect { results ->
                _uiState.update { it.copy(searchResults = results) }
            }
        }
    }
    
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
    
    fun saveMediaToInternal(context: Context, sourceUri: Uri): String? {
        return try {
            val mimeType = context.contentResolver.getType(sourceUri)
            val isVideo = mimeType?.startsWith("video") == true
            
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val ext = if (isVideo) ".mp4" else ".jpg"
            val prefix = if (isVideo) "video_" else "photo_"
            val fileName = "${prefix}${System.currentTimeMillis()}$ext"
            val file = File(context.filesDir, fileName)
            
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun getEntriesForMonth(year: Int, month: Int): List<JournalEntry> {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        
        return _uiState.value.entries.filter { entry ->
            entry.date >= startDate && entry.date <= endDate
        }
    }
    
    fun getFormattedDate(date: LocalDate): String {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        
        return when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))
        }
    }
    
    fun getRelativeDate(date: LocalDate): String {
        val today = LocalDate.now()
        val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(date, today).toInt()
        
        return when {
            daysDiff == 0 -> "Today"
            daysDiff == 1 -> "Yesterday"
            daysDiff < 7 -> "$daysDiff days ago"
            daysDiff < 30 -> "${daysDiff / 7} weeks ago"
            daysDiff < 365 -> "${daysDiff / 30} months ago"
            else -> "${daysDiff / 365} years ago"
        }
    }
    
    fun exportBackup(context: Context): Uri? {
        return try {
            val dbFolder = context.getDatabasePath("journal_database").parentFile
            val filesDir = context.filesDir
            
            val backupFile = File(context.cacheDir, "OneLineADay_Backup_${System.currentTimeMillis()}.zip")
            val zipOut = java.util.zip.ZipOutputStream(FileOutputStream(backupFile))
            
            // Backup DB files
            dbFolder?.listFiles()?.forEach { file ->
                if (file.name.startsWith("journal_database")) {
                    val entry = java.util.zip.ZipEntry("db/${file.name}")
                    zipOut.putNextEntry(entry)
                    file.inputStream().copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
            
            // Backup Images and Videos
            filesDir.listFiles()?.forEach { file ->
                if ((file.name.startsWith("photo_") && file.name.endsWith(".jpg")) ||
                    (file.name.startsWith("video_") && file.name.endsWith(".mp4"))) {
                    val entry = java.util.zip.ZipEntry("media/${file.name}")
                    zipOut.putNextEntry(entry)
                    file.inputStream().copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
            
            // Backup Audio Memories
            filesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("audio_") && file.name.endsWith(".m4a")) {
                    val entry = java.util.zip.ZipEntry("audio/${file.name}")
                    zipOut.putNextEntry(entry)
                    file.inputStream().copyTo(zipOut)
                    zipOut.closeEntry()
                }
            }
            
            zipOut.close()
            
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                backupFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun importBackup(context: Context, backupUri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val dbFolder = context.getDatabasePath("journal_database").parentFile
                val filesDir = context.filesDir
                
                context.contentResolver.openInputStream(backupUri)?.use { input ->
                    val zipIn = java.util.zip.ZipInputStream(input)
                    var entry = zipIn.nextEntry
                    
                    while (entry != null) {
                        val file = if (entry.name.startsWith("db/")) {
                            File(dbFolder, entry.name.removePrefix("db/"))
                        } else if (entry.name.startsWith("photos/") || entry.name.startsWith("media/")) {
                            val targetName = entry.name.removePrefix("photos/").removePrefix("media/")
                            File(filesDir, targetName)
                        } else if (entry.name.startsWith("audio/")) {
                            File(filesDir, entry.name.removePrefix("audio/"))
                        } else {
                            null
                        }
                        
                        file?.let { f ->
                            if (f.exists()) f.delete()
                            FileOutputStream(f).use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                        
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
                
                _uiState.update { it.copy(message = "Backup restored successfully! Please restart the app.") }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(message = "Failed to restore backup.") }
            }
        }
    }
    
    suspend fun exportToPdf(context: Context): android.net.Uri? {
        val state = _uiState.value
        return com.onelineaday.dailydiary.utils.PdfExportHelper.generateJournalPdf(
            context, 
            state.entries,
            state.currentStreak,
            state.longestStreak,
            state.totalEntries
        )
    }
}
