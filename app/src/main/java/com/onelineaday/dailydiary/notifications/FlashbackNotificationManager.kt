package com.onelineaday.dailydiary.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.onelineaday.dailydiary.R
import java.time.ZonedDateTime

/**
 * Manages daily flashback notification scheduling and notification channel configuration.
 */
class FlashbackNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "flashback_memories"
        const val NOTIFICATION_ID = 1002
        const val REQUEST_CODE = 2002
        const val PREFS_KEY_ENABLED = "flashback_notifications_enabled"
        const val PREFS_KEY_HOUR = "flashback_hour"
        const val PREFS_KEY_MINUTE = "flashback_minute"
        const val EXTRA_NAV_DESTINATION = "extra_nav_destination"
        const val DESTINATION_ON_THIS_DAY = "on_this_day"

        /**
         * Resolves a privacy-safe lockscreen teaser string based strictly on year offsets.
         * Guarantees zero leakage of diary entry text, mood, or private metadata.
         */
        fun formatTeaserMessage(
            yearOffsets: List<Int>,
            oneYearText: String,
            xYearsFormat: String,
            multiYearsText: String
        ): String {
            return when {
                yearOffsets.size == 1 && yearOffsets[0] == 1 -> oneYearText
                yearOffsets.size == 1 -> String.format(xYearsFormat, yearOffsets[0])
                else -> multiYearsText
            }
        }
    }

    init {
        createNotificationChannel()
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.flashback_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.flashback_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Schedules a daily recurring flashback check alarm at [hour]:[minute].
     */
    fun scheduleDailyFlashback(hour: Int = 9, minute: Int = 0) {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            flashbackPendingIntent()
        )
    }

    /**
     * Cancels scheduled flashback alarm.
     */
    fun cancelDailyFlashback() {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        manager.cancel(flashbackPendingIntent())
    }

    private fun flashbackPendingIntent(): PendingIntent {
        val intent = Intent(context, FlashbackReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
