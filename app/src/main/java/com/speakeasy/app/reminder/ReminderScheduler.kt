package com.speakeasy.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.speakeasy.app.data.DailyReminder
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sets or clears the daily study reminder: an inexact repeating alarm (no exact-alarm permission needed)
 * that wakes [ReminderReceiver] around the chosen time each day.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "study-reminder"
    private const val REQUEST_CODE = 2000

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Schedules the reminder at [time] ("HH:MM") when [on], or cancels it. Safe to call repeatedly. */
    fun apply(context: Context, on: Boolean, time: String) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = alarmIntent(context)
        if (!on) {
            alarms.cancel(intent)
            return
        }
        ensureChannel(context)
        val first = DailyReminder.nextTrigger(LocalDateTime.now(), time)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, AlarmManager.INTERVAL_DAY, intent)
    }

    /** The "Nhắc học" notification channel (Android 8+, which is every supported version). */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Nhắc học", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nhắc bạn học tiếng Anh mỗi ngày để giữ chuỗi"
            },
        )
    }
}
