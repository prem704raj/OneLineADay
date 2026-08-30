package com.onelineaday.dailydiary.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Represents a single journal entry - one line about the user's day
 */
@Entity(
    tableName = "journal_entries",
    primaryKeys = ["date", "journalId"]
)
data class JournalEntry(
    val date: LocalDate,
    val journalId: String = "default",
    val content: String,
    val mood: Mood = Mood.NEUTRAL,
    val photoUri: String? = null,
    val mediaUris: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

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
