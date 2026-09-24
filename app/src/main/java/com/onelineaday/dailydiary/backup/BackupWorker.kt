package com.onelineaday.dailydiary.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onelineaday.dailydiary.data.JournalDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val archiveManager = BackupArchiveManager(applicationContext, JournalDatabase.getDatabase(applicationContext))
                val backupDir = File(applicationContext.filesDir, "auto_backups")
                backupDir.mkdirs()
                
                // Keep only last 3 backups
                val backups = backupDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                backups.drop(2).forEach { it.delete() }
                
                // Create new backup
                val result = archiveManager.exportBackup()
                if (result is BackupExportResult.Success) {
                    val destFile = File(backupDir, "auto_backup_${System.currentTimeMillis()}.zip")
                    result.file.copyTo(destFile, overwrite = true)
                    applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit()
                        .putLong("last_auto_backup", System.currentTimeMillis())
                        .apply()
                    Result.success()
                } else {
                    Result.retry()
                }
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }
}
