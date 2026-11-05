package com.speakeasy.app

import com.speakeasy.app.data.Levels
import com.speakeasy.app.data.VideoContent
import com.speakeasy.app.data.Videos
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideosTest {

    private val content: VideoContent = Json { ignoreUnknownKeys = true }
        .decodeFromString(VideoContent.serializer(), File("src/main/assets/videos.json").readText())

    @Test
    fun channelsParseWithUniqueHandles() {
        assertTrue(content.channels.isNotEmpty())
        assertEquals(content.channels.size, content.channels.map { it.handle }.toSet().size)
        content.channels.forEach { assertTrue("${it.handle} name", it.name.isNotBlank() && it.about.isNotBlank()) }
    }

    @Test
    fun everyChannelHasAtLeastTenVideos() {
        content.channels.forEach { assertTrue("${it.handle} has ${it.videos.size}", it.videos.size >= 10) }
    }

    /** Ids go straight into the embed and thumbnail URLs, so each must be a well-formed YouTube id. */
    @Test
    fun videoIdsAreYouTubeIds() {
        val ids = content.channels.flatMap { it.videos }.map { it.id }
        ids.forEach { assertTrue("id $it", Videos.idPattern.matches(it)) }
        assertEquals(ids.size, ids.toSet().size)
        content.channels.flatMap { it.videos }.forEach { assertTrue("${it.id} title", it.title.isNotBlank()) }
    }

    @Test
    fun levelsAreValid() {
        content.channels.forEach { assertTrue("${it.handle} level ${it.level}", it.level in Levels.all) }
    }

    @Test
    fun urlsUseYouTubeHosts() {
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/mqdefault.jpg", Videos.thumbnail("abcdefghijk"))
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", Videos.watchUrl("abcdefghijk"))
        assertTrue(Videos.embedUrl("abcdefghijk").startsWith("https://www.youtube-nocookie.com/embed/abcdefghijk?playsinline=1"))
    }
}
