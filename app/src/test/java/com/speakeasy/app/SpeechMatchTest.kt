package com.speakeasy.app

import com.speakeasy.app.speech.SpeechMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechMatchTest {

    @Test
    fun perfectMatchIgnoresCaseAndPunctuation() {
        val r = SpeechMatch.compare("Can I get a latte, please?", "can i get a latte please")
        assertEquals(100, r.score)
        assertEquals(3, r.stars)
    }

    @Test
    fun contractionsMatchExpandedSpeech() {
        val r = SpeechMatch.compare("I'm still getting used to everything.", "I am still getting used to everything")
        assertEquals(100, r.score)
        assertTrue(r.words.first().second)
    }

    @Test
    fun missingWordsAreFlagged() {
        val r = SpeechMatch.compare("Is the flight on time?", "is flight time")
        assertEquals(listOf(true, false, true, false, true), r.words.map { it.second })
        assertEquals(60, r.score)
    }

    @Test
    fun emptySpeechScoresZero() {
        val r = SpeechMatch.compare("Hello there", "")
        assertEquals(0, r.score)
        assertFalse(r.words.any { it.second })
    }
}
