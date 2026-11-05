package com.speakeasy.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Locale
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One spaced-repetition card. [due], [last] and [interval] are in days (epoch day / day count).
 * [stability] and [difficulty] are the FSRS memory state; 0 stability means the card was never graded
 * under FSRS. [ease] is left over from the old SM-2 scheduler and only read to migrate such cards.
 */
@Serializable
data class ReviewCard(
    val id: String,
    val front: String,
    val back: String,
    val extra: String? = null,
    val interval: Int = 0,
    val ease: Float = 2.5f,
    val due: Long = 0,
    val reps: Int = 0,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val lapses: Int = 0,
    val last: Long = -1,
)

/** Answer buttons, in FSRS order: AGAIN = 1 … EASY = 4. */
enum class Grade(val label: String, val emoji: String) {
    AGAIN("Quên", "😵"),
    HARD("Khó", "😓"),
    GOOD("Nhớ", "🙂"),
    EASY("Dễ", "😎"),
    ;

    val value: Int get() = ordinal + 1
}

/** FSRS-4.5 with its default weights, aiming for 90% recall when a card comes due. */
object Srs {

    private val W = doubleArrayOf(
        0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031, 1.6474,
        0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755,
    )
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81
    private const val RETENTION = 0.9

    /** Chance of recalling a card [t] days after its last review. */
    fun retrievability(t: Double, s: Double): Double = (1 + FACTOR * t / s).pow(DECAY)

    private fun initStability(g: Int) = W[g - 1]

    private fun initDifficulty(g: Int) = (W[4] - (g - 3) * W[5]).coerceIn(1.0, 10.0)

    // Moves toward the "Good" starting difficulty a little each review, so no card is stuck at 10.
    private fun nextDifficulty(d: Double, g: Int) =
        (W[7] * initDifficulty(3) + (1 - W[7]) * (d - W[6] * (g - 3))).coerceIn(1.0, 10.0)

    private fun recallStability(d: Double, s: Double, r: Double, g: Int): Double {
        val hard = if (g == 2) W[15] else 1.0
        val easy = if (g == 4) W[16] else 1.0
        return s * (exp(W[8]) * (11 - d) * s.pow(-W[9]) * (exp(W[10] * (1 - r)) - 1) * hard * easy + 1)
    }

    private fun forgetStability(d: Double, s: Double, r: Double): Double =
        minOf(s, W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp(W[14] * (1 - r)))

    /** Days until recall drops to [RETENTION]; equals round(S) for 90%. */
    fun interval(s: Double): Int =
        (s / FACTOR * (RETENTION.pow(1 / DECAY) - 1)).roundToInt().coerceIn(1, 36500)

    fun schedule(card: ReviewCard, grade: Grade, today: Long): ReviewCard {
        val g = grade.value
        // An SM-2 card keeps its progress: its gap becomes the stability, its ease the difficulty.
        val migrated = card.reps > 0 && card.stability == 0.0
        val s = if (migrated) maxOf(card.interval, 1).toDouble() else card.stability
        val d = if (migrated) (11 - card.ease * 2.0).coerceIn(1.0, 10.0) else card.difficulty
        val elapsed = when {
            migrated -> maxOf(card.interval, 0).toLong()
            card.last < 0 -> 0L
            else -> maxOf(0L, today - card.last)
        }
        val (stability, difficulty) = if (s == 0.0) {
            initStability(g) to initDifficulty(g)
        } else {
            val r = retrievability(elapsed.toDouble(), s)
            val next = if (g == 1) forgetStability(d, s, r) else recallStability(d, s, r, g)
            next to nextDifficulty(d, g)
        }
        // A miss comes back in this session; anything else waits for the FSRS interval.
        val due = if (grade == Grade.AGAIN) today else today + interval(stability)
        return card.copy(
            stability = stability,
            difficulty = difficulty,
            due = due,
            last = today,
            interval = (due - today).toInt(),
            reps = if (grade == Grade.AGAIN && card.reps > 0) 0 else card.reps + 1,
            lapses = if (grade == Grade.AGAIN && card.reps > 0) card.lapses + 1 else card.lapses,
        )
    }

    /** Short label for the gap a grade would give, shown under each button. */
    fun preview(card: ReviewCard, grade: Grade, today: Long = LocalDate.now().toEpochDay()): String =
        gapLabel((schedule(card, grade, today).due - today).toInt())

    fun gapLabel(days: Int): String = when {
        days <= 0 -> "ngay"
        days >= 365 -> "${String.format(Locale.US, "%.1f", days / 365.0)} năm"
        days >= 60 -> "${(days / 30.0).roundToInt()} tháng"
        else -> "$days ngày"
    }

    /** Cards a finished lesson adds to the deck: its words, phrases and native expressions. */
    fun cardsFor(topic: Topic): List<ReviewCard> =
        topic.words.map { ReviewCard("w:${it.word}", it.word, it.vi, "${it.ipa} · ${it.example}") } +
            topic.phrases.map { ReviewCard("p:${it.en}", it.en, it.vi, it.tip) } +
            topic.native.map { ReviewCard("n:${it.natural}", it.natural, it.vi, it.note) }
}

class ReviewStore(context: Context, private val today: () -> LocalDate = LocalDate::now) {

    private val prefs = context.getSharedPreferences("review", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(ReviewCard.serializer())
    private val _cards = MutableStateFlow(
        prefs.getString("cards", null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
    )
    val cards: StateFlow<List<ReviewCard>> = _cards.asStateFlow()

    val todayDay: Long get() = today().toEpochDay()

    fun due(): List<ReviewCard> = _cards.value.filter { it.due <= todayDay }

    private fun save(cards: List<ReviewCard>) {
        _cards.value = cards
        prefs.edit().putString("cards", json.encodeToString(serializer, cards)).apply()
    }

    /** Adds the topic's cards that are not in the deck yet; they are due immediately. */
    fun enroll(topic: Topic) {
        add(Srs.cardsFor(topic))
    }

    /** Adds any cards whose id is not in the deck yet, due today; returns how many were new. */
    fun add(cards: List<ReviewCard>): Int {
        val known = _cards.value.map { it.id }.toSet()
        val fresh = cards.distinctBy { it.id }.filter { it.id !in known }.map { it.copy(due = todayDay) }
        if (fresh.isNotEmpty()) save(_cards.value + fresh)
        return fresh.size
    }

    fun grade(card: ReviewCard, grade: Grade): ReviewCard {
        val next = Srs.schedule(card, grade, todayDay)
        save(_cards.value.map { if (it.id == card.id) next else it })
        return next
    }

    fun reset() {
        prefs.edit().clear().apply()
        _cards.value = emptyList()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
