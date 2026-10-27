package com.onelineaday.dailydiary.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.onelineaday.dailydiary.MainActivity
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.data.JournalDatabase
import com.onelineaday.dailydiary.memory.LeapDayFlashbackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * BroadcastReceiver triggered by AlarmManager to check for historical memories and post privacy-safe notifications.
 */
class FlashbackReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val enabled = prefs.getBoolean(FlashbackNotificationManager.PREFS_KEY_ENABLED, false)
                if (!enabled) return@launch

                val today = LocalDate.now()
                val targets = LeapDayFlashbackEngine.computeFlashbackTargets(today, historyYears = 5)
                val targetDates = targets.map { it.queryDate }

                val db = JournalDatabase.getDatabase(context)
                val matchingEntries = db.journalDao().getEntriesForDatesOnce(targetDates)

                if (matchingEntries.isNotEmpty()) {
                    val yearOffsets = matchingEntries.map { today.year - it.date.year }.distinct().sorted()
                    showFlashbackNotification(context, yearOffsets)
                }

                // Reschedule for the next day
                val hour = prefs.getInt(FlashbackNotificationManager.PREFS_KEY_HOUR, 9)
                val minute = prefs.getInt(FlashbackNotificationManager.PREFS_KEY_MINUTE, 0)
                FlashbackNotificationManager(context).scheduleDailyFlashback(hour, minute)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showFlashbackNotification(context: Context, yearOffsets: List<Int>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                FlashbackNotificationManager.EXTRA_NAV_DESTINATION,
                FlashbackNotificationManager.DESTINATION_ON_THIS_DAY
            )
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            FlashbackNotificationManager.REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Strict lock-screen privacy teaser: never expose raw journal content
        val teaser = FlashbackNotificationManager.formatTeaserMessage(
            yearOffsets = yearOffsets,
            oneYearText = context.getString(R.string.flashback_teaser_one_year),
            xYearsFormat = context.getString(R.string.flashback_teaser_x_years),
            multiYearsText = context.getString(R.string.flashback_teaser_multi_years)
        )

        val publicNotification = NotificationCompat.Builder(context, FlashbackNotificationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_journal)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.flashback_public_teaser))
            .build()

        val notification = NotificationCompat.Builder(context, FlashbackNotificationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_journal)
            .setContentTitle(context.getString(R.string.flashback_notification_title))
            .setContentText(teaser)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        manager.notify(FlashbackNotificationManager.NOTIFICATION_ID, notification)
    }
}
