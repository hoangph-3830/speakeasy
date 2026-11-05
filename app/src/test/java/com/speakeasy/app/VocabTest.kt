package com.speakeasy.app

import com.speakeasy.app.data.Vocab
import com.speakeasy.app.data.VocabContent
import com.speakeasy.app.data.VocabWord
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class VocabTest {

    private val vocab: VocabContent =
        json.decodeFromString(VocabContent.serializer(), File("src/main/assets/vocab.json").readText())

    private val words = listOf(
        VocabWord("daughter", vi = "con gái"), VocabWord("cousin", vi = "anh chị em họ"),
        VocabWord("marry", vi = "kết hôn"), VocabWord("crisis", vi = "khủng hoảng"),
        VocabWord("report", vi = "bản tin"), VocabWord("announce", vi = "thông báo"),
    )

    @Test
    fun vocabAssetIsComplete() {
        assertTrue(vocab.groups.isNotEmpty())
        assertEquals(vocab.groups.size, vocab.groups.map { it.id }.toSet().size)
        vocab.groups.forEach { g ->
            assertTrue(g.id, g.words.isNotEmpty())
            g.words.forEach { assertTrue("${g.id}/${it.w}", it.w.isNotBlank() && it.vi.isNotBlank()) }
        }
    }

    @Test
    fun quizHasFourDistinctOptionsEvenForSmallGroups() {
        val quiz = Vocab.quiz(words.take(2), words, random = Random(1))
        assertEquals(2, quiz.size)
        quiz.forEach { q ->
            assertEquals(4, q.options.toSet().size)
            assertEquals(q.word.vi, q.options[q.answer])
        }
        assertEquals(6, Vocab.quiz(words, words, count = 10, random = Random(2)).size)
    }

    @Test
    fun searchMatchesEnglishAndUnaccentedVietnamese() {
        assertEquals(listOf("cousin"), Vocab.search(words, "cous").map { it.w })
        assertEquals(listOf("daughter"), Vocab.search(words, "con gai").map { it.w })
        assertEquals(listOf("crisis"), Vocab.search(words, "khủng").map { it.w })
        assertTrue(Vocab.search(words, " ").isEmpty())
    }

    @Test
    fun reviewCardsUseTheVocabId() {
        val card = Vocab.card(VocabWord("daughter", "/ˈdɔːtər/", "n", "con gái", "Their daughter is five."))
        assertEquals("v:daughter", card.id)
        assertEquals("/ˈdɔːtər/ · Their daughter is five.", card.extra)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
