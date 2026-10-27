package com.onelineaday.dailydiary.memory

import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FlashbackMemoryTest {

    @Test
    fun `formatTimeAgo correctly formats single and multiple years`() {
        assertEquals("1 year ago", FlashbackMemory.formatTimeAgo(1))
        assertEquals("2 years ago", FlashbackMemory.formatTimeAgo(2))
        assertEquals("5 years ago", FlashbackMemory.formatTimeAgo(5))
    }

    @Test
    fun `media helpers aggregate mediaUris and photoUri without duplicates`() {
        val entryWithPhotos = JournalEntry(
            date = LocalDate.of(2024, 10, 27),
            content = "Great day at the park!",
            mood = Mood.HAPPY,
            photoUri = "content://media/external/images/media/1",
            mediaUris = listOf(
                "content://media/external/images/media/1",
                "content://media/external/images/media/2"
            )
        )

        val memory = FlashbackMemory(
            entry = entryWithPhotos,
            yearOffset = 2,
            targetDate = LocalDate.of(2024, 10, 27)
        )

        assertTrue(memory.hasMedia)
        assertEquals(2, memory.allMediaUris.size)
        assertEquals("content://media/external/images/media/1", memory.allMediaUris[0])
        assertEquals("content://media/external/images/media/2", memory.allMediaUris[1])
        assertFalse(memory.hasAudio)
    }

    @Test
    fun `observedFromLeapDay alias mirrors isLeapDayObservation`() {
        val entry = JournalEntry(
            date = LocalDate.of(2024, 2, 29),
            content = "Once every 4 years!",
            mood = Mood.EXCITED
        )

        val leapObservedMemory = FlashbackMemory(
            entry = entry,
            yearOffset = 1,
            targetDate = LocalDate.of(2024, 2, 29),
            isLeapDayObservation = true
        )

        assertTrue(leapObservedMemory.isLeapDayObservation)
        assertTrue(leapObservedMemory.observedFromLeapDay)

        val standardMemory = FlashbackMemory(
            entry = entry,
            yearOffset = 1,
            targetDate = LocalDate.of(2024, 2, 29),
            isLeapDayObservation = false
        )

        assertFalse(standardMemory.isLeapDayObservation)
        assertFalse(standardMemory.observedFromLeapDay)
    }
}
