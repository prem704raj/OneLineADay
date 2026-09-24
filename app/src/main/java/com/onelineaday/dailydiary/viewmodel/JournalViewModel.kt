package com.onelineaday.dailydiary.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onelineaday.dailydiary.backup.BackupArchiveManager
import com.onelineaday.dailydiary.backup.BackupExportResult
import com.onelineaday.dailydiary.backup.BackupRestoreResult
import com.onelineaday.dailydiary.data.*
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.media.MediaStorageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val activeJournalId: String = "default",
    val journals: List<Journal> = emptyList(),
    val isSaving: Boolean = false
)

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = JournalDatabase.getDatabase(application)
    private val repository = JournalRepository(database.journalDao())
    private val mediaStorage = MediaStorageManager(application)
    private val backupManager = BackupArchiveManager(application, database)
    
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()
    
    private val _todayDate = MutableStateFlow(LocalDate.now())
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    
    private val _activeJournalId = MutableStateFlow(prefs.getString("active_journal_id", "default") ?: "default")
    private var dataLoadJob: Job? = null
    private var selectedDateJob: Job? = null
    private var searchJob: Job? = null
    
    private val saveMutex = Mutex()
    
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

        viewModelScope.launch {
            runCatching {
                cleanupOrphansNow()
            }
        }

        setSelectedDate(LocalDate.now())
    }

    fun refreshToday() {
        val now = LocalDate.now()
        if (_todayDate.value != now) {
            val wasTrackingToday = _selectedDate.value == _todayDate.value
            _todayDate.value = now

            if (wasTrackingToday) {
                _selectedDate.value = now
                _uiState.update { it.copy(selectedDate = now) }
            }

            loadData(_activeJournalId.value)
            setSelectedDate(_selectedDate.value)
        }
    }
    
    fun setActiveJournal(journalId: String) {
        if (_activeJournalId.value == journalId) return

        searchJob?.cancel()
        selectedDateJob?.cancel()

        _uiState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList()
            )
        }

        _activeJournalId.value = journalId
        com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(getApplication())
        setSelectedDate(_selectedDate.value)
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
                    val streak = repository.calculateStreak(entries)
                    val longestStreak = repository.calculateLongestStreak(entries)
                    
                    _uiState.update { 
                        it.copy(
                            entries = entries,
                            currentStreak = streak,
                            longestStreak = longestStreak,
                            totalEntries = entries.size,
                            isLoading = false
                        )
                    }
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
    
    fun saveEntry(
        content: String,
        mood: Mood,
        photoUri: String? = null,
        mediaUris: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        audioUri: String? = null
    ) {
        viewModelScope.launch {
            if (!saveMutex.tryLock()) return@launch

            _uiState.update { it.copy(isSaving = true) }

            try {
                refreshToday()

                val journalId = _activeJournalId.value
                val date = _selectedDate.value

                val existingEntry = repository.getEntryByDateOnce(date, journalId)

                val finalMediaUris =
                    mediaUris
                        .filter { it.isNotBlank() }
                        .distinct()
                        .toMutableList()

                if (photoUri != null && photoUri.isNotBlank() && photoUri !in finalMediaUris) {
                    finalMediaUris.add(0, photoUri)
                }

                val normalizedTags =
                    tags
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinctBy { it.lowercase() }

                val now = System.currentTimeMillis()

                val entry =
                    if (existingEntry != null) {
                        existingEntry.copy(
                            content = content,
                            mood = mood,
                            photoUri = photoUri,
                            mediaUris = finalMediaUris,
                            tags = normalizedTags,
                            audioUri = audioUri,
                            updatedAt = now
                        )
                    } else {
                        JournalEntry(
                            date = date,
                            journalId = journalId,
                            content = content,
                            mood = mood,
                            photoUri = photoUri,
                            mediaUris = finalMediaUris,
                            tags = normalizedTags,
                            audioUri = audioUri,
                            createdAt = now,
                            updatedAt = now
                        )
                    }

                repository.saveEntry(entry)
                com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(getApplication())
                cleanupNoLongerReferenced(existingEntry, entry)
                _uiState.update {
                    it.copy(
                        message = getApplication<Application>().getString(R.string.entry_saved),
                        entrySaved = true
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(message = getApplication<Application>().getString(R.string.save_failed))
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                saveMutex.unlock()
            }
        }
    }
    
    fun clearEntrySavedFlag() {
        _uiState.update { it.copy(entrySaved = false) }
    }
    
    fun deleteEntry(entry: JournalEntry) {
        lastDeletedEntry = entry
        viewModelScope.launch {
            repository.deleteEntry(entry)
            com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(getApplication())
            _uiState.update { it.copy(message = getApplication<Application>().getString(R.string.entry_deleted_undo)) }
        }
    }
    
    fun updateEntry(
        entry: JournalEntry,
        newContent: String,
        newMood: Mood,
        newPhotoUri: String?,
        newMediaUris: List<String> = emptyList(),
        newAudioUri: String? = null,
        newTags: List<String> = entry.tags
    ) {
        viewModelScope.launch {
            val updatedEntry = entry.copy(
                content = newContent,
                mood = newMood,
                photoUri = newPhotoUri,
                mediaUris = newMediaUris,
                audioUri = newAudioUri,
                tags = newTags,
                updatedAt = System.currentTimeMillis()
            )
            repository.saveEntry(updatedEntry)
            com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(getApplication())
            cleanupNoLongerReferenced(entry, updatedEntry)
            _uiState.update { it.copy(message = getApplication<Application>().getString(R.string.entry_updated)) }
        }
    }
    
    fun togglePin(entry: JournalEntry) {
        viewModelScope.launch {
            val updatedEntry = entry.copy(isPinned = !entry.isPinned)
            repository.saveEntry(updatedEntry)
            _uiState.update { it.copy(message = if (updatedEntry.isPinned) getApplication<Application>().getString(R.string.entry_pinned) else getApplication<Application>().getString(R.string.entry_unpinned)) }
        }
    }
    
    fun deleteEntryByDate(date: LocalDate) {
        viewModelScope.launch {
            val entry = repository.getEntryByDateOnce(date, _activeJournalId.value)
            if (entry != null) {
                lastDeletedEntry = entry
            }
            repository.deleteEntryByDate(date, _activeJournalId.value)
            com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(getApplication())
            _uiState.update { it.copy(message = getApplication<Application>().getString(R.string.entry_deleted_undo)) }
        }
    }
    
    fun undoDelete() {
        val entryToRestore = lastDeletedEntry ?: return
        lastDeletedEntry = null
        viewModelScope.launch {
            repository.saveEntry(entryToRestore)
            com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(getApplication())
            _uiState.update { it.copy(message = getApplication<Application>().getString(R.string.entry_restored)) }
            loadData(_activeJournalId.value)
        }
    }
    
    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
        _uiState.update { it.copy(selectedDate = date) }
        
        selectedDateJob?.cancel()
        val journalId = _activeJournalId.value

        selectedDateJob = viewModelScope.launch {
            repository.getEntryByDate(date, journalId).collectLatest { entry ->
                _uiState.update { it.copy(todayEntry = entry) }
            }
        }
    }
    
    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        
        val journalId = _activeJournalId.value

        searchJob = viewModelScope.launch {
            delay(250)
            repository.searchEntries(query.trim(), journalId).collectLatest { results ->
                if (_uiState.value.searchQuery.trim() == query.trim() && _activeJournalId.value == journalId) {
                    _uiState.update { it.copy(searchResults = results) }
                }
            }
        }
    }
    
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
    
    suspend fun importMedia(sourceUri: Uri): String? {
        return mediaStorage.importVisualMedia(sourceUri).getOrNull()?.absolutePath
    }

    private fun JournalEntry.attachmentPaths(): Set<String> = buildSet {
        photoUri?.let(::add)
        addAll(mediaUris)
        audioUri?.let(::add)
    }

    private suspend fun cleanupNoLongerReferenced(
        previous: JournalEntry?,
        current: JournalEntry?
    ) {
        if (previous == null) {
            return
        }

        val candidates = previous.attachmentPaths() - (current?.attachmentPaths() ?: emptySet())
        if (candidates.isEmpty()) {
            return
        }

        val allEntries = repository.getAllEntriesForBackup()
        val stillReferenced = allEntries.flatMap { it.attachmentPaths() }.toSet()

        candidates.forEach { path ->
            mediaStorage.deleteIfUnreferenced(
                path = path,
                referencedPaths = stillReferenced
            )
        }
    }

    private suspend fun cleanupOrphansNow() {
        val entries = repository.getAllEntriesForBackup()
        val referenced = entries.flatMap { it.attachmentPaths() }.toMutableSet()

        // Preserve current one-level shake undo.
        lastDeletedEntry?.attachmentPaths()?.let {
            referenced.addAll(it)
        }

        mediaStorage.cleanupOrphans(referenced)
    }
    

    suspend fun exportBackup(): BackupExportResult {
        return backupManager.exportBackup()
    }

    suspend fun restoreBackup(backupUri: Uri): BackupRestoreResult {
        val result = backupManager.restoreBackup(backupUri)
        if (result is BackupRestoreResult.Success) {
            loadData(_activeJournalId.value)
            setSelectedDate(_selectedDate.value)
        }
        return result
    }


    suspend fun exportToPdf(context: Context): Uri? {
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
