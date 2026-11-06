package com.speakeasy.app

import com.speakeasy.app.data.CalendarDay
import com.speakeasy.app.data.Content
import com.speakeasy.app.data.DailyReminder
import com.speakeasy.app.data.LearningPlan
import com.speakeasy.app.data.PlacementAnswer
import com.speakeasy.app.data.Progress
import com.speakeasy.app.data.Streaks
import com.speakeasy.app.data.WeekTotals
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import kotlin.random.Random

/** Mirrors the web tests for the placement test, the study calendar and the daily reminder. */
class LearningPlanTest {

    private val content: Content =
        Json { ignoreUnknownKeys = true }.decodeFromString(Content.serializer(), File("src/main/assets/content.json").readText())

    private fun ans(level: String, vararg oks: Boolean) = oks.map { PlacementAnswer(level, it) }

    @Test
    fun placementQuizHasThreeQuestionsPerLevelWithFourOptions() {
        repeat(20) { seed ->
            val quiz = LearningPlan.placementQuiz(content.topics, Random(seed))
            assertEquals(LearningPlan.LEVEL_ORDER.size * LearningPlan.PLACEMENT_PER_LEVEL, quiz.size)
            assertEquals(LearningPlan.LEVEL_ORDER.flatMap { lv -> List(3) { lv } }, quiz.map { it.level })
            quiz.forEach { q ->
                assertEquals(q.prompt, 4, q.options.toSet().size)
                assertEquals(1, q.options.count { it == q.answer })
            }
        }
    }

    @Test
    fun placementStopsEarlyAndPlacesAtTheHighestUnbrokenLevel() {
        assertEquals("", LearningPlan.placementResult(emptyList()))
        assertEquals("", LearningPlan.placementResult(ans("A1", true, false, false)))
        assertEquals("A2", LearningPlan.placementResult(ans("A1", true, true, false) + ans("A2", true, true, true) + ans("B1", false, false)))
        // A pass above a failed level does not count.
        assertEquals("", LearningPlan.placementResult(ans("A1", false, false) + ans("A2", true, true, true)))
        assertTrue(LearningPlan.levelFailed(ans("B1", false, false), "B1"))
        assertFalse(LearningPlan.levelFailed(ans("B1", true, false), "B1"))
        assertFalse(LearningPlan.levelFailed(ans("A1", false, false), "B1"))
        assertEquals("A1", LearningPlan.studyLevel(""))
        assertEquals("B2", LearningPlan.studyLevel("B1"))
        assertEquals("C2", LearningPlan.studyLevel("C2"))
    }

    @Test
    fun studyCalendarKeepsXpPerDayForHalfAYearAndComparesWeeks() {
        var p = Progress()
        p = Streaks.addXp(p, 10, 1000)
        p = Streaks.addXp(p, 5, 1000)
        p = Streaks.addXp(p, 7, 1001)
        assertEquals(mapOf(1000L to 15, 1001L to 7), p.history)
        assertEquals(p, Streaks.addXp(p, 0, 1002))
        val trimmed = Streaks.recordDay(p.history, 1000L + Streaks.HISTORY_DAYS + 1, 3)
        assertEquals(listOf(1001L, 1001L + Streaks.HISTORY_DAYS), trimmed.keys.sorted())
        val days = LearningPlan.calendarDays(p.history, 1001, 2)
        assertEquals(14, days.size)
        assertEquals(listOf(CalendarDay(1000, 15), CalendarDay(1001, 7)), days.takeLast(2))
        assertEquals(988L, days.first().day)
        assertEquals(WeekTotals(7, 4), LearningPlan.weekTotals(mapOf(1001L to 7, 994L to 4, 980L to 100), 1001))
        assertEquals(listOf(0, 1, 2, 3, 3, 4), listOf(0, 5, 20, 30, 59, 60).map { LearningPlan.heat(it, 30) })
        assertEquals(84, LearningPlan.calendarDays(emptyMap(), 500).size)
    }

    @Test
    fun streakIsAtRiskOnlyInTheEveningWithNothingStudiedToday() {
        assertTrue(LearningPlan.streakAtRisk(Progress(streak = 3, todayXp = 0), 19))
        assertTrue(LearningPlan.streakAtRisk(Progress(streak = 3, todayXp = 0), 18))
        assertFalse(LearningPlan.streakAtRisk(Progress(streak = 3, todayXp = 5), 19))
        assertFalse(LearningPlan.streakAtRisk(Progress(streak = 3, todayXp = 0), 12))
        assertFalse(LearningPlan.streakAtRisk(Progress(streak = 0, todayXp = 0), 21))
    }

    @Test
    fun reminderFiresAtTheNextChosenTimeAndStaysQuietOnceTheGoalIsMet() {
        val now = LocalDateTime.of(2026, 11, 6, 9, 0)
        assertEquals(LocalDateTime.of(2026, 11, 6, 20, 0), DailyReminder.nextTrigger(now, "20:00"))
        assertEquals(LocalDateTime.of(2026, 11, 7, 7, 5), DailyReminder.nextTrigger(now, "07:05"))
        assertEquals(LocalDateTime.of(2026, 11, 7, 9, 0), DailyReminder.nextTrigger(now, "09:00"))
        assertEquals(LocalDateTime.of(2026, 11, 6, 20, 0), DailyReminder.nextTrigger(now, "nonsense"))
        assertEquals("07:05", DailyReminder.format(7, 5))
        assertNull(DailyReminder.message(Progress(todayXp = 30, dailyGoal = 30, streak = 4)))
        assertTrue(DailyReminder.message(Progress(todayXp = 10, dailyGoal = 30, streak = 4))!!.contains("4 ngày"))
        assertFalse(DailyReminder.message(Progress(streak = 0))!!.contains("Chuỗi"))
        assertEquals("20:00", Progress().reminderTime)
        assertFalse(Progress().reminderOn)
        assertNull(Progress().placedLevel)
    }
}
