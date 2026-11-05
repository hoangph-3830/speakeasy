package com.speakeasy.app

import com.speakeasy.app.speech.Sentences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentencesTest {

    @Test
    fun splitsAtSentenceEnds() {
        assertEquals(
            listOf("Prices rose again.", "Why?", "Nobody knows!", "He said “wait.”", "Then he left…", "Fine"),
            Sentences.split("Prices rose again. Why? Nobody knows! He said “wait.” Then he left… Fine"),
        )
    }

    @Test
    fun abbreviationsDoNotEndSentences() {
        assertEquals(
            listOf("The U.S. Federal Reserve held rates in Q3, said Mr. Smith of Acme Inc. on Monday.", "Dr. Jones disagreed."),
            Sentences.split("The U.S. Federal Reserve held rates in Q3, said Mr. Smith of Acme Inc. on Monday. Dr. Jones disagreed."),
        )
        assertEquals(listOf("Talks resumed in the U.K. after a pause, e.g. in trade."), Sentences.split("Talks resumed in the U.K. after a pause, e.g. in trade."))
    }

    @Test
    fun longSentencesAreSplitUnderTheLimit() {
        val clause = "the central bank kept interest rates on hold for another month"
        val long = (1..8).joinToString(", ") { "$clause $it" } + "."
        val chunks = Sentences.split(long)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= Sentences.MAX_CHUNK })
        assertEquals(long, chunks.joinToString(" "))

        // No commas at all: falls back to splitting between words.
        val words = (1..80).joinToString(" ") { "word$it" }
        val pieces = Sentences.split(words)
        assertTrue(pieces.all { it.length <= Sentences.MAX_CHUNK })
        assertEquals(words, pieces.joinToString(" "))
    }

    @Test
    fun emptyTextHasNoChunks() {
        assertTrue(Sentences.split("   ").isEmpty())
    }
}
