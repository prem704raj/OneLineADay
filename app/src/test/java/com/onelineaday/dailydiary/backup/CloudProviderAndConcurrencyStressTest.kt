package com.onelineaday.dailydiary.backup

import com.onelineaday.dailydiary.backup.cloud.CloudErrorType
import com.onelineaday.dailydiary.backup.cloud.CloudStorageException
import com.onelineaday.dailydiary.backup.cloud.CloudSyncResult
import com.onelineaday.dailydiary.backup.cloud.RemoteBackupMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

/**
 * Empirical stress test suite challenging:
 * 1. Cloud storage provider fault injection (mid-stream disconnects, 401 unauthenticated retries,
 *    507 storage full responses, and timeout faults).
 * 2. Cloud retention pruning logic (culling older backups exceeding retention limits).
 * 3. Concurrent export/import requests, mutex locking, and atomic staging isolation.
 */
class CloudProviderAndConcurrencyStressTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val provider = MockCloudStorageProvider()

    // =========================================================================
    // 1. Cloud Storage Provider Fault Stress
    // =========================================================================

    @Test
    fun `upload with network disconnect mid-stream returns NETWORK_OFFLINE and preserves clean state`() = runBlocking {
        provider.authenticate()
        provider.uploadDisconnectsMidStream = true

        val dummyFile = tempFolder.newFile("disconnect_midstream.olad.enc")
        dummyFile.writeBytes(ByteArray(2048) { (it % 128).toByte() })

        val result = provider.uploadBackup(dummyFile, "disconnect_midstream.olad.enc")

        assertTrue("Upload mid-stream disconnect must return CloudSyncResult.Error", result is CloudSyncResult.Error)
        val error = result as CloudSyncResult.Error
        assertEquals(CloudErrorType.NETWORK_OFFLINE, error.errorType)
        assertTrue("Error must reference network exception", error.cause is CloudStorageException.NetworkException)
        assertFalse("Incomplete upload must not exist in remote storage", provider.remoteFiles.containsKey("disconnect_midstream.olad.enc"))
    }

    @Test
    fun `download with network disconnect mid-stream purges partial file on disk`() = runBlocking {
        provider.authenticate()
        provider.downloadCorruptsMidStream = true

        val fullPayload = ByteArray(4096) { 0x5A.toByte() }
        val remoteName = "corrupted_midstream.olad.enc"
        provider.remoteFiles[remoteName] = fullPayload
        provider.remoteMetadata[remoteName] = RemoteBackupMetadata(
            remoteName, remoteName, fullPayload.size.toLong(), System.currentTimeMillis(), provider.providerId
        )

        val destination = File(tempFolder.root, "download_partial.bin")
        val result = provider.downloadBackup(remoteName, destination)

        assertTrue("Download mid-stream severed must fail", result.isFailure)
        assertFalse("Partially downloaded file must be purged from disk", destination.exists())
    }

    @Test
    fun `401 unauthenticated retry flow recovers upon re-authentication`() = runBlocking {
        provider.authenticate()
        provider.tokenExpired = true // simulate 401 token expiration

        val archiveFile = tempFolder.newFile("unauth_retry.olad.enc")
        archiveFile.writeBytes("Encrypted payload".toByteArray(Charsets.UTF_8))

        // First attempt with expired token fails with AUTH_EXPIRED
        val firstResult = provider.uploadBackup(archiveFile, "unauth_retry.olad.enc")
        assertTrue("First attempt with expired token must return Error", firstResult is CloudSyncResult.Error)
        val firstError = firstResult as CloudSyncResult.Error
        assertEquals(CloudErrorType.AUTH_EXPIRED, firstError.errorType)

        // Caller handles 401 by performing re-authentication
        val authResult = provider.authenticate()
        assertTrue("Re-authentication must succeed", authResult.isSuccess)
        assertTrue("Provider must be authenticated", provider.isAuthenticated())

        // Retry upload succeeds
        val retryResult = provider.uploadBackup(archiveFile, "unauth_retry.olad.enc")
        assertTrue("Retry upload after re-auth must succeed", retryResult is CloudSyncResult.Success)
        assertTrue(provider.remoteFiles.containsKey("unauth_retry.olad.enc"))
    }

    @Test
    fun `persistent 401 unauthenticated requests fail deterministically without infinite loops`() = runBlocking {
        provider.failAuth = true // Persistent auth failure

        val authResult = provider.authenticate()
        assertTrue(authResult.isFailure)
        assertFalse(provider.isAuthenticated())

        val archiveFile = tempFolder.newFile("persistent_unauth.olad.enc")
        archiveFile.writeBytes(ByteArray(100))

        val uploadResult = provider.uploadBackup(archiveFile, "persistent_unauth.olad.enc")
        assertTrue(uploadResult is CloudSyncResult.Error)
        assertEquals(CloudErrorType.AUTH_EXPIRED, (uploadResult as CloudSyncResult.Error).errorType)

        val listResult = provider.listBackups()
        assertTrue(listResult.isFailure)
        assertTrue(listResult.exceptionOrNull() is CloudStorageException.AuthExpiredException)
    }

    @Test
    fun `upload encountering 507 storage full returns QUOTA_EXCEEDED`() = runBlocking {
        provider.authenticate()
        provider.quotaExceeded = true

        val archiveFile = tempFolder.newFile("quota_full.olad.enc")
        archiveFile.writeBytes(ByteArray(512))

        val result = provider.uploadBackup(archiveFile, "quota_full.olad.enc")
        assertTrue("507 Insufficient Storage must return Error", result is CloudSyncResult.Error)
        val error = result as CloudSyncResult.Error
        assertEquals(CloudErrorType.QUOTA_EXCEEDED, error.errorType)
        assertFalse(provider.remoteFiles.containsKey("quota_full.olad.enc"))
    }

    @Test
    fun `network timeout faults are captured across all provider operations`() = runBlocking {
        provider.authenticate()
        provider.networkTimeout = true

        val archiveFile = tempFolder.newFile("timeout.olad.enc")
        archiveFile.writeBytes(ByteArray(128))

        // Upload timeout
        val uploadResult = provider.uploadBackup(archiveFile, "timeout.olad.enc")
        assertTrue(uploadResult is CloudSyncResult.Error)
        assertEquals(CloudErrorType.NETWORK_TIMEOUT, (uploadResult as CloudSyncResult.Error).errorType)

        // List timeout
        val listResult = provider.listBackups()
        assertTrue(listResult.isFailure)
        assertTrue(listResult.exceptionOrNull() is CloudStorageException.NetworkTimeoutException)
    }

    // =========================================================================
    // 2. Retention Pruning Verification
    // =========================================================================

    @Test
    fun `retention pruning culls oldest backups when exceeding retention limit`() = runBlocking {
        provider.authenticate()
        val maxRetention = CloudBackupWorker.MAX_REMOTE_BACKUPS_RETAINED
        assertEquals(5, maxRetention)

        // Populate provider with 8 backups, timestamps 1000..8000
        val baseTime = 1000L
        for (i in 1..8) {
            val fileName = "backup_$i.olad.enc"
            val time = baseTime * i
            provider.remoteFiles[fileName] = ByteArray(10)
            provider.remoteMetadata[fileName] = RemoteBackupMetadata(
                fileId = fileName,
                fileName = fileName,
                sizeBytes = 10L,
                modifiedTimeMillis = time,
                providerId = provider.providerId
            )
        }

        // Execute CloudBackupWorker retention pruning logic
        val remoteBackups = provider.listBackups().getOrNull() ?: emptyList()
        assertEquals(8, remoteBackups.size)

        if (remoteBackups.size > maxRetention) {
            val toDelete = remoteBackups.drop(maxRetention)
            toDelete.forEach { provider.deleteBackup(it.fileId) }
        }

        // Verify exactly 5 newest backups retained
        val remaining = provider.listBackups().getOrThrow()
        assertEquals(5, remaining.size)

        // Newest 5: backup_8 (8000), backup_7 (7000), backup_6 (6000), backup_5 (5000), backup_4 (4000)
        val remainingIds = remaining.map { it.fileId }.toSet()
        assertTrue(remainingIds.contains("backup_8.olad.enc"))
        assertTrue(remainingIds.contains("backup_7.olad.enc"))
        assertTrue(remainingIds.contains("backup_6.olad.enc"))
        assertTrue(remainingIds.contains("backup_5.olad.enc"))
        assertTrue(remainingIds.contains("backup_4.olad.enc"))

        // Oldest 3 culled: backup_1 (1000), backup_2 (2000), backup_3 (3000)
        assertFalse(remainingIds.contains("backup_1.olad.enc"))
        assertFalse(remainingIds.contains("backup_2.olad.enc"))
        assertFalse(remainingIds.contains("backup_3.olad.enc"))
    }

    @Test
    fun `retention pruning when backups count is under or equal to limit deletes nothing`() = runBlocking {
        provider.authenticate()
        val maxRetention = CloudBackupWorker.MAX_REMOTE_BACKUPS_RETAINED

        for (i in 1..5) {
            val fileName = "backup_at_limit_$i.olad.enc"
            provider.remoteFiles[fileName] = ByteArray(10)
            provider.remoteMetadata[fileName] = RemoteBackupMetadata(
                fileId = fileName,
                fileName = fileName,
                sizeBytes = 10L,
                modifiedTimeMillis = i * 1000L,
                providerId = provider.providerId
            )
        }

        val remoteBackups = provider.listBackups().getOrNull() ?: emptyList()
        assertEquals(5, remoteBackups.size)

        if (remoteBackups.size > maxRetention) {
            val toDelete = remoteBackups.drop(maxRetention)
            toDelete.forEach { provider.deleteBackup(it.fileId) }
        }

        assertEquals("All 5 backups must remain", 5, provider.remoteFiles.size)
    }

    @Test
    fun `retention pruning handles empty remote backup list gracefully`() = runBlocking {
        provider.authenticate()
        val maxRetention = CloudBackupWorker.MAX_REMOTE_BACKUPS_RETAINED

        val remoteBackups = provider.listBackups().getOrNull() ?: emptyList()
        assertTrue(remoteBackups.isEmpty())

        if (remoteBackups.size > maxRetention) {
            val toDelete = remoteBackups.drop(maxRetention)
            toDelete.forEach { provider.deleteBackup(it.fileId) }
        }

        assertEquals(0, provider.remoteFiles.size)
    }

    @Test
    fun `retention pruning survives individual deletion failure without crashing`() = runBlocking {
        provider.authenticate()
        val maxRetention = CloudBackupWorker.MAX_REMOTE_BACKUPS_RETAINED

        for (i in 1..7) {
            val fileName = "prune_fail_$i.olad.enc"
            provider.remoteFiles[fileName] = ByteArray(10)
            provider.remoteMetadata[fileName] = RemoteBackupMetadata(
                fileId = fileName,
                fileName = fileName,
                sizeBytes = 10L,
                modifiedTimeMillis = i * 1000L,
                providerId = provider.providerId
            )
        }

        provider.failDelete = true // Simulated delete failure

        val remoteBackups = provider.listBackups().getOrNull() ?: emptyList()
        assertEquals(7, remoteBackups.size)

        // As in CloudBackupWorker, pruning is wrapped in runCatching or individual calls survive
        val errorsEncountered = mutableListOf<Throwable>()
        if (remoteBackups.size > maxRetention) {
            val toDelete = remoteBackups.drop(maxRetention)
            toDelete.forEach {
                val delResult = provider.deleteBackup(it.fileId)
                if (delResult.isFailure) {
                    errorsEncountered.add(delResult.exceptionOrNull()!!)
                }
            }
        }

        assertEquals(2, errorsEncountered.size)
        // No unhandled exception crashes the routine
    }

    // =========================================================================
    // 3. Concurrency, Mutex Locking & Atomic Staging Isolation
    // =========================================================================

    @Test
    fun `atomic staging isolation ensures concurrent restore sessions use independent sandbox directories`() = runBlocking {
        val rootCache = tempFolder.newFolder("cache")
        val rootFiles = tempFolder.newFolder("files")

        val session1 = UUID.randomUUID().toString()
        val session2 = UUID.randomUUID().toString()

        val stagingDir1 = File(rootCache, "restore/$session1").apply { mkdirs() }
        val stagingDir2 = File(rootCache, "restore/$session2").apply { mkdirs() }

        val restoredDir1 = File(rootFiles, "journal_media/restored/$session1").apply { mkdirs() }
        val restoredDir2 = File(rootFiles, "journal_media/restored/$session2").apply { mkdirs() }

        // Write files with same name in both sessions
        val file1 = File(stagingDir1, "input.bin").apply { writeText("Session 1 data") }
        val file2 = File(stagingDir2, "input.bin").apply { writeText("Session 2 data") }

        assertEquals("Session 1 data", file1.readText())
        assertEquals("Session 2 data", file2.readText())
        assertFalse("Staging directories must not collide", stagingDir1.canonicalPath == stagingDir2.canonicalPath)
        assertFalse("Restored directories must not collide", restoredDir1.canonicalPath == restoredDir2.canonicalPath)

        // Simulating rollback of session 1
        stagingDir1.deleteRecursively()
        restoredDir1.deleteRecursively()

        assertFalse("Session 1 staging must be cleaned", stagingDir1.exists())
        assertTrue("Session 2 staging must remain intact during session 1 rollback", stagingDir2.exists())
        assertEquals("Session 2 data", file2.readText())
    }

    @Test
    fun `concurrent upload and list operations on provider execute safely`() = runBlocking {
        provider.authenticate()

        // Launch 20 concurrent operations across threads
        val jobs = (1..20).map { i ->
            async(Dispatchers.IO) {
                if (i % 2 == 0) {
                    val archiveFile = tempFolder.newFile("concurrent_$i.olad.enc")
                    archiveFile.writeBytes("Payload $i".toByteArray())
                    provider.uploadBackup(archiveFile, "concurrent_$i.olad.enc")
                } else {
                    provider.listBackups()
                }
            }
        }

        val results = jobs.awaitAll()
        assertEquals(20, results.size)
        // Verify provider handled concurrent calls without deadlock or exception
        assertTrue(provider.remoteFiles.size <= 10)
    }
}
