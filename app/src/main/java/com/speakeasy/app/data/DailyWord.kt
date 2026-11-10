package com.speakeasy.app.data

/** Colour of a guessed letter: right place, in the word elsewhere, or not (or no more copies left). */
enum class LetterState { HIT, NEAR, MISS }

/** Today's board, kept in progress so leaving and coming back keeps the guesses. */
data class DailyBoard(val day: Long, val guesses: List<String>)

/** Puzzle record; [dist] counts wins by number of tries (index 0 = solved in one). */
data class DailyStats(
    val played: Int = 0,
    val won: Int = 0,
    val streak: Int = 0,
    val best: Int = 0,
    val lastWonDay: Long = -1,
    val dist: List<Int> = List(DailyWord.MAX_GUESSES) { 0 },
) {
    fun encode() = (listOf(played, won, streak, best, lastWonDay) + dist).joinToString(",")

    companion object {
        /** Reads [encode]'s output; anything malformed starts fresh. */
        fun decode(text: String?): DailyStats {
            val n = text?.split(',')?.map { it.toLongOrNull() ?: return DailyStats() } ?: return DailyStats()
            if (n.size != 5 + DailyWord.MAX_GUESSES) return DailyStats()
            return DailyStats(n[0].toInt(), n[1].toInt(), n[2].toInt(), n[3].toInt(), n[4], n.drop(5).map { it.toInt() })
        }
    }
}

/**
 * Rules of "Từ của ngày": one five-letter word a day, the same for every learner (and the same as the
 * web app for the same epoch day), six tries with green/yellow/grey letter hints.
 */
object DailyWord {
    const val WORD_LENGTH = 5
    const val MAX_GUESSES = 6
    private val plain = Regex("^[a-z]{$WORD_LENGTH}$")

    /** Five plain letters, once each word, in a stable order so every device picks the same word. */
    fun puzzleWords(groups: List<VocabGroup>): List<VocabWord> =
        groups.flatMap { it.words }.filter { plain.matches(it.w) }
            // Like the web's Map: the first spelling sets the order, the last one wins the details.
            .associateBy { it.w }.values.sortedBy { it.w }

    /** Spreads consecutive days across the list (a fixed step coprime with its length). */
    fun wordOfDay(words: List<VocabWord>, day: Long): VocabWord {
        val size = words.size.toLong()
        var step = 7919L
        while (gcd(step, size) != 1L) step++
        return words[Math.floorMod(day * step, size).toInt()]
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    /** Exact hits are counted first so a repeated letter is never over-reported. */
    fun scoreGuess(guess: String, answer: String): List<LetterState> {
        val result = MutableList(guess.length) { LetterState.MISS }
        val left = mutableMapOf<Char, Int>()
        answer.forEachIndexed { i, ch -> if (guess.getOrNull(i) != ch) left[ch] = (left[ch] ?: 0) + 1 }
        guess.forEachIndexed { i, ch ->
            if (answer.getOrNull(i) == ch) result[i] = LetterState.HIT
            else if ((left[ch] ?: 0) > 0) {
                result[i] = LetterState.NEAR
                left[ch] = left.getValue(ch) - 1
            }
        }
        return result
    }

    fun validGuess(text: String) = plain.matches(text)
    fun isWon(guesses: List<String>, answer: String) = answer in guesses
    fun isOver(guesses: List<String>, answer: String) = isWon(guesses, answer) || guesses.size >= MAX_GUESSES

    /** Best colour seen so far for each letter, for the on-screen keyboard. */
    fun keyColours(guesses: List<String>, answer: String): Map<Char, LetterState> {
        val rank = mapOf(LetterState.MISS to 0, LetterState.NEAR to 1, LetterState.HIT to 2)
        val out = mutableMapOf<Char, LetterState>()
        for (g in guesses) scoreGuess(g, answer).forEachIndexed { i, c ->
            val prev = out[g[i]]
            if (prev == null || rank.getValue(c) > rank.getValue(prev)) out[g[i]] = c
        }
        return out
    }

    /** Stats after finishing the puzzle of [day]; the streak counts days solved in a row. */
    fun recordResult(stats: DailyStats, day: Long, won: Boolean, tries: Int): DailyStats {
        val dist = stats.dist.toMutableList()
        if (won) dist[tries - 1]++
        val streak = if (!won) 0 else if (stats.lastWonDay == day - 1) stats.streak + 1 else 1
        return DailyStats(
            played = stats.played + 1,
            won = stats.won + if (won) 1 else 0,
            streak = streak,
            best = maxOf(stats.best, streak),
            lastWonDay = if (won) day else stats.lastWonDay,
            dist = dist,
        )
    }

    /** XP for solving the puzzle: fewer tries, more XP. */
    fun xpFor(tries: Int) = 5 + (MAX_GUESSES - tries) * 2

    /** The coloured squares people paste into a chat, without giving the word away. */
    fun shareText(guesses: List<String>, answer: String, day: Long): String {
        val squares = mapOf(LetterState.HIT to "🟩", LetterState.NEAR to "🟨", LetterState.MISS to "⬜")
        val tries = if (isWon(guesses, answer)) guesses.size.toString() else "X"
        return (listOf("SpeakEasy · Từ của ngày #$day $tries/$MAX_GUESSES") +
            guesses.map { g -> scoreGuess(g, answer).joinToString("") { squares.getValue(it) } }).joinToString("\n")
    }
}
