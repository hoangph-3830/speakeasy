package com.speakeasy.app.speech

/** Result of comparing what the learner said against the target sentence. */
data class MatchResult(
    /** Target words in original form, paired with whether they were spoken. */
    val words: List<Pair<String, Boolean>>,
    val score: Int,
) {
    val stars: Int
        get() = when {
            score >= 90 -> 3
            score >= 70 -> 2
            score >= 40 -> 1
            else -> 0
        }

    val feedback: String
        get() = when (stars) {
            3 -> "Xuất sắc! Phát âm rất chuẩn 🎉"
            2 -> "Tốt lắm! Gần như hoàn hảo 👍"
            1 -> "Khá rồi, thử lại chậm hơn nhé 💪"
            else -> "Chưa nghe rõ, nghe mẫu rồi thử lại nhé 🙂"
        }
}

object SpeechMatch {

    private val contractions = mapOf(
        "i'm" to "i am", "you're" to "you are", "we're" to "we are", "they're" to "they are",
        "it's" to "it is", "that's" to "that is", "what's" to "what is", "here's" to "here is",
        "there's" to "there is", "let's" to "let us", "i'll" to "i will", "you'll" to "you will",
        "we'll" to "we will", "i'd" to "i would", "i've" to "i have", "we've" to "we have",
        "don't" to "do not", "doesn't" to "does not", "can't" to "can not", "cannot" to "can not",
        "isn't" to "is not", "aren't" to "are not", "won't" to "will not", "she's" to "she is",
        "he's" to "he is", "who's" to "who is", "how's" to "how is", "where's" to "where is",
        "they've" to "they have",
    )

    private fun normalize(word: String): String =
        word.lowercase().replace('’', '\'').trim { !it.isLetterOrDigit() && it != '\'' }.trim('\'')

    /** Splits into comparable tokens, expanding contractions so "I'm" matches "I am". */
    fun tokens(text: String): List<String> =
        text.split(Regex("\\s+"))
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .flatMap { (contractions[it] ?: it).replace('-', ' ').split(' ') }
            .filter { it.isNotEmpty() }

    fun compare(target: String, spoken: String): MatchResult {
        val display = target.split(Regex("\\s+")).filter { it.isNotBlank() }
        // Each displayed word expands to one or more tokens; it counts as said if all of them align.
        val groups = display.map { tokens(it) }
        val flat = groups.flatten()
        val matched = lcsMatches(flat, tokens(spoken))
        var i = 0
        val words = display.mapIndexed { idx, w ->
            val n = groups[idx].size
            val ok = n > 0 && (i until i + n).all { matched[it] }
            i += n
            w to ok
        }
        val score = if (flat.isEmpty()) 0 else matched.count { it } * 100 / flat.size
        return MatchResult(words, score)
    }

    /** Marks which target tokens appear, in order, in what was heard (longest common subsequence). */
    private fun lcsMatches(target: List<String>, heard: List<String>): BooleanArray {
        val dp = Array(target.size + 1) { IntArray(heard.size + 1) }
        for (a in target.indices.reversed()) for (b in heard.indices.reversed()) {
            dp[a][b] = if (target[a] == heard[b]) dp[a + 1][b + 1] + 1 else maxOf(dp[a + 1][b], dp[a][b + 1])
        }
        val out = BooleanArray(target.size)
        var a = 0
        var b = 0
        while (a < target.size && b < heard.size) {
            when {
                target[a] == heard[b] -> { out[a] = true; a++; b++ }
                dp[a + 1][b] >= dp[a][b + 1] -> a++
                else -> b++
            }
        }
        return out
    }
}
