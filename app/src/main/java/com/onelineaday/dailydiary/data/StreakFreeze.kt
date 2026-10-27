package com.onelineaday.dailydiary.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.LocalDate

@Entity(
    tableName = "streak_freezes",
    primaryKeys = ["date", "journalId"],
    foreignKeys = [
        ForeignKey(
            entity = Journal::class,
            parentColumns = ["id"],
            childColumns = ["journalId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["journalId"])
    ]
)
data class StreakFreeze(
    val date: LocalDate,
    val journalId: String = JournalEntry.DEFAULT_JOURNAL_ID,
    val reason: String = "AUTOMATIC",
    val createdAt: Long = System.currentTimeMillis()
)

