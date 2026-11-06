package com.speakeasy.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

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

object Streaks {
    const val MAX_FREEZES = 2

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
}

/** Local, offline replacement for Engrisk's Firestore `userProgress` collection. */
class ProgressStore(context: Context, private val today: () -> LocalDate = LocalDate::now) {

    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    private val _progress = MutableStateFlow(Streaks.rollOver(read(), today().toEpochDay()))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private fun readMap(key: String): Map<String, Int> =
        prefs.getStringSet(key, emptySet())!!.mapNotNull {
            val (k, v) = it.split('=', limit = 2).takeIf { parts -> parts.size == 2 } ?: return@mapNotNull null
            v.toIntOrNull()?.let { n -> k to n }
        }.toMap()

    private fun read() = Progress(
        name = prefs.getString("name", "") ?: "",
        xp = prefs.getInt("xp", 0),
        todayXp = prefs.getInt("todayXp", 0),
        dailyGoal = prefs.getInt("dailyGoal", 30),
        streak = prefs.getInt("streak", 0),
        bestStreak = prefs.getInt("bestStreak", 0),
        lastActiveDay = prefs.getLong("lastActiveDay", -1),
        done = prefs.getStringSet("done", emptySet())!!.toSet(),
        learnedWords = prefs.getStringSet("learnedWords", emptySet())!!.toSet(),
        speechRate = prefs.getFloat("speechRate", 0.9f),
        accent = prefs.getString("accent", "US") ?: "US",
        voiceName = prefs.getString("voiceName", "") ?: "",
        freezes = prefs.getInt("freezes", 0),
        freezeEarnedDay = prefs.getLong("freezeEarnedDay", -1),
        freezeUsedDay = prefs.getLong("freezeUsedDay", -1),
        counters = readMap("counters"),
        countersDay = prefs.getLong("countersDay", -1),
        claimed = prefs.getStringSet("claimed", emptySet())!!.toSet(),
        soundScores = readMap("soundScores"),
        gameBest = readMap("gameBest"),
    )

    private fun save(p: Progress) {
        _progress.value = p
        prefs.edit()
            .putString("name", p.name)
            .putInt("xp", p.xp)
            .putInt("todayXp", p.todayXp)
            .putInt("dailyGoal", p.dailyGoal)
            .putInt("streak", p.streak)
            .putInt("bestStreak", p.bestStreak)
            .putLong("lastActiveDay", p.lastActiveDay)
            .putStringSet("done", p.done)
            .putStringSet("learnedWords", p.learnedWords)
            .putFloat("speechRate", p.speechRate)
            .putString("accent", p.accent)
            .putString("voiceName", p.voiceName)
            .putInt("freezes", p.freezes)
            .putLong("freezeEarnedDay", p.freezeEarnedDay)
            .putLong("freezeUsedDay", p.freezeUsedDay)
            .putStringSet("counters", p.counters.map { (k, v) -> "$k=$v" }.toSet())
            .putLong("countersDay", p.countersDay)
            .putStringSet("claimed", p.claimed)
            .putStringSet("soundScores", p.soundScores.map { (k, v) -> "$k=$v" }.toSet())
            .putStringSet("gameBest", p.gameBest.map { (k, v) -> "$k=$v" }.toSet())
            .apply()
    }

    private fun current() = Streaks.rollOver(_progress.value, today().toEpochDay())

    fun addXp(amount: Int) {
        if (amount <= 0) return
        val p = current()
        val day = today().toEpochDay()
        val streak = if (p.lastActiveDay == day) p.streak else p.streak + 1
        val todayXp = p.todayXp + amount
        // Meeting the daily goal earns a streak freeze, once per day.
        val earnsFreeze = todayXp >= p.dailyGoal && p.freezeEarnedDay != day && p.freezes < Streaks.MAX_FREEZES
        save(
            p.copy(
                xp = p.xp + amount,
                todayXp = todayXp,
                streak = streak,
                bestStreak = maxOf(p.bestStreak, streak),
                lastActiveDay = day,
                freezes = if (earnsFreeze) p.freezes + 1 else p.freezes,
                freezeEarnedDay = if (earnsFreeze) day else p.freezeEarnedDay,
            )
        )
    }

    /** Counts toward today's quests. */
    fun count(key: String, n: Int = 1) {
        val p = current()
        save(p.copy(counters = p.counters + (key to (p.counters[key] ?: 0) + n)))
    }

    /** Claims a finished quest's reward; returns the XP given (0 if not finished or already claimed). */
    fun claim(quest: Quest): Int {
        val p = current()
        if (quest.id in p.claimed || p.count(quest.id) < quest.target) return 0
        save(p.copy(claimed = p.claimed + quest.id))
        addXp(quest.reward)
        return quest.reward
    }

    /** Keeps the best score per pronunciation lesson. */
    fun recordSound(lessonId: String, score: Int) {
        val p = current()
        if ((p.soundScores[lessonId] ?: -1) < score) save(p.copy(soundScores = p.soundScores + (lessonId to score)))
    }

    /** Keeps a challenge's best score; returns true when [score] is a new record. */
    fun recordGameBest(id: String, score: Int, lowerIsBetter: Boolean = false): Boolean {
        val p = current()
        if (!Challenges.isRecord(p.gameBest[id], score, lowerIsBetter)) return false
        save(p.copy(gameBest = p.gameBest + (id to score)))
        return true
    }

    /** Marks an activity done and returns the XP earned: full XP the first time, a small bonus on replays. */
    fun complete(topicId: String, activity: Activity, bonus: Int = 0): Int {
        val key = "$topicId:${activity.name}"
        val first = key !in _progress.value.done
        if (first) save(_progress.value.copy(done = _progress.value.done + key))
        val earned = (if (first) activity.xp else 2) + bonus
        addXp(earned)
        return earned
    }

    /** Records a finished item that is not a topic activity, e.g. "story:station". */
    fun markDone(key: String) {
        if (key !in _progress.value.done) save(_progress.value.copy(done = _progress.value.done + key))
    }

    fun toggleWord(word: String) {
        val p = _progress.value
        save(p.copy(learnedWords = if (word in p.learnedWords) p.learnedWords - word else p.learnedWords + word))
    }

    fun setName(name: String) = save(_progress.value.copy(name = name.trim()))
    fun setDailyGoal(goal: Int) = save(_progress.value.copy(dailyGoal = goal))
    fun setSpeechRate(rate: Float) = save(_progress.value.copy(speechRate = rate))
    fun setAccent(accent: String) = save(_progress.value.copy(accent = accent))
    fun setVoiceName(name: String) = save(_progress.value.copy(voiceName = name))

    fun reset() {
        prefs.edit().clear().apply()
        _progress.value = Progress()
    }
}
