package com.onelineaday.dailydiary.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Adversarial Stress Harness for Draft Persistence, Process Death,
 * Malformed Storage Resilience, and Debounce Cancellation.
 */
class DraftManagerAdversarialStressTest {

    private lateinit var sharedPrefs: FakeSharedPreferences
    private lateinit var draftManager: DraftManager

    @Before
    fun setUp() {
        sharedPrefs = FakeSharedPreferences()
        draftManager = DraftManager(sharedPrefs)
    }

    @Test
    fun `process death recovery restores complex draft with multi-media and audio bit-for-bit`() {
        val originalDraft = JournalDraft(
            text = "Midnight reflections.\nLine 2 with special chars: <>&\"'/\nLine 3.",
            mood = "EXCITED",
            mediaUris = listOf(
                "content://media/external/images/media/1001",
                "content://media/external/images/media/1002",
                "file:///data/user/0/com.onelineaday.dailydiary/files/media/photo_3.jpg"
            ),
            tags = listOf("midnight", "deep-thought", "v5-hardening"),
            audioUri = "content://media/external/audio/media/505",
            timestamp = 1730005555000L
        )

        // Process A saves draft
        draftManager.saveDraft(originalDraft)

        // HARD SIMULATED PROCESS KILL: original manager discarded
        val resurrectedManager = DraftManager(sharedPrefs)

        // Verify restoration
        val restored = resurrectedManager.loadDraft()
        assertEquals(originalDraft.text, restored.text)
        assertEquals(originalDraft.mood, restored.mood)
        assertEquals(originalDraft.mediaUris, restored.mediaUris)
        assertEquals(originalDraft.tags, restored.tags)
        assertEquals(originalDraft.audioUri, restored.audioUri)
        assertEquals(originalDraft.timestamp, restored.timestamp)

        // Verify StateFlow initialized with restored draft without explicit loadDraft call
        assertEquals(originalDraft, resurrectedManager.draftFlow.value)
        assertTrue(resurrectedManager.hasDraft())
    }

    @Test
    fun `unicode, emojis, RTL, CJK, and complex formatting persist bit-for-bit across process death`() {
        val complexText = buildString {
            append("Emoji family: 👨‍👩‍👧‍👦 🏳️‍🌈 🦹‍♂️ 🌟 💖 ☕\n")
            append("RTL Arabic: مرحبا بالعالم، هذا اختبار للنص العربي.\n")
            append("RTL Hebrew: שלום עולם, בדיקת טקסט בעברית.\n")
            append("CJK Japanese: こんにちは世界！今日も一日お疲れ様でした。\n")
            append("German Umlauts: Äpfel, Übergrößen, süß, Maßstab.\n")
            append("Math & Code: ∑(x² + y²) = ∞ && (a <= b || c >= d) <tag attr=\"val\">")
        }

        val draft = JournalDraft(
            text = complexText,
            mood = "GRATEFUL",
            tags = listOf("日本語タグ", "وسم_عربي", "tag_with_\"quotes\"_and_\\slashes"),
            mediaUris = listOf("content://media/item?id=123&type=image#anchor"),
            audioUri = null
        )

        draftManager.saveDraft(draft)

        val freshProcessManager = DraftManager(sharedPrefs)
        val restored = freshProcessManager.loadDraft()

        assertEquals("Unicode text must match bit-for-bit", complexText, restored.text)
        assertEquals(draft.tags, restored.tags)
        assertEquals(draft.mediaUris, restored.mediaUris)
    }

    @Test
    fun `corrupt json syntax error recovers gracefully`() {
        sharedPrefs.edit().putString(DraftManager.KEY_ACTIVE_DRAFT, "{ broken json").apply()
        val freshManager = DraftManager(sharedPrefs)
        val draft = freshManager.loadDraft()
        assertNotNull(draft)
        assertTrue(draft.isEmpty())
        assertFalse(freshManager.hasDraft())
    }

    @Test
    fun `storage containing literal null json string recovers gracefully without null leakage`() {
        sharedPrefs.edit().putString(DraftManager.KEY_ACTIVE_DRAFT, "null").apply()
        val freshManager = DraftManager(sharedPrefs)
        val draft = freshManager.loadDraft()
        val isNull = (draft as Any?) == null
        assertFalse("Draft must not be null when storage contains 'null'", isNull)
        assertTrue(draft.isEmpty())
        assertFalse(freshManager.hasDraft())
    }

    @Test
    fun `storage containing json with explicit null text field sanitizes text to empty string`() {
        sharedPrefs.edit().putString(DraftManager.KEY_ACTIVE_DRAFT, "{\"text\": null}").apply()
        val freshManager = DraftManager(sharedPrefs)
        val draft = freshManager.loadDraft()
        assertNotNull(draft)
        val textIsNull = (draft.text as String?) == null
        assertFalse("Text must not be null after sanitization", textIsNull)
        assertEquals("", draft.text)
        assertTrue(draft.isEmpty())
    }

    @Test
    fun `storage containing json array instead of object recovers gracefully`() {
        sharedPrefs.edit().putString(DraftManager.KEY_ACTIVE_DRAFT, "[1, 2, 3]").apply()
        val freshManager = DraftManager(sharedPrefs)
        val draft = freshManager.loadDraft()
        assertNotNull(draft)
        assertTrue(draft.isEmpty())
        assertFalse(freshManager.hasDraft())
    }

    @Test
    fun `storage containing empty string recovers gracefully without null leakage`() {
        sharedPrefs.edit().putString(DraftManager.KEY_ACTIVE_DRAFT, "").apply()
        val freshManager = DraftManager(sharedPrefs)
        val draft = freshManager.loadDraft()
        val isNull = (draft as Any?) == null
        assertFalse("Draft must not be null when storage contains empty string", isNull)
        assertTrue(draft.isEmpty())
        assertFalse(freshManager.hasDraft())
    }

    @Test
    fun `concurrent access from multiple threads maintains integrity without crashing`() {
        val threadCount = 8
        val iterationsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until iterationsPerThread) {
                        when (i % 3) {
                            0 -> draftManager.saveDraft(
                                JournalDraft(
                                    text = "Thread $t iteration $i",
                                    mood = "PEACEFUL",
                                    tags = listOf("t$t", "i$i")
                                )
                            )
                            1 -> {
                                val loaded = draftManager.loadDraft()
                                assertNotNull(loaded)
                            }
                            2 -> draftManager.clearDraft()
                        }
                    }
                } catch (e: Throwable) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue("All concurrent operations should finish within timeout", completed)
        assertEquals("Zero errors expected under concurrent stress", 0, errorCount.get())
    }

    @Test
    fun `massive payload stress test with 50000 chars and 100 media URIs`() {
        val hugeText = "Daily diary entry line with substantial content.\n".repeat(1000) // ~50,000 chars
        val hugeMediaList = (1..100).map { "content://media/external/images/huge_item_$it.jpg" }
        val hugeTagList = (1..100).map { "tag_stress_index_$it" }

        val hugeDraft = JournalDraft(
            text = hugeText,
            mood = "PRODUCTIVE",
            mediaUris = hugeMediaList,
            tags = hugeTagList,
            audioUri = "content://media/audio/large_voice_memo.mp4",
            timestamp = 1730009999000L
        )

        draftManager.saveDraft(hugeDraft)

        val resurrected = DraftManager(sharedPrefs)
        val restored = resurrected.loadDraft()

        assertEquals(hugeText.length, restored.text.length)
        assertEquals(hugeText, restored.text)
        assertEquals(100, restored.mediaUris.size)
        assertEquals(100, restored.tags.size)
        assertEquals(hugeDraft.audioUri, restored.audioUri)
    }

    @Test
    fun `debounce cancellation on saveEntry prevents draft resurrection under tight timing`() = runBlocking {
        val controller = DraftAutosaveController(draftManager, this, debounceDelayMs = 60L)

        // User types content
        controller.onContentChanged("Entry almost ready to be saved...")
        delay(20) // Only 20ms into 60ms debounce window

        // User clicks save entry immediately
        controller.onEntrySaved()

        // Wait well past the original debounce window
        delay(100)

        // Verify draft was NOT written by the cancelled job
        assertFalse("Draft must remain cleared after onEntrySaved()", draftManager.hasDraft())
        assertTrue("Draft text must be blank", draftManager.loadDraft().isEmpty())

        // Simulate process death right after save
        val freshManager = DraftManager(sharedPrefs)
        assertFalse("Restored manager must see no draft", freshManager.hasDraft())
    }

    @Test
    fun `rapid typing stream with 100 keystrokes yields exactly 1 write of final content`() = runBlocking {
        val controller = DraftAutosaveController(draftManager, this, debounceDelayMs = 40L)

        // 100 rapid keystrokes separated by 2ms (< 40ms debounce)
        for (i in 1..100) {
            controller.onContentChanged("Keystroke $i")
            delay(2)
        }

        // Wait for final debounce to trigger
        delay(80)

        assertEquals("Exactly 1 disk write should occur for continuous typing stream", 1, controller.diskWriteCount)
        assertEquals("Keystroke 100", draftManager.loadDraft().text)
    }
}
