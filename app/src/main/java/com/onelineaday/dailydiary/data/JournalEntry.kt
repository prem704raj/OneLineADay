package com.onelineaday.dailydiary.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.LocalDate

/**
 * Represents a single journal entry - one line about the user's day
 */
@Entity(
    tableName = "journal_entries",
    primaryKeys = ["date", "journalId"],
    foreignKeys = [
        ForeignKey(
            entity = Journal::class,
            parentColumns = ["id"],
            childColumns = ["journalId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["journalId"])
    ]
)
data class JournalEntry(
    val date: LocalDate,
    val journalId: String = DEFAULT_JOURNAL_ID,
    val content: String,
    val mood: Mood = Mood.NEUTRAL,
    val photoUri: String? = null,
    val mediaUris: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val audioUri: String? = null,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_JOURNAL_ID = "default"
    }
}

/**
 * Available mood options
 */
enum class Mood(val emoji: String, val label: String) {
    HAPPY("😊", "Happy"),
    NEUTRAL("😐", "Neutral"),
    SAD("😢", "Sad"),
    ANGRY("😤", "Angry"),
    EXCITED("🤩", "Excited"),
    TIRED("🥱", "Tired"),
    MOTIVATED("💪", "Motivated"),
    LOVED("❤️", "Loved"),
    ANXIOUS("😰", "Anxious"),
    GRATEFUL("🙏", "Grateful")
}
