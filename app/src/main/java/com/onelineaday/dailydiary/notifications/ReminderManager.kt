package com.onelineaday.dailydiary.notifications

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
import java.util.Calendar
import android.app.AlarmManager

/**
 * Notification manager for daily reminders
 */
class ReminderManager(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val CHANNEL_NAME = "Daily Reminder"
        const val NOTIFICATION_ID = 1001
        const val REQUEST_CODE = 2001
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = "Daily reminder to write your one line"
                enableVibration(true)
                setShowBadge(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun scheduleDailyReminder(hour: Int = 20, minute: Int = 0) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Set time for 8 PM by default
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            
            // If time already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        // Schedule repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
    
    fun cancelReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

/**
 * Broadcast receiver for handling reminder alarms
 */
class ReminderReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        showNotification(context)
    }
    
    private fun showNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Intent to open app when notification is tapped
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Motivational messages
        val messages = listOf(
            "✨ How was your day? Capture it in one line!",
            "📝 Just 10 seconds to preserve today's memory",
            "🌟 Your future self will thank you for writing today",
            "💭 What's the one thing you want to remember about today?",
            "🔥 Keep your streak alive! Write your daily line",
            "📖 Every great story starts with one line",
            "🎯 10 seconds. 1 sentence. Infinite memories."
        )
        
        val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("One Line A Day")
            .setContentText(messages.random())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        
        notificationManager.notify(ReminderManager.NOTIFICATION_ID, notification)
    }
}

/**
 * Boot receiver to reschedule alarms after device restart
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                // Check if reminders were enabled and reschedule
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (prefs.getBoolean("reminders_enabled", false)) {
                    val hour = prefs.getInt("reminder_hour", 20)
                    val minute = prefs.getInt("reminder_minute", 0)
                    ReminderManager(context).scheduleDailyReminder(hour, minute)
                }
            }
        }
    }
}
