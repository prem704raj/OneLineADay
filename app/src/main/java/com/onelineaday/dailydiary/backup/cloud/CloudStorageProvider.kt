package com.onelineaday.dailydiary.backup.cloud

import java.io.File

/**
 * Vendor-neutral interface for cloud backup storage providers.
 * Supports Google Drive (REST v3 drive.appdata) and WebDAV (RFC 4918).
 */
interface CloudStorageProvider {
    val providerId: String
    val displayName: String

    suspend fun isAuthenticated(): Boolean
    suspend fun authenticate(): Result<Unit>
    suspend fun uploadBackup(archiveFile: File, remoteFileName: String): CloudSyncResult
    suspend fun listBackups(): Result<List<RemoteBackupMetadata>>
    suspend fun downloadBackup(remoteFileId: String, destinationFile: File): Result<Unit>
    suspend fun deleteBackup(remoteFileId: String): Result<Unit>
    suspend fun disconnect()
}
