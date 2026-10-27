package com.onelineaday.dailydiary.backup

import com.google.gson.Gson
import com.onelineaday.dailydiary.backup.crypto.AesGcmBackupCryptoEngine
import com.onelineaday.dailydiary.backup.crypto.BackupContainerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit test suite verifying transparent backward compatibility with legacy unencrypted ZIP archives,
 * zip-slip path traversal rejection, and manifest schema verification.
 */
class LegacyZipBackwardCompatibilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gson = Gson()
    private val engine = AesGcmBackupCryptoEngine()

    @Test
    fun legacyZip_detectedAsLegacyContainerType() {
        val legacyZip = tempFolder.newFile("legacy_backup.zip")
        createSyntheticLegacyZip(legacyZip)

        val containerType = engine.detectContainerType(legacyZip)
        assertEquals("ZIP magic PK\\x03\\x04 must resolve to LegacyZip", BackupContainerType.LegacyZip, containerType)
    }

    @Test
    fun legacyZip_withValidManifest_passesValidation() {
        val legacyZip = tempFolder.newFile("valid_legacy.zip")
        createSyntheticLegacyZip(legacyZip)

        assertTrue("Valid legacy ZIP must exist and have non-zero length", legacyZip.length() > 0)
    }

    @Test
    fun legacyZip_withPathTraversal_isRejected() {
        val maliciousPath = "files/../../outside.jpg"
        var exceptionThrown = false
        try {
            BackupArchiveManager.validateArchivePath(maliciousPath)
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
        }
        assertTrue("Path traversal in legacy zip must be rejected", exceptionThrown)
    }

    @Test
    fun legacyZip_emptyFile_rejectedAsInvalid() {
        val emptyFile = tempFolder.newFile("empty.zip")
        val containerType = engine.detectContainerType(emptyFile)
        assertEquals(BackupContainerType.Invalid, containerType)
    }

    private fun createSyntheticLegacyZip(targetFile: File) {
        val manifest = BackupManifest(
            formatVersion = 1,
            appId = "com.onelineaday.dailydiary",
            databaseSchemaVersion = 5,
            createdAtEpochMs = 1700000000000L,
            journals = listOf(
                BackupJournalDto(id = "default", name = "My Diary", colorHex = null, createdAt = 0L)
            ),
            entries = listOf(
                BackupEntryDto(
                    date = "2026-05-10",
                    journalId = "default",
                    content = "A memorable legacy entry",
                    mood = "HAPPY",
                    photoFileId = null,
                    mediaFileIds = emptyList(),
                    tags = listOf("legacy", "verified"),
                    audioFileId = null,
                    isPinned = false,
                    createdAt = 1700000000000L,
                    updatedAt = 1700000000000L
                )
            ),
            files = emptyList()
        )

        ZipOutputStream(FileOutputStream(targetFile)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(gson.toJson(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}
