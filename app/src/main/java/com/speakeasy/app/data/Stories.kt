package com.speakeasy.app.data

import kotlinx.serialization.Serializable

@Serializable
data class StoryContent(val stories: List<Story>)

/** A Duolingo-style listening story: lines read in character voices, with questions along the way. */
@Serializable
data class Story(
    val id: String,
    val title: String,
    val titleVi: String,
    val emoji: String,
    val category: String,
    val characters: List<StoryCharacter>,
    val items: List<StoryItem>,
) {
    fun pitchOf(speaker: String?) = characters.firstOrNull { it.name == speaker }?.pitch ?: 1f
    val questionCount: Int get() = items.count { it.isQuestion }
}

@Serializable
data class StoryCharacter(val name: String, val pitch: Float)

/** Either a spoken line (`speaker`, `en`, `vi`) or a question (`q`, `options`, `answer`). */
@Serializable
data class StoryItem(
    val type: String,
    val speaker: String? = null,
    val en: String = "",
    val vi: String = "",
    val q: String = "",
    val options: List<String> = emptyList(),
    val answer: Int = 0,
) {
    val isQuestion: Boolean get() = type == "question"
}
