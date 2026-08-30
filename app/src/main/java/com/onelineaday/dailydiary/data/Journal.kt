package com.onelineaday.dailydiary.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "journals")
data class Journal(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
