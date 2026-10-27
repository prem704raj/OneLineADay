package com.onelineaday.dailydiary.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onelineaday.dailydiary.backup.cloud.CloudBackupPrefs
import com.onelineaday.dailydiary.backup.cloud.CloudErrorType
import com.onelineaday.dailydiary.backup.cloud.CloudStorageProvider
import com.onelineaday.dailydiary.backup.cloud.CloudSyncResult
import com.onelineaday.dailydiary.backup.cloud.GoogleDriveCloudProvider
import com.onelineaday.dailydiary.backup.cloud.WebDavCloudProvider
import com.onelineaday.dailydiary.data.JournalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scheduled background worker executing automated encrypted cloud backups
 * using WorkManager with network and battery constraints.
 */
class CloudBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val MAX_REMOTE_BACKUPS_RETAINED = 5
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = CloudBackupPrefs(applicationContext)

        if (!prefs.isAutoBackupEnabled()) {
            return@withContext Result.success()
        }

        val providerType = prefs.getActiveProviderType()
        if (providerType == "none" || providerType.isBlank()) {
            return@withContext Result.success()
        }

        val provider: CloudStorageProvider = when (providerType) {
            "google_drive" -> {
                GoogleDriveCloudProvider(tokenProvider = {
                    prefs.getGoogleAccountEmail()
                })
            }
            "webdav" -> {
                val serverUrl = prefs.getWebDavServerUrl()
                val username = prefs.getWebDavUsername()
                val password = prefs.getWebDavPassword()
                if (serverUrl.isBlank() || username.isBlank()) {
                    prefs.updateStatus("FAILED", error = "WebDAV configuration is incomplete.")
                    return@withContext Result.failure()
                }
                WebDavCloudProvider(
                    serverUrl = serverUrl,
                    username = username,
                    password = password
                )
            }
            else -> return@withContext Result.success()
        }

        if (!provider.isAuthenticated()) {
            prefs.updateStatus("AUTH_EXPIRED", error = "Cloud account disconnected. Re-authentication required.")
            return@withContext Result.failure()
        }

        val passphrase = prefs.getSavedPassphrase()
        if (passphrase == null || passphrase.isEmpty()) {
            prefs.updateStatus("FAILED", error = "No passphrase configured for unattended backup.")
            return@withContext Result.failure()
        }

        try {
            val database = JournalDatabase.getDatabase(applicationContext)
            val backupManager = EncryptedBackupManager(applicationContext, database)

            val exportResult = backupManager.exportEncryptedBackup(passphrase)
            if (exportResult !is BackupExportResult.Success) {
                val reason = (exportResult as? BackupExportResult.Failure)?.reason ?: "Export failed."
                prefs.updateStatus("FAILED", error = reason)
                return@withContext Result.retry()
            }

            val exportFile = exportResult.file
            val remoteFileName = "OneLineADay_${System.currentTimeMillis()}.olad.enc"

            val uploadResult = provider.uploadBackup(exportFile, remoteFileName)
            exportFile.delete() // Clean up local cache export immediately

            when (uploadResult) {
                is CloudSyncResult.Success -> {
                    prefs.updateStatus("SUCCESS", System.currentTimeMillis())

                    // Prune older remote backups to prevent quota bloat
                    runCatching {
                        val remoteBackups = provider.listBackups().getOrNull() ?: emptyList()
                        if (remoteBackups.size > MAX_REMOTE_BACKUPS_RETAINED) {
                            val toDelete = remoteBackups.drop(MAX_REMOTE_BACKUPS_RETAINED)
                            toDelete.forEach { provider.deleteBackup(it.fileId) }
                        }
                    }

                    Result.success()
                }
                is CloudSyncResult.Error -> {
                    prefs.updateStatus("FAILED", error = uploadResult.message)
                    if (uploadResult.errorType == CloudErrorType.NETWORK_OFFLINE ||
                        uploadResult.errorType == CloudErrorType.NETWORK_TIMEOUT
                    ) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            prefs.updateStatus("FAILED", error = e.message ?: "Cloud backup encountered an error.")
            Result.retry()
        }
    }
}
