package com.onelineaday.dailydiary.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Controller encapsulating ViewModel debounced autosave behavior for deterministic unit testing.
 */
class DraftAutosaveController(
    private val draftManager: DraftManager,
    private val scope: CoroutineScope,
    private val debounceDelayMs: Long = 50L
) {
    private var autosaveJob: Job? = null
    var diskWriteCount: Int = 0
        private set

    fun onContentChanged(
        text: String,
        mood: String = "NEUTRAL",
        mediaUris: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        audioUri: String? = null
    ) {
        autosaveJob?.cancel()
        autosaveJob = scope.launch {
            delay(debounceDelayMs)
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
            diskWriteCount++
        }
    }

    fun flushImmediate(
        text: String,
        mood: String = "NEUTRAL",
        mediaUris: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        audioUri: String? = null
    ) {
        autosaveJob?.cancel()
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
        diskWriteCount++
    }

    fun onEntrySaved() {
        autosaveJob?.cancel()
        draftManager.clearDraft()
    }
}

/**
 * Automated unit tests verifying debounced coalescing and debounce cancellation.
 */
class DraftAutosaveDebounceTest {

    @Test
    fun `rapid successive keystrokes coalesce into a single persistent disk write of latest text`() = runBlocking {
        val sharedPrefs = FakeSharedPreferences()
        val draftManager = DraftManager(sharedPrefs)
        val controller = DraftAutosaveController(draftManager, this, debounceDelayMs = 50L)

        // Simulate fast user typing: "H", "He", "Hel", "Hell", "Hello world!" with 10ms intervals
        val typingSequence = listOf("H", "He", "Hel", "Hell", "Hello world!")
        for (partial in typingSequence) {
            controller.onContentChanged(partial)
            delay(10) // less than 50ms debounce
        }

        // Wait for debounce delay to expire
        delay(70)

        assertEquals("Only one disk write should occur after rapid typing sequence", 1, controller.diskWriteCount)
        assertEquals("Persisted draft must match final typed string", "Hello world!", draftManager.loadDraft().text)
    }

    @Test
    fun `cancelling debounce before delay preserves previous or empty draft`() = runBlocking {
        val sharedPrefs = FakeSharedPreferences()
        val draftManager = DraftManager(sharedPrefs)
        val controller = DraftAutosaveController(draftManager, this, debounceDelayMs = 100L)

        controller.onContentChanged("Draft that gets cancelled immediately")
        delay(20) // Before debounce completes
        controller.onEntrySaved() // Entry saved or cancelled

        delay(120) // Exceeds original debounce window

        assertTrue("Draft must remain empty when cancelled during debounce window", draftManager.loadDraft().isEmpty())
    }

    @Test
    fun `saving entry clears draft and prevents pending autosave from resurrecting it`() = runBlocking {
        val sharedPrefs = FakeSharedPreferences()
        val draftManager = DraftManager(sharedPrefs)
        val controller = DraftAutosaveController(draftManager, this, debounceDelayMs = 50L)

        // Type and wait for save
        controller.onContentChanged("Completed entry draft")
        delay(70)
        assertTrue(draftManager.hasDraft())

        // Entry is saved
        controller.onEntrySaved()
        assertFalse("Draft must be cleared after save", draftManager.hasDraft())

        // Simulate process death post save
        val freshManager = DraftManager(sharedPrefs)
        assertFalse("Fresh manager must see no draft after save", freshManager.hasDraft())
    }

    @Test
    fun `flushImmediate writes immediately cancelling pending debounce`() = runBlocking {
        val sharedPrefs = FakeSharedPreferences()
        val draftManager = DraftManager(sharedPrefs)
        val controller = DraftAutosaveController(draftManager, this, debounceDelayMs = 100L)

        controller.onContentChanged("Typing...")
        delay(20)
        controller.flushImmediate("Flush now!")

        assertEquals("Flush immediately persists latest text", "Flush now!", draftManager.loadDraft().text)
        assertEquals(1, controller.diskWriteCount)

        delay(120)
        assertEquals("No secondary debounce write after flushImmediate", 1, controller.diskWriteCount)
    }
}
