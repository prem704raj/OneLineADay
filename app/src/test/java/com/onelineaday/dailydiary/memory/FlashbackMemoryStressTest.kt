package com.onelineaday.dailydiary.memory

import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Empirical Stress Test Suite for [FlashbackMemory].
 *
 * Adversarially challenges:
 * 1. Multi-year ordering and timeline sorting stability.
 * 2. Media deduplication (photoUri vs mediaUris intersection, internal duplicates, nullability).
 * 3. Audio existence flags across null, blank, whitespace, and valid URI strings.
 * 4. Relative time-ago formatting across boundary years.
 * 5. Leap day observation alias consistency.
 */
class FlashbackMemoryStressTest {

    // =========================================================================
    // 1. Multi-Year Timeline Sorting & Ordering Stability
    // =========================================================================

    @Test
    fun `multi-year flashback list sorts consistently by chronological date and year offset`() {
        val memories = listOf(
            createMemory(year = 2021, offset = 5, content = "Five years ago"),
            createMemory(year = 2024, offset = 2, content = "Two years ago"),
            createMemory(year = 2025, offset = 1, content = "One year ago"),
            createMemory(year = 2022, offset = 4, content = "Four years ago"),
            createMemory(year = 2023, offset = 3, content = "Three years ago")
        )

        // Sort chronologically newest first
        val sortedByDate = memories.sortedByDescending { it.entry.date }
        assertEquals(2025, sortedByDate[0].entry.date.year)
        assertEquals(2024, sortedByDate[1].entry.date.year)
        assertEquals(2023, sortedByDate[2].entry.date.year)
        assertEquals(2022, sortedByDate[3].entry.date.year)
        assertEquals(2021, sortedByDate[4].entry.date.year)

        // Sort by yearOffset ascending matches the same order
        val sortedByOffset = memories.sortedBy { it.yearOffset }
        assertEquals(sortedByDate, sortedByOffset)
    }

    // =========================================================================
    // 2. Media Deduplication Stress Testing
    // =========================================================================

    @Test
    fun `photoUri identical to an item in mediaUris is not duplicated`() {
        val sharedUri = "content://media/external/images/101"
        val entry = JournalEntry(
            date = LocalDate.of(2023, 5, 20),
            content = "Deduplication check",
            mood = Mood.HAPPY,
            photoUri = sharedUri,
            mediaUris = listOf(sharedUri, "content://media/external/images/102")
        )

        val memory = FlashbackMemory(entry = entry, yearOffset = 3, targetDate = entry.date)
        assertTrue(memory.hasMedia)
        assertEquals(2, memory.allMediaUris.size)
        assertEquals(sharedUri, memory.allMediaUris[0])
        assertEquals("content://media/external/images/102", memory.allMediaUris[1])
    }

    @Test
    fun `mediaUris with internal duplicates are stripped down to distinct items`() {
        val duplicateUri = "file:///storage/emulated/0/DCIM/photo.jpg"
        val entry = JournalEntry(
            date = LocalDate.of(2022, 3, 15),
            content = "Duplicate check",
            mood = Mood.GRATEFUL,
            photoUri = null,
            mediaUris = listOf(duplicateUri, duplicateUri, duplicateUri, "file:///storage/other.jpg")
        )

        val memory = FlashbackMemory(entry = entry, yearOffset = 4, targetDate = entry.date)
        assertEquals(2, memory.allMediaUris.size)
        assertEquals(duplicateUri, memory.allMediaUris[0])
        assertEquals("file:///storage/other.jpg", memory.allMediaUris[1])
    }

    @Test
    fun `empty photoUri and empty mediaUris cleanly results in hasMedia false`() {
        val entry = JournalEntry(
            date = LocalDate.of(2021, 1, 1),
            content = "Text only memory",
            mood = Mood.NEUTRAL,
            photoUri = null,
            mediaUris = emptyList()
        )

        val memory = FlashbackMemory(entry = entry, yearOffset = 5, targetDate = entry.date)
        assertFalse(memory.hasMedia)
        assertTrue(memory.allMediaUris.isEmpty())
    }

    @Test
    fun `distinct photoUri is appended to mediaUris seamlessly`() {
        val entry = JournalEntry(
            date = LocalDate.of(2020, 8, 12),
            content = "Distinct photo check",
            mood = Mood.EXCITED,
            photoUri = "content://images/primary.jpg",
            mediaUris = listOf("content://images/secondary1.jpg", "content://images/secondary2.jpg")
        )

        val memory = FlashbackMemory(entry = entry, yearOffset = 6, targetDate = entry.date)
        assertEquals(3, memory.allMediaUris.size)
        assertEquals("content://images/secondary1.jpg", memory.allMediaUris[0])
        assertEquals("content://images/secondary2.jpg", memory.allMediaUris[1])
        assertEquals("content://images/primary.jpg", memory.allMediaUris[2])
    }

    // =========================================================================
    // 3. Audio Existence Flags Stress Testing
    // =========================================================================

    @Test
    fun `audio flag rejects null, empty, and whitespace-only audioUri strings`() {
        val nullAudioEntry = JournalEntry(
            date = LocalDate.of(2024, 1, 1),
            content = "No audio",
            mood = Mood.HAPPY,
            audioUri = null
        )
        assertFalse("null audioUri must be false", FlashbackMemory(nullAudioEntry, 2, nullAudioEntry.date).hasAudio)

        val emptyAudioEntry = JournalEntry(
            date = LocalDate.of(2024, 1, 1),
            content = "Empty audio",
            mood = Mood.HAPPY,
            audioUri = ""
        )
        assertFalse("empty audioUri must be false", FlashbackMemory(emptyAudioEntry, 2, emptyAudioEntry.date).hasAudio)

        val blankAudioEntry = JournalEntry(
            date = LocalDate.of(2024, 1, 1),
            content = "Blank audio",
            mood = Mood.HAPPY,
            audioUri = "   \t\n   "
        )
        assertFalse("blank audioUri must be false", FlashbackMemory(blankAudioEntry, 2, blankAudioEntry.date).hasAudio)

        val validAudioEntry = JournalEntry(
            date = LocalDate.of(2024, 1, 1),
            content = "Valid audio",
            mood = Mood.HAPPY,
            audioUri = "file:///data/user/0/audio/recording_1.m4a"
        )
        assertTrue("valid audioUri must be true", FlashbackMemory(validAudioEntry, 2, validAudioEntry.date).hasAudio)
    }

    // =========================================================================
    // 4. Relative Time-Ago Formatting
    // =========================================================================

    @Test
    fun `formatTimeAgo correctly handles 1 through 10 years and edge cases`() {
        assertEquals("1 year ago", FlashbackMemory.formatTimeAgo(1))
        for (y in 2..10) {
            assertEquals("$y years ago", FlashbackMemory.formatTimeAgo(y))
        }
    }

    // =========================================================================
    // 5. Leap Day Observation Flags and Alias Consistency
    // =========================================================================

    @Test
    fun `leap day observation flag and observedFromLeapDay alias are strictly equal`() {
        val entry = JournalEntry(
            date = LocalDate.of(2020, 2, 29),
            content = "Leap Day 2020",
            mood = Mood.EXCITED
        )

        val leapObserved = FlashbackMemory(
            entry = entry,
            yearOffset = 6,
            targetDate = LocalDate.of(2020, 2, 29),
            isLeapDayObservation = true
        )
        assertTrue(leapObserved.isLeapDayObservation)
        assertTrue(leapObserved.observedFromLeapDay)

        val standard = FlashbackMemory(
            entry = entry,
            yearOffset = 6,
            targetDate = LocalDate.of(2020, 2, 29),
            isLeapDayObservation = false
        )
        assertFalse(standard.isLeapDayObservation)
        assertFalse(standard.observedFromLeapDay)
    }

    private fun createMemory(year: Int, offset: Int, content: String): FlashbackMemory {
        val date = LocalDate.of(year, 10, 27)
        val entry = JournalEntry(
            date = date,
            content = content,
            mood = Mood.HAPPY
        )
        return FlashbackMemory(entry = entry, yearOffset = offset, targetDate = date)
    }
}
