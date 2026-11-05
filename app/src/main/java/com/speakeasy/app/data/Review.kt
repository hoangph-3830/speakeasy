package com.speakeasy.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.roundToInt

/** One spaced-repetition card. [due] and [interval] are in days (epoch day / day count). */
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
)

enum class Grade(val label: String, val emoji: String) {
    AGAIN("Quên", "😵"),
    HARD("Khó", "😓"),
    GOOD("Nhớ", "🙂"),
    EASY("Dễ", "😎"),
}

/** A simplified SM-2: good answers stretch the gap by the card's ease, misses reset it. */
object Srs {

    fun schedule(card: ReviewCard, grade: Grade, today: Long): ReviewCard = when (grade) {
        Grade.AGAIN -> card.copy(reps = 0, interval = 0, ease = maxOf(1.3f, card.ease - 0.2f), due = today)
        Grade.HARD -> {
            val days = maxOf(1, (card.interval * 1.2f).roundToInt())
            card.copy(interval = days, ease = maxOf(1.3f, card.ease - 0.15f), due = today + days, reps = card.reps + 1)
        }
        Grade.GOOD -> {
            val days = when (card.reps) {
                0 -> 1
                1 -> 3
                else -> maxOf(card.interval + 1, (card.interval * card.ease).roundToInt())
            }
            card.copy(interval = days, due = today + days, reps = card.reps + 1)
        }
        Grade.EASY -> {
            val days = if (card.reps == 0) 4 else maxOf(card.interval + 2, (card.interval * card.ease * 1.3f).roundToInt())
            card.copy(interval = days, ease = card.ease + 0.15f, due = today + days, reps = card.reps + 1)
        }
    }

    /** Short label for the gap a grade would give, shown under each button. */
    fun preview(card: ReviewCard, grade: Grade): String {
        val days = schedule(card, grade, 0).due
        return if (days == 0L) "ngay" else "$days ngày"
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
