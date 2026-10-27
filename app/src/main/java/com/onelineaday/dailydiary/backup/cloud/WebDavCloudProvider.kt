package com.onelineaday.dailydiary.backup.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone

/**
 * WebDAV cloud storage provider adhering to RFC 4918.
 * Supports Nextcloud, ownCloud, Synology NAS, QNAP, Fastmail, and standard Apache/Nginx WebDAV servers.
 */
class WebDavCloudProvider(
    private val serverUrl: String,
    private val username: String,
    private val password: String,
    private val targetFolder: String = "OneLineADay"
) : CloudStorageProvider {

    override val providerId: String = "webdav"
    override val displayName: String = "WebDAV"

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val TIMEOUT_MILLIS = 30_000

        private val RFC1123_FORMAT = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
    }

    private val authHeader: String
        get() {
            val credentials = "$username:$password"
            val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
            return "Basic $encoded"
        }

    private val baseEndpoint: String
        get() {
            val normalized = serverUrl.trimEnd('/')
            return "$normalized/$targetFolder"
        }

    override suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        if (serverUrl.isBlank() || username.isBlank()) return@withContext false
        runCatching {
            authenticate().isSuccess
        }.getOrDefault(false)
    }

    override suspend fun authenticate(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(serverUrl.isNotBlank()) { "WebDAV server URL cannot be blank." }
            require(username.isNotBlank()) { "WebDAV username cannot be blank." }

            val url = URL(serverUrl.trimEnd('/') + "/")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                setCustomMethod(this, "PROPFIND")
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Depth", "0")
            }

            val code = connection.responseCode
            if (code !in 200..299 && code != 207) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw createHttpException(code, errorBody)
            }
        }
    }

    private fun ensureFolderExists() {
        val folderUrl = URL("$baseEndpoint/")
        val connection = (folderUrl.openConnection() as HttpURLConnection).apply {
            setCustomMethod(this, "MKCOL")
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Authorization", authHeader)
        }

        val code = connection.responseCode
        // 201 Created = success; 405 Method Not Allowed = already exists; 301/302 = redirection
        if (code !in 200..299 && code != 405) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401) throw CloudStorageException.AuthExpiredException("WebDAV credentials invalid.")
            // Some servers return 409 Conflict if parent doesn't exist, or 507 Insufficient Storage
            if (code == 507) throw CloudStorageException.QuotaExceededException("WebDAV storage quota exceeded.")
        }
    }

    override suspend fun uploadBackup(
        archiveFile: File,
        remoteFileName: String
    ): CloudSyncResult = withContext(Dispatchers.IO) {
        if (!archiveFile.exists() || !archiveFile.isFile) {
            return@withContext CloudSyncResult.Error(
                CloudErrorType.FILE_NOT_FOUND,
                "Backup archive file does not exist: ${archiveFile.absolutePath}"
            )
        }

        try {
            ensureFolderExists()

            val fileUrl = URL("$baseEndpoint/$remoteFileName")
            val connection = (fileUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Content-Type", "application/octet-stream")
                setFixedLengthStreamingMode(archiveFile.length())
            }

            BufferedOutputStream(connection.outputStream).use { out ->
                FileInputStream(archiveFile).use { inStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                }
            }

            val code = connection.responseCode
            if (code in 200..299) {
                CloudSyncResult.Success(
                    remoteFileId = remoteFileName,
                    timestampMillis = System.currentTimeMillis(),
                    bytesTransferred = archiveFile.length()
                )
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                mapHttpError(code, errorBody)
            }
        } catch (e: Exception) {
            CloudSyncResult.Error(
                CloudErrorType.NETWORK_OFFLINE,
                "WebDAV upload failed: ${e.message}",
                e
            )
        }
    }

    override suspend fun listBackups(): Result<List<RemoteBackupMetadata>> = withContext(Dispatchers.IO) {
        runCatching {
            val folderUrl = URL("$baseEndpoint/")
            val connection = (folderUrl.openConnection() as HttpURLConnection).apply {
                setCustomMethod(this, "PROPFIND")
                doOutput = true
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Authorization", authHeader)
                setRequestProperty("Depth", "1")
                setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            }

            val propfindXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <d:propfind xmlns:d="DAV:">
                  <d:prop>
                    <d:displayname/>
                    <d:getcontentlength/>
                    <d:getlastmodified/>
                    <d:resourcetype/>
                  </d:prop>
                </d:propfind>
            """.trimIndent()

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(propfindXml) }

            val code = connection.responseCode
            if (code in 200..299 || code == 207) {
                val xmlResponse = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseMultiStatusXml(xmlResponse)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw createHttpException(code, errorBody)
            }
        }
    }

    override suspend fun downloadBackup(
        remoteFileId: String,
        destinationFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileUrl = if (remoteFileId.startsWith("http://") || remoteFileId.startsWith("https://")) {
                URL(remoteFileId)
            } else {
                URL("$baseEndpoint/$remoteFileId")
            }

            val connection = (fileUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Authorization", authHeader)
            }

            val code = connection.responseCode
            if (code in 200..299) {
                val parentDir = destinationFile.parentFile
                if (parentDir != null && !parentDir.exists()) parentDir.mkdirs()

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
                throw createHttpException(code, errorBody)
            }
        }
    }

    override suspend fun deleteBackup(remoteFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileUrl = if (remoteFileId.startsWith("http://") || remoteFileId.startsWith("https://")) {
                URL(remoteFileId)
            } else {
                URL("$baseEndpoint/$remoteFileId")
            }

            val connection = (fileUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                setRequestProperty("Authorization", authHeader)
            }

            val code = connection.responseCode
            if (code !in 200..299 && code != 404) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw createHttpException(code, errorBody)
            }
        }
    }

    override suspend fun disconnect() {
        // In-memory credentials cleared
    }

    private fun setCustomMethod(connection: HttpURLConnection, method: String) {
        try {
            connection.requestMethod = method
        } catch (_: Exception) {
            // Desktop JRE / OpenJDK HttpURLConnection method whitelist fallback
            try {
                val field = HttpURLConnection::class.java.getDeclaredField("method")
                field.isAccessible = true
                field.set(connection, method)
            } catch (_: Exception) {
                // Method override header for WebDAV proxies
                connection.requestMethod = "POST"
                connection.setRequestProperty("X-HTTP-Method-Override", method)
                connection.setRequestProperty("X-Method-Override", method)
            }
        }
    }

    private fun parseMultiStatusXml(xml: String): List<RemoteBackupMetadata> {
        val result = mutableListOf<RemoteBackupMetadata>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var inResponse = false
        var currentHref: String? = null
        var isCollection = false
        var contentLength: Long = 0L
        var lastModified: Long = 0L
        var displayName: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase() ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "response" -> {
                            inResponse = true
                            currentHref = null
                            isCollection = false
                            contentLength = 0L
                            lastModified = 0L
                            displayName = null
                        }
                        "collection" -> isCollection = true
                        "href" -> if (inResponse && currentHref == null) {
                            currentHref = parser.nextText().trim()
                        }
                        "displayname" -> if (inResponse) {
                            displayName = parser.nextText().trim()
                        }
                        "getcontentlength" -> if (inResponse) {
                            contentLength = parser.nextText().trim().toLongOrNull() ?: 0L
                        }
                        "getlastmodified" -> if (inResponse) {
                            val dateStr = parser.nextText().trim()
                            lastModified = runCatching {
                                RFC1123_FORMAT.parse(dateStr)?.time
                            }.getOrNull() ?: System.currentTimeMillis()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "response" && inResponse) {
                        inResponse = false
                        val href = currentHref ?: ""
                        val fileName = displayName ?: href.trimEnd('/').substringAfterLast('/')
                        // Exclude directories and targetFolder itself
                        if (!isCollection && fileName.isNotBlank() && (fileName.endsWith(".olad.enc") || fileName.endsWith(".zip"))) {
                            result.add(
                                RemoteBackupMetadata(
                                    fileId = fileName,
                                    fileName = fileName,
                                    sizeBytes = contentLength,
                                    modifiedTimeMillis = lastModified,
                                    providerId = providerId
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return result.sortedByDescending { it.modifiedTimeMillis }
    }

    private fun mapHttpError(code: Int, body: String): CloudSyncResult.Error {
        return when (code) {
            401 -> CloudSyncResult.Error(CloudErrorType.AUTH_EXPIRED, "WebDAV authentication expired.")
            403 -> CloudSyncResult.Error(CloudErrorType.STORAGE_ERROR, "WebDAV permission denied: $body")
            404 -> CloudSyncResult.Error(CloudErrorType.FILE_NOT_FOUND, "WebDAV destination not found.")
            507 -> CloudSyncResult.Error(CloudErrorType.QUOTA_EXCEEDED, "WebDAV server storage quota reached.")
            else -> CloudSyncResult.Error(CloudErrorType.UNKNOWN, "HTTP $code: $body")
        }
    }

    private fun createHttpException(code: Int, body: String): CloudStorageException {
        return when (code) {
            401 -> CloudStorageException.AuthExpiredException("WebDAV authentication failed: $body")
            507 -> CloudStorageException.QuotaExceededException("WebDAV storage quota exceeded: $body")
            404 -> CloudStorageException.FileNotFoundException("Resource not found: $body")
            else -> CloudStorageException.ServerException(code, body)
        }
    }
}
