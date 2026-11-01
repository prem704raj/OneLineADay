package com.onelineaday.dailydiary.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encapsulates an in-progress, uncommitted daily journal entry draft.
 */
data class JournalDraft(
    @SerializedName("text") val text: String = "",
    @SerializedName("mood") val mood: String = "NEUTRAL",
    @SerializedName("mediaUris") val mediaUris: List<String> = emptyList(),
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("audioUri") val audioUri: String? = null,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    fun isEmpty(): Boolean =
        (text ?: "").isBlank() && (mediaUris ?: emptyList()).isEmpty() && (tags ?: emptyList()).isEmpty() && (audioUri == null || audioUri.isBlank())

    fun isNotEmpty(): Boolean = !isEmpty()
}

/**
 * Thread-safe persistent draft manager backed by private SharedPreferences.
 * Provides instant memory caching via StateFlow and synchronous disk persistence.
 */
class DraftManager(
    private val prefs: SharedPreferences
) {
    /**
     * Primary Android constructor initializing private SharedPreferences ("journal_draft_prefs").
     */
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val gson = Gson()
    private val lock = Any()

    private val _draftFlow = MutableStateFlow(loadDraft())
    val draftFlow: StateFlow<JournalDraft> = _draftFlow.asStateFlow()

    fun saveDraft(draft: JournalDraft) {
        synchronized(lock) {
            prefs.edit().putString(KEY_ACTIVE_DRAFT, gson.toJson(draft)).apply()
            _draftFlow.value = draft
        }
    }

    fun loadDraft(): JournalDraft {
        synchronized(lock) {
            val json = prefs.getString(KEY_ACTIVE_DRAFT, null) ?: return JournalDraft()
            if (json.isBlank()) return JournalDraft()
            return runCatching {
                gson.fromJson(json, JournalDraft::class.java)
            }.getOrNull()?.let { parsed ->
                JournalDraft(
                    text = parsed.text ?: "",
                    mood = parsed.mood ?: "NEUTRAL",
                    mediaUris = parsed.mediaUris ?: emptyList(),
                    tags = parsed.tags ?: emptyList(),
                    audioUri = parsed.audioUri,
                    timestamp = parsed.timestamp
                )
            } ?: JournalDraft()
        }
    }

    fun clearDraft() {
        synchronized(lock) {
            prefs.edit().remove(KEY_ACTIVE_DRAFT).apply()
            _draftFlow.value = JournalDraft()
        }
    }

    fun hasDraft(): Boolean = loadDraft().isNotEmpty()

    companion object {
        const val PREFS_NAME = "journal_draft_prefs"
        const val KEY_ACTIVE_DRAFT = "active_draft"
    }
}
