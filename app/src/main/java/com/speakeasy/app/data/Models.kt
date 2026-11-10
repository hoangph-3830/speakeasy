package com.speakeasy.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Content(
    val categories: List<Category>,
    val topics: List<Topic>,
    /** The learning path: stages ordered easy to hard (A1 → B2); every topic sits in exactly one. */
    val path: List<Stage> = emptyList(),
)

/** One stage of the learning path, listing its topic ids in the order to learn them. */
@Serializable
data class Stage(
    val id: String,
    val level: String,
    val title: String,
    val subtitle: String = "",
    val topics: List<String>,
    /** "kids" for stages meant for children; null for the general path. */
    val audience: String? = null,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val nameEn: String,
    val emoji: String,
    val color: String,
)

@Serializable
data class Topic(
    val id: String,
    val category: String,
    val title: String,
    val titleVi: String,
    val emoji: String,
    val level: String,
    val phrases: List<Phrase>,
    val words: List<Word>,
    val dialogue: Dialogue,
    val native: List<Native> = emptyList(),
) {
    /** Every English sentence in the topic with its translation, for speaking and listening drills. */
    val sentences: List<Pair<String, String>>
        get() = (phrases.map { it.en to it.vi } + native.map { it.natural to it.vi } + dialogue.lines.map { it.en to it.vi })
            .distinctBy { it.first }
}

/** A textbook sentence next to what a native speaker would actually say. */
@Serializable
data class Native(val plain: String, val natural: String, val vi: String, val note: String? = null)

@Serializable
data class Phrase(val en: String, val vi: String, val tip: String? = null)

@Serializable
data class Word(val word: String, val ipa: String, val vi: String, val example: String)

@Serializable
data class Dialogue(val roleA: String, val roleB: String, val lines: List<Line>)

@Serializable
data class Line(
    @SerialName("s") val speaker: String,
    val en: String,
    val vi: String,
) {
    val isUser: Boolean get() = speaker == "B"
}

/** The learning activities a topic offers; each one is tracked separately in progress. */
enum class Activity(val label: String, val emoji: String, val xp: Int) {
    PHRASES("Cụm từ & từ vựng", "📚", 10),
    DIALOGUE("Hội thoại nhập vai", "💬", 15),
    SPEAK("Luyện nói", "🎤", 15),
    GAME("Chơi mà học", "🎮", 10),
}
