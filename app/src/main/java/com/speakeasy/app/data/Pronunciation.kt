package com.speakeasy.app.data

import com.speakeasy.app.speech.SpeechMatch
import kotlinx.serialization.Serializable

@Serializable
data class PronunciationContent(val lessons: List<SoundLesson>)

/**
 * One ELSA-style drill. [pairs] are minimal pairs: the first word carries the lesson's sound,
 * the second is what Vietnamese speakers tend to say instead. Linking and stress lessons have none.
 */
@Serializable
data class SoundLesson(
    val id: String,
    val title: String,
    val ipa: String,
    val emoji: String,
    val tip: String,
    val pairs: List<List<String>> = emptyList(),
    val sentences: List<SoundSentence>,
)

/** [sounds] is how the sentence really sounds in fast speech (linking, stress marks). */
@Serializable
data class SoundSentence(val en: String, val vi: String, val sounds: String? = null)

sealed interface WordVerdict {
    /** The recognizer heard the target word. */
    data object Clear : WordVerdict

    /** It heard the look-alike word instead, i.e. the sound came out wrong. */
    data class Confused(val heardAs: String) : WordVerdict

    /** Something else entirely. */
    data class Unclear(val heard: String) : WordVerdict
}

object Pronunciation {

    /**
     * The phone has no phoneme scorer, so we use the recognizer as a native listener:
     * if any of its guesses is the target word, a native ear would understand it too.
     */
    fun judgeWord(target: String, lookAlike: String?, alternatives: List<String>): WordVerdict {
        val t = SpeechMatch.tokens(target)
        val heard = alternatives.map { SpeechMatch.tokens(it) }
        return when {
            heard.any { it.containsAll(t) } -> WordVerdict.Clear
            lookAlike != null && heard.firstOrNull()?.containsAll(SpeechMatch.tokens(lookAlike)) == true ->
                WordVerdict.Confused(lookAlike)
            else -> WordVerdict.Unclear(alternatives.firstOrNull().orEmpty())
        }
    }

    /** Best match across the recognizer's guesses, so one odd top guess doesn't sink a good attempt. */
    fun bestSentenceScore(target: String, alternatives: List<String>) =
        alternatives.map { SpeechMatch.compare(target, it) }.maxByOrNull { it.score }
            ?: SpeechMatch.compare(target, "")

    fun percent(correct: Int, total: Int) = if (total == 0) 0 else correct * 100 / total
}
