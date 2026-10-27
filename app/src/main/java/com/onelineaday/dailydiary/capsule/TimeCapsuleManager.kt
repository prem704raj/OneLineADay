package com.onelineaday.dailydiary.capsule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.onelineaday.dailydiary.MainActivity
import com.onelineaday.dailydiary.notifications.FlashbackNotificationManager
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class TimeCapsuleManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "time_capsule_prefs"
        private const val KEY_CAPSULES_JSON = "saved_time_capsules"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAllCapsules(journalId: String = "default"): List<TimeCapsule> {
        val json = prefs.getString(KEY_CAPSULES_JSON, null) ?: return emptyList()
        val type = object : TypeToken<List<TimeCapsule>>() {}.type
        val list: List<TimeCapsule> = runCatching { gson.fromJson<List<TimeCapsule>>(json, type) }.getOrElse { emptyList() }
        return list.filter { it.journalId == journalId }
    }

    fun saveCapsule(capsule: TimeCapsule) {
        val all = getAllCapsulesRaw().toMutableList()
        all.removeAll { it.id == capsule.id }
        all.add(capsule)
        saveCapsulesRaw(all)
        scheduleUnlockAlarm(capsule)
    }

    fun revealCapsule(id: String): TimeCapsule? {
        val all = getAllCapsulesRaw().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index >= 0) {
            val updated = all[index].copy(isRevealed = true)
            all[index] = updated
            saveCapsulesRaw(all)
            return updated
        }
        return null
    }

    fun deleteCapsule(id: String) {
        val all = getAllCapsulesRaw().toMutableList()
        all.removeAll { it.id == id }
        saveCapsulesRaw(all)
    }

    private fun getAllCapsulesRaw(): List<TimeCapsule> {
        val json = prefs.getString(KEY_CAPSULES_JSON, null) ?: return emptyList()
        val type = object : TypeToken<List<TimeCapsule>>() {}.type
        return runCatching { gson.fromJson<List<TimeCapsule>>(json, type) }.getOrElse { emptyList() }
    }

    private fun saveCapsulesRaw(list: List<TimeCapsule>) {
        prefs.edit().putString(KEY_CAPSULES_JSON, gson.toJson(list)).apply()
    }

    private fun scheduleUnlockAlarm(capsule: TimeCapsule) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerMillis = capsule.unlockDate.atTime(9, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            if (triggerMillis > System.currentTimeMillis()) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(FlashbackNotificationManager.EXTRA_NAV_DESTINATION, "time_capsules")
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    capsule.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            }
        } catch (_: Exception) {
            // Ignore security exception if exact alarms restricted
        }
    }
}

