package com.onelineaday.dailydiary.backup.cloud

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Google Drive cloud storage provider using REST API v3 and drive.appdata scope.
 * Files are isolated in the hidden application data folder ('appDataFolder'),
 * protecting user privacy and eliminating excessive permissions.
 */
class GoogleDriveCloudProvider(
    private val tokenProvider: suspend () -> String?
) : CloudStorageProvider {

    override val providerId: String = "google_drive"
    override val displayName: String = "Google Drive"

    private val gson = Gson()

    companion object {
        private const val API_BASE = "https://www.googleapis.com"
        private const val UPLOAD_BASE = "https://www.googleapis.com/upload"
        private const val BOUNDARY = "----OneLineADayCloudBoundary7MA4YWxkTrZu0gW"
        private const val BUFFER_SIZE = 64 * 1024
        private const val TIMEOUT_MILLIS = 30_000
    }

    override suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        !token.isNullOrBlank()
    }

    override suspend fun authenticate(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenProvider()
            require(!token.isNullOrBlank()) { "Google Drive authorization token unavailable." }
        }
    }

    override suspend fun uploadBackup(
        archiveFile: File,
        remoteFileName: String
    ): CloudSyncResult = withContext(Dispatchers.IO) {
        val token = tokenProvider()
            ?: return@withContext CloudSyncResult.Error(
                CloudErrorType.AUTH_EXPIRED,
                "Google account is not authenticated."
            )

        if (!archiveFile.exists() || !archiveFile.isFile) {
            return@withContext CloudSyncResult.Error(
                CloudErrorType.FILE_NOT_FOUND,
                "Backup archive file does not exist: ${archiveFile.absolutePath}"
            )
        }

        try {
            val uploadUrl = URL("$UPLOAD_BASE/drive/v3/files?uploadType=multipart")
            val connection = (uploadUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/related; boundary=$BOUNDARY")
            }

            val metadataJson = JsonObject().apply {
                addProperty("name", remoteFileName)
                val parentsArray = com.google.gson.JsonArray().apply { add("appDataFolder") }
                add("parents", parentsArray)
            }.toString()

            val headerPart = buildString {
                append("--$BOUNDARY\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(metadataJson)
                append("\r\n--$BOUNDARY\r\n")
                append("Content-Type: application/octet-stream\r\n\r\n")
            }.toByteArray(StandardCharsets.UTF_8)

            val footerPart = "\r\n--$BOUNDARY--\r\n".toByteArray(StandardCharsets.UTF_8)

            val totalContentLength = headerPart.size + archiveFile.length() + footerPart.size
            connection.setFixedLengthStreamingMode(totalContentLength)

            BufferedOutputStream(connection.outputStream).use { out ->
                out.write(headerPart)
                FileInputStream(archiveFile).use { fileIn ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (fileIn.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
                out.write(footerPart)
                out.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                val fileId = json.get("id")?.asString ?: remoteFileName
                CloudSyncResult.Success(
                    remoteFileId = fileId,
                    timestampMillis = System.currentTimeMillis(),
                    bytesTransferred = archiveFile.length()
                )
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                mapHttpError(responseCode, errorBody)
            }
        } catch (e: Exception) {
            CloudSyncResult.Error(
                CloudErrorType.NETWORK_OFFLINE,
                "Failed to upload backup to Google Drive: ${e.message}",
                e
            )
        }
    }

    override suspend fun listBackups(): Result<List<RemoteBackupMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenProvider()
                ?: throw CloudStorageException.AuthExpiredException("Google Drive token not available.")

            val queryParams = "spaces=appDataFolder&fields=files(id,name,size,modifiedTime)&orderBy=modifiedTime desc"
            val url = URL("$API_BASE/drive/v3/files?$queryParams")

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Authorization", "Bearer $token")
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val root = gson.fromJson(responseBody, JsonObject::class.java)
                val filesArray = root.getAsJsonArray("files") ?: com.google.gson.JsonArray()

                val resultList = mutableListOf<RemoteBackupMetadata>()
                for (element in filesArray) {
                    val obj = element.asJsonObject
                    val id = obj.get("id")?.asString ?: continue
                    val name = obj.get("name")?.asString ?: "backup.olad.enc"
                    val size = obj.get("size")?.asLong ?: 0L
                    val modifiedStr = obj.get("modifiedTime")?.asString
                    val modifiedMillis = parseRfc3339Millis(modifiedStr)

                    resultList.add(
                        RemoteBackupMetadata(
                            fileId = id,
                            fileName = name,
                            sizeBytes = size,
                            modifiedTimeMillis = modifiedMillis,
                            providerId = providerId
                        )
                    )
                }
                resultList
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw createHttpException(responseCode, errorBody)
            }
        }
    }

    override suspend fun downloadBackup(
        remoteFileId: String,
        destinationFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenProvider()
                ?: throw CloudStorageException.AuthExpiredException("Google Drive token not available.")

            val url = URL("$API_BASE/drive/v3/files/$remoteFileId?alt=media")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Authorization", "Bearer $token")
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val parentDir = destinationFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }

                val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.downloading")
                try {
                    BufferedInputStream(connection.inputStream).use { inStream ->
                        BufferedOutputStream(FileOutputStream(tempFile)).use { outStream ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var read: Int
                            while (inStream.read(buffer).also { read = it } != -1) {
                                outStream.write(buffer, 0, read)
                            }
                            outStream.flush()
                        }
                    }
                    if (destinationFile.exists()) destinationFile.delete()
                    if (!tempFile.renameTo(destinationFile)) {
                        tempFile.copyTo(destinationFile, overwrite = true)
                        tempFile.delete()
                    }
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw createHttpException(responseCode, errorBody)
            }
        }
    }

    override suspend fun deleteBackup(remoteFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenProvider()
                ?: throw CloudStorageException.AuthExpiredException("Google Drive token not available.")

            val url = URL("$API_BASE/drive/v3/files/$remoteFileId")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Authorization", "Bearer $token")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299 && responseCode != 404) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw createHttpException(responseCode, errorBody)
            }
        }
    }

    override suspend fun disconnect() {
        // Token revocation or clearance managed by caller tokenProvider
    }

    private fun mapHttpError(code: Int, body: String): CloudSyncResult.Error {
        return when (code) {
            401 -> CloudSyncResult.Error(CloudErrorType.AUTH_EXPIRED, "Google Drive token expired.")
            403 -> if (body.contains("quota", ignoreCase = true)) {
                CloudSyncResult.Error(CloudErrorType.QUOTA_EXCEEDED, "Google Drive quota exceeded.")
            } else {
                CloudSyncResult.Error(CloudErrorType.STORAGE_ERROR, "Google Drive access forbidden: $body")
            }
            404 -> CloudSyncResult.Error(CloudErrorType.FILE_NOT_FOUND, "Google Drive file not found.")
            in 500..599 -> CloudSyncResult.Error(CloudErrorType.STORAGE_ERROR, "Google Drive server error ($code).")
            else -> CloudSyncResult.Error(CloudErrorType.UNKNOWN, "HTTP $code: $body")
        }
    }

    private fun createHttpException(code: Int, body: String): CloudStorageException {
        return when (code) {
            401 -> CloudStorageException.AuthExpiredException("Google Drive token expired: $body")
            403 -> if (body.contains("quota", ignoreCase = true)) {
                CloudStorageException.QuotaExceededException("Google Drive quota exceeded: $body")
            } else {
                CloudStorageException.ServerException(code, body)
            }
            404 -> CloudStorageException.FileNotFoundException("Remote file not found on Google Drive: $body")
            else -> CloudStorageException.ServerException(code, body)
        }
    }

    private fun parseRfc3339Millis(dateTimeStr: String?): Long {
        if (dateTimeStr.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching {
            java.time.Instant.parse(dateTimeStr).toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }
}
