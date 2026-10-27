package com.onelineaday.dailydiary.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.onelineaday.dailydiary.backup.crypto.AesGcmBackupCryptoEngine
import com.onelineaday.dailydiary.backup.crypto.BackupContainerType
import com.onelineaday.dailydiary.backup.crypto.BackupCryptoEngine
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
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Enterprise-grade backup manager integrating authenticated AES-256-GCM containerization,
 * stream-based compression with zero-plaintext export, two-phase atomic restore with
 * bad-tag rollback, and legacy ZIP backward compatibility.
 */
class EncryptedBackupManager(
    private val context: Context,
    private val database: JournalDatabase,
    private val cryptoEngine: BackupCryptoEngine = AesGcmBackupCryptoEngine()
) {

    companion object {
        const val FORMAT_VERSION = 1
        private const val APP_ID = "com.onelineaday.dailydiary"
        private const val MAX_ARCHIVE_BYTES = 4L * 1024L * 1024L * 1024L // 4 GB
        private const val MAX_EXPANDED_BYTES = 8L * 1024L * 1024L * 1024L // 8 GB
        private const val MAX_SINGLE_FILE_BYTES = 2L * 1024L * 1024L * 1024L // 2 GB
        private const val MAX_MANIFEST_BYTES = 5L * 1024L * 1024L // 5 MB
        private const val BUFFER_SIZE = 64 * 1024
    }

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    /**
     * Streams current journal database and media attachments directly into an authenticated
     * AES-256-GCM encrypted binary container (.olad.enc) on disk.
     */
    suspend fun exportEncryptedBackup(passphrase: CharArray): BackupExportResult = withContext(Dispatchers.IO) {
        runCatching {
            require(passphrase.isNotEmpty()) { "Recovery passphrase cannot be empty." }

            val dao = database.journalDao()
            val snapshot = database.withTransaction {
                EncryptedBackupSnapshot(
                    journals = dao.getAllJournalsOnce(),
                    entries = dao.getAllEntriesForBackup()
                )
            }

            require(snapshot.journals.isNotEmpty()) { "No journal metadata exists to back up." }

            val sourceToDescriptor = linkedMapOf<String, BackupFileDto>()

            fun descriptorFor(path: String, kind: BackupFileKind): BackupFileDto? {
                val file = File(path)
                if (!file.exists() || !file.isFile) {
                    android.util.Log.w("EncryptedBackupManager", "Skipping missing attachment: $path")
                    return null
                }
                return sourceToDescriptor.getOrPut(path) {
                    require(file.length() <= MAX_SINGLE_FILE_BYTES) { "Attachment exceeds supported size." }
                    val id = UUID.randomUUID().toString()
                    val extension = file.extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }?.let { ".$it" } ?: ".bin"
                    BackupFileDto(
                        id = id,
                        archivePath = "files/$id$extension",
                        kind = kind.name,
                        byteSize = file.length(),
                        sha256 = sha256(file)
                    )
                }
            }

            val entryDtos = snapshot.entries.map { entry ->
                val photoDesc = entry.photoUri?.let { descriptorFor(it, BackupFileKind.MEDIA) }
                val mediaDescs = entry.mediaUris.mapNotNull { descriptorFor(it, BackupFileKind.MEDIA) }
                val audioDesc = entry.audioUri?.let { descriptorFor(it, BackupFileKind.AUDIO) }

                BackupEntryDto(
                    date = entry.date.toString(),
                    journalId = entry.journalId,
                    content = entry.content,
                    mood = entry.mood.name,
                    photoFileId = photoDesc?.id,
                    mediaFileIds = mediaDescs.map { it.id },
                    tags = entry.tags,
                    audioFileId = audioDesc?.id,
                    isPinned = entry.isPinned,
                    createdAt = entry.createdAt,
                    updatedAt = entry.updatedAt
                )
            }

            val manifest = BackupManifest(
                formatVersion = FORMAT_VERSION,
                appId = APP_ID,
                databaseSchemaVersion = database.openHelper.readableDatabase.version.takeIf { it > 0 } ?: 5,
                createdAtEpochMs = System.currentTimeMillis(),
                journals = snapshot.journals.map {
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
                "Backup exceeds supported total expanded size."
            }

            val exportDir = File(context.cacheDir, "journal_exports").apply { mkdirs() }
            val outputFile = File(exportDir, "OneLineADay_${System.currentTimeMillis()}.olad.enc")

            // Direct streaming pipeline: FileOutputStream -> CipherOutputStream -> BufferedOutputStream -> ZipOutputStream
            FileOutputStream(outputFile).use { fos ->
                val encryptingStream = cryptoEngine.createEncryptingOutputStream(passphrase, fos)
                ZipOutputStream(BufferedOutputStream(encryptingStream, BUFFER_SIZE)).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(gson.toJson(manifest).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    sourceToDescriptor.forEach { (sourcePath, descriptor) ->
                        zip.putNextEntry(ZipEntry(descriptor.archivePath))
                        FileInputStream(File(sourcePath)).use { input ->
                            input.copyTo(zip, bufferSize = BUFFER_SIZE)
                        }
                        zip.closeEntry()
                    }
                }
            }

            require(outputFile.length() <= MAX_ARCHIVE_BYTES) {
                outputFile.delete()
                "Encrypted backup archive exceeds size limit."
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", outputFile)
            BackupExportResult.Success(uri = uri, file = outputFile)
        }.getOrElse {
            BackupExportResult.Failure(reason = it.message ?: "Encrypted backup export failed.")
        }
    }

    /**
     * Two-phase atomic restore:
     * Phase 1 (Verification & Decryption): Decrypts into isolated sandbox cache. Bad tags abort immediately.
     * Phase 2 (Validation & Insertion): Validates zip integrity, unpacks media, and executes atomic Room transaction.
     */
    suspend fun restoreBackup(
        sourceUri: Uri,
        passphrase: CharArray? = null
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        val session = UUID.randomUUID().toString()
        val stagingDirectory = File(context.cacheDir, "restore/$session").apply { mkdirs() }
        val restoredFilesDirectory = File(context.filesDir, "journal_media/restored/$session").apply { mkdirs() }
        var databaseCommitted = false

        try {
            val stagedInput = File(stagingDirectory, "input.bin")
            copyUriWithLimit(sourceUri, stagedInput, MAX_ARCHIVE_BYTES)

            val archiveToRestore: File = when (val containerType = cryptoEngine.detectContainerType(stagedInput)) {
                is BackupContainerType.Encrypted -> {
                    if (passphrase == null || passphrase.isEmpty()) {
                        return@withContext BackupRestoreResult.Failure("Passphrase required for encrypted backup archive.")
                    }

                    val decryptedZip = File(stagingDirectory, "decrypted.zip")
                    val decryptResult = stagedInput.inputStream().use { cipherIn ->
                        decryptedZip.outputStream().use { plainOut ->
                            cryptoEngine.decryptStream(passphrase, cipherIn, plainOut)
                        }
                    }

                    if (decryptResult.isFailure) {
                        return@withContext BackupRestoreResult.Failure(
                            "Incorrect recovery passphrase or corrupted backup archive."
                        )
                    }
                    decryptedZip
                }
                is BackupContainerType.LegacyZip -> {
                    stagedInput
                }
                is BackupContainerType.Invalid -> {
                    return@withContext BackupRestoreResult.Failure("Invalid or corrupted backup container format.")
                }
            }

            // Phase 2: Validate decrypted archive and commit to Room
            val validated = validateArchive(archiveToRestore)

            val usableSpace = context.filesDir.usableSpace
            val requiredAttachmentBytes = validated.manifest.files.sumOf { it.byteSize }
            require(usableSpace >= requiredAttachmentBytes + 20L * 1024L * 1024L) {
                "Insufficient device storage to restore backup attachments."
            }

            val restoredPathById = mutableMapOf<String, String>()

            ZipFile(archiveToRestore).use { zip ->
                validated.manifest.files.forEach { descriptor ->
                    val zipEntry = zip.getEntry(descriptor.archivePath)
                        ?: error("Referenced attachment missing from archive: ${descriptor.archivePath}")

                    val extension = descriptor.archivePath.substringAfterLast('.', "bin")
                        .takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) } ?: "bin"

                    val destination = safeRestoreDestination(restoredFilesDirectory, descriptor.id, extension)

                    zip.getInputStream(zipEntry).use { input ->
                        copyZipEntryWithExactLimit(input, destination, descriptor.byteSize)
                    }

                    require(destination.length() == descriptor.byteSize) { "Attachment size verification failed." }
                    require(sha256(destination).equals(descriptor.sha256, ignoreCase = true)) {
                        "Attachment checksum verification failed."
                    }
                    restoredPathById[descriptor.id] = destination.absolutePath
                }
            }

            val journals = validated.manifest.journals.map {
                Journal(id = it.id, name = it.name, colorHex = it.colorHex, createdAt = it.createdAt)
            }
            require(journals.isNotEmpty()) { "Backup contains no journal metadata." }
            val journalIds = journals.map { it.id }.toSet()
            require(journalIds.size == journals.size) { "Duplicate journal IDs found." }

            val seenEntryKeys = hashSetOf<String>()
            val entries = validated.manifest.entries.map { dto ->
                require(dto.journalId in journalIds) { "Entry references unknown journal: ${dto.journalId}" }
                val date = LocalDate.parse(dto.date)
                val key = "${dto.journalId}|$date"
                require(seenEntryKeys.add(key)) { "Duplicate entry date/journal pair: $key" }

                val mood = runCatching { Mood.valueOf(dto.mood) }.getOrElse { Mood.NEUTRAL }
                val photoPath = dto.photoFileId?.let { restoredPathById[it] ?: error("Missing photo reference.") }
                val mediaPaths = dto.mediaFileIds.map { id -> restoredPathById[id] ?: error("Missing media reference.") }
                val audioPath = dto.audioFileId?.let { restoredPathById[it] ?: error("Missing audio reference.") }

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
                check(dao.getJournalCountOnce() == journals.size) { "Journal verification failed." }
                check(dao.getTotalEntryCountOnce() == entries.size) { "Entry verification failed." }
            }
            databaseCommitted = true

            BackupRestoreResult.Success(restoredJournals = journals.size, restoredEntries = entries.size)
        } catch (t: Throwable) {
            if (!databaseCommitted) {
                runCatching { restoredFilesDirectory.deleteRecursively() }
            }
            BackupRestoreResult.Failure(reason = t.message ?: "Restore failed.")
        } finally {
            runCatching { stagingDirectory.deleteRecursively() }
        }
    }

    /**
     * Sniffs and inspects backup contents without modifying any persistent state.
     */
    suspend fun inspectBackup(
        sourceUri: Uri,
        passphrase: CharArray? = null
    ): BackupPreviewResult = withContext(Dispatchers.IO) {
        val session = UUID.randomUUID().toString()
        val stagingDir = File(context.cacheDir, "inspect/$session").apply { mkdirs() }

        try {
            val stagedInput = File(stagingDir, "input.bin")
            copyUriWithLimit(sourceUri, stagedInput, MAX_ARCHIVE_BYTES)

            val archiveFile = when (val type = cryptoEngine.detectContainerType(stagedInput)) {
                is BackupContainerType.Encrypted -> {
                    if (passphrase == null || passphrase.isEmpty()) {
                        return@withContext BackupPreviewResult.Failure("Passphrase required for encrypted backup.")
                    }
                    val decryptedZip = File(stagingDir, "decrypted.zip")
                    val decryptResult = stagedInput.inputStream().use { cipherIn ->
                        decryptedZip.outputStream().use { plainOut ->
                            cryptoEngine.decryptStream(passphrase, cipherIn, plainOut)
                        }
                    }
                    if (decryptResult.isFailure) {
                        return@withContext BackupPreviewResult.Failure("Incorrect recovery passphrase.")
                    }
                    decryptedZip
                }
                is BackupContainerType.LegacyZip -> stagedInput
                is BackupContainerType.Invalid -> {
                    return@withContext BackupPreviewResult.Failure("Invalid backup container format.")
                }
            }

            val validated = validateArchive(archiveFile)
            val entries = validated.manifest.entries
            val minDate = entries.minOfOrNull { it.date }
            val maxDate = entries.maxOfOrNull { it.date }

            BackupPreviewResult.Success(
                journalCount = validated.manifest.journals.size,
                entryCount = entries.size,
                minDate = minDate,
                maxDate = maxDate,
                totalAttachmentBytes = validated.manifest.files.sumOf { it.byteSize }
            )
        } catch (t: Throwable) {
            BackupPreviewResult.Failure(reason = t.message ?: "Inspection failed.")
        } finally {
            runCatching { stagingDir.deleteRecursively() }
        }
    }

    /**
     * Sniffs backup container type from Uri.
     */
    suspend fun detectContainerType(sourceUri: Uri): BackupContainerType = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                cryptoEngine.detectContainerType(input)
            } ?: BackupContainerType.Invalid
        }.getOrElse { BackupContainerType.Invalid }
    }

    private fun validateArchive(archive: File): EncryptedValidatedBackup {
        require(archive.exists() && archive.isFile) { "Archive file not found." }
        require(archive.length() <= MAX_ARCHIVE_BYTES) { "Archive exceeds supported size." }

        ZipFile(archive).use { zip ->
            val manifestEntry = zip.getEntry("manifest.json") ?: error("Manifest is missing.")
            require(manifestEntry.size <= MAX_MANIFEST_BYTES) { "Manifest is too large." }

            val manifest = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use {
                gson.fromJson(it, BackupManifest::class.java)
            } ?: error("Manifest could not be parsed.")

            require(manifest.appId == APP_ID) { "Unsupported application backup identifier: ${manifest.appId}" }
            require(manifest.formatVersion == FORMAT_VERSION) { "Unsupported backup version: ${manifest.formatVersion}" }

            val seenNames = hashSetOf<String>()
            val entriesEnum = zip.entries()
            while (entriesEnum.hasMoreElements()) {
                val entry = entriesEnum.nextElement()
                seenNames.add(entry.name)
            }

            manifest.files.forEach { fileDto ->
                require(fileDto.archivePath in seenNames) { "Missing archive attachment: ${fileDto.archivePath}" }
            }

            return EncryptedValidatedBackup(manifest)
        }
    }

    private fun copyUriWithLimit(sourceUri: Uri, destination: File, maxBytes: Long) {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: error("Selected backup file could not be opened.")

        input.use { inStream ->
            FileOutputStream(destination).use { outStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                var read: Int
                while (inStream.read(buffer).also { read = it } != -1) {
                    total += read
                    require(total <= maxBytes) { "Backup exceeds supported size." }
                    outStream.write(buffer, 0, read)
                }
                outStream.flush()
            }
        }
    }

    private fun copyZipEntryWithExactLimit(input: InputStream, destination: File, expectedBytes: Long) {
        FileOutputStream(destination).use { out ->
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                total += read
                require(total <= expectedBytes) { "Attachment exceeds expected bytes." }
                out.write(buffer, 0, read)
            }
            out.flush()
            require(total == expectedBytes) { "Attachment ended prematurely." }
        }
    }

    private fun safeRestoreDestination(root: File, fileId: String, extension: String): File {
        val canonicalRoot = root.canonicalFile
        val destination = File(canonicalRoot, "$fileId.$extension").canonicalFile
        require(destination.parentFile == canonicalRoot) {
            "Restore destination escaped its root."
        }
        return destination
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { inStream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (inStream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private data class EncryptedBackupSnapshot(
    val journals: List<Journal>,
    val entries: List<JournalEntry>
)

private data class EncryptedValidatedBackup(
    val manifest: BackupManifest
)
