package com.speakeasy.app

import com.speakeasy.app.data.DictEntry
import com.speakeasy.app.data.Dictionary
import com.speakeasy.app.data.Lemmatizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryTest {

    private val known = setOf(
        "stop", "walk", "like", "study", "city", "box", "cat", "make", "run", "lie", "big", "happy", "fast",
        "late", "go", "see", "child", "person", "man", "good", "parent", "watch", "add", "write", "daughter",
    )

    private fun lemma(word: String) = Lemmatizer.lemma(word) { it in known }

    @Test
    fun normalizesPunctuationAndApostrophes() {
        assertEquals("daughter's", Lemmatizer.normalize("“Daughter’s,"))
        assertEquals("stop", Lemmatizer.normalize("(STOP!)"))
        assertEquals("well-known", Lemmatizer.normalize("well-known."))
    }

    @Test
    fun regularInflectionsFindTheBaseWord() {
        val cases = mapOf(
            "cats" to "cat", "boxes" to "box", "cities" to "city", "watches" to "watch", "parents'" to "parent",
            "daughter's" to "daughter", "walked" to "walk", "liked" to "like", "studied" to "study",
            "stopped" to "stop", "added" to "add", "walking" to "walk", "making" to "make", "running" to "run",
            "lying" to "lie", "bigger" to "big", "biggest" to "big", "happier" to "happy", "happiest" to "happy",
            "faster" to "fast", "later" to "late", "Stopped," to "stop",
        )
        cases.forEach { (word, base) -> assertEquals(word, base, lemma(word)) }
    }

    @Test
    fun irregularFormsUseTheTable() {
        val cases = mapOf(
            "went" to "go", "gone" to "go", "saw" to "see", "seen" to "see", "children" to "child",
            "people" to "person", "men" to "man", "better" to "good", "best" to "good", "wrote" to "write",
        )
        cases.forEach { (word, base) -> assertEquals(word, base, lemma(word)) }
        assertTrue(Lemmatizer.IRREGULAR.size >= 100)
    }

    @Test
    fun exactWordComesFirstAndUnknownIsNull() {
        assertEquals("stop", Lemmatizer.candidates("stop").first())
        assertNull(lemma("xylophone"))
        assertNull(lemma("ss"))
    }

    @Test
    fun lookupPrefersVocabularyEntries() {
        val dict = Dictionary {
            listOf(
                DictEntry("run", "/rʌn/", "v", "chạy", "I run every morning."),
                DictEntry("run", "/rʌn/", "", "chạy (chủ đề)", ""),
                DictEntry("child", "/tʃaɪld/", "n", "đứa trẻ", "The child is asleep."),
            )
        }
        assertEquals("chạy", dict.lookup("Running")?.vi)
        assertEquals("child", dict.lookup("children’s")?.word)
        assertNull(dict.lookup("banana"))
    }

    @Test
    fun parsesGoogleTranslateResponse() {
        val response = """[[["Xin chào. ","Hello. ",null,null,10],["Bạn khỏe không?","How are you?",null,null,10]],null,"en"]"""
        assertEquals("Xin chào. Bạn khỏe không?", Dictionary.parseGoogle(response))
    }
}
