package com.onelineaday.dailydiary.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

class MediaStorageManager(
    private val context: Context
) {

    private val mediaRoot =
        File(
            context.filesDir,
            "journal_media"
        ).apply {
            mkdirs()
        }

    suspend fun importVisualMedia(
        sourceUri: Uri
    ): Result<StoredMedia> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver

                val mimeType =
                    resolver.getType(sourceUri)?.lowercase(Locale.ROOT)
                        ?: error("Unable to determine media type.")

                require(
                    mimeType.startsWith("image/") || mimeType.startsWith("video/")
                ) {
                    "Only image and video files are supported."
                }

                val category =
                    if (mimeType.startsWith("video/")) {
                        "video"
                    } else {
                        "image"
                    }

                val extension =
                    determineSafeExtension(
                        displayName = queryDisplayName(sourceUri),
                        mimeType = mimeType
                    )

                val directory =
                    File(mediaRoot, category).apply {
                        mkdirs()
                    }

                val destination =
                    File(directory, "${UUID.randomUUID()}$extension")

                val input =
                    resolver.openInputStream(sourceUri)
                        ?: error("Selected media could not be opened.")

                try {
                    input.use { source ->
                        FileOutputStream(destination).use { output ->
                            source.copyTo(output)
                            output.fd.sync()
                        }
                    }

                    require(
                        destination.exists() && destination.length() > 0L
                    ) {
                        "Selected media was empty."
                    }

                    StoredMedia(
                        absolutePath = destination.absolutePath,
                        mimeType = mimeType,
                        byteSize = destination.length()
                    )
                } catch (t: Throwable) {
                    runCatching { destination.delete() }
                    throw t
                }
            }
        }

    fun isManagedPath(path: String): Boolean {
        val canonicalRoot =
            runCatching { mediaRoot.canonicalFile }.getOrNull() ?: return false

        val canonicalFile =
            runCatching { File(path).canonicalFile }.getOrNull() ?: return false

        return canonicalFile.path.startsWith(canonicalRoot.path + File.separator)
    }

    suspend fun deleteIfUnreferenced(
        path: String,
        referencedPaths: Set<String>
    ): Boolean =
        withContext(Dispatchers.IO) {
            val fileName = runCatching { File(path).name }.getOrNull()
            val referencedFileNames = referencedPaths.mapNotNull { runCatching { File(it).name }.getOrNull() }.toSet()

            if (fileName != null && fileName in referencedFileNames) {
                return@withContext false
            }

            val canonical = runCatching { File(path).canonicalPath }.getOrNull()
            val absolute = runCatching { File(path).absolutePath }.getOrNull()
            val canonicalReferenced = referencedPaths.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }.toSet()
            val absoluteReferenced = referencedPaths.mapNotNull { runCatching { File(it).absolutePath }.getOrNull() }.toSet()

            if (path in referencedPaths ||
                (canonical != null && (canonical in canonicalReferenced || canonical in referencedPaths)) ||
                (absolute != null && (absolute in absoluteReferenced || absolute in referencedPaths))
            ) {
                return@withContext false
            }

            if (!isManagedPath(path)) {
                return@withContext false
            }

            runCatching { File(path).delete() }.getOrDefault(false)
        }

    suspend fun cleanupOrphans(
        referencedPaths: Set<String>
    ): Int =
        withContext(Dispatchers.IO) {
            if (!mediaRoot.exists()) {
                return@withContext 0
            }

            val referencedFileNames = referencedPaths.mapNotNull { path ->
                runCatching { File(path).name }.getOrNull()
            }.filter { it.isNotBlank() }.toSet()

            val canonicalReferenced = referencedPaths.mapNotNull { p ->
                runCatching { File(p).canonicalPath }.getOrNull()
            }.toSet()

            val absoluteReferenced = referencedPaths.mapNotNull { p ->
                runCatching { File(p).absolutePath }.getOrNull()
            }.toSet()

            var deleted = 0
            val now = System.currentTimeMillis()
            // 24-hour grace period: never delete recent files
            val gracePeriodMillis = 24 * 60 * 60 * 1000L

            mediaRoot
                .walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    if (now - file.lastModified() < gracePeriodMillis) {
                        return@forEach
                    }

                    val canonicalPath = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
                    val absolutePath = runCatching { file.absolutePath }.getOrNull() ?: return@forEach
                    val fileName = file.name

                    val isReferenced = fileName in referencedFileNames ||
                            canonicalPath in canonicalReferenced ||
                            absolutePath in absoluteReferenced ||
                            absolutePath in referencedPaths ||
                            canonicalPath in referencedPaths

                    if (!isReferenced && isManagedPath(canonicalPath)) {
                        if (runCatching { file.delete() }.getOrDefault(false)) {
                            deleted++
                        }
                    }
                }

            deleted
        }

    companion object {
        fun resolveMediaFile(context: Context, pathOrUri: String): File? {
            if (!pathOrUri.startsWith("/")) return null
            val direct = File(pathOrUri)
            if (direct.exists()) return direct

            // Try swapping /data/user/0/ and /data/data/ symlinks
            if (pathOrUri.contains("/data/user/0/")) {
                val alt = File(pathOrUri.replace("/data/user/0/", "/data/data/"))
                if (alt.exists()) return alt
            } else if (pathOrUri.contains("/data/data/")) {
                val alt = File(pathOrUri.replace("/data/data/", "/data/user/0/"))
                if (alt.exists()) return alt
            }

            // Fallback: search by file name inside internal storage journal_media
            val name = direct.name
            if (name.isNotEmpty()) {
                val mediaRoot = File(context.filesDir, "journal_media")
                if (mediaRoot.exists()) {
                    val found = mediaRoot.walkTopDown().firstOrNull { it.name == name }
                    if (found != null && found.exists()) return found
                }
            }

            return direct
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver
            .query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }

    private fun determineSafeExtension(
        displayName: String?,
        mimeType: String
    ): String {
        val fromName =
            displayName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }

        if (fromName != null) {
            return ".$fromName"
        }

        return when (mimeType) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/heic", "image/heif" -> ".heic"
            "image/gif" -> ".gif"
            "video/mp4" -> ".mp4"
            "video/webm" -> ".webm"
            "video/3gpp" -> ".3gp"
            else -> ".bin"
        }
    }
}

data class StoredMedia(
    val absolutePath: String,
    val mimeType: String,
    val byteSize: Long
)
