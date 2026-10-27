package com.onelineaday.dailydiary.backup

import com.onelineaday.dailydiary.backup.cloud.CloudErrorType
import com.onelineaday.dailydiary.backup.cloud.CloudStorageException
import com.onelineaday.dailydiary.backup.cloud.CloudStorageProvider
import com.onelineaday.dailydiary.backup.cloud.CloudSyncResult
import com.onelineaday.dailydiary.backup.cloud.RemoteBackupMetadata
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException

/**
 * High-fidelity test double simulating cloud storage operations and error conditions
 * for offline, deterministic unit testing.
 */
class MockCloudStorageProvider(
    override val providerId: String = "mock_provider",
    override val displayName: String = "Mock Cloud Provider"
) : CloudStorageProvider {

    var authenticated: Boolean = false
    val remoteFiles: MutableMap<String, ByteArray> = mutableMapOf()
    val remoteMetadata: MutableMap<String, RemoteBackupMetadata> = mutableMapOf()

    // Fault injection flags
    var failAuth: Boolean = false
    var failUpload: Boolean = false
    var failDownload: Boolean = false
    var networkTimeout: Boolean = false
    var quotaExceeded: Boolean = false
    var tokenExpired: Boolean = false
    var downloadCorruptsMidStream: Boolean = false
    var uploadDisconnectsMidStream: Boolean = false
    var failDelete: Boolean = false

    override suspend fun isAuthenticated(): Boolean {
        if (tokenExpired) return false
        return authenticated
    }

    override suspend fun authenticate(): Result<Unit> {
        if (failAuth) {
            authenticated = false
            return Result.failure(CloudStorageException.AuthExpiredException("Mock authentication failed."))
        }
        authenticated = true
        tokenExpired = false
        return Result.success(Unit)
    }

    override suspend fun uploadBackup(archiveFile: File, remoteFileName: String): CloudSyncResult {
        if (!authenticated || tokenExpired) {
            return CloudSyncResult.Error(
                CloudErrorType.AUTH_EXPIRED,
                "Authentication required before upload."
            )
        }
        if (networkTimeout) {
            return CloudSyncResult.Error(
                CloudErrorType.NETWORK_TIMEOUT,
                "Network timeout during upload.",
                SocketTimeoutException("Mock socket timeout")
            )
        }
        if (uploadDisconnectsMidStream) {
            return CloudSyncResult.Error(
                CloudErrorType.NETWORK_OFFLINE,
                "Connection severed mid-stream during upload.",
                CloudStorageException.NetworkException("Connection reset by peer mid-stream")
            )
        }
        if (quotaExceeded) {
            return CloudSyncResult.Error(
                CloudErrorType.QUOTA_EXCEEDED,
                "Cloud storage quota exceeded (507 Insufficient Storage)."
            )
        }
        if (failUpload) {
            return CloudSyncResult.Error(
                CloudErrorType.STORAGE_ERROR,
                "Internal server error during upload."
            )
        }
        if (!archiveFile.exists()) {
            return CloudSyncResult.Error(
                CloudErrorType.FILE_NOT_FOUND,
                "Source file not found."
            )
        }

        val bytes = archiveFile.readBytes()
        remoteFiles[remoteFileName] = bytes
        val metadata = RemoteBackupMetadata(
            fileId = remoteFileName,
            fileName = remoteFileName,
            sizeBytes = bytes.size.toLong(),
            modifiedTimeMillis = System.currentTimeMillis(),
            providerId = providerId
        )
        remoteMetadata[remoteFileName] = metadata

        return CloudSyncResult.Success(
            remoteFileId = remoteFileName,
            timestampMillis = metadata.modifiedTimeMillis,
            bytesTransferred = bytes.size.toLong()
        )
    }

    override suspend fun listBackups(): Result<List<RemoteBackupMetadata>> {
        if (!authenticated || tokenExpired) {
            return Result.failure(CloudStorageException.AuthExpiredException("Not authenticated."))
        }
        if (networkTimeout) {
            return Result.failure(CloudStorageException.NetworkTimeoutException("Timeout fetching list."))
        }
        val sortedList = remoteMetadata.values.sortedByDescending { it.modifiedTimeMillis }
        return Result.success(sortedList)
    }

    override suspend fun downloadBackup(remoteFileId: String, destinationFile: File): Result<Unit> {
        if (!authenticated || tokenExpired) {
            return Result.failure(CloudStorageException.AuthExpiredException("Not authenticated."))
        }
        if (failDownload) {
            return Result.failure(CloudStorageException.ServerException(500, "Download failed."))
        }
        val bytes = remoteFiles[remoteFileId]
            ?: return Result.failure(CloudStorageException.FileNotFoundException("Remote file not found: $remoteFileId"))

        return runCatching {
            val parent = destinationFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()

            if (downloadCorruptsMidStream) {
                // Write partial bytes and throw exception
                FileOutputStream(destinationFile).use { out ->
                    out.write(bytes.take(bytes.size / 2).toByteArray())
                }
                destinationFile.delete() // Cleanup on failure
                throw CloudStorageException.NetworkException("Connection severed mid-stream")
            }

            FileOutputStream(destinationFile).use { out ->
                out.write(bytes)
                out.flush()
            }
        }
    }

    override suspend fun deleteBackup(remoteFileId: String): Result<Unit> {
        if (!authenticated || tokenExpired) {
            return Result.failure(CloudStorageException.AuthExpiredException("Not authenticated."))
        }
        if (failDelete) {
            return Result.failure(CloudStorageException.ServerException(500, "Simulated remote delete failure."))
        }
        remoteFiles.remove(remoteFileId)
        remoteMetadata.remove(remoteFileId)
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        authenticated = false
    }
}
