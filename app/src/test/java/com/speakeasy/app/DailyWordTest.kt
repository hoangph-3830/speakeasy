package com.speakeasy.app

import com.speakeasy.app.data.DailyStats
import com.speakeasy.app.data.DailyWord
import com.speakeasy.app.data.LetterState.HIT
import com.speakeasy.app.data.LetterState.MISS
import com.speakeasy.app.data.LetterState.NEAR
import com.speakeasy.app.data.VocabContent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DailyWordTest {

    private val words = DailyWord.puzzleWords(
        Json { ignoreUnknownKeys = true }.decodeFromString(VocabContent.serializer(), File("src/main/assets/vocab.json").readText()).groups,
    )

    @Test
    fun sameWordForADayAndAWeekOfDifferentWords() {
        assertTrue(words.size > 100 && words.all { Regex("^[a-z]{5}$").matches(it.w) })
        assertEquals(words.size, words.map { it.w }.toSet().size)
        assertEquals(DailyWord.wordOfDay(words, 20400).w, DailyWord.wordOfDay(words, 20400).w)
        assertEquals(7, (0 until 7).map { DailyWord.wordOfDay(words, 20400L + it).w }.toSet().size)
    }

    @Test
    fun picksTheSameWordAsTheWebApp() {
        // Expected values come from web/js/daily-word.js run over web/data/vocab.json (the same file as the asset).
        assertEquals(473, words.size)
        val expected = mapOf(0L to "about", 20400L to "emoji", 20767L to "quiet", 21000L to "occur", -3L to "sport")
        expected.forEach { (day, w) -> assertEquals("day $day", w, DailyWord.wordOfDay(words, day).w) }
    }

    @Test
    fun letterColoursHandleRepeatedLetters() {
        assertEquals(listOf(HIT, HIT, HIT, HIT, HIT), DailyWord.scoreGuess("crane", "crane"))
        assertEquals(listOf(NEAR, MISS, NEAR, MISS, HIT), DailyWord.scoreGuess("eerie", "there"))
        assertEquals(listOf(MISS, MISS, HIT, HIT, MISS), DailyWord.scoreGuess("lllll", "hello"))
        assertEquals(listOf(NEAR, NEAR, HIT, MISS, NEAR), DailyWord.scoreGuess("apple", "paper"))
        assertEquals(mapOf('h' to HIT, 'x' to MISS, 'l' to HIT, 'e' to HIT, 'o' to HIT), DailyWord.keyColours(listOf("hxllx", "hello"), "hello"))
    }

    @Test
    fun guessesMustBeFivePlainLetters() {
        assertTrue(DailyWord.validGuess("hello"))
        assertFalse(DailyWord.validGuess("hell"))
        assertFalse(DailyWord.validGuess("Hello"))
        assertFalse(DailyWord.validGuess("he1lo"))
        assertTrue(DailyWord.isOver(listOf("aaaaa", "bbbbb", "ccccc", "ddddd", "eeeee", "fffff"), "hello"))
        assertFalse(DailyWord.isOver(listOf("aaaaa", "bbbbb", "ccccc", "ddddd", "eeeee"), "hello"))
        assertFalse(DailyWord.isWon(listOf("aaaaa"), "hello"))
        assertTrue(DailyWord.isOver(listOf("hello"), "hello"))
    }

    @Test
    fun streaksCountDaysSolvedInARow() {
        var s = DailyWord.recordResult(DailyStats(), 100, true, 3)
        s = DailyWord.recordResult(s, 101, true, 4)
        assertEquals(listOf(2, 2, 2, 2, 1, 1), listOf(s.played, s.won, s.streak, s.best, s.dist[2], s.dist[3]))
        assertEquals(1, DailyWord.recordResult(s, 103, true, 2).streak) // skipped a day
        val lost = DailyWord.recordResult(s, 102, false, 6)
        assertEquals(listOf(0, 2), listOf(lost.streak, lost.best))
        assertEquals(101L, lost.lastWonDay)
        assertEquals(listOf(5, 15), listOf(DailyWord.xpFor(6), DailyWord.xpFor(1)))
    }

    @Test
    fun shareTextAndStoredStatsRoundTrip() {
        assertEquals(
            "SpeakEasy · Từ của ngày #7 2/6\n🟩🟩🟩⬜🟩\n🟩🟩🟩🟩🟩",
            DailyWord.shareText(listOf("crate", "crane"), "crane", 7),
        )
        assertTrue(DailyWord.shareText(listOf("aaaaa"), "crane", 7).startsWith("SpeakEasy · Từ của ngày #7 X/6"))
        val s = DailyWord.recordResult(DailyWord.recordResult(DailyStats(), 100, true, 3), 101, false, 6)
        assertEquals(s, DailyStats.decode(s.encode()))
        assertEquals(DailyStats(), DailyStats.decode(null))
        assertEquals(DailyStats(), DailyStats.decode("1,2,x"))
    }
}
