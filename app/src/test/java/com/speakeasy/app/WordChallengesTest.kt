package com.speakeasy.app

import com.speakeasy.app.data.VocabContent
import com.speakeasy.app.data.VocabGroup
import com.speakeasy.app.data.VocabWord
import com.speakeasy.app.data.WordChallenges
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Mirrors the web test "new challenges: fill the gap, letter scramble and spelling". */
class WordChallengesTest {

    private val groups: List<VocabGroup> =
        Json { ignoreUnknownKeys = true }.decodeFromString(VocabContent.serializer(), File("src/main/assets/vocab.json").readText()).groups

    @Test
    fun clozeBlanksTheSentenceAndOffersFourDistinctWordsWithTheAnswerOnce() {
        repeat(200) { seed ->
            val r = WordChallenges.clozeRound(groups, Random(seed))
            assertTrue(r.sentence, r.sentence.contains(WordChallenges.BLANK))
            assertEquals(4, r.options.toSet().size)
            assertEquals(1, r.options.count { it == r.answer })
            assertEquals(r.word.exVi, r.translation)
        }
    }

    @Test
    fun clozeTopsUpOptionsFromOtherPartsOfSpeechWhenTooFewMatch() {
        val g = VocabGroup(
            "g", "G", "x",
            listOf(
                VocabWord("apple", pos = "n", vi = "táo", ex = "I ate an apple."),
                VocabWord("run", pos = "v", vi = "chạy", ex = "Run!"),
                VocabWord("big", pos = "adj", vi = "to", ex = "Big."),
                VocabWord("fast", pos = "adv", vi = "nhanh", ex = "Fast."),
                VocabWord("ice cream", pos = "n", vi = "kem", ex = "Ice cream."),
            ),
        )
        repeat(30) { seed ->
            val r = WordChallenges.clozeRound(listOf(g), Random(seed))
            assertEquals(4, r.options.toSet().size)
            assertFalse("ice cream" in r.options)
        }
    }

    @Test
    fun blankOutReplacesOnlyTheFirstWholeWordIgnoringCase() {
        assertEquals("He ate an _____. An apple a day.", WordChallenges.blankOut("He ate an apple. An apple a day.", "apple"))
        assertEquals("Pineapple and _____", WordChallenges.blankOut("Pineapple and apple", "apple"))
        assertEquals("_____ it.", WordChallenges.blankOut("Read it.", "read"))
        assertEquals("No match here.", WordChallenges.blankOut("No match here.", "apple"))
    }

    @Test
    fun anagramWordsAreScrambledButKeepTheirLetters() {
        val words = WordChallenges.anagramWords(groups)
        assertEquals(WordChallenges.ANAGRAM_ROUNDS, words.size)
        repeat(20) { seed ->
            for (w in WordChallenges.anagramWords(groups, Random(seed))) {
                assertTrue(w.w, Regex("^[a-z]{4,8}$").matches(w.w))
                val letters = WordChallenges.scrambleLetters(w.w, Random(seed))
                assertNotEquals(w.w, letters.joinToString(""))
                assertEquals(w.w.toList().sorted(), letters.sorted())
            }
        }
        // Two distinct letters only: the fallback rotation still never spells the word.
        repeat(20) { seed -> assertNotEquals("abab", WordChallenges.scrambleLetters("abab", Random(seed)).joinToString("")) }
    }

    @Test
    fun spellingTakesTenPlainWordsAndIgnoresCaseAndSpaces() {
        val words = WordChallenges.spellingWords(groups)
        assertEquals(WordChallenges.SPELLING_ROUNDS, words.size)
        words.forEach { assertTrue(it.w, Regex("^[a-z]{3,10}$").matches(it.w)) }
        assertTrue(WordChallenges.spelledRight("  Apple ", "apple"))
        assertFalse(WordChallenges.spelledRight("aple", "apple"))
        assertFalse(WordChallenges.spelledRight("", "apple"))
    }
}
