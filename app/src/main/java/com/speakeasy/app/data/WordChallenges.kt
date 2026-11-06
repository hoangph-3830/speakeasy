package com.speakeasy.app.data

import kotlin.random.Random

/** A sentence with one word blanked out and four words to choose from; [answer] is in [options] once. */
data class ClozeQuestion(
    val word: VocabWord,
    val sentence: String,
    val translation: String,
    val options: List<String>,
    val answer: String,
)

/** Pure rules for the round-based word challenges: fill the gap, letter scramble and spelling. */
object WordChallenges {
    const val CLOZE_ROUNDS = 10
    const val ANAGRAM_ROUNDS = 8
    const val SPELLING_ROUNDS = 10
    const val BLANK = "_____"

    private val plainWord = Regex("^[a-z]+$", RegexOption.IGNORE_CASE)
    private val anagramWord = Regex("^[a-z]{4,8}$")
    private val spellingWord = Regex("^[a-z]{3,10}$")

    private fun tokens(text: String) = text.lowercase().split(Regex("[^a-z']+"))

    /** Words whose example sentence uses the word itself, so it can be blanked out. */
    private fun clozeable(w: VocabWord) = w.ex.isNotEmpty() && plainWord.matches(w.w) && w.w.lowercase() in tokens(w.ex)

    /** The example sentence with the word's first whole-word use replaced by a blank. */
    fun blankOut(sentence: String, word: String): String {
        val m = Regex("(^|[^A-Za-z'])${Regex.escape(word)}(?![A-Za-z'])", RegexOption.IGNORE_CASE).find(sentence) ?: return sentence
        return sentence.replaceRange(m.range, m.groupValues[1] + BLANK)
    }

    /** A sentence with one word missing and four words to choose from, of the same part of speech if possible. */
    fun clozeRound(groups: List<VocabGroup>, random: Random = Random.Default): ClozeQuestion {
        val all = groups.flatMap { it.words }
        val word = all.filter(::clozeable).random(random)
        val others = all.filter { it.w != word.w && plainWord.matches(it.w) }
        val same = others.filter { it.pos == word.pos }
        val pool = (if (same.map { it.w }.distinct().size >= 3) same else others).map { it.w }.distinct()
        val options = (pool.shuffled(random).take(3) + word.w).shuffled(random)
        return ClozeQuestion(word, blankOut(word.ex, word.w), word.exVi, options, word.w)
    }

    /** Words of 4-8 plain letters, so every tile is one keyboard letter. */
    fun anagramWords(groups: List<VocabGroup>, random: Random = Random.Default, count: Int = ANAGRAM_ROUNDS) =
        groups.flatMap { it.words }.filter { anagramWord.matches(it.w) && it.w.toSet().size > 1 }
            .distinctBy { it.w }.shuffled(random).take(count)

    /** The letters in a new order that never spells the word already. */
    fun scrambleLetters(word: String, random: Random = Random.Default): List<Char> {
        var letters = word.toList().shuffled(random)
        repeat(10) { if (letters.joinToString("") == word) letters = word.toList().shuffled(random) }
        if (letters.joinToString("") == word) letters = letters.drop(1) + letters.first()
        return letters
    }

    /** Plain words of 3-10 letters, which are fair to spell from sound alone. */
    fun spellingWords(groups: List<VocabGroup>, random: Random = Random.Default, count: Int = SPELLING_ROUNDS) =
        groups.flatMap { it.words }.filter { spellingWord.matches(it.w) }.distinctBy { it.w }.shuffled(random).take(count)

    /** Spelling is checked letter for letter; case and surrounding spaces do not count. */
    fun spelledRight(typed: String, word: String) = typed.trim().lowercase() == word.lowercase()
}
