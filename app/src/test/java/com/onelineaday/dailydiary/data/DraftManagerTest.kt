package com.onelineaday.dailydiary.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure Kotlin in-memory SharedPreferences test double.
 */
internal class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = HashMap(data)
    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        @Suppress("UNCHECKED_CAST") (data[key] as? Set<String>) ?: defValues
    override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = EditorImpl()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val toRemove = mutableSetOf<String>()
        private var shouldClear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { temp[key] = values; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { temp[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { toRemove.add(key); return this }
        override fun clear(): SharedPreferences.Editor { shouldClear = true; return this }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (shouldClear) data.clear()
            for (key in toRemove) data.remove(key)
            data.putAll(temp)
            for (listener in listeners) {
                for (key in temp.keys + toRemove) {
                    listener.onSharedPreferenceChanged(this@FakeSharedPreferences, key)
                }
            }
            temp.clear()
            toRemove.clear()
            shouldClear = false
        }
    }
}

/**
 * Automated tests for DraftManager and persistent draft storage across process death.
 */
class DraftManagerTest {

    private lateinit var sharedPrefs: FakeSharedPreferences
    private lateinit var draftManager: DraftManager

    @Before
    fun setUp() {
        sharedPrefs = FakeSharedPreferences()
        draftManager = DraftManager(sharedPrefs)
    }

    @Test
    fun `initial state has empty draft and returns false for hasDraft`() {
        val initial = draftManager.loadDraft()
        assertTrue("Initial draft text must be blank", initial.text.isEmpty())
        assertEquals("NEUTRAL", initial.mood)
        assertTrue("Initial media list must be empty", initial.mediaUris.isEmpty())
        assertTrue("Initial tags must be empty", initial.tags.isEmpty())
        assertNull("Initial audioUri must be null", initial.audioUri)
        assertFalse(draftManager.hasDraft())
        assertTrue(draftManager.draftFlow.value.isEmpty())
    }

    @Test
    fun `saving draft updates stateFlow and persists to underlying storage`() {
        val draft = JournalDraft(
            text = "Reflecting on an amazing sunset walk.",
            mood = "GRATEFUL",
            mediaUris = listOf("content://media/external/images/1", "content://media/external/images/2"),
            tags = listOf("nature", "peace"),
            audioUri = "content://media/external/audio/5",
            timestamp = 1700000000000L
        )

        draftManager.saveDraft(draft)

        assertEquals(draft, draftManager.loadDraft())
        assertEquals(draft, draftManager.draftFlow.value)
        assertTrue(draftManager.hasDraft())
        assertTrue(sharedPrefs.contains(DraftManager.KEY_ACTIVE_DRAFT))
    }

    @Test
    fun `simulated process death restores active draft completely across new manager instantiation`() {
        val draft = JournalDraft(
            text = "Writing in progress when OS suddenly kills the process...",
            mood = "EXCITED",
            mediaUris = listOf(
                "file:///storage/emulated/0/Android/data/com.onelineaday.dailydiary/files/media/img1.jpg",
                "file:///storage/emulated/0/Android/data/com.onelineaday/files/media/img2.jpg"
            ),
            tags = listOf("unexpected", "resilience"),
            audioUri = "file:///storage/emulated/0/audio/voice.m4a",
            timestamp = 1730000000000L
        )

        // Process 1 writes draft
        draftManager.saveDraft(draft)

        // --- SIMULATED HARD OS PROCESS KILL ---
        // Reference to original draftManager is discarded.
        // A brand new DraftManager instance is created from the persisted storage.
        val resurrectedManager = DraftManager(sharedPrefs)

        val restored = resurrectedManager.loadDraft()
        assertEquals("Text must match post process death", draft.text, restored.text)
        assertEquals("Mood must match post process death", draft.mood, restored.mood)
        assertEquals("Media URIs must match post process death", draft.mediaUris, restored.mediaUris)
        assertEquals("Tags must match post process death", draft.tags, restored.tags)
        assertEquals("Audio URI must match post process death", draft.audioUri, restored.audioUri)
        assertEquals("Timestamp must match post process death", draft.timestamp, restored.timestamp)
        assertEquals("StateFlow must emit restored draft immediately on start", draft, resurrectedManager.draftFlow.value)
        assertTrue(resurrectedManager.hasDraft())
    }

    @Test
    fun `clearing draft removes entry from disk and emits empty draft to flow`() {
        val draft = JournalDraft(text = "Unsaved draft", mood = "SAD")
        draftManager.saveDraft(draft)
        assertTrue(draftManager.hasDraft())

        draftManager.clearDraft()

        val loaded = draftManager.loadDraft()
        assertTrue("Loaded draft must be empty after clear", loaded.isEmpty())
        assertTrue("Flow must be empty after clear", draftManager.draftFlow.value.isEmpty())
        assertFalse(draftManager.hasDraft())
        assertFalse(sharedPrefs.contains(DraftManager.KEY_ACTIVE_DRAFT))

        // Verify across simulated restart as well
        val newManager = DraftManager(sharedPrefs)
        assertTrue(newManager.loadDraft().isEmpty())
        assertFalse(newManager.hasDraft())
    }

    @Test
    fun `corrupt json in storage falls back gracefully to default draft without exception`() {
        // Inject corrupt data
        sharedPrefs.edit().putString(DraftManager.KEY_ACTIVE_DRAFT, "{broken: json: [unclosed").apply()

        val loaded = draftManager.loadDraft()
        assertNotNull(loaded)
        assertTrue(loaded.isEmpty())
        assertEquals("NEUTRAL", loaded.mood)
        assertFalse(draftManager.hasDraft())
    }

    @Test
    fun `special characters unicode emojis and long text are safely preserved`() {
        val longContent = "🌟 Beautiful sunset in Zürich! Über den Wolken: \uD83C\uDF05 \uD83C\uDF38 \uD83D\uDC96. " + "A".repeat(5000)
        val complexDraft = JournalDraft(
            text = longContent,
            mood = "HAPPY",
            mediaUris = (1..10).map { "content://media/item_$it.jpg" },
            tags = listOf("tag1", "日本語", "العربية", "tag_with_\"quotes\""),
            audioUri = null
        )

        draftManager.saveDraft(complexDraft)

        val newManager = DraftManager(sharedPrefs)
        val restored = newManager.loadDraft()

        assertEquals(complexDraft.text, restored.text)
        assertEquals(complexDraft.tags, restored.tags)
        assertEquals(10, restored.mediaUris.size)
    }

    @Test
    fun `idempotent repeated clear calls do not crash or throw`() {
        draftManager.clearDraft()
        draftManager.clearDraft()
        draftManager.clearDraft()
        assertTrue(draftManager.loadDraft().isEmpty())
    }

    @Test
    fun `empty draft content helper methods evaluate correctly`() {
        val emptyDraft = JournalDraft()
        assertTrue(emptyDraft.isEmpty())
        assertFalse(emptyDraft.isNotEmpty())

        val draftWithText = JournalDraft(text = "Hello")
        assertFalse(draftWithText.isEmpty())
        assertTrue(draftWithText.isNotEmpty())

        val draftWithMedia = JournalDraft(mediaUris = listOf("uri1"))
        assertFalse(draftWithMedia.isEmpty())
        assertTrue(draftWithMedia.isNotEmpty())

        val draftWithTags = JournalDraft(tags = listOf("tag1"))
        assertFalse(draftWithTags.isEmpty())
        assertTrue(draftWithTags.isNotEmpty())

        val draftWithAudio = JournalDraft(audioUri = "audio_uri")
        assertFalse(draftWithAudio.isEmpty())
        assertTrue(draftWithAudio.isNotEmpty())

        val blankWhitespaceDraft = JournalDraft(text = "   ")
        assertTrue(blankWhitespaceDraft.isEmpty())
    }
}
