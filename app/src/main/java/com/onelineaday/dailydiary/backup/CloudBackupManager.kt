package com.onelineaday.dailydiary.backup

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.onelineaday.dailydiary.data.JournalDatabase
import java.util.concurrent.TimeUnit

class CloudBackupManager(private val context: Context) {
    
    suspend fun exportToCloud(): Uri? {
        val archiveManager = BackupArchiveManager(context, JournalDatabase.getDatabase(context))
        val result = archiveManager.exportBackup()
        return when (result) {
            is BackupExportResult.Success -> result.uri
            else -> null
        }
    }
    
    // Schedule periodic auto-backup
    fun scheduleAutoBackup(intervalHours: Int) {
        val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        ).build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "auto_backup",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
    
    fun cancelAutoBackup() {
        WorkManager.getInstance(context).cancelUniqueWork("auto_backup")
    }
}
