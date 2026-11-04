package com.speakeasy.app

import com.speakeasy.app.data.AiException
import com.speakeasy.app.data.AiLevel
import com.speakeasy.app.data.AiTutor
import com.speakeasy.app.data.ChatTurn
import com.speakeasy.app.data.Content
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiTutorTest {

    private val sample = """
        {"reply":"Nice! What brings you here?","reply_vi":"Hay quá! Điều gì đưa bạn tới đây?",
         "correction":{"has_issue":true,"corrected":"I just started here this week.","explanation_vi":"Dùng 'just started'."},
         "suggestions":["I'm here for work.","My friend invited me."]}
    """.trimIndent()

    @Test
    fun parsesStructuredReplyAndRoundTrips() {
        val r = AiTutor.parse(sample)
        assertTrue(r.correction.hasIssue)
        assertEquals(2, r.suggestions.size)
        assertEquals(r, AiTutor.parse(AiTutor.encode(r)))
    }

    @Test(expected = AiException::class)
    fun malformedReplyIsAFriendlyError() {
        AiTutor.parse("Sorry, here is your answer:")
    }

    @Test
    fun everyTopicBecomesARolePlay() {
        val content = Json.decodeFromString(Content.serializer(), File("src/main/assets/content.json").readText())
        val scenarios = AiTutor.scenarios(content)
        assertEquals(content.topics.size + 2, scenarios.size)
        assertEquals(scenarios.size, scenarios.map { it.id }.toSet().size)
        val prompt = AiTutor.systemPrompt(scenarios.last(), AiLevel.NATIVE)
        assertTrue(scenarios.last().aiRole in prompt && AiLevel.NATIVE.prompt in prompt)
    }

    @Test
    fun geminiRequestAlternatesUserAndModelTurns() {
        val scenario = AiTutor.scenarios(
            Json.decodeFromString(Content.serializer(), File("src/main/assets/content.json").readText())
        ).first()
        val history = listOf(
            ChatTurn(fromUser = true, text = AiTutor.START_CUE, hidden = true),
            ChatTurn(fromUser = false, text = "Hi!", raw = sample),
            ChatTurn(fromUser = true, text = "I am fine"),
        )
        val body = AiTutor.requestBody(scenario, AiLevel.MEDIUM, history)
        val roles = body["contents"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("user", "model", "user"), roles)
        assertEquals("application/json", body["generationConfig"]!!.jsonObject["responseMimeType"]!!.jsonPrimitive.content)
    }

    @Test
    fun readsGeminiResponseAndRejectsBlockedOnes() {
        val response = """{"candidates":[{"content":{"role":"model","parts":[{"text":${Json.encodeToString(String.serializer(), sample)}}]}}]}"""
        assertEquals("Nice! What brings you here?", AiTutor.parse(AiTutor.replyText(response)).reply)
        val blocked = runCatching { AiTutor.replyText("""{"promptFeedback":{"blockReason":"SAFETY"}}""") }
        assertTrue(blocked.exceptionOrNull() is AiException)
    }
}
