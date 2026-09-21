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
            if (path in referencedPaths || !isManagedPath(path)) {
                return@withContext false
            }

            runCatching { File(path).delete() }.getOrDefault(false)
        }

    suspend fun cleanupOrphans(
        referencedPaths: Set<String>
    ): Int =
        withContext(
            Dispatchers.IO
        ) {
            if (!mediaRoot.exists()) {
                return@withContext 0
            }

            var deleted = 0

            mediaRoot
                .walkTopDown()
                .filter {
                    it.isFile
                }
                .forEach { file ->

                    val path =
                        runCatching {
                            file.canonicalPath
                        }.getOrNull()
                            ?: return@forEach

                    if (
                        path !in
                        referencedPaths &&
                        isManagedPath(path)
                    ) {
                        if (
                            runCatching {
                                file.delete()
                            }.getOrDefault(false)
                        ) {
                            deleted++
                        }
                    }
                }

            deleted
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
