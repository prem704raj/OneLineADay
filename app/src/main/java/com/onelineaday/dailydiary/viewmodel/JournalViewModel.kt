package com.onelineaday.dailydiary.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onelineaday.dailydiary.backup.BackupArchiveManager
import com.onelineaday.dailydiary.backup.BackupExportResult
import com.onelineaday.dailydiary.backup.BackupRestoreResult
import com.onelineaday.dailydiary.backup.BackupPreviewResult
import com.onelineaday.dailydiary.data.*
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.media.MediaStorageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.onelineaday.dailydiary.memory.FlashbackMemory
import com.onelineaday.dailydiary.memory.FlashbackRepository
import com.onelineaday.dailydiary.prompts.DailySparkPrompt
import com.onelineaday.dailydiary.prompts.PromptCategory
import com.onelineaday.dailydiary.prompts.PromptPreferences
import com.onelineaday.dailydiary.prompts.PromptRotationEngine

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
    val isSaving: Boolean = false,
    val dailyPrompt: DailySparkPrompt? = null,
    val isPromptAnswered: Boolean = false,
    val promptCategoryFilter: PromptCategory? = null,
    val promptShuffleOffset: Int = 0,
    val streakFreezes: List<StreakFreeze> = emptyList(),
    val availableFreezes: Int = 0,
    val restDays: Set<java.time.DayOfWeek> = emptySet(),
    val isAiOptIn: Boolean = false
)

class JournalViewModel @JvmOverloads constructor(
    application: Application,
    val draftManager: DraftManager = DraftManager(application)
) : AndroidViewModel(application) {
    
    private val database = JournalDatabase.getDatabase(application)
    private val repository = JournalRepository(database.journalDao())
    val streakManager = StreakManager(repository, application)
    val aiKeyManager = com.onelineaday.dailydiary.ai.privacy.AiKeyManager(application)
    private val mediaStorage = MediaStorageManager(application)
    private val backupManager = BackupArchiveManager(application, database)
    private val flashbackRepository = FlashbackRepository(database.journalDao())
    private val promptPreferences = PromptPreferences(application)
    
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(JournalUiState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()
    
    private val _todayDate = MutableStateFlow(LocalDate.now())
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    
    private val _activeJournalId = MutableStateFlow(prefs.getString("active_journal_id", "default") ?: "default")
    private var dataLoadJob: Job? = null
    private var selectedDateJob: Job? = null
    private var searchJob: Job? = null
    private var draftAutosaveJob: Job? = null
    
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

        setSelectedDate(LocalDate.now())

        viewModelScope.launch(Dispatchers.IO) {
            try {
                cleanupOrphansNow()
            } catch (e: Exception) {
                android.util.Log.w("JournalViewModel", "Initial orphan cleanup skipped: ${e.message}")
            }
        }
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

    val duoJournalManager by lazy {
        com.onelineaday.dailydiary.duo.DuoJournalManager(repository, getApplication())
    }

    fun createDuoJournal(partnerName: String, pin: String) {
        viewModelScope.launch {
            val journal = duoJournalManager.createDuoJournal(partnerName, pin)
            setActiveJournal(journal.id)
        }
    }

    fun verifyDuoPin(inputPin: String): Boolean {
        return duoJournalManager.verifyDuoPin(inputPin)
    }

    fun isDuoJournal(journalId: String): Boolean {
        return journalId.startsWith("duo_") || duoJournalManager.getDuoJournalId() == journalId
    }
    
    private fun loadData(journalId: String) {
        dataLoadJob?.cancel()
        dataLoadJob = viewModelScope.launch {
            // Check & auto-apply streak freeze if yesterday was missed and inventory available
            launch(Dispatchers.IO) {
                runCatching {
                    streakManager.autoApplyFreezeIfNeeded(LocalDate.now(), journalId)
                }
            }

            // Observe streak freezes
            launch {
                repository.getStreakFreezes(journalId).collect { freezes ->
                    val available = streakManager.getAvailableFreezesCount(journalId)
                    val restDays = streakManager.getRestDays()
                    val entries = _uiState.value.entries
                    val frozenDates = freezes.map { it.date }.toSet()
                    val restDates = (0..365).map { LocalDate.now().minusDays(it.toLong()) }
                        .filter { it.dayOfWeek in restDays }.toSet()
                    val streak = repository.calculateCurrentStreak(entries, LocalDate.now(), frozenDates + restDates)

                    _uiState.update {
                        it.copy(
                            streakFreezes = freezes,
                            availableFreezes = available,
                            restDays = restDays,
                            currentStreak = streak,
                            isAiOptIn = aiKeyManager.isCloudAiOptIn()
                        )
                    }
                }
            }

            // Load all entries
            launch {
                repository.getAllEntries(journalId).collect { entries ->
                    val frozenDates = _uiState.value.streakFreezes.map { it.date }.toSet()
                    val restDays = streakManager.getRestDays()
                    val restDates = (0..365).map { LocalDate.now().minusDays(it.toLong()) }
                        .filter { it.dayOfWeek in restDays }.toSet()
                    val streak = repository.calculateCurrentStreak(entries, LocalDate.now(), frozenDates + restDates)
                    val longestStreak = repository.calculateLongestStreak(entries)
                    val available = streakManager.getAvailableFreezesCount(journalId)

                    _uiState.update { 
                        it.copy(
                            entries = entries,
                            currentStreak = streak,
                            longestStreak = longestStreak,
                            totalEntries = entries.size,
                            availableFreezes = available,
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

    fun distillText(rawText: String): String {
        return com.onelineaday.dailydiary.ai.distill.LocalTextDistillationEngine.distillToSingleLine(rawText)
    }

    fun setRestDays(days: Set<java.time.DayOfWeek>) {
        streakManager.setRestDays(days)
        loadData(_activeJournalId.value)
    }
    
    /**
     * Debounced autosave for uncommitted journal drafts (500ms delay).
     * Cancels any pending autosave job and schedules a new one on Dispatchers.IO.
     * If all input fields are blank/empty, clears the active draft.
     */
    fun autosaveDraftDebounced(
        text: String,
        mood: String,
        mediaUris: List<String>,
        tags: List<String>,
        audioUri: String?
    ) {
        draftAutosaveJob?.cancel()
        draftAutosaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            if (text.isBlank() && mediaUris.isEmpty() && tags.isEmpty() && audioUri.isNullOrBlank()) {
                draftManager.clearDraft()
            } else {
                draftManager.saveDraft(
                    JournalDraft(
                        text = text,
                        mood = mood,
                        mediaUris = mediaUris,
                        tags = tags,
                        audioUri = audioUri,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * Synchronously saves the draft immediately, cancelling any pending debounced job.
     */
    fun saveDraftNow(
        text: String,
        mood: String,
        mediaUris: List<String>,
        tags: List<String>,
        audioUri: String?
    ) {
        cancelDraftAutosave()
        if (text.isBlank() && mediaUris.isEmpty() && tags.isEmpty() && audioUri.isNullOrBlank()) {
            draftManager.clearDraft()
        } else {
            draftManager.saveDraft(
                JournalDraft(
                    text = text,
                    mood = mood,
                    mediaUris = mediaUris,
                    tags = tags,
                    audioUri = audioUri,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Cancels any pending autosave debounce job.
     */
    fun cancelDraftAutosave() {
        draftAutosaveJob?.cancel()
        draftAutosaveJob = null
    }

    /**
     * Clears persistent draft immediately and cancels any pending autosave job.
     */
    fun clearDraft() {
        cancelDraftAutosave()
        draftManager.clearDraft()
    }

    /**
     * Loads the current persistent draft.
     */
    fun loadDraft(): JournalDraft = draftManager.loadDraft()

    fun saveEntry(
        content: String,
        mood: Mood,
        photoUri: String? = null,
        mediaUris: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        audioUri: String? = null
    ) {
        viewModelScope.launch {
            saveMutex.withLock {
                _uiState.update { it.copy(isSaving = true) }

                try {
                    refreshToday()

                    val journalId = _activeJournalId.value
                    val date = _selectedDate.value

                    if (date.isAfter(LocalDate.now())) {
                        _uiState.update {
                            it.copy(message = getApplication<Application>().getString(R.string.future_date_not_allowed))
                        }
                        return@withLock
                    }

                    val existingEntry = repository.getEntryByDateOnce(date, journalId)

                    val finalMediaUris =
                        mediaUris
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(10)
                            .toMutableList()

                    if (photoUri != null && photoUri.isNotBlank() && photoUri !in finalMediaUris && finalMediaUris.size < 10) {
                        finalMediaUris.add(0, photoUri)
                    }

                    val primaryPhoto = photoUri ?: finalMediaUris.firstOrNull()

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
                                photoUri = primaryPhoto,
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
                                photoUri = primaryPhoto,
                                mediaUris = finalMediaUris,
                                tags = normalizedTags,
                                audioUri = audioUri,
                                createdAt = now,
                                updatedAt = now
                            )
                        }

                    repository.saveEntry(entry)
                    cancelDraftAutosave()
                    draftManager.clearDraft()
                    com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(getApplication())
                    _uiState.value.dailyPrompt?.let { prompt ->
                        promptPreferences.markPromptAnswered(date, prompt.id)
                    }
                    _uiState.update {
                        it.copy(
                            message = getApplication<Application>().getString(R.string.entry_saved),
                            entrySaved = true,
                            isPromptAnswered = true
                        )
                    }

                    runCatching {
                        cleanupNoLongerReferenced(existingEntry, entry)
                    }.onFailure { e ->
                        android.util.Log.w("JournalViewModel", "Post-save orphan cleanup error: ${e.message}")
                    }
                } catch (t: Throwable) {
                    _uiState.update {
                        it.copy(message = getApplication<Application>().getString(R.string.save_failed))
                    }
                } finally {
                    _uiState.update { it.copy(isSaving = false) }
                }
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
            val finalMedia = newMediaUris.filter { it.isNotBlank() }.distinct().take(10)
            val primaryPhoto = newPhotoUri ?: finalMedia.firstOrNull()
            val updatedEntry = entry.copy(
                content = newContent,
                mood = newMood,
                photoUri = primaryPhoto,
                mediaUris = finalMedia,
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
        updateDailyPromptForDate(date)
        
        selectedDateJob?.cancel()
        val journalId = _activeJournalId.value

        selectedDateJob = viewModelScope.launch {
            repository.getEntryByDate(date, journalId).collectLatest { entry ->
                _uiState.update { it.copy(todayEntry = entry) }
            }
        }
    }

    // --- On This Day / Flashback Methods ---

    fun getFlashbacksFlow(
        date: LocalDate = _uiState.value.selectedDate,
        historyYears: Int = 5,
        chronologicalAscending: Boolean = false
    ): Flow<List<FlashbackMemory>> {
        return flashbackRepository.getFlashbacksFlow(
            date = date,
            journalId = _uiState.value.activeJournalId,
            historyYears = historyYears,
            chronologicalAscending = chronologicalAscending
        )
    }

    suspend fun getFlashbacksOnce(
        date: LocalDate = _uiState.value.selectedDate,
        historyYears: Int = 5,
        chronologicalAscending: Boolean = false
    ): List<FlashbackMemory> {
        return flashbackRepository.getFlashbacksOnce(
            date = date,
            journalId = _uiState.value.activeJournalId,
            historyYears = historyYears,
            chronologicalAscending = chronologicalAscending
        )
    }

    // --- Daily Spark Prompts Methods ---

    fun updateDailyPromptForDate(date: LocalDate = _uiState.value.selectedDate) {
        val categoryFilter = promptPreferences.getSelectedCategoryFilter()
        val shuffleOffset = promptPreferences.getShuffleOffset(date)

        val prompt = if (categoryFilter != null) {
            PromptRotationEngine.promptForDateAndCategory(date, categoryFilter, shuffleOffset)
        } else {
            PromptRotationEngine.promptForDate(date, shuffleOffset)
        }

        val isAnswered = promptPreferences.isDatePromptAnswered(date) ||
                promptPreferences.isPromptAnswered(prompt.id)

        _uiState.update {
            it.copy(
                dailyPrompt = prompt,
                isPromptAnswered = isAnswered,
                promptCategoryFilter = categoryFilter,
                promptShuffleOffset = shuffleOffset
            )
        }
    }

    fun shuffleDailyPrompt() {
        val date = _uiState.value.selectedDate
        promptPreferences.incrementShuffleOffset(date)
        updateDailyPromptForDate(date)
    }

    fun setPromptCategory(category: PromptCategory?) {
        promptPreferences.setSelectedCategoryFilter(category)
        val date = _uiState.value.selectedDate
        updateDailyPromptForDate(date)
    }

    fun markPromptAnswered(promptId: String, date: LocalDate = _uiState.value.selectedDate) {
        promptPreferences.markPromptAnswered(date, promptId)
        updateDailyPromptForDate(date)
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
            repository.searchEntries(query.trim(), journalId).collectLatest { sqlResults ->
                if (_uiState.value.searchQuery.trim() == query.trim() && _activeJournalId.value == journalId) {
                    val smartMatches = com.onelineaday.dailydiary.search.SmartSearchEngine.search(_uiState.value.entries, query.trim())
                    val combinedResults = if (smartMatches.isNotEmpty()) {
                        val smartEntries: List<com.onelineaday.dailydiary.data.JournalEntry> = smartMatches.map { it.entry }
                        (smartEntries + sqlResults).distinctBy { "${it.journalId}_${it.date}" }
                    } else {
                        sqlResults
                    }
                    _uiState.update { it.copy(searchResults = combinedResults) }
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
        photoUri?.let { path ->
            add(path)
            runCatching { java.io.File(path).canonicalPath }.getOrNull()?.let(::add)
            runCatching { java.io.File(path).absolutePath }.getOrNull()?.let(::add)
        }
        mediaUris.forEach { path ->
            add(path)
            runCatching { java.io.File(path).canonicalPath }.getOrNull()?.let(::add)
            runCatching { java.io.File(path).absolutePath }.getOrNull()?.let(::add)
        }
        audioUri?.let { path ->
            add(path)
            runCatching { java.io.File(path).canonicalPath }.getOrNull()?.let(::add)
            runCatching { java.io.File(path).absolutePath }.getOrNull()?.let(::add)
        }
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

    suspend fun inspectBackup(backupUri: Uri): BackupPreviewResult {
        return backupManager.inspectBackup(backupUri)
    }

    suspend fun restoreBackup(backupUri: Uri): BackupRestoreResult {
        // Create an automatic safety snapshot before overwriting
        try {
            backupManager.createSafetySnapshot()
        } catch (_: Exception) {}

        val result = backupManager.restoreBackup(backupUri)
        if (result is BackupRestoreResult.Success) {
            val journals = repository.getAllJournalsOnce()
            val targetId = if (journals.any { it.id == _activeJournalId.value }) {
                _activeJournalId.value
            } else {
                journals.firstOrNull()?.id ?: "default"
            }
            if (_activeJournalId.value != targetId) {
                _activeJournalId.value = targetId
                prefs.edit().putString("active_journal_id", targetId).apply()
            }
            loadData(targetId)
            setSelectedDate(_selectedDate.value)
            com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(getApplication())
            runCatching { cleanupOrphansNow() }
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