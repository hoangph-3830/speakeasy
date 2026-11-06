package com.speakeasy.app.data

import kotlinx.serialization.Serializable

@Serializable
data class VideoContent(val updated: String = "", val channels: List<VideoChannel>)

/** An official YouTube channel; its videos are only ever played through YouTube's own embedded player. */
@Serializable
data class VideoChannel(
    val handle: String,
    val name: String,
    /** CEFR level, "A1" to "C2". */
    val level: String,
    val kids: Boolean = false,
    /** One Vietnamese line about the channel. */
    val about: String = "",
    val videos: List<Video>,
)

/** [id] is the 11-character YouTube video id. */
@Serializable
data class Video(val id: String, val title: String)

object Videos {
    val idPattern = Regex("[A-Za-z0-9_-]{11}")

    fun doneKey(id: String) = "video:$id"

    fun thumbnail(id: String) = "https://i.ytimg.com/vi/$id/mqdefault.jpg"

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    /** The privacy-enhanced embed: English captions on, Vietnamese player UI, no unrelated suggestions. */
    fun embedUrl(id: String) =
        "https://www.youtube-nocookie.com/embed/$id?playsinline=1&rel=0&cc_load_policy=1&cc_lang_pref=en&hl=vi&enablejsapi=1"
}
