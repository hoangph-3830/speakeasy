package com.speakeasy.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

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
        history = readMap("history").mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }.toMap(),
        placedLevel = if (prefs.contains("placedLevel")) prefs.getString("placedLevel", "") else null,
        reminderTime = prefs.getString("reminderTime", "20:00") ?: "20:00",
        reminderOn = prefs.getBoolean("reminderOn", false),
        twisterBest = readMap("twisterBest"),
        audience = prefs.getString("audience", "adult") ?: "adult",
        daily = prefs.getString("dailyGuesses", null)?.let { g ->
            DailyBoard(prefs.getLong("dailyDay", -1), g.split(',').filter { it.isNotEmpty() })
        },
        dailyStats = DailyStats.decode(prefs.getString("dailyStats", null)),
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
            .putStringSet("history", p.history.map { (k, v) -> "$k=$v" }.toSet())
            .apply {
                if (p.placedLevel == null) remove("placedLevel") else putString("placedLevel", p.placedLevel)
            }
            .putString("reminderTime", p.reminderTime)
            .putBoolean("reminderOn", p.reminderOn)
            .putStringSet("twisterBest", p.twisterBest.map { (k, v) -> "$k=$v" }.toSet())
            .putString("audience", p.audience)
            .apply {
                if (p.daily == null) remove("dailyGuesses").remove("dailyDay")
                else putLong("dailyDay", p.daily.day).putString("dailyGuesses", p.daily.guesses.joinToString(","))
            }
            .putString("dailyStats", p.dailyStats.encode())
            .apply()
    }

    private fun current() = Streaks.rollOver(_progress.value, today().toEpochDay())

    fun addXp(amount: Int) {
        if (amount > 0) save(Streaks.addXp(current(), amount, today().toEpochDay()))
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

    /** Keeps the best score per tongue twister. */
    fun recordTwister(id: String, score: Int) {
        val p = current()
        if ((p.twisterBest[id] ?: -1) < score) save(p.copy(twisterBest = p.twisterBest + (id to score)))
    }

    /**
     * Saves the guesses of [day]'s word puzzle; finishing it updates the record, and solving it earns XP.
     * Returns the XP earned.
     */
    fun saveDaily(day: Long, guesses: List<String>, answer: String): Int {
        val p = current()
        val over = DailyWord.isOver(guesses, answer)
        val won = DailyWord.isWon(guesses, answer)
        val stats = if (over) DailyWord.recordResult(p.dailyStats, day, won, guesses.size) else p.dailyStats
        save(p.copy(daily = DailyBoard(day, guesses), dailyStats = stats))
        val xp = if (won) DailyWord.xpFor(guesses.size) else 0
        addXp(xp)
        return xp
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
    fun setPlacedLevel(level: String) = save(_progress.value.copy(placedLevel = level))
    fun setAudience(audience: String) = save(_progress.value.copy(audience = audience))
    fun setReminder(on: Boolean, time: String) = save(_progress.value.copy(reminderOn = on, reminderTime = time))

    fun reset() {
        prefs.edit().clear().apply()
        _progress.value = Progress()
    }
}
