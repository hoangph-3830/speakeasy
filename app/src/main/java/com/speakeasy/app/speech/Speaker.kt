package com.speakeasy.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Thin wrapper over Android TextToSpeech (the engine Engrisk used for flashcards). */
class Speaker(context: Context) {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Text currently being spoken, so the UI can highlight it. */
    private val _speaking = MutableStateFlow<String?>(null)
    val speaking: StateFlow<String?> = _speaking.asStateFlow()

    // Touched from the TTS callback thread as well as the main thread.
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()

    var rate: Float = 0.9f

    /** "US" or "UK"; falls back to the engine default if that voice is not installed. */
    var accent: String = "US"
        set(value) {
            field = value
            if (_ready.value) tts.language = localeFor(value)
        }

    private fun localeFor(accent: String) = if (accent == "UK") Locale.UK else Locale.US

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = localeFor(accent)
            _ready.value = true
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            // The highlight is set in say(), so nothing to do when audio starts.
            override fun onStart(id: String) = Unit

            override fun onDone(id: String) = finish(id)

            @Deprecated("Deprecated in Java")
            override fun onError(id: String) = finish(id)

            override fun onError(id: String, errorCode: Int) = finish(id)

            // Called when a newer utterance flushes this one.
            override fun onStop(id: String, interrupted: Boolean) = finish(id)
        })
    }

    private fun finish(id: String) {
        // A flushed utterance finishes after its replacement started; keep the newer highlight.
        if (id == currentId) _speaking.value = null
        callbacks.remove(id)?.invoke()
    }

    @Volatile
    private var currentId: String? = null

    /** Speaks [text]; [onDone] runs on a background thread when it finishes or fails. */
    fun say(text: String, slow: Boolean = false, pitch: Float = 1f, onDone: (() -> Unit)? = null) {
        val id = UUID.randomUUID().toString()
        currentId = id
        _speaking.value = text
        if (onDone != null) callbacks[id] = onDone
        tts.setSpeechRate(if (slow) rate * 0.6f else rate)
        // Pitch gives story characters distinct voices with a single TTS engine.
        tts.setPitch(pitch)
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id)
    }

    /** Suspends until [text] has been spoken; cancelling the caller stops speech. */
    suspend fun sayAndWait(text: String, pitch: Float = 1f) = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { stop() }
        say(text, pitch = pitch) { if (cont.isActive) cont.resume(Unit) }
    }

    fun stop() {
        tts.stop()
        currentId = null
        _speaking.value = null
        // Release anyone waiting in sayAndWait so their coroutines don't hang.
        val pending = callbacks.values.toList()
        callbacks.clear()
        pending.forEach { it() }
    }

    fun shutdown() = tts.shutdown()
}
