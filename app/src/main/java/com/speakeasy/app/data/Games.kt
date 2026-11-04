package com.speakeasy.app.data

import kotlin.random.Random

/** One question in a game session. */
sealed interface Round {
    /** Tap English words and their Vietnamese meaning in pairs. */
    data class Match(val pairs: List<Pair<String, String>>) : Round

    /** Rebuild a sentence from shuffled word tiles. */
    data class Scramble(val sentence: String, val hint: String, val tiles: List<String>) : Round

    /** Hear the audio, pick what was said. */
    data class Listen(val audio: String, val options: List<String>, val answer: String) : Round

    /** Pick the right meaning (English prompt → Vietnamese options, or the reverse). */
    data class Meaning(val prompt: String, val options: List<String>, val answer: String, val speakPrompt: Boolean) : Round

    /** Hear a sentence and type it out. */
    data class Dictation(val sentence: String, val hint: String) : Round
}

enum class GameMode(val id: String, val title: String, val emoji: String, val description: String) {
    MIX("mix", "Thử thách tổng hợp", "🌈", "Mỗi vòng một kiểu chơi khác nhau"),
    MATCH("match", "Ghép cặp từ", "🧩", "Nối từ tiếng Anh với nghĩa tiếng Việt"),
    SCRAMBLE("scramble", "Xếp câu", "🧱", "Sắp xếp các từ thành câu hoàn chỉnh"),
    LISTEN("listen", "Nghe & chọn", "🎧", "Nghe câu nói rồi chọn đáp án đúng"),
    DICTATION("dictation", "Nghe & chép", "✍️", "Nghe người bản xứ nói rồi gõ lại từng từ"),
    MEANING("meaning", "Đoán nghĩa", "💡", "Chọn nghĩa đúng của mẫu câu");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id } ?: MIX
    }
}

object Games {

    /** Builds a session from the given topics; one topic for a lesson, all topics for free play. */
    fun build(topics: List<Topic>, mode: GameMode, random: Random = Random.Default, size: Int = 8): List<Round> {
        val words = topics.flatMap { it.words }.distinctBy { it.word }
        val sentences = topics.flatMap { it.sentences }.distinctBy { it.first }
        val short = sentences.filter { it.first.split(" ").size in 3..9 }.ifEmpty { sentences }

        fun match() = Round.Match(words.shuffled(random).take(5).map { it.word to it.vi })

        fun scramble(): Round.Scramble {
            val (en, vi) = short.random(random)
            val tiles = en.split(" ")
            var shuffled = tiles.shuffled(random)
            // Avoid handing out an already-solved puzzle.
            repeat(5) { if (shuffled == tiles) shuffled = tiles.shuffled(random) }
            return Round.Scramble(en, vi, shuffled)
        }

        fun listen(): Round.Listen {
            val useWords = random.nextBoolean() && words.size >= 4
            val pool = if (useWords) words.map { it.word } else short.map { it.first }
            val answer = pool.random(random)
            val options = (pool.filter { it != answer }.shuffled(random).take(3) + answer).shuffled(random)
            return Round.Listen(answer, options, answer)
        }

        fun meaning(): Round.Meaning {
            val reverse = random.nextBoolean()
            val (en, vi) = short.random(random)
            val others = short.filter { it.first != en }.shuffled(random).take(3)
            return if (reverse) {
                Round.Meaning(vi, (others.map { it.first } + en).shuffled(random), en, speakPrompt = false)
            } else {
                Round.Meaning(en, (others.map { it.second } + vi).shuffled(random), vi, speakPrompt = true)
            }
        }

        fun dictation(): Round.Dictation {
            val (en, vi) = short.random(random)
            return Round.Dictation(en, vi)
        }

        val makers: List<() -> Round> = when (mode) {
            GameMode.MATCH -> listOf(::match)
            GameMode.SCRAMBLE -> listOf(::scramble)
            GameMode.LISTEN -> listOf(::listen)
            GameMode.MEANING -> listOf(::meaning)
            GameMode.DICTATION -> listOf(::dictation)
            GameMode.MIX -> listOf(::match, ::scramble, ::listen, ::dictation, ::meaning)
        }
        val count = when (mode) {
            GameMode.MATCH -> 4
            GameMode.MIX -> size + 2
            else -> size
        }
        return List(count) { i -> makers[i % makers.size]() }
    }

    /** Joins tiles the same way the sentence was split, so a correct order compares equal. */
    fun isSolved(round: Round.Scramble, answer: List<String>) = answer.joinToString(" ") == round.sentence
}
