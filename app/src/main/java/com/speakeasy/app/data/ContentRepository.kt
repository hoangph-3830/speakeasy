package com.speakeasy.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.time.LocalDate

class ContentRepository(private val context: Context) {

    val content: Content = context.assets.open("content.json").bufferedReader().use {
        json.decodeFromString(Content.serializer(), it.readText())
    }

    val pronunciation: PronunciationContent by lazy {
        context.assets.open("pronunciation.json").bufferedReader().use {
            json.decodeFromString(PronunciationContent.serializer(), it.readText())
        }
    }

    val stories: StoryContent by lazy {
        context.assets.open("stories.json").bufferedReader().use {
            json.decodeFromString(StoryContent.serializer(), it.readText())
        }
    }

    /** The "3000 từ vựng" lists; large, so only parsed when first opened. */
    val vocab: VocabContent by lazy {
        context.assets.open("vocab.json").bufferedReader().use {
            json.decodeFromString(VocabContent.serializer(), it.readText())
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }

    val categories: List<Category> get() = content.categories
    val topics: List<Topic> get() = content.topics

    /** The learning path; older content without one falls back to a single stage of every topic. */
    val path: List<Stage>
        get() = content.path.ifEmpty { listOf(Stage("all", "A1", "Tất cả chủ đề", topics = topics.map { it.id })) }

    fun topic(id: String): Topic? = topics.firstOrNull { it.id == id }

    fun category(id: String): Category? = categories.firstOrNull { it.id == id }

    fun topicsIn(categoryId: String): List<Topic> = topics.filter { it.category == categoryId }

    /** Rotates through all topics, one per calendar day. */
    fun topicOfTheDay(today: LocalDate = LocalDate.now()): Topic =
        topics[Math.floorMod(today.toEpochDay(), topics.size.toLong()).toInt()]

    val allWords: List<Word> get() = topics.flatMap { it.words }.distinctBy { it.word }

    fun vocabGroup(id: String): VocabGroup? = vocab.groups.firstOrNull { it.id == id }

    val vocabWords: List<VocabWord> get() = vocab.groups.flatMap { it.words }
}
