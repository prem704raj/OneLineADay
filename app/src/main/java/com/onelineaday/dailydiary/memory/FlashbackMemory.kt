package com.onelineaday.dailydiary.memory

import com.onelineaday.dailydiary.data.JournalEntry
import java.time.LocalDate

/**
 * Rich domain model packaging a retrieved historical entry with multi-year timeline metadata.
 */
data class FlashbackMemory(
    val entry: JournalEntry,
    val yearOffset: Int,
    val targetDate: LocalDate,
    val isLeapDayObservation: Boolean = false,
    val formattedTimeAgo: String = formatTimeAgo(yearOffset)
) {
    /** Alias matching PROJECT.md and E2E test naming */
    val observedFromLeapDay: Boolean get() = isLeapDayObservation

    /** Combined list of media attachments preserving primary photo priority */
    val allMediaUris: List<String>
        get() = (entry.mediaUris + listOfNotNull(entry.photoUri).filter { it !in entry.mediaUris }).distinct()

    val hasMedia: Boolean get() = allMediaUris.isNotEmpty()
    val hasAudio: Boolean get() = !entry.audioUri.isNullOrBlank()

    companion object {
        fun formatTimeAgo(yearOffset: Int): String = when (yearOffset) {
            1 -> "1 year ago"
            else -> "$yearOffset years ago"
        }
    }
}
