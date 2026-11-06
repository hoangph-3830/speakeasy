package com.speakeasy.app.data

import kotlinx.serialization.Serializable

@Serializable
data class PictureBookContent(val source: String = "", val books: List<PictureBook>)

/**
 * An illustrated storybook from Global Storybooks / African Storybook, with a human Vietnamese
 * translation. The credits are required by the CC licences, so the reader always shows them.
 */
@Serializable
data class PictureBook(
    val id: String,
    val title: String,
    val titleVi: String,
    /** CEFR level, "A1" to "C2". */
    val level: String,
    val words: Int = 0,
    /** Null when the book has no illustrations on the CDN. */
    val cover: String? = null,
    /** "CC BY" or "CC BY-NC". */
    val license: String,
    /** Author names. */
    val text: String,
    val illustration: String,
    val translation: String,
    val pages: List<BookPage>,
)

/** One page: the English text, its Vietnamese translation and the remote illustration, if it has one. */
@Serializable
data class BookPage(val en: String, val vi: String, val img: String? = null)
