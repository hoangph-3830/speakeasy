package com.speakeasy.app.data

import kotlin.random.Random

/** The four vocabulary challenges on the "Chơi" tab; scores are kept per [id] in [Progress.gameBest]. */
enum class Challenge(
    val id: String,
    val title: String,
    val emoji: String,
    val description: String,
    /** Memory counts moves, so fewer is better; the others count points. */
    val lowerIsBetter: Boolean = false,
) {
    SPEED("speed", "Đua 60 giây", "⚡", "Chọn nghĩa đúng nhanh nhất có thể, chuỗi 5 câu đúng được x2 điểm"),
    MEMORY("memory", "Lật thẻ trí nhớ", "🃏", "Tìm cặp từ tiếng Anh và nghĩa tiếng Việt, càng ít lượt càng giỏi", lowerIsBetter = true),
    HANGMAN("hangman", "Đoán chữ", "🔤", "Đoán từng chữ cái theo nghĩa và phiên âm, có 6 mạng"),
    TRUEFALSE("truefalse", "Đúng hay Sai", "✅", "Nghĩa này đúng không? Trả lời thật nhanh trong 45 giây");

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** An English word with four Vietnamese meanings; [answer] is one of [options], exactly once. */
data class ChoiceQuestion(val word: VocabWord, val options: List<String>, val answer: String)

/** An English word shown with [shown]; [isTrue] when that is its real meaning. */
data class TrueFalseQuestion(val word: VocabWord, val shown: String, val isTrue: Boolean)

/** One memory card: the English word or its meaning; two cards match when they share [pairId]. */
data class MemoryCard(val id: Int, val pairId: Int, val text: String, val english: Boolean)

/** Score, current run of correct answers and time left in the 60-second race. */
data class SpeedState(val score: Int = 0, val streak: Int = 0, val timeLeftMs: Long = Challenges.SPEED_SECONDS * 1000L) {
    /** The streak has reached the bonus, so answers are worth double ("🔥 x2"). */
    val doubled: Boolean get() = streak >= Challenges.STREAK_BONUS_AT
    val isOver: Boolean get() = timeLeftMs <= 0
}

/** Pure rules for the challenges: round generation, scoring and records. The screens only add timing and taps. */
object Challenges {
    const val SPEED_SECONDS = 60
    const val TRUEFALSE_SECONDS = 45
    const val STREAK_BONUS_AT = 5
    const val WRONG_PENALTY_MS = 3_000L
    const val MEMORY_PAIRS = 6
    const val HANGMAN_ROUNDS = 5
    const val HANGMAN_LIVES = 6

    private val hangmanWord = Regex("^[a-z]{4,8}$")

    /** Up to three wrong meanings for [word]: from its own group first, then from every group. */
    private fun wrongMeanings(word: VocabWord, group: List<VocabWord>, all: List<VocabWord>, random: Random): List<String> {
        val fromGroup = group.map { it.vi }.distinct().filter { it != word.vi }.shuffled(random).take(3)
        if (fromGroup.size == 3) return fromGroup
        val more = all.map { it.vi }.distinct().filter { it != word.vi && it !in fromGroup }.shuffled(random)
        return fromGroup + more.take(3 - fromGroup.size)
    }

    /** A word picked evenly over the whole list, with the rest of its group. */
    private fun pickWord(groups: List<VocabGroup>, random: Random): Pair<VocabWord, List<VocabWord>> {
        val (word, group) = groups.flatMap { g -> g.words.map { it to g } }.random(random)
        return word to group.words
    }

    /** A speed-race question: one word and four meanings, the wrong ones from the same group when possible. */
    fun choice(groups: List<VocabGroup>, random: Random = Random.Default): ChoiceQuestion {
        val (word, group) = pickWord(groups, random)
        val options = (wrongMeanings(word, group, groups.flatMap { it.words }, random) + word.vi).shuffled(random)
        return ChoiceQuestion(word, options, word.vi)
    }

    /** True or false, half the time each; a false one shows another word's meaning from the same group. */
    fun trueFalse(groups: List<VocabGroup>, random: Random = Random.Default): TrueFalseQuestion {
        val (word, group) = pickWord(groups, random)
        if (random.nextBoolean()) return TrueFalseQuestion(word, word.vi, isTrue = true)
        val wrong = wrongMeanings(word, group, groups.flatMap { it.words }, random).firstOrNull()
            ?: return TrueFalseQuestion(word, word.vi, isTrue = true)
        return TrueFalseQuestion(word, wrong, isTrue = false)
    }

    /** Points for a correct answer given the streak including it: double from the fifth in a row. */
    fun speedPoints(streak: Int) = if (streak >= STREAK_BONUS_AT) 2 else 1

    /** Applies one speed-race answer; a miss resets the streak and costs [WRONG_PENALTY_MS]. */
    fun speedAnswer(state: SpeedState, correct: Boolean): SpeedState =
        if (correct) (state.streak + 1).let { streak -> state.copy(score = state.score + speedPoints(streak), streak = streak) }
        else state.copy(streak = 0, timeLeftMs = (state.timeLeftMs - WRONG_PENALTY_MS).coerceAtLeast(0))

    /** Six words with distinct spellings and meanings, dealt as twelve shuffled cards (English + Vietnamese). */
    fun memoryDeck(words: List<VocabWord>, random: Random = Random.Default, pairs: Int = MEMORY_PAIRS): List<MemoryCard> {
        val picked = words.shuffled(random).distinctBy { it.w.lowercase() }.distinctBy { it.vi }.take(pairs)
        return picked.flatMapIndexed { i, w -> listOf(MemoryCard(0, i, w.w, english = true), MemoryCard(0, i, w.vi, english = false)) }
            .shuffled(random)
            .mapIndexed { i, card -> card.copy(id = i) }
    }

    /** Two different cards of the same word. */
    fun isPair(a: MemoryCard, b: MemoryCard) = a.id != b.id && a.pairId == b.pairId

    /** Words a learner can spell on the A–Z keyboard: 4 to 8 lowercase letters, no spaces or hyphens. */
    fun hangmanPool(words: List<VocabWord>) = words.filter { hangmanWord.matches(it.w) }.distinctBy { it.w }

    fun hangmanWords(words: List<VocabWord>, random: Random = Random.Default, count: Int = HANGMAN_ROUNDS) =
        hangmanPool(words).shuffled(random).take(count)

    /** The word with unguessed letters as "_", e.g. "c _ t". */
    fun masked(word: String, guessed: Set<Char>) = word.map { if (it in guessed) it else '_' }.joinToString(" ")

    fun livesLeft(word: String, guessed: Set<Char>) = HANGMAN_LIVES - guessed.count { it !in word }

    fun isSolved(word: String, guessed: Set<Char>) = word.all { it in guessed }

    fun isLost(word: String, guessed: Set<Char>) = livesLeft(word, guessed) <= 0

    /** Whether [score] beats [best] (none yet counts as beaten); memory is won in fewer moves. */
    fun isRecord(best: Int?, score: Int, lowerIsBetter: Boolean): Boolean =
        best == null || if (lowerIsBetter) score < best else score > best

    /** XP for a finished game. */
    fun xp(challenge: Challenge, score: Int) = when (challenge) {
        Challenge.SPEED, Challenge.TRUEFALSE -> score / 2
        Challenge.MEMORY -> 5
        Challenge.HANGMAN -> score * 2
    }
}
