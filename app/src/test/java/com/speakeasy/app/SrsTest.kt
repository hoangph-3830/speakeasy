package com.speakeasy.app

import com.speakeasy.app.data.Grade
import com.speakeasy.app.data.ReviewCard
import com.speakeasy.app.data.Srs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsTest {

    private val fresh = ReviewCard("w:gate", "gate", "cổng")

    @Test
    fun goodAnswersGrowTheGap() {
        var card = fresh
        val gaps = (1..4).map { day ->
            card = Srs.schedule(card, Grade.GOOD, today = 100L * day)
            card.interval
        }
        assertEquals(listOf(1, 3), gaps.take(2))
        assertTrue(gaps.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun forgettingResetsAndLowersEase() {
        val learned = fresh.copy(interval = 10, reps = 4, ease = 2.5f)
        val again = Srs.schedule(learned, Grade.AGAIN, today = 50)
        assertEquals(50, again.due)
        assertEquals(0, again.reps)
        assertTrue(again.ease < learned.ease)
    }

    @Test
    fun easyBeatsGoodBeatsHard() {
        val card = fresh.copy(interval = 5, reps = 3)
        val (hard, good, easy) = listOf(Grade.HARD, Grade.GOOD, Grade.EASY).map { Srs.schedule(card, it, 0).interval }
        assertTrue(hard < good && good < easy)
        assertEquals("ngay", Srs.preview(card, Grade.AGAIN))
    }
}
