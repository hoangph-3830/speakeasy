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

class WriteModeTest {

    private val content: Content =
        Json.decodeFromString(Content.serializer(), File("src/main/assets/content.json").readText())

    @Test
    fun everyTopicGivesAVietnamesePromptWithAnEnglishAnswerThatPassesItself() {
        for (t in content.topics) for (r in Games.build(listOf(t), GameMode.WRITE)) {
            assertTrue(t.id, r is Round.Write)
            r as Round.Write
            assertTrue(t.id, r.prompt.isNotBlank() && r.answer.isNotBlank())
            assertTrue(t.id, t.sentences.any { it.first == r.answer && it.second == r.prompt })
            assertEquals(100, SpeechMatch.compare(r.answer, r.answer.lowercase()).score)
        }
        assertTrue(Games.build(content.topics, GameMode.MIX, size = 12).any { it is Round.Write })
    }

    @Test
    fun smallSlipsPassButMissingWordsDoNot() {
        assertTrue(SpeechMatch.compare("I would like a coffee, please.", "I would like coffee please").score >= Games.WRITE_PASS)
        assertTrue(SpeechMatch.compare("I would like a coffee, please.", "I like tea").score < Games.WRITE_PASS)
        assertEquals("📝", GameMode.from("write").emoji)
    }
}
