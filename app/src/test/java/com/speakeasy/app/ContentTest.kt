package com.speakeasy.app

import com.speakeasy.app.data.Content
import com.speakeasy.app.data.GameMode
import com.speakeasy.app.data.Games
import com.speakeasy.app.data.Round
import com.speakeasy.app.speech.SpeechMatch
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class ContentTest {

    private val content: Content =
        Json.decodeFromString(Content.serializer(), File("src/main/assets/content.json").readText())

    @Test
    fun everyTopicIsComplete() {
        val categories = content.categories.map { it.id }.toSet()
        assertEquals(content.topics.size, content.topics.map { it.id }.toSet().size)
        content.topics.forEach { t ->
            assertTrue("${t.id} category", t.category in categories)
            assertTrue("${t.id} phrases", t.phrases.size >= 5)
            assertTrue("${t.id} words", t.words.size >= 5)
            assertTrue("${t.id} learner lines", t.dialogue.lines.any { it.isUser })
            assertTrue("${t.id} other lines", t.dialogue.lines.any { !it.isUser })
            assertTrue("${t.id} native", t.native.size >= 3 && t.native.all { it.natural != it.plain })
        }
        categories.forEach { c -> assertTrue(c, content.topics.count { it.category == c } >= 3) }
    }

    @Test
    fun gamesBuildForEveryTopicAndMode() {
        val random = Random(42)
        content.topics.forEach { t ->
            GameMode.entries.forEach { mode ->
                val rounds = Games.build(listOf(t), mode, random)
                assertTrue(rounds.isNotEmpty())
                rounds.forEach { r ->
                    when (r) {
                        is Round.Match -> assertEquals(5, r.pairs.size)
                        is Round.Scramble -> assertTrue(Games.isSolved(r, r.sentence.split(" ")))
                        is Round.Listen -> assertTrue(r.answer in r.options && r.options.size == 4)
                        is Round.Meaning -> assertTrue(r.answer in r.options && r.options.size == 4)
                        is Round.Dictation -> assertEquals(100, SpeechMatch.compare(r.sentence, r.sentence.lowercase()).score)
                    }
                }
            }
        }
    }
}
