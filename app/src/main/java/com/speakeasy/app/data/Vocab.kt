package com.speakeasy.app.data

import kotlinx.serialization.Serializable
import java.text.Normalizer
import kotlin.random.Random

@Serializable
data class VocabContent(val groups: List<VocabGroup>)

/** One themed list of the "3000 từ vựng" core vocabulary. */
@Serializable
data class VocabGroup(
    val id: String,
    val title: String,
    val emoji: String,
    val words: List<VocabWord>,
)

@Serializable
data class VocabWord(
    val w: String,
    val ipa: String = "",
    val pos: String = "",
    val vi: String,
    val ex: String = "",
    val exVi: String = "",
)

/** A multiple-choice question: the English word, four Vietnamese meanings, the right one at [answer]. */
data class VocabQuestion(val word: VocabWord, val options: List<String>, val answer: Int)

object Vocab {

    /** Lowercase with Vietnamese tone marks removed, so "con gai" finds "con gái". */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
            .trim()

    /** Words whose English or Vietnamese contains [query]; words that start with it come first. */
    fun search(words: List<VocabWord>, query: String, limit: Int = 60): List<VocabWord> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        val hits = words.filter { fold(it.w).contains(q) || fold(it.vi).contains(q) }.distinctBy { it.w }
        return hits.sortedBy { if (fold(it.w).startsWith(q) || fold(it.vi).startsWith(q)) 0 else 1 }.take(limit)
    }

    /** Up to [count] questions over [words]; wrong options come from the group, then from [pool] if it is too small. */
    fun quiz(words: List<VocabWord>, pool: List<VocabWord>, count: Int = 10, random: Random = Random.Default): List<VocabQuestion> =
        words.shuffled(random).take(count).map { word ->
            val fromGroup = words.map { it.vi }.distinct().filter { it != word.vi }.shuffled(random)
            val wrong = if (fromGroup.size >= 3) fromGroup.take(3)
            else fromGroup + pool.map { it.vi }.distinct().filter { it != word.vi && it !in fromGroup }.shuffled(random).take(3 - fromGroup.size)
            val options = (wrong + word.vi).shuffled(random)
            VocabQuestion(word, options, options.indexOf(word.vi))
        }

    /** Vietnamese name for a part-of-speech code such as "n" or "adj". */
    fun posLabel(pos: String): String = when (pos.trim().lowercase()) {
        "n" -> "danh từ"
        "v" -> "động từ"
        "adj" -> "tính từ"
        "adv" -> "trạng từ"
        "prep" -> "giới từ"
        "pron" -> "đại từ"
        "conj" -> "liên từ"
        "det" -> "hạn định từ"
        "num" -> "số từ"
        "int", "excl" -> "thán từ"
        "phr", "phrase" -> "cụm từ"
        else -> pos
    }

    /** The review card for a word; news-reader words share the same "v:" ids. */
    fun card(word: VocabWord) =
        ReviewCard("v:${word.w}", word.w, word.vi, listOf(word.ipa, word.ex).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null })
}
