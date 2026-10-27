package com.onelineaday.dailydiary.backup.cloud

/**
 * Metadata representing a remote backup archive in cloud storage.
 */
data class RemoteBackupMetadata(
    val fileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedTimeMillis: Long,
    val providerId: String
)
