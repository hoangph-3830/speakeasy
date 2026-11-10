package com.speakeasy.app

import com.speakeasy.app.data.PronunciationContent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PronunciationExtrasTest {

    private val content = Json { ignoreUnknownKeys = true }
        .decodeFromString(PronunciationContent.serializer(), File("src/main/assets/pronunciation.json").readText())

    @Test
    fun twistersHaveALevelAndUniqueIds() {
        assertEquals(18, content.lessons.size)
        assertTrue(content.twisters.isNotEmpty())
        assertEquals(content.twisters.size, content.twisters.map { it.id }.toSet().size)
        assertTrue(content.twisters.all { it.level in setOf("easy", "medium", "hard") && it.en.isNotBlank() && it.vi.isNotBlank() })
        listOf("easy", "medium", "hard").forEach { l -> assertTrue(l, content.twisters.any { it.level == l }) }
    }

    @Test
    fun ipaChartCoversEveryGroupWithAnExample() {
        assertTrue(content.ipa.all { it.type in setOf("vowel", "diphthong", "consonant") && it.example.isNotBlank() })
        listOf("vowel", "diphthong", "consonant").forEach { t -> assertTrue(t, content.ipa.any { it.type == t }) }
        assertEquals(content.ipa.size, content.ipa.map { it.symbol }.toSet().size)
    }
}
