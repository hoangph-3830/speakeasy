package com.speakeasy.app

import com.speakeasy.app.data.Progress
import com.speakeasy.app.data.Pronunciation
import com.speakeasy.app.data.PronunciationContent
import com.speakeasy.app.data.StoryContent
import com.speakeasy.app.data.Streaks
import com.speakeasy.app.data.WordVerdict
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ListenSpeakTest {

    @Test
    fun recognizerGuessesDecideTheWordVerdict() {
        assertEquals(WordVerdict.Clear, Pronunciation.judgeWord("sheep", "ship", listOf("ship", "sheep")))
        assertEquals(WordVerdict.Confused("ship"), Pronunciation.judgeWord("sheep", "ship", listOf("ship", "chip")))
        assertTrue(Pronunciation.judgeWord("think", "sink", listOf("pink")) is WordVerdict.Unclear)
        assertEquals(100, Pronunciation.bestSentenceScore("Check it out!", listOf("check it", "check it out")).score)
    }

    @Test
    fun freezesCoverMissedDaysOtherwiseStreakBreaks() {
        val p = Progress(streak = 5, lastActiveDay = 10, freezes = 1, todayXp = 40, countersDay = 10)
        val nextDay = Streaks.rollOver(p, 11)
        assertEquals(5, nextDay.streak)
        assertEquals(0, nextDay.todayXp)

        val frozen = Streaks.rollOver(p, 12)
        assertEquals(5, frozen.streak)
        assertEquals(0, frozen.freezes)
        assertEquals(11, frozen.lastActiveDay)
        assertEquals(12, frozen.freezeUsedDay)

        assertEquals(0, Streaks.rollOver(p, 13).streak)
    }

    @Test
    fun questCountersResetEachDay() {
        val p = Progress(counters = mapOf("speak" to 7), claimed = setOf("speak"), countersDay = 3, lastActiveDay = 3)
        assertEquals(7, Streaks.rollOver(p, 3).count("speak"))
        val tomorrow = Streaks.rollOver(p, 4)
        assertEquals(0, tomorrow.count("speak"))
        assertTrue(tomorrow.claimed.isEmpty())
    }

    @Test
    fun labAndStoryContentIsWellFormed() {
        val json = Json { ignoreUnknownKeys = true }
        val lab = json.decodeFromString(PronunciationContent.serializer(), File("src/main/assets/pronunciation.json").readText())
        assertTrue(lab.lessons.size >= 8)
        lab.lessons.forEach { l ->
            assertTrue(l.id, l.pairs.all { it.size == 2 && it[0] != it[1] })
            assertTrue(l.id, l.sentences.isNotEmpty())
        }
        val stories = json.decodeFromString(StoryContent.serializer(), File("src/main/assets/stories.json").readText())
        stories.stories.forEach { s ->
            val names = s.characters.map { it.name }.toSet()
            s.items.forEach { item ->
                if (item.isQuestion) assertTrue(s.id, item.answer in item.options.indices)
                else assertTrue("${s.id}: ${item.speaker}", item.speaker in names)
            }
            assertTrue(s.id, s.questionCount >= 3)
        }
    }
}
