package com.onelineaday.dailydiary.backup.cloud

/**
 * Domain exceptions for cloud storage operations.
 */
sealed class CloudStorageException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    class NetworkException(
        message: String = "Network connection failed",
        cause: Throwable? = null
    ) : CloudStorageException(message, cause)

    class NetworkTimeoutException(
        message: String = "Request timed out",
        cause: Throwable? = null
    ) : CloudStorageException(message, cause)

    class AuthExpiredException(
        message: String = "Authentication expired or invalid credentials",
        cause: Throwable? = null
    ) : CloudStorageException(message, cause)

    class QuotaExceededException(
        message: String = "Cloud storage quota exceeded",
        cause: Throwable? = null
    ) : CloudStorageException(message, cause)

    class FileNotFoundException(
        message: String = "Remote backup not found",
        cause: Throwable? = null
    ) : CloudStorageException(message, cause)

    class ServerException(
        val httpCode: Int,
        message: String,
        cause: Throwable? = null
    ) : CloudStorageException("Server error ($httpCode): $message", cause)

    class InvalidConfigurationException(
        message: String
    ) : CloudStorageException(message)
}
