package com.speakeasy.app

import com.speakeasy.app.data.Activity
import com.speakeasy.app.data.Content
import com.speakeasy.app.data.LearningPath
import com.speakeasy.app.data.Levels
import com.speakeasy.app.data.Stage
import com.speakeasy.app.data.StoryContent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LearningPathTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val content: Content =
        json.decodeFromString(Content.serializer(), File("src/main/assets/content.json").readText())
    private val all = Activity.entries.size

    @Test
    fun everyTopicIsOnThePathExactlyOnce() {
        val onPath = content.path.flatMap { it.topics }
        assertTrue(content.path.isNotEmpty())
        assertEquals(onPath.size, onPath.toSet().size)
        assertEquals(content.topics.map { it.id }.toSet(), onPath.toSet())
    }

    @Test
    fun stagesGoEasyToHard() {
        val ranks = content.path.map { Levels.rank(it.level) }
        assertTrue(content.path.all { it.level in Levels.all && it.title.isNotBlank() && it.topics.isNotEmpty() })
        assertEquals(ranks.sorted(), ranks)
    }

    @Test
    fun nextIsTheFirstUnfinishedTopicAlongThePath() {
        val path = listOf(Stage("s1", "A1", "One", topics = listOf("a", "b")), Stage("s2", "A2", "Two", topics = listOf("c")))
        val done = mutableMapOf("a" to all, "b" to 2)

        val first = LearningPath.next(path, { done[it] ?: 0 })!!
        assertEquals("b", first.topicId)
        assertEquals(0, first.stageIndex)

        done["b"] = all
        val second = LearningPath.next(path, { done[it] ?: 0 })!!
        assertEquals("c", second.topicId)
        assertEquals(1, second.stageIndex)
        assertTrue(LearningPath.isComplete(path[0], { done[it] ?: 0 }))
        assertEquals(0.5f, LearningPath.progress(Stage("x", "A1", "X", topics = listOf("a", "c")), { done[it] ?: 0 }))

        done["c"] = all
        assertNull(LearningPath.next(path, { done[it] ?: 0 }))
    }

    @Test
    fun nextStartsAtTheFirstTopicOfTheRealPath() {
        val step = LearningPath.next(content.path) { 0 }!!
        assertEquals(content.path.first().topics.first(), step.topicId)
        assertNull(LearningPath.next(content.path) { all })
    }

    @Test
    fun storyLevelDefaultsToA2() {
        val parsed = json.decodeFromString(
            StoryContent.serializer(),
            """{"stories":[
              {"id":"x","title":"X","titleVi":"X","emoji":"📖","category":"life","characters":[],"items":[]},
              {"id":"y","title":"Y","titleVi":"Y","emoji":"📖","category":"life","level":"B1","characters":[],"items":[]}
            ]}""",
        )
        assertEquals(listOf("A2", "B1"), parsed.stories.map { it.level })
        val real = json.decodeFromString(StoryContent.serializer(), File("src/main/assets/stories.json").readText())
        assertTrue(real.stories.all { it.level in Levels.all })
    }
}
