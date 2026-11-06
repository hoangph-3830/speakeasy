package com.speakeasy.app

import com.speakeasy.app.data.Challenge
import com.speakeasy.app.data.Challenges
import com.speakeasy.app.data.Levels
import com.speakeasy.app.data.SpeedState
import com.speakeasy.app.data.VocabContent
import com.speakeasy.app.data.VocabGroup
import com.speakeasy.app.data.VocabWord
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class ChallengesTest {

    private val vocab: VocabContent =
        Json { ignoreUnknownKeys = true }.decodeFromString(VocabContent.serializer(), File("src/main/assets/vocab.json").readText())

    private val family = VocabGroup(
        "family", "Gia đình", "👪",
        listOf(VocabWord("daughter", vi = "con gái"), VocabWord("cousin", vi = "anh chị em họ"), VocabWord("son", vi = "con trai")),
    )
    private val news = VocabGroup(
        "news", "Tin tức", "📰",
        listOf(VocabWord("crisis", vi = "khủng hoảng"), VocabWord("report", vi = "bản tin"), VocabWord("announce", vi = "thông báo")),
    )

    @Test
    fun choiceHasFourOptionsWithTheAnswerExactlyOnce() {
        repeat(200) { seed ->
            val q = Challenges.choice(vocab.groups, Random(seed))
            assertEquals(4, q.options.size)
            assertEquals(4, q.options.toSet().size)
            assertEquals(1, q.options.count { it == q.answer })
            assertEquals(q.word.vi, q.answer)
        }
    }

    @Test
    fun choiceTopsUpFromOtherGroupsWhenTheGroupIsSmall() {
        // A three-word group only has two other meanings, so one must come from elsewhere.
        repeat(50) { seed ->
            val q = Challenges.choice(listOf(family, news), Random(seed))
            assertEquals(4, q.options.toSet().size)
            assertEquals(1, q.options.count { it == q.answer })
        }
    }

    @Test
    fun choicePrefersMeaningsFromTheSameGroup() {
        repeat(50) { seed ->
            val q = Challenges.choice(vocab.groups, Random(seed))
            val group = vocab.groups.first { g -> g.words.any { it === q.word } }
            assertTrue(q.options.all { o -> group.words.any { it.vi == o } })
        }
    }

    @Test
    fun trueFalseIsRightAboutHalfTheTimeAndHonest() {
        val qs = List(400) { Challenges.trueFalse(vocab.groups, Random(it)) }
        qs.forEach { q -> assertEquals(q.isTrue, q.shown == q.word.vi) }
        val trues = qs.count { it.isTrue }
        assertTrue("$trues of 400", trues in 160..240)
    }

    @Test
    fun speedScoresOneThenTwoOnceTheStreakReachesFive() {
        var s = SpeedState()
        val scores = List(7) { s = Challenges.speedAnswer(s, correct = true); s.score }
        // The fifth in a row and every one after it are doubled.
        assertEquals(listOf(1, 2, 3, 4, 6, 8, 10), scores)
        assertEquals(1, Challenges.speedPoints(4))
        assertEquals(2, Challenges.speedPoints(5))
        assertTrue(s.doubled)
    }

    @Test
    fun speedMissResetsStreakAndCostsThreeSeconds() {
        val s = Challenges.speedAnswer(SpeedState(score = 9, streak = 6, timeLeftMs = 20_000), correct = false)
        assertEquals(9, s.score)
        assertEquals(0, s.streak)
        assertEquals(17_000, s.timeLeftMs)
        assertFalse(s.doubled)
        assertEquals(1, Challenges.speedAnswer(s, true).score - s.score)
    }

    @Test
    fun speedPenaltyNeverGoesBelowZero() {
        val s = Challenges.speedAnswer(SpeedState(timeLeftMs = 1_500), correct = false)
        assertEquals(0, s.timeLeftMs)
        assertTrue(s.isOver)
        assertEquals(60_000, SpeedState().timeLeftMs)
    }

    @Test
    fun memoryDeckHasSixPairsOfWordAndMeaning() {
        repeat(50) { seed ->
            val words = vocab.groups.flatMap { it.words }
            val deck = Challenges.memoryDeck(words, Random(seed))
            assertEquals(12, deck.size)
            assertEquals((0 until 12).toList(), deck.map { it.id })
            deck.groupBy { it.pairId }.values.forEach { pair ->
                assertEquals(2, pair.size)
                assertEquals(1, pair.count { it.english })
                val word = words.first { it.w == pair.first { c -> c.english }.text }
                assertEquals(word.vi, pair.first { !it.english }.text)
            }
            // No two cards read the same, so a face never gives away a wrong pair.
            assertEquals(12, deck.map { it.text }.toSet().size)
        }
    }

    @Test
    fun memoryPairsMatchOnlyTheirOwnPartner() {
        val deck = Challenges.memoryDeck(family.words + news.words, Random(3))
        val first = deck.first()
        val partner = deck.first { it.pairId == first.pairId && it.id != first.id }
        assertTrue(Challenges.isPair(first, partner))
        assertTrue(Challenges.isPair(partner, first))
        assertFalse(Challenges.isPair(first, first))
        deck.filter { it.pairId != first.pairId }.forEach { assertFalse(Challenges.isPair(first, it)) }
    }

    @Test
    fun hangmanWordsAreFourToEightPlainLetters() {
        val pool = Challenges.hangmanPool(vocab.groups.flatMap { it.words })
        assertTrue(pool.size > 100)
        pool.forEach { assertTrue(it.w, Regex("^[a-z]{4,8}$").matches(it.w)) }
        val mixed = listOf("cat", "ice cream", "e-mail", "Monday", "apple", "elephants", "strength").map { VocabWord(it, vi = it) }
        assertEquals(listOf("apple", "strength"), Challenges.hangmanPool(mixed).map { it.w })
        val round = Challenges.hangmanWords(vocab.groups.flatMap { it.words }, Random(1))
        assertEquals(5, round.size)
        assertEquals(5, round.map { it.w }.toSet().size)
    }

    @Test
    fun hangmanRevealsGuessedLettersAndCountsLives() {
        assertEquals("_ _ _ _ _", Challenges.masked("apple", emptySet()))
        assertEquals("a p p _ _", Challenges.masked("apple", setOf('a', 'p', 'z')))
        assertEquals(6, Challenges.livesLeft("apple", setOf('a', 'p')))
        assertEquals(5, Challenges.livesLeft("apple", setOf('a', 'z')))
        assertFalse(Challenges.isSolved("apple", setOf('a', 'p', 'l')))
        assertTrue(Challenges.isSolved("apple", setOf('a', 'p', 'l', 'e', 'x')))
        val sixMisses = "bcdfgh".toSet()
        assertEquals(0, Challenges.livesLeft("apple", sixMisses))
        assertTrue(Challenges.isLost("apple", sixMisses))
        assertFalse(Challenges.isLost("apple", "bcdfg".toSet()))
    }

    @Test
    fun recordsRespectHigherOrLowerIsBetter() {
        assertTrue(Challenges.isRecord(null, 4, lowerIsBetter = false))
        assertTrue(Challenges.isRecord(null, 0, lowerIsBetter = false))
        assertTrue(Challenges.isRecord(10, 11, lowerIsBetter = false))
        assertFalse(Challenges.isRecord(10, 10, lowerIsBetter = false))
        assertFalse(Challenges.isRecord(10, 9, lowerIsBetter = false))
        assertTrue(Challenges.isRecord(null, 14, lowerIsBetter = true))
        assertTrue(Challenges.isRecord(12, 9, lowerIsBetter = true))
        assertFalse(Challenges.isRecord(12, 12, lowerIsBetter = true))
        assertFalse(Challenges.isRecord(12, 15, lowerIsBetter = true))
        assertTrue(Challenge.MEMORY.lowerIsBetter)
        assertFalse(Challenge.SPEED.lowerIsBetter)
    }

    @Test
    fun xpFollowsEachGamesRule() {
        assertEquals(7, Challenges.xp(Challenge.SPEED, 15))
        assertEquals(5, Challenges.xp(Challenge.MEMORY, 22))
        assertEquals(8, Challenges.xp(Challenge.HANGMAN, 4))
        assertEquals(3, Challenges.xp(Challenge.TRUEFALSE, 7))
        assertEquals(6, Challenges.xp(Challenge.CLOZE, 6))
        assertEquals(8, Challenges.xp(Challenge.ANAGRAM, 8))
        assertEquals(0, Challenges.xp(Challenge.SPELLING, 0))
        assertEquals(
            listOf("speed", "memory", "hangman", "truefalse", "cloze", "anagram", "spelling"),
            Challenge.entries.map { it.id },
        )
        assertTrue(Challenge.entries.filter { it != Challenge.MEMORY }.none { it.lowerIsBetter })
        assertEquals(Challenge.HANGMAN, Challenge.from("hangman"))
    }

    @Test
    fun levelsRunFromA1ToC2() {
        assertEquals(listOf("A1", "A2", "B1", "B2", "C1", "C2"), Levels.all)
        assertEquals("Cao cấp", Levels.name("C1"))
        assertEquals("Thành thạo", Levels.name("C2"))
        assertTrue(Levels.rank("C2") > Levels.rank("C1") && Levels.rank("C1") > Levels.rank("B2"))
        assertEquals(6, Levels.rank("X9"))
    }
}
