package com.speakeasy.app

import com.speakeasy.app.data.Grade
import com.speakeasy.app.data.ReviewCard
import com.speakeasy.app.data.Srs
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsTest {

    private val fresh = ReviewCard("w:gate", "gate", "cổng")
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun newCardStartsFromTheDefaultStabilities() {
        val gaps = Grade.entries.map { Srs.schedule(fresh, it, today = 100).due - 100 }
        assertEquals(listOf(0L, 1L, 4L, 14L), gaps)
        val good = Srs.schedule(fresh, Grade.GOOD, today = 100)
        assertEquals(100L, good.last)
        assertEquals(1, good.reps)
        assertEquals(4, good.interval)
        assertEquals("ngay", Srs.preview(fresh, Grade.AGAIN, 0))
        assertEquals("4 ngày", Srs.preview(fresh, Grade.GOOD, 0))
    }

    @Test
    fun goodAfterGoodGrowsTheGap() {
        var card = fresh
        var day = 0L
        val gaps = (1..4).map {
            card = Srs.schedule(card, Grade.GOOD, today = day)
            day = card.due
            card.interval
        }
        assertTrue(gaps.toString(), gaps.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun forgettingLowersStabilityAndCountsALapse() {
        val learned = Srs.schedule(Srs.schedule(fresh, Grade.GOOD, 0), Grade.GOOD, 4)
        val again = Srs.schedule(learned, Grade.AGAIN, today = learned.due)
        assertEquals(learned.due, again.due)
        assertEquals(0, again.reps)
        assertEquals(1, again.lapses)
        assertTrue(again.stability < learned.stability)
        assertTrue(again.difficulty > learned.difficulty)
    }

    @Test
    fun easyBeatsGoodBeatsHard() {
        val card = Srs.schedule(fresh, Grade.GOOD, 0)
        val (hard, good, easy) = listOf(Grade.HARD, Grade.GOOD, Grade.EASY).map { Srs.schedule(card, it, 4).interval }
        assertTrue(hard < good && good < easy)
    }

    @Test
    fun oldSm2CardsStillLoadAndSchedule() {
        val saved = """[{"id":"w:gate","front":"gate","back":"cổng","interval":10,"ease":2.5,"due":50,"reps":3}]"""
        val card = json.decodeFromString<List<ReviewCard>>(saved).single()
        assertEquals(0.0, card.stability, 0.0)
        val good = Srs.schedule(card, Grade.GOOD, today = 50)
        assertTrue(good.interval > 10)
        assertEquals(4, good.reps)
        assertEquals(50L, good.last)
        assertTrue(good.difficulty in 1.0..10.0)
        val again = Srs.schedule(card, Grade.AGAIN, today = 50)
        assertTrue(again.stability <= 10.0)
        assertEquals(1, again.lapses)
    }

    @Test
    fun gapLabels() {
        assertEquals("ngay", Srs.gapLabel(0))
        assertEquals("59 ngày", Srs.gapLabel(59))
        assertEquals("3 tháng", Srs.gapLabel(90))
        assertEquals("1.5 năm", Srs.gapLabel(548))
    }
}
