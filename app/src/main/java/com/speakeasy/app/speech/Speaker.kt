package com.speakeasy.app.speech

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

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
            if (_ready.value) applyVoice()
        }

    /** A voice the learner picked in settings; empty, or not installed, means the best one for [accent]. */
    var voiceName: String = ""
        set(value) {
            field = value
            if (_ready.value) applyVoice()
        }

    // The voice every utterance uses; a preview may borrow another one, so say() puts it back.
    @Volatile
    private var voice: Voice? = null

    private fun localeFor(accent: String) = if (accent == "UK") Locale.UK else Locale.US

    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            applyVoice()
            _ready.value = true
        }
    }

    // Chunks of the sayAll() batch in progress, by utterance id, so onStart can report which one is playing.
    private val chunks = ConcurrentHashMap<String, Int>()
    private val chunkTexts = ConcurrentHashMap<String, String>()

    @Volatile
    private var lastChunk: String? = null

    @Volatile
    private var onChunk: ((Int) -> Unit)? = null

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {
                // Single utterances set their highlight in say(); queued chunks only start here.
                val index = chunks[id] ?: return
                currentId = id
                _speaking.value = chunkTexts[id]
                onChunk?.let { report -> main.post { if (chunks[id] == index) report(index) } }
            }

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
        // Between two queued chunks the next one is about to start, so the highlight stays too.
        val midBatch = chunks.containsKey(id) && id != lastChunk
        if (id == currentId && !midBatch) _speaking.value = null
        callbacks.remove(id)?.invoke()
    }

    @Volatile
    private var currentId: String? = null

    /**
     * English voices on this device, best first: the [accent]'s locale, then high quality, then
     * network voices while online (they usually sound more natural) or offline ones otherwise.
     */
    fun englishVoices(): List<Voice> {
        if (!_ready.value) return emptyList()
        val locale = localeFor(accent)
        val online = isOnline()
        val installed = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == "en" && "notInstalled" !in it.features.orEmpty() }
        return installed.sortedWith(
            compareByDescending<Voice> { it.locale == locale }
                .thenByDescending { it.quality >= Voice.QUALITY_HIGH }
                .thenByDescending { it.isNetworkConnectionRequired == online }
                .thenByDescending { it.quality }
                .thenBy { it.name },
        )
    }

    /** The voice automatic mode uses: the top-ranked one in the accent's own locale. */
    fun autoVoice(): Voice? = englishVoices().firstOrNull { it.locale == localeFor(accent) }

    private fun applyVoice() {
        // Setting the language first keeps a sensible default when no voice list is available.
        tts.language = localeFor(accent)
        val voices = englishVoices()
        voice = voices.firstOrNull { voiceName.isNotEmpty() && it.name == voiceName } ?: autoVoice()
        voice?.let { runCatching { tts.voice = it } }
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        return runCatching {
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(false)
    }

    private fun prepare(slow: Boolean, pitch: Float, using: Voice?) {
        tts.setSpeechRate(if (slow) rate * 0.6f else rate)
        // Pitch gives story characters distinct voices with a single TTS engine.
        tts.setPitch(pitch)
        val wanted = using ?: voice
        if (wanted != null && runCatching { tts.voice?.name }.getOrNull() != wanted.name) runCatching { tts.voice = wanted }
    }

    /** Speaks [text]; [onDone] runs on a background thread when it finishes or fails. [using] previews another voice. */
    fun say(text: String, slow: Boolean = false, pitch: Float = 1f, using: Voice? = null, onDone: (() -> Unit)? = null) {
        clearChunks()
        val id = UUID.randomUUID().toString()
        currentId = id
        _speaking.value = text
        if (onDone != null) callbacks[id] = onDone
        prepare(slow, pitch, using)
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id)
    }

    /**
     * Speaks [chunks] back to back, queued at once so the engine never pauses or resets between them.
     * [onChunk] gets the index starting to play and [onDone] runs after the last one or on stop();
     * both run on the main thread. While it plays, [speaking] is [label] or else the current chunk.
     */
    fun sayAll(chunks: List<String>, label: String? = null, onChunk: (Int) -> Unit = {}, onDone: () -> Unit = {}) {
        stop()
        val parts = chunks.filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            main.post(onDone)
            return
        }
        prepare(slow = false, pitch = 1f, using = null)
        val batch = UUID.randomUUID().toString()
        val ids = parts.indices.map { "$batch:$it" }
        ids.forEachIndexed { i, id ->
            this.chunks[id] = i
            chunkTexts[id] = label ?: parts[i]
        }
        this.onChunk = onChunk
        lastChunk = ids.last()
        callbacks[ids.last()] = { main.post(onDone) }
        currentId = ids.first()
        _speaking.value = label ?: parts.first()
        for ((i, id) in ids.withIndex()) {
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (tts.speak(parts[i], mode, null, id) != TextToSpeech.SUCCESS) {
                stop()
                return
            }
        }
    }

    /** Speaks a paragraph sentence by sentence, so long text is not cut off by the engine's input limit. */
    fun sayLong(text: String) = sayAll(Sentences.split(text), label = text)

    /** Suspends until [text] has been spoken; cancelling the caller stops speech. */
    suspend fun sayAndWait(text: String, pitch: Float = 1f) = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { stop() }
        say(text, pitch = pitch) { if (cont.isActive) cont.resume(Unit) }
    }

    private fun clearChunks() {
        chunks.clear()
        chunkTexts.clear()
        lastChunk = null
        onChunk = null
    }

    fun stop() {
        tts.stop()
        clearChunks()
        currentId = null
        _speaking.value = null
        // Release anyone waiting in sayAndWait so their coroutines don't hang.
        val pending = callbacks.values.toList()
        callbacks.clear()
        pending.forEach { it() }
    }

    fun shutdown() = tts.shutdown()
}

/** Splits text into sentence-sized pieces for [Speaker.sayAll]; plain Kotlin so it runs in unit tests. */
object Sentences {
    const val MAX_CHUNK = 220

    // End punctuation, an optional closing quote or bracket, then the space before the next sentence.
    private val boundary = Regex("""[.!?…]["”’)]?\s+""")
    private val initials = Regex("""^(?:[A-Za-z]\.)+$""")
    private val abbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "st", "jr", "sr", "gen", "gov", "sen", "rep", "col", "lt", "sgt", "capt",
        "inc", "ltd", "co", "corp", "vs", "no", "approx", "est", "dept", "jan", "feb", "mar", "apr", "jun", "jul",
        "aug", "sep", "sept", "oct", "nov", "dec", "mt", "ft", "etc",
    )

    /** Sentences of [text], each under [max] characters (long ones split at commas, then at spaces). */
    fun split(text: String, max: Int = MAX_CHUNK): List<String> =
        sentences(text.trim()).flatMap { fit(it, max) }

    private fun sentences(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        var start = 0
        for (m in boundary.findAll(text)) {
            if (m.value.first() == '.' && !endsSentence(text, start, m)) continue
            out += text.substring(start, m.range.first + m.value.trimEnd().length)
            start = m.range.last + 1
        }
        if (start < text.length) out += text.substring(start)
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** A full stop after "U.S." or "Mr", or before a lower-case word, does not end the sentence. */
    private fun endsSentence(text: String, start: Int, m: kotlin.text.MatchResult): Boolean {
        val next = text.getOrNull(m.range.last + 1)
        if (next != null && next.isLowerCase()) return false
        val word = text.substring(start, m.range.first + 1).substringAfterLast(' ').trimStart('(', '"', '“', '‘')
        return !initials.matches(word) && word.dropLast(1).lowercase() !in abbreviations
    }

    /** Breaks a too-long sentence at commas, semicolons or colons, and at spaces if a piece is still too long. */
    private fun fit(sentence: String, max: Int): List<String> {
        if (sentence.length <= max) return listOf(sentence)
        val clauses = sentence.split(Regex("""(?<=[,;:])\s+""")).flatMap { clause ->
            if (clause.length <= max) listOf(clause) else pack(clause.split(' '), max)
        }
        return pack(clauses, max)
    }

    /** Joins neighbouring pieces with spaces while they stay within [max]. */
    private fun pack(pieces: List<String>, max: Int): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (p in pieces.filter { it.isNotEmpty() }) {
            if (current.isNotEmpty() && current.length + 1 + p.length > max) {
                out += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(p)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}
