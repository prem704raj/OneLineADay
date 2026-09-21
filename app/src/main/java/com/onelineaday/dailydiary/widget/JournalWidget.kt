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
import com.onelineaday.dailydiary.security.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class JournalWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id ->
                    updateWidgetAsync(
                        context,
                        appWidgetManager,
                        id
                    )
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

            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.widget_journal
                )

            val openIntent =
                Intent(
                    context,
                    MainActivity::class.java
                ).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )

            views.setOnClickPendingIntent(
                R.id.widget_container,
                pendingIntent
            )

            val prefs =
                context.getSharedPreferences(
                    AppLockManager.PREFS_NAME,
                    Context.MODE_PRIVATE
                )

            val locked =
                prefs.getBoolean(
                    AppLockManager.KEY_APP_LOCK,
                    false
                )

            val journalId =
                prefs.getString(
                    "active_journal_id",
                    "default"
                ) ?: "default"

            val dateText =
                LocalDate.now().format(
                    DateTimeFormatter.ofPattern(
                        "EEEE, MMM d"
                    )
                )

            if (locked) {

                views.setTextViewText(
                    R.id.widget_date,
                    dateText
                )

                views.setTextViewText(
                    R.id.widget_mood,
                    "🔒"
                )

                views.setTextViewText(
                    R.id.widget_content,
                    context.getString(
                        R.string.widget_locked_message
                    )
                )

            } else {

                runCatching {
                    JournalDatabase
                        .getDatabase(context)
                        .journalDao()
                        .getEntryByDate(
                            LocalDate.now(),
                            journalId
                        )
                }.onSuccess { entry ->

                    if (entry != null) {

                        views.setTextViewText(
                            R.id.widget_date,
                            context.getString(
                                R.string.today
                            )
                        )

                        views.setTextViewText(
                            R.id.widget_mood,
                            entry.mood.emoji
                        )

                        views.setTextViewText(
                            R.id.widget_content,
                            entry.content
                        )

                    } else {
                        showEmpty(
                            context,
                            views,
                            dateText
                        )
                    }

                }.onFailure {
                    showEmpty(
                        context,
                        views,
                        dateText
                    )
                }
            }

            withContext(Dispatchers.Main) {
                appWidgetManager
                    .updateAppWidget(
                        appWidgetId,
                        views
                    )
            }
        }

        private fun showEmpty(
            context: Context,
            views: RemoteViews,
            dateText: String
        ) {
            views.setTextViewText(
                R.id.widget_date,
                dateText
            )

            views.setTextViewText(
                R.id.widget_mood,
                "✏️"
            )

            views.setTextViewText(
                R.id.widget_content,
                context.getString(
                    R.string.widget_empty_message
                )
            )
        }

        fun updateAllWidgets(
            context: Context
        ) {
            val manager =
                AppWidgetManager
                    .getInstance(context)

            val component =
                ComponentName(
                    context,
                    JournalWidget::class.java
                )

            val ids =
                manager.getAppWidgetIds(
                    component
                )

            CoroutineScope(Dispatchers.IO).launch {
                ids.forEach { id ->
                    updateWidgetAsync(
                        context,
                        manager,
                        id
                    )
                }
            }
        }
    }
}
