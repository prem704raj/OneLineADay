package com.onelineaday.dailydiary.backup

import com.onelineaday.dailydiary.backup.cloud.CloudErrorType
import com.onelineaday.dailydiary.backup.cloud.CloudSyncResult
import com.onelineaday.dailydiary.backup.cloud.RemoteBackupMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit test suite verifying CloudStorageProvider abstraction contract,
 * state transitions, streaming uploads/downloads, and simulated fault recovery.
 */
class CloudStorageProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val provider = MockCloudStorageProvider()

    @Test
    fun testAuthSuccess_transitionsState() = runBlocking {
        provider.failAuth = false
        val result = provider.authenticate()
        assertTrue("Authentication should succeed", result.isSuccess)
        assertTrue("Provider must be authenticated", provider.isAuthenticated())
    }

    @Test
    fun testAuthFailure_keepsStateFalse() = runBlocking {
        provider.failAuth = true
        val result = provider.authenticate()
        assertTrue("Authentication should fail", result.isFailure)
        assertFalse("Provider must remain unauthenticated", provider.isAuthenticated())
    }

    @Test
    fun testUpload_success() = runBlocking {
        provider.authenticate()
        val archiveFile = tempFolder.newFile("OneLineADay_test.olad.enc")
        val content = "Encrypted backup binary data content".toByteArray()
        archiveFile.writeBytes(content)

        val result = provider.uploadBackup(archiveFile, "OneLineADay_test.olad.enc")
        assertTrue("Upload must succeed", result is CloudSyncResult.Success)
        val success = result as CloudSyncResult.Success
        assertEquals("OneLineADay_test.olad.enc", success.remoteFileId)
        assertEquals(content.size.toLong(), success.bytesTransferred)
        assertTrue(provider.remoteFiles.containsKey("OneLineADay_test.olad.enc"))
    }

    @Test
    fun testUpload_unauthenticated_returnsError() = runBlocking {
        val archiveFile = tempFolder.newFile("test_unauth.olad.enc")
        archiveFile.writeBytes(ByteArray(100))

        val result = provider.uploadBackup(archiveFile, "test_unauth.olad.enc")
        assertTrue("Upload without auth must return Error", result is CloudSyncResult.Error)
        val error = result as CloudSyncResult.Error
        assertEquals(CloudErrorType.AUTH_EXPIRED, error.errorType)
        assertFalse(provider.remoteFiles.containsKey("test_unauth.olad.enc"))
    }

    @Test
    fun testUpload_networkTimeout_returnsError() = runBlocking {
        provider.authenticate()
        provider.networkTimeout = true
        val archiveFile = tempFolder.newFile("test_timeout.olad.enc")
        archiveFile.writeBytes(ByteArray(100))

        val result = provider.uploadBackup(archiveFile, "test_timeout.olad.enc")
        assertTrue("Upload with timeout must return Error", result is CloudSyncResult.Error)
        val error = result as CloudSyncResult.Error
        assertEquals(CloudErrorType.NETWORK_TIMEOUT, error.errorType)
    }

    @Test
    fun testUpload_quotaExceeded_returnsError() = runBlocking {
        provider.authenticate()
        provider.quotaExceeded = true
        val archiveFile = tempFolder.newFile("test_quota.olad.enc")
        archiveFile.writeBytes(ByteArray(100))

        val result = provider.uploadBackup(archiveFile, "test_quota.olad.enc")
        assertTrue("Upload with quota full must return Error", result is CloudSyncResult.Error)
        val error = result as CloudSyncResult.Error
        assertEquals(CloudErrorType.QUOTA_EXCEEDED, error.errorType)
    }

    @Test
    fun testListBackups_sortedChronologically() = runBlocking {
        provider.authenticate()

        val meta1 = RemoteBackupMetadata("id1", "file1.olad.enc", 1000L, 100_000L, provider.providerId)
        val meta2 = RemoteBackupMetadata("id2", "file2.olad.enc", 2000L, 300_000L, provider.providerId)
        val meta3 = RemoteBackupMetadata("id3", "file3.olad.enc", 1500L, 200_000L, provider.providerId)

        provider.remoteMetadata["file1.olad.enc"] = meta1
        provider.remoteMetadata["file2.olad.enc"] = meta2
        provider.remoteMetadata["file3.olad.enc"] = meta3

        val listResult = provider.listBackups()
        assertTrue(listResult.isSuccess)
        val list = listResult.getOrThrow()
        assertEquals(3, list.size)
        // Ordered newest to oldest: file2 (300k), file3 (200k), file1 (100k)
        assertEquals("id2", list[0].fileId)
        assertEquals("id3", list[1].fileId)
        assertEquals("id1", list[2].fileId)
    }

    @Test
    fun testListBackups_empty() = runBlocking {
        provider.authenticate()
        val listResult = provider.listBackups()
        assertTrue(listResult.isSuccess)
        assertTrue(listResult.getOrThrow().isEmpty())
    }

    @Test
    fun testDownload_success() = runBlocking {
        provider.authenticate()
        val testData = "Restored diary entry payload".toByteArray()
        provider.remoteFiles["remote_backup.olad.enc"] = testData

        val destinationFile = tempFolder.newFile("downloaded.olad.enc")
        val result = provider.downloadBackup("remote_backup.olad.enc", destinationFile)
        assertTrue("Download must succeed", result.isSuccess)
        assertArrayEquals(testData, destinationFile.readBytes())
    }

    @Test
    fun testDownload_notFound() = runBlocking {
        provider.authenticate()
        val destinationFile = tempFolder.newFile("not_found.olad.enc")
        val result = provider.downloadBackup("non_existent_id", destinationFile)
        assertTrue("Download non-existent file must fail", result.isFailure)
    }

    @Test
    fun testDownload_midStreamFailure_cleansUpDestination() = runBlocking {
        provider.authenticate()
        provider.downloadCorruptsMidStream = true
        val testData = ByteArray(1024) { (it % 256).toByte() }
        provider.remoteFiles["severed.olad.enc"] = testData

        val destinationFile = File(tempFolder.root, "severed_dest.olad.enc")
        val result = provider.downloadBackup("severed.olad.enc", destinationFile)
        assertTrue("Mid-stream connection loss must fail", result.isFailure)
        assertFalse("Destination file must be purged on mid-stream failure", destinationFile.exists())
    }

    @Test
    fun testDelete_removesFileAndMetadata() = runBlocking {
        provider.authenticate()
        provider.remoteFiles["to_delete.olad.enc"] = ByteArray(50)
        provider.remoteMetadata["to_delete.olad.enc"] = RemoteBackupMetadata("to_delete.olad.enc", "to_delete.olad.enc", 50L, 1000L, provider.providerId)

        val deleteResult = provider.deleteBackup("to_delete.olad.enc")
        assertTrue("Deletion must succeed", deleteResult.isSuccess)
        assertFalse(provider.remoteFiles.containsKey("to_delete.olad.enc"))
        assertFalse(provider.remoteMetadata.containsKey("to_delete.olad.enc"))
    }
}
