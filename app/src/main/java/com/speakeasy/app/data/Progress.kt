package com.speakeasy.app.data

data class Progress(
    val name: String = "",
    val xp: Int = 0,
    val todayXp: Int = 0,
    val dailyGoal: Int = 30,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveDay: Long = -1,
    /** "topicId:ACTIVITY" keys. */
    val done: Set<String> = emptySet(),
    val learnedWords: Set<String> = emptySet(),
    val speechRate: Float = 0.9f,
    val accent: String = "US",
    /** TTS voice picked in settings; empty means the best installed voice for [accent]. */
    val voiceName: String = "",
    /** Streak freezes in stock (Duolingo-style): one is earned per day the goal is met, two at most. */
    val freezes: Int = 0,
    val freezeEarnedDay: Long = -1,
    val freezeUsedDay: Long = -1,
    /** Today's activity counts for daily quests ("speak", "listen", "review"), and quests already claimed. */
    val counters: Map<String, Int> = emptyMap(),
    val countersDay: Long = -1,
    val claimed: Set<String> = emptySet(),
    /** Best score (0-100) per pronunciation lesson id. */
    val soundScores: Map<String, Int> = emptyMap(),
    /** Best score per vocabulary challenge id ("speed", "memory"...); memory keeps the fewest moves. */
    val gameBest: Map<String, Int> = emptyMap(),
    /** Epoch day → XP earned that day, for the study calendar; about six months are kept. */
    val history: Map<Long, Int> = emptyMap(),
    /** Placement test result: null = not taken, "" = start from A1, else the level passed. */
    val placedLevel: String? = null,
    /** Daily reminder time "HH:MM", and whether the reminder notification is on. */
    val reminderTime: String = "20:00",
    val reminderOn: Boolean = false,
    /** Best score (0-100) per tongue twister id. */
    val twisterBest: Map<String, Int> = emptyMap(),
    /** "adult" or "kids": which learning path Home shows. */
    val audience: String = "adult",
    /** Today's word puzzle, and the record over all days played. */
    val daily: DailyBoard? = null,
    val dailyStats: DailyStats = DailyStats(),
) {
    fun isDone(topicId: String, activity: Activity) = "$topicId:${activity.name}" in done

    fun doneCount(topicId: String) = Activity.entries.count { isDone(topicId, it) }

    fun count(key: String) = if (key == Quests.XP) todayXp else counters[key] ?: 0

    val level: Int get() = xp / 100 + 1
    val levelProgress: Float get() = (xp % 100) / 100f
    val goalProgress: Float get() = (todayXp.toFloat() / dailyGoal).coerceIn(0f, 1f)

    /** ELSA-style overall pronunciation score: the average of lessons tried so far. */
    val speakingScore: Int? get() = soundScores.values.takeIf { it.isNotEmpty() }?.average()?.toInt()
}

data class Quest(val id: String, val title: String, val emoji: String, val target: Int, val reward: Int)

object Quests {
    const val SPEAK = "speak"
    const val LISTEN = "listen"
    const val REVIEW = "review"
    const val XP = "xp"

    fun today(p: Progress) = listOf(
        Quest(SPEAK, "Nói 10 câu tiếng Anh", "🎤", 10, 10),
        Quest(LISTEN, "Hoàn thành 5 bài nghe", "🎧", 5, 10),
        Quest(REVIEW, "Ôn 10 thẻ ghi nhớ", "🔁", 10, 10),
        Quest(XP, "Đạt mục tiêu ${p.dailyGoal} XP", "🎯", p.dailyGoal, 5),
    )
}

/** Pure day-to-day rules over [Progress]: rolling over to a new day, earning XP and the XP history. */
object Streaks {
    const val MAX_FREEZES = 2
    const val HISTORY_DAYS = 182

    /**
     * Brings progress up to [day]: resets today's XP and quest counters, and either breaks the streak
     * or spends one freeze per missed day when there are enough of them.
     */
    fun rollOver(p: Progress, day: Long): Progress {
        val fresh = if (p.countersDay == day) p else p.copy(counters = emptyMap(), claimed = emptySet(), countersDay = day)
        if (p.lastActiveDay == day || p.lastActiveDay < 0) return fresh
        val missed = (day - p.lastActiveDay - 1).toInt()
        return when {
            missed == 0 -> fresh.copy(todayXp = 0)
            p.streak > 0 && p.freezes >= missed ->
                fresh.copy(todayXp = 0, freezes = p.freezes - missed, lastActiveDay = day - 1, freezeUsedDay = day)
            else -> fresh.copy(todayXp = 0, streak = 0)
        }
    }

    /** Adds XP to the day's total and drops days older than about six months. */
    fun recordDay(history: Map<Long, Int>, day: Long, amount: Int): Map<Long, Int> =
        (history + (day to (history[day] ?: 0) + amount)).filterKeys { it >= day - HISTORY_DAYS }

    /** Earns [amount] XP on [day]: extends the streak, records the day and maybe earns a freeze. */
    fun addXp(p: Progress, amount: Int, day: Long): Progress {
        if (amount <= 0) return p
        val streak = if (p.lastActiveDay == day) p.streak else p.streak + 1
        val todayXp = p.todayXp + amount
        // Meeting the daily goal earns a streak freeze, once per day.
        val earnsFreeze = todayXp >= p.dailyGoal && p.freezeEarnedDay != day && p.freezes < MAX_FREEZES
        return p.copy(
            xp = p.xp + amount,
            todayXp = todayXp,
            streak = streak,
            bestStreak = maxOf(p.bestStreak, streak),
            lastActiveDay = day,
            history = recordDay(p.history, day, amount),
            freezes = if (earnsFreeze) p.freezes + 1 else p.freezes,
            freezeEarnedDay = if (earnsFreeze) day else p.freezeEarnedDay,
        )
    }
}
