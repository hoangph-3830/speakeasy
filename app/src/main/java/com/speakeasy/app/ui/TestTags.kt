package com.speakeasy.app.ui

/**
 * Stable ids for UI automation (Appium / sungen). With `testTagsAsResourceId` on, each tag shows up as
 * the element's resource-id, so tests do not depend on the Vietnamese labels.
 * Keep the values stable: renaming one breaks the selectors that use it.
 */
object TestTags {
    // Screens: "screen_" + the route without its arguments, e.g. "topic/{id}/phrases" → "screen_topic_phrases".
    // No regex here: Android's ICU engine rejects patterns the JVM accepts, which crashed the app once.
    fun screen(route: String) = "screen_" + route.split('/').filterNot { it.startsWith("{") }.joinToString("_")
    fun tab(route: String) = "tab_$route"

    const val BACK = "btn_back"
    const val SPEAK = "btn_speak"
    const val SPEAK_SLOW = "btn_speak_slow"
    const val MIC = "btn_mic"

    const val DIALOG_COMPLETE = "dialog_complete"
    const val DIALOG_CONTINUE = "btn_dialog_continue"
    const val DIALOG_AGAIN = "btn_dialog_again"

    const val NAME_DIALOG = "dialog_name"
    const val NAME_INPUT = "input_name"
    const val NAME_CONFIRM = "btn_name_confirm"
    const val NAME_SKIP = "btn_name_skip"

    fun shortcut(key: String) = "shortcut_$key"
    fun topic(id: String) = "topic_$id"
    const val CONTINUE_LEARNING = "btn_continue_learning"
    const val CONTINUE_REVIEW = "btn_continue_review"

    const val CHAT_INPUT = "input_chat"
    const val CHAT_SEND = "btn_chat_send"
    const val SPELLING_INPUT = "input_spelling"
    const val GAME_INPUT = "input_game_answer"
    const val VOCAB_SEARCH = "input_vocab_search"
    const val SPEECH_RATE = "slider_speech_rate"
    const val REMINDER_SWITCH = "switch_reminder"
    fun storyKind(pictures: Boolean) = if (pictures) "tab_story_pictures" else "tab_story_audio"

    const val RESET_DIALOG = "dialog_reset"
    const val RESET_CONFIRM = "btn_reset_confirm"
    const val RESET_CANCEL = "btn_reset_cancel"
}
