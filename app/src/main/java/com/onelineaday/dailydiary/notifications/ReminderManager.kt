package com.onelineaday.dailydiary.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.onelineaday.dailydiary.MainActivity
import com.onelineaday.dailydiary.R
import java.time.ZonedDateTime

class ReminderManager(
    private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1001
        const val REQUEST_CODE = 2001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            enableVibration(true)
            setShowBadge(true)
        }

        manager.createNotificationChannel(channel)
    }

    fun scheduleDailyReminder(
        hour: Int = 20,
        minute: Int = 0
    ) {
        require(hour in 0..23)
        require(minute in 0..59)

        val now = ZonedDateTime.now()
        var next = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }

        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            reminderPendingIntent()
        )
    }

    fun cancelReminder() {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(reminderPendingIntent())
    }

    private fun reminderPendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        showNotification(context)

        val prefs = context.getSharedPreferences(
            "settings",
            Context.MODE_PRIVATE
        )

        if (prefs.getBoolean("reminders_enabled", false)) {
            ReminderManager(context).scheduleDailyReminder(
                hour = prefs.getInt("reminder_hour", 20),
                minute = prefs.getInt("reminder_minute", 0)
            )
        }
    }

    private fun showNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messages = listOf(
            R.string.reminder_message_1,
            R.string.reminder_message_2,
            R.string.reminder_message_3,
            R.string.reminder_message_4
        )

        val message = context.getString(messages.random())

        val notification = NotificationCompat.Builder(
            context,
            ReminderManager.CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_stat_journal)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        manager.notify(ReminderManager.NOTIFICATION_ID, notification)
    }
}

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val prefs = context.getSharedPreferences(
                    "settings",
                    Context.MODE_PRIVATE
                )

                if (prefs.getBoolean("reminders_enabled", false)) {
                    ReminderManager(context).scheduleDailyReminder(
                        hour = prefs.getInt("reminder_hour", 20),
                        minute = prefs.getInt("reminder_minute", 0)
                    )
                }
            }
        }
    }
}
