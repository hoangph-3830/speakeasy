package com.speakeasy.app

import com.speakeasy.app.ui.TestTags
import org.junit.Assert.assertEquals
import org.junit.Test

class TestTagsTest {
    @Test
    fun screenTagDropsRouteArguments() {
        assertEquals("screen_home", TestTags.screen("home"))
        assertEquals("screen_topic_detail", TestTags.screen("topic/{id}"))
        assertEquals("screen_topic_phrases", TestTags.screen("topic/{id}/phrases"))
        assertEquals("screen_game_detail", TestTags.screen("game/{id}/{mode}"))
    }

    @Test
    fun listAndDetailScreensNeverShareATag() {
        listOf("vocab" to "vocab/{id}", "lab" to "lab/{id}", "ai" to "ai/{scenario}").forEach { (list, detail) ->
            assertEquals("screen_$list", TestTags.screen(list))
            assertEquals("screen_${list}_detail", TestTags.screen(detail))
        }
    }

    @Test
    fun parameterTagsKeepTheirValue() {
        assertEquals("tab_news", TestTags.tab("news"))
        assertEquals("topic_greetings", TestTags.topic("greetings"))
        assertEquals("tab_story_pictures", TestTags.storyKind(true))
        assertEquals("tab_story_audio", TestTags.storyKind(false))
        assertEquals("goal_50", TestTags.goal(50))
        assertEquals("accent_uk", TestTags.accent("UK"))
        assertEquals("key_z", TestTags.key('z'))
    }
}
