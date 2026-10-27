package com.onelineaday.dailydiary.backup

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.onelineaday.dailydiary.backup.cloud.CloudBackupPrefs
import com.onelineaday.dailydiary.data.JournalDatabase
import java.util.concurrent.TimeUnit

/**
 * Orchestrator for scheduling and executing periodic encrypted cloud backups
 * using Android WorkManager with network and battery constraints.
 */
class CloudBackupManager(private val context: Context) {

    companion object {
        const val UNIQUE_WORK_NAME = "cloud_auto_backup"
        const val WORK_TAG = "cloud_backup"
    }

    private val prefs = CloudBackupPrefs(context)

    /**
     * Schedules periodic encrypted backup to cloud storage with battery not low,
     * unmetered network constraints, and a 2-hour flex window.
     */
    fun scheduleAutoBackup(
        intervalHours: Long = 24L,
        requireUnmetered: Boolean = true,
        requireBatteryNotLow: Boolean = true
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (requireUnmetered) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresBatteryNotLow(requireBatteryNotLow)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<CloudBackupWorker>(
            intervalHours, TimeUnit.HOURS,
            2L, TimeUnit.HOURS // 2-hour flex window
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        prefs.setAutoBackupEnabled(true)
        prefs.setBackupIntervalHours(intervalHours)
        prefs.setRequireUnmeteredNetwork(requireUnmetered)
        prefs.setRequireBatteryNotLow(requireBatteryNotLow)
    }

    fun cancelAutoBackup() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        prefs.setAutoBackupEnabled(false)
    }

    fun triggerImmediateBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    suspend fun exportToCloud(): Uri? {
        val database = JournalDatabase.getDatabase(context)
        val backupManager = EncryptedBackupManager(context, database)
        val passphrase = prefs.getSavedPassphrase()
        return if (passphrase != null && passphrase.isNotEmpty()) {
            val result = backupManager.exportEncryptedBackup(passphrase)
            if (result is BackupExportResult.Success) result.uri else null
        } else {
            val archiveManager = BackupArchiveManager(context, database)
            val result = archiveManager.exportBackup()
            if (result is BackupExportResult.Success) result.uri else null
        }
    }
}
