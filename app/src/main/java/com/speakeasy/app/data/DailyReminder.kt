package com.speakeasy.app.data

import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** Pure rules for the daily study reminder; the alarm and the notification live in the reminder package. */
object DailyReminder {
    const val DEFAULT_TIME = "20:00"

    /** "HH:MM" as a time of day; anything unreadable falls back to [DEFAULT_TIME]. */
    fun parse(time: String): LocalTime {
        val parts = time.split(':').mapNotNull { it.trim().toIntOrNull() }
        return if (parts.size == 2 && parts[0] in 0..23 && parts[1] in 0..59) LocalTime.of(parts[0], parts[1])
        else LocalTime.of(20, 0)
    }

    fun format(hour: Int, minute: Int) = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    /** The next moment at [time]: later today, or tomorrow when that time has already passed. */
    fun nextTrigger(now: LocalDateTime, time: String): LocalDateTime {
        val today = now.toLocalDate().atTime(parse(time))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** The notification text, or null when today's goal is already met and there is nothing to remind. */
    fun message(p: Progress): String? = when {
        p.todayXp >= p.dailyGoal -> null
        p.streak > 0 -> "🔥 Chuỗi ${p.streak} ngày đang chờ bạn! Học 1 bài ngắn để giữ chuỗi nhé."
        else -> "Đến giờ học tiếng Anh rồi! 15 phút mỗi ngày là đủ để tiến bộ."
    }
}
