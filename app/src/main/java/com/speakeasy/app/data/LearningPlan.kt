package com.speakeasy.app.data

import kotlin.random.Random

/** "What does this sentence mean": an English [prompt] and four Vietnamese [options], [answer] once. */
data class PlacementQuestion(val level: String, val prompt: String, val answer: String, val options: List<String>)

data class PlacementAnswer(val level: String, val ok: Boolean)

/** One square of the study calendar. */
data class CalendarDay(val day: Long, val xp: Int)

data class WeekTotals(val thisWeek: Int, val lastWeek: Int)

/** Pure rules for the structured path around the lessons: placement test, study calendar, reminder. */
object LearningPlan {
    val LEVEL_ORDER = Levels.all
    const val PLACEMENT_PER_LEVEL = 3

    /** Correct answers out of [PLACEMENT_PER_LEVEL] needed to pass a level. */
    const val PLACEMENT_PASS = 2

    /** Three "what does this sentence mean" questions per level, easiest level first. */
    fun placementQuiz(topics: List<Topic>, random: Random = Random.Default): List<PlacementQuestion> =
        LEVEL_ORDER.flatMap { lv ->
            val pool = topics.filter { it.level == lv }.flatMap { it.sentences }
                .filter { (en, _) -> en.split(' ').size in 3..14 }
                .distinctBy { it.second }
            if (pool.size < 4) return@flatMap emptyList()
            pool.shuffled(random).take(PLACEMENT_PER_LEVEL).map { (en, vi) ->
                val wrong = pool.filter { it.second != vi }.shuffled(random).take(3).map { it.second }
                PlacementQuestion(lv, en, vi, (wrong + vi).shuffled(random))
            }
        }

    /** Whether the learner has already missed too many questions of [level] to pass it. */
    fun levelFailed(answers: List<PlacementAnswer>, level: String) =
        answers.count { it.level == level && !it.ok } > PLACEMENT_PER_LEVEL - PLACEMENT_PASS

    /** Highest level passed with every easier level passed too; "" when even A1 was missed. */
    fun placementResult(answers: List<PlacementAnswer>): String {
        var placed = ""
        for (lv in LEVEL_ORDER) {
            val mine = answers.filter { it.level == lv }
            if (mine.isEmpty() || mine.count { it.ok } < PLACEMENT_PASS) break
            placed = lv
        }
        return placed
    }

    /** The level the learner should study next after placing at [placed]. */
    fun studyLevel(placed: String): String =
        LEVEL_ORDER[minOf(LEVEL_ORDER.indexOf(placed) + 1, LEVEL_ORDER.lastIndex)]

    /** The last [weeks] weeks as columns of seven days ending [today], oldest first. */
    fun calendarDays(history: Map<Long, Int>, today: Long, weeks: Int = 12): List<CalendarDay> {
        val n = weeks * 7
        return List(n) { i -> (today - n + 1 + i).let { CalendarDay(it, history[it] ?: 0) } }
    }

    /** XP over the last seven days and the seven before that. */
    fun weekTotals(history: Map<Long, Int>, today: Long): WeekTotals {
        fun sum(from: Long, to: Long) = (from..to).sumOf { history[it] ?: 0 }
        return WeekTotals(sum(today - 6, today), sum(today - 13, today - 7))
    }

    /** Heat level 0-4 for a day, relative to the daily goal. */
    fun heat(xp: Int, goal: Int): Int = when {
        xp <= 0 -> 0
        xp * 2 < goal -> 1
        xp < goal -> 2
        xp < goal * 2 -> 3
        else -> 4
    }

    /** In the evening, with nothing studied yet today, a running streak is about to break. */
    fun streakAtRisk(p: Progress, hour: Int) = p.streak > 0 && p.todayXp == 0 && hour >= 18
}
