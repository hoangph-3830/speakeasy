package com.speakeasy.app

import com.speakeasy.app.data.DictEntry
import com.speakeasy.app.data.ReadingStats
import com.speakeasy.app.data.WordStatus
import com.speakeasy.app.data.WordStatuses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordStatusTest {

    private val learned = setOf("cat")
    private val deck = setOf("v:dog")

    @Test
    fun statusFollowsLearnedSetAndDeck() {
        assertEquals(WordStatus.KNOWN, WordStatuses.of("cat", learned, deck))
        assertEquals(WordStatus.LEARNING, WordStatuses.of("dog", learned, deck))
        assertEquals(WordStatus.NEW, WordStatuses.of("bird", learned, deck))
        assertEquals(WordStatus.NOT_IN_DICTIONARY, WordStatuses.of(null, learned, deck))
    }

    @Test
    fun grammarWordsAreNotColoured() {
        assertNull(WordStatuses.colourable(DictEntry("the", "", "det", "cái", "")))
        assertEquals("bird", WordStatuses.colourable(DictEntry("bird", "", "n", "chim", ""))?.word)
    }

    @Test
    fun statsCountDistinctHeadwords() {
        val stats = ReadingStats.of(listOf("cat", "cat", "dog", "bird", "fish")) { WordStatuses.of(it, learned, deck) }
        assertEquals(ReadingStats(known = 1, learning = 1, new = 2), stats)
        assertEquals(25, stats.knownPercent)
    }
}
