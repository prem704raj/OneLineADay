package com.onelineaday.dailydiary.backup.cloud

/**
 * Result model for cloud backup synchronization operations.
 */
sealed class CloudSyncResult {
    data class Success(
        val remoteFileId: String,
        val timestampMillis: Long,
        val bytesTransferred: Long = 0L
    ) : CloudSyncResult()

    data class Error(
        val errorType: CloudErrorType,
        val message: String,
        val cause: Throwable? = null
    ) : CloudSyncResult()
}

/**
 * Categorized cloud error types for granular user messaging and retry logic.
 */
enum class CloudErrorType {
    NETWORK_TIMEOUT,
    NETWORK_OFFLINE,
    AUTH_EXPIRED,
    QUOTA_EXCEEDED,
    STORAGE_ERROR,
    FILE_NOT_FOUND,
    INVALID_CONFIGURATION,
    CANCELLED,
    UNKNOWN
}
