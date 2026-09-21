package com.onelineaday.dailydiary.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onelineaday.dailydiary.data.Journal
import com.onelineaday.dailydiary.data.JournalDatabase
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: JournalDatabase
    private lateinit var manager: BackupArchiveManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = BackupArchiveManager(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testARoundTripExportAndRestore() = runBlocking {
        val dao = database.journalDao()
        val journal = Journal(id = "j1", name = "Test Journal", colorHex = "#FF0000", createdAt = 1000L)
        dao.insertJournalsForRestore(listOf(journal))

        val mediaDir = File(context.filesDir, "journal_media").apply { mkdirs() }
        val testMedia = File(mediaDir, "test_image.jpg").apply {
            writeText("dummy image content")
        }

        val entry = JournalEntry(
            date = LocalDate.of(2026, 9, 21),
            journalId = "j1",
            content = "round trip test entry",
            mood = Mood.EXCITED,
            photoUri = testMedia.absolutePath,
            mediaUris = listOf(testMedia.absolutePath),
            tags = listOf("tag1", "test"),
            audioUri = null,
            isPinned = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insertEntriesForRestore(listOf(entry))

        val exportResult = manager.exportBackup()
        assertTrue("Export should succeed", exportResult is BackupExportResult.Success)
        val exportedFile = (exportResult as BackupExportResult.Success).file

        // Clear DB
        dao.deleteAllEntriesForRestore()
        dao.deleteAllJournalsForRestore()
        assertEquals(0, dao.getJournalCountOnce())
        assertEquals(0, dao.getTotalEntryCountOnce())

        // Restore
        val restoreResult = manager.restoreBackup(Uri.fromFile(exportedFile))
        assertTrue("Restore should succeed", restoreResult is BackupRestoreResult.Success)
        val success = restoreResult as BackupRestoreResult.Success
        assertEquals(1, success.restoredJournals)
        assertEquals(1, success.restoredEntries)

        val restoredEntries = dao.getAllEntriesForBackup()
        assertEquals(1, restoredEntries.size)
        val restoredEntry = restoredEntries[0]
        assertEquals("round trip test entry", restoredEntry.content)
        assertEquals(listOf("tag1", "test"), restoredEntry.tags)
        assertTrue(restoredEntry.photoUri != null && File(restoredEntry.photoUri!!).exists())
    }

    @Test
    fun testBRollbackOnCorruptArchive() = runBlocking {
        val dao = database.journalDao()
        val journal = Journal(id = "default", name = "My Diary", colorHex = null, createdAt = 1000L)
        dao.insertJournalsForRestore(listOf(journal))
        val entry = JournalEntry(
            date = LocalDate.of(2026, 1, 1),
            journalId = "default",
            content = "original entry",
            mood = Mood.HAPPY,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        dao.insertEntriesForRestore(listOf(entry))

        // Create a corrupt archive (invalid zip)
        val corruptFile = File(context.cacheDir, "corrupt.zip").apply {
            writeText("not a valid zip file")
        }

        val result = manager.restoreBackup(Uri.fromFile(corruptFile))
        assertTrue("Restore should fail", result is BackupRestoreResult.Failure)

        // Verify original entry preserved
        val entries = dao.getAllEntriesForBackup()
        assertEquals(1, entries.size)
        assertEquals("original entry", entries[0].content)
    }

    @Test
    fun testCMaliciousManifestFileId() = runBlocking {
        val maliciousZip = File(context.cacheDir, "malicious.zip")
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            val maliciousManifest = """
                {
                    "formatVersion": 1,
                    "appId": "com.onelineaday.dailydiary",
                    "databaseSchemaVersion": 5,
                    "createdAtEpochMs": 1000,
                    "journals": [{"id": "default", "name": "Diary", "createdAt": 1000}],
                    "entries": [],
                    "files": [
                        {
                            "id": "../escape",
                            "archivePath": "files/escape.bin",
                            "kind": "MEDIA",
                            "byteSize": 4,
                            "sha256": "9f83461663ba372fed6f4e8b3582262b9f3fb398a446da37c1258f41834072b2"
                        }
                    ]
                }
            """.trimIndent()
            zip.write(maliciousManifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("files/escape.bin"))
            zip.write("test".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val result = manager.restoreBackup(Uri.fromFile(maliciousZip))
        assertTrue("Restore should fail for malicious file ID", result is BackupRestoreResult.Failure)
    }
}

