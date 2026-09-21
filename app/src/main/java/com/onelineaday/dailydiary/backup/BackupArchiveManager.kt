package com.onelineaday.dailydiary.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.onelineaday.dailydiary.data.Journal
import com.onelineaday.dailydiary.data.JournalDatabase
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupArchiveManager(
    private val context: Context,
    private val database: JournalDatabase
) {

    companion object {
        const val FORMAT_VERSION = 1

        private const val APP_ID = "com.onelineaday.dailydiary"

        private const val MAX_ARCHIVE_BYTES = 4L * 1024L * 1024L * 1024L

        private const val MAX_EXPANDED_BYTES = 8L * 1024L * 1024L * 1024L

        private const val MAX_SINGLE_FILE_BYTES = 2L * 1024L * 1024L * 1024L

        private const val MAX_ZIP_ENTRIES = 100_000

        internal fun validateArchivePath(rawPath: String): String {
            require(rawPath.isNotBlank()) {
                "Blank archive path."
            }

            require(!rawPath.contains('\\')) {
                "Backslash paths are rejected."
            }

            require(!rawPath.startsWith("/")) {
                "Absolute archive paths are rejected."
            }

            require(!Regex("^[A-Za-z]:").containsMatchIn(rawPath)) {
                "Drive-qualified paths are rejected."
            }

            val segments = rawPath.split('/')

            require(
                segments.none { it == "." || it == ".." || it.isBlank() }
            ) {
                "Path traversal is rejected."
            }

            return rawPath
        }

        private val FILE_ID_REGEX =
            Regex(
                "^[0-9a-fA-F]{8}-" +
                    "[0-9a-fA-F]{4}-" +
                    "[0-9a-fA-F]{4}-" +
                    "[0-9a-fA-F]{4}-" +
                    "[0-9a-fA-F]{12}$"
            )

        internal fun validateFileId(
            fileId: String
        ): String {
            require(
                FILE_ID_REGEX.matches(
                    fileId
                )
            ) {
                "Invalid backup file ID."
            }

            return fileId
        }
    }

    private val gson: Gson =
        GsonBuilder()
            .disableHtmlEscaping()
            .create()

    suspend fun exportBackup(): BackupExportResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val dao = database.journalDao()

                val snapshot =
                    database.withTransaction {
                        BackupSnapshot(
                            journals = dao.getAllJournalsOnce(),
                            entries = dao.getAllEntriesForBackup()
                        )
                    }

                require(snapshot.journals.isNotEmpty()) {
                    "No journal metadata exists."
                }

                val sourceToDescriptor = linkedMapOf<String, BackupFileDto>()

                fun descriptorFor(
                    path: String,
                    kind: BackupFileKind
                ): BackupFileDto =
                    sourceToDescriptor.getOrPut(path) {
                        val file = File(path)

                        require(file.exists() && file.isFile) {
                            "A referenced attachment is missing: $path"
                        }

                        require(file.length() <= MAX_SINGLE_FILE_BYTES) {
                            "An attachment exceeds the supported size."
                        }

                        val id = UUID.randomUUID().toString()

                        val extension =
                            file.extension
                                .takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
                                ?.let { ".$it" }
                                ?: ".bin"

                        BackupFileDto(
                            id = id,
                            archivePath = "files/$id$extension",
                            kind = kind.name,
                            byteSize = file.length(),
                            sha256 = sha256(file)
                        )
                    }

                val entryDtos =
                    snapshot.entries.map { entry ->
                        val photoId =
                            entry.photoUri
                                ?.takeIf { it.startsWith("/") }
                                ?.let { descriptorFor(it, BackupFileKind.MEDIA).id }

                        val mediaIds =
                            entry.mediaUris
                                .filter { it.startsWith("/") }
                                .distinct()
                                .map { descriptorFor(it, BackupFileKind.MEDIA).id }

                        val audioId =
                            entry.audioUri
                                ?.takeIf { it.startsWith("/") }
                                ?.let { descriptorFor(it, BackupFileKind.AUDIO).id }

                        BackupEntryDto(
                            date = entry.date.toString(),
                            journalId = entry.journalId,
                            content = entry.content,
                            mood = entry.mood.name,
                            photoFileId = photoId,
                            mediaFileIds = mediaIds,
                            tags = entry.tags,
                            audioFileId = audioId,
                            isPinned = entry.isPinned,
                            createdAt = entry.createdAt,
                            updatedAt = entry.updatedAt
                        )
                    }

                val manifest =
                    BackupManifest(
                        formatVersion = FORMAT_VERSION,
                        appId = APP_ID,
                        databaseSchemaVersion = JournalDatabase.VERSION,
                        createdAtEpochMs = System.currentTimeMillis(),
                        journals =
                            snapshot.journals.map {
                                BackupJournalDto(
                                    id = it.id,
                                    name = it.name,
                                    colorHex = it.colorHex,
                                    createdAt = it.createdAt
                                )
                            },
                        entries = entryDtos,
                        files = sourceToDescriptor.values.toList()
                    )

                require(manifest.files.sumOf { it.byteSize } <= MAX_EXPANDED_BYTES) {
                    "Backup exceeds the supported expanded size."
                }

                val exportDir =
                    File(context.cacheDir, "journal_exports").apply {
                        mkdirs()
                    }

                val output =
                    File(
                        exportDir,
                        "OneLineADay_${System.currentTimeMillis()}.olad.zip"
                    )

                ZipOutputStream(
                    BufferedOutputStream(FileOutputStream(output))
                ).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(gson.toJson(manifest).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    sourceToDescriptor.forEach { (sourcePath, descriptor) ->
                        zip.putNextEntry(ZipEntry(descriptor.archivePath))
                        FileInputStream(File(sourcePath)).use { input ->
                            input.copyTo(zip)
                        }
                        zip.closeEntry()
                    }
                }

                require(output.length() <= MAX_ARCHIVE_BYTES) {
                    output.delete()
                    "Backup archive is too large."
                }

                val uri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        output
                    )

                BackupExportResult.Success(
                    uri = uri,
                    file = output
                )
            }.getOrElse {
                BackupExportResult.Failure(
                    reason = it.message ?: "Backup failed."
                )
            }
        }

    suspend fun restoreBackup(
        sourceUri: Uri
    ): BackupRestoreResult =
        withContext(Dispatchers.IO) {
            val session = UUID.randomUUID().toString()

            val stagingDirectory = File(context.cacheDir, "restore/$session")
            val restoredFilesDirectory = File(context.filesDir, "journal_media/restored/$session")

            var databaseCommitted = false

            try {
                stagingDirectory.mkdirs()
                restoredFilesDirectory.mkdirs()

                val stagedArchive = File(stagingDirectory, "input.zip")

                copyUriWithLimit(
                    sourceUri = sourceUri,
                    destination = stagedArchive,
                    maxBytes = MAX_ARCHIVE_BYTES
                )

                val validated = validateArchive(stagedArchive)

                val restoredPathById = mutableMapOf<String, String>()

                ZipFile(stagedArchive).use { zip ->
                    validated.manifest.files.forEach { descriptor ->
                        val zipEntry =
                            zip.getEntry(descriptor.archivePath)
                                ?: error("A referenced attachment is missing: ${descriptor.archivePath}")

                        val extension =
                            descriptor.archivePath
                                .substringAfterLast('.', "bin")
                                .takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
                                ?: "bin"

                        val destination =
                            safeRestoreDestination(
                                root = restoredFilesDirectory,
                                fileId = descriptor.id,
                                extension = extension
                            )

                        zip.getInputStream(zipEntry).use { input ->
                            copyZipEntryWithExactLimit(
                                input = input,
                                destination = destination,
                                expectedBytes = descriptor.byteSize
                            )
                        }

                        require(destination.length() == descriptor.byteSize) {
                            "Attachment size validation failed."
                        }

                        require(
                            sha256(destination).equals(descriptor.sha256, ignoreCase = true)
                        ) {
                            "Attachment checksum validation failed."
                        }

                        restoredPathById[descriptor.id] = destination.absolutePath
                    }
                }

                val journals =
                    validated.manifest.journals.map {
                        Journal(
                            id = it.id,
                            name = it.name,
                            colorHex = it.colorHex,
                            createdAt = it.createdAt
                        )
                    }

                require(journals.isNotEmpty()) {
                    "Backup contains no journals."
                }

                val journalIds = journals.map { it.id }.toSet()

                require(journalIds.size == journals.size) {
                    "Duplicate journal IDs were found."
                }

                val seenEntryKeys = hashSetOf<String>()

                val entries =
                    validated.manifest.entries.map { dto ->
                        require(dto.journalId in journalIds) {
                            "Entry references an unknown journal: ${dto.journalId}"
                        }

                        val date = LocalDate.parse(dto.date)
                        val key = "${dto.journalId}|$date"

                        require(seenEntryKeys.add(key)) {
                            "Duplicate entry date/journal pair: $key"
                        }

                        val mood =
                            runCatching { Mood.valueOf(dto.mood) }.getOrElse {
                                Mood.NEUTRAL
                            }

                        val photoPath =
                            dto.photoFileId?.let {
                                restoredPathById[it] ?: error("Missing photo reference.")
                            }

                        val mediaPaths =
                            dto.mediaFileIds.map { fileId ->
                                restoredPathById[fileId] ?: error("Missing media reference.")
                            }

                        val audioPath =
                            dto.audioFileId?.let {
                                restoredPathById[it] ?: error("Missing audio reference.")
                            }

                        JournalEntry(
                            date = date,
                            journalId = dto.journalId,
                            content = dto.content,
                            mood = mood,
                            photoUri = photoPath,
                            mediaUris = mediaPaths,
                            tags = dto.tags,
                            audioUri = audioPath,
                            isPinned = dto.isPinned,
                            createdAt = dto.createdAt,
                            updatedAt = dto.updatedAt
                        )
                    }

                val dao = database.journalDao()

                database.withTransaction {
                    dao.deleteAllEntriesForRestore()
                    dao.deleteAllJournalsForRestore()

                    dao.insertJournalsForRestore(journals)
                    dao.insertEntriesForRestore(entries)

                    check(dao.getJournalCountOnce() == journals.size) {
                        "Journal verification failed."
                    }

                    check(dao.getTotalEntryCountOnce() == entries.size) {
                        "Entry verification failed."
                    }
                }

                databaseCommitted = true

                BackupRestoreResult.Success(
                    restoredJournals = journals.size,
                    restoredEntries = entries.size
                )
            } catch (t: Throwable) {
                if (!databaseCommitted) {
                    runCatching {
                        restoredFilesDirectory.deleteRecursively()
                    }
                }

                BackupRestoreResult.Failure(
                    reason = t.message ?: "Restore failed."
                )
            } finally {
                runCatching {
                    stagingDirectory.deleteRecursively()
                }
            }
        }

    private fun validateArchive(archive: File): ValidatedBackup {
        ZipFile(archive).use { zip ->
            val seenNames = hashSetOf<String>()

            var entryCount = 0
            var expandedBytes = 0L

            val enumeration = zip.entries()

            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                entryCount++

                require(entryCount <= MAX_ZIP_ENTRIES) {
                    "Too many archive entries."
                }

                val name = validateArchivePath(entry.name)

                require(seenNames.add(name)) {
                    "Duplicate archive entry."
                }

                if (entry.isDirectory) {
                    continue
                }

                val size = entry.size

                require(size >= 0L) {
                    "Archive entry has an unknown size."
                }

                require(size <= MAX_SINGLE_FILE_BYTES) {
                    "Archive entry is too large."
                }

                expandedBytes += size

                require(expandedBytes <= MAX_EXPANDED_BYTES) {
                    "Expanded archive is too large."
                }

                require(
                    name == "manifest.json" ||
                    name.matches(Regex("""files/[0-9a-fA-F-]{36}\.[A-Za-z0-9]{1,10}"""))
                ) {
                    "Unsupported archive path: $name"
                }
            }

            val manifestEntry =
                zip.getEntry("manifest.json")
                    ?: error("Backup manifest is missing.")

            val json =
                zip.getInputStream(manifestEntry)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

            val manifest =
                gson.fromJson(json, BackupManifest::class.java)
                    ?: error("Backup manifest is invalid.")

            require(manifest.formatVersion == FORMAT_VERSION) {
                "Unsupported backup format version: ${manifest.formatVersion}"
            }

            require(manifest.appId == APP_ID) {
                "Backup belongs to another application."
            }

            require(
                manifest.databaseSchemaVersion in
                    1..JournalDatabase.VERSION
            ) {
                "Unsupported database schema version: " +
                    manifest.databaseSchemaVersion
            }

            val fileIds = hashSetOf<String>()
            val filePaths = hashSetOf<String>()

            manifest.files.forEach { descriptor ->
                val validatedId =
                    validateFileId(
                        descriptor.id
                    )

                require(
                    descriptor.archivePath.startsWith(
                        "files/$validatedId."
                    )
                ) {
                    "Backup file ID/path mismatch."
                }

                require(
                    descriptor.kind in
                        BackupFileKind.entries.map {
                            it.name
                        }
                ) {
                    "Invalid backup file kind."
                }

                require(fileIds.add(descriptor.id)) {
                    "Duplicate backup file ID."
                }

                require(filePaths.add(descriptor.archivePath)) {
                    "Duplicate backup file path."
                }

                require(descriptor.archivePath in seenNames) {
                    "Backup references a missing file."
                }

                require(descriptor.byteSize in 0..MAX_SINGLE_FILE_BYTES) {
                    "Invalid backup file size."
                }

                require(descriptor.sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
                    "Invalid backup checksum."
                }
            }

            return ValidatedBackup(manifest = manifest)
        }
    }


    private fun copyUriWithLimit(
        sourceUri: Uri,
        destination: File,
        maxBytes: Long
    ) {
        val input =
            context.contentResolver.openInputStream(sourceUri)
                ?: error("Selected backup could not be opened.")

        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L

                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) {
                        "Backup archive exceeds the supported size."
                    }
                    output.write(buffer, 0, read)
                }

                output.fd.sync()
            }
        }
    }

    private fun safeRestoreDestination(
        root: File,
        fileId: String,
        extension: String
    ): File {
        val validatedId =
            validateFileId(
                fileId
            )

        require(
            extension.matches(
                Regex(
                    "[A-Za-z0-9]{1,10}"
                )
            )
        ) {
            "Invalid backup extension."
        }

        val canonicalRoot =
            root.canonicalFile

        val destination =
            File(
                canonicalRoot,
                "$validatedId.$extension"
            ).canonicalFile

        require(
            destination.parentFile ==
                canonicalRoot
        ) {
            "Restore destination escaped its root."
        }

        return destination
    }

    private fun copyZipEntryWithExactLimit(
        input: java.io.InputStream,
        destination: File,
        expectedBytes: Long
    ) {
        require(
            expectedBytes in
                0..MAX_SINGLE_FILE_BYTES
        ) {
            "Invalid expected attachment size."
        }

        FileOutputStream(
            destination
        ).use { output ->
            val buffer =
                ByteArray(
                    DEFAULT_BUFFER_SIZE
                )

            var total =
                0L

            while (true) {
                val read =
                    input.read(
                        buffer
                    )

                if (read < 0) {
                    break
                }

                total +=
                    read

                require(
                    total <=
                        expectedBytes
                ) {
                    "Attachment exceeded declared size."
                }

                output.write(
                    buffer,
                    0,
                    read
                )
            }

            output.fd.sync()

            require(
                total ==
                    expectedBytes
            ) {
                "Attachment size validation failed."
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private data class BackupSnapshot(
    val journals: List<Journal>,
    val entries: List<JournalEntry>
)

data class BackupManifest(
    val formatVersion: Int,
    val appId: String,
    val databaseSchemaVersion: Int,
    val createdAtEpochMs: Long,
    val journals: List<BackupJournalDto>,
    val entries: List<BackupEntryDto>,
    val files: List<BackupFileDto>
)

data class BackupJournalDto(
    val id: String,
    val name: String,
    val colorHex: String?,
    val createdAt: Long
)

data class BackupEntryDto(
    val date: String,
    val journalId: String,
    val content: String,
    val mood: String,
    val photoFileId: String?,
    val mediaFileIds: List<String>,
    val tags: List<String>,
    val audioFileId: String?,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class BackupFileDto(
    val id: String,
    val archivePath: String,
    val kind: String,
    val byteSize: Long,
    val sha256: String
)

enum class BackupFileKind {
    MEDIA,
    AUDIO
}

private data class ValidatedBackup(
    val manifest: BackupManifest
)

sealed interface BackupExportResult {
    data class Success(
        val uri: Uri,
        val file: File
    ) : BackupExportResult

    data class Failure(
        val reason: String
    ) : BackupExportResult
}

sealed interface BackupRestoreResult {
    data class Success(
        val restoredJournals: Int,
        val restoredEntries: Int
    ) : BackupRestoreResult

    data class Failure(
        val reason: String
    ) : BackupRestoreResult
}
