package com.onelineaday.dailydiary.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.onelineaday.dailydiary.MainActivity
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.data.JournalDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Home screen widget for One Line A Day
 * Shows today's entry (or private prompt if App Lock is enabled)
 */
class JournalWidget : AppWidgetProvider() {
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateWidgetAsync(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    companion object {
        suspend fun updateWidgetAsync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) = withContext(Dispatchers.IO) {
            val views = RemoteViews(context.packageName, R.layout.widget_journal)
            
            // Set click intent to open app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val isAppLockEnabled = prefs.getBoolean("app_lock_enabled", false)
            val activeJournalId = prefs.getString("active_journal_id", "default") ?: "default"

            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))

            if (isAppLockEnabled) {
                // Privacy protection: Never leak journal content on launcher widget when app lock is active
                views.setTextViewText(R.id.widget_date, today)
                views.setTextViewText(R.id.widget_mood, "🔒")
                views.setTextViewText(R.id.widget_content, "Tap to unlock your journal")
            } else {
                try {
                    val database = JournalDatabase.getDatabase(context)
                    val entry = database.journalDao().getEntryByDate(LocalDate.now(), activeJournalId)
                    
                    if (entry != null) {
                        views.setTextViewText(R.id.widget_date, "Today")
                        views.setTextViewText(R.id.widget_mood, entry.mood.emoji)
                        views.setTextViewText(R.id.widget_content, entry.content)
                    } else {
                        views.setTextViewText(R.id.widget_date, today)
                        views.setTextViewText(R.id.widget_mood, "✏️")
                        views.setTextViewText(R.id.widget_content, "Tap to write your line for today...")
                    }
                } catch (e: Exception) {
                    views.setTextViewText(R.id.widget_date, today)
                    views.setTextViewText(R.id.widget_mood, "✏️")
                    views.setTextViewText(R.id.widget_content, "Tap to write your line for today...")
                }
            }

            withContext(Dispatchers.Main) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                updateWidgetAsync(context, appWidgetManager, appWidgetId)
            }
        }
        
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, JournalWidget::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            
            CoroutineScope(Dispatchers.IO).launch {
                for (widgetId in widgetIds) {
                    updateWidgetAsync(context, appWidgetManager, widgetId)
                }
            }
        }
    }
}
