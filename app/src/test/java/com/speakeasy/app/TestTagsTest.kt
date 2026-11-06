package com.speakeasy.app

import com.speakeasy.app.ui.TestTags
import org.junit.Assert.assertEquals
import org.junit.Test

class TestTagsTest {
    @Test
    fun screenTagDropsRouteArguments() {
        assertEquals("screen_home", TestTags.screen("home"))
        assertEquals("screen_topic", TestTags.screen("topic/{id}"))
        assertEquals("screen_topic_phrases", TestTags.screen("topic/{id}/phrases"))
        assertEquals("screen_game", TestTags.screen("game/{id}/{mode}"))
        assertEquals("screen_ai", TestTags.screen("ai/{scenario}"))
    }

    @Test
    fun parameterTagsKeepTheirValue() {
        assertEquals("tab_news", TestTags.tab("news"))
        assertEquals("topic_greetings", TestTags.topic("greetings"))
        assertEquals("tab_story_pictures", TestTags.storyKind(true))
        assertEquals("tab_story_audio", TestTags.storyKind(false))
    }
}
