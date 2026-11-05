package com.speakeasy.app.data

import com.speakeasy.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** What the news reader shows for a tapped word. */
data class DictEntry(val word: String, val ipa: String, val pos: String, val vi: String, val example: String) {
    companion object {
        fun of(w: VocabWord) = DictEntry(w.w, w.ipa, w.pos, w.vi, w.ex)
        fun of(w: Word) = DictEntry(w.word, w.ipa, "", w.vi, w.example)
    }
}

/** Rule-based English lemmatizer: enough to find "stopped" under "stop" without a full morphology database. */
object Lemmatizer {

    /** Lowercase, curly apostrophes made straight, punctuation stripped from both ends. */
    fun normalize(raw: String): String =
        raw.lowercase()
            .replace('’', '\'').replace('‘', '\'')
            .trim { !it.isLetterOrDigit() && it != '\'' }
            .trim('\'')

    private fun doubled(stem: String) =
        stem.length >= 3 && stem.last() == stem[stem.length - 2] && stem.last() !in "aeiouwxy"

    /** Possible dictionary forms of [word], most likely first; the word itself is always first. */
    fun candidates(word: String): List<String> {
        val w = normalize(word)
        val out = linkedSetOf(w)
        IRREGULAR[w]?.let { out += it }
        val base = when {
            w.endsWith("'s") -> w.dropLast(2)
            w.endsWith("s'") -> w.dropLast(1)
            else -> w
        }
        out += base
        IRREGULAR[base]?.let { out += it }
        fun suffix(end: String, vararg stems: String) {
            if (base.length > end.length && base.endsWith(end)) stems.forEach { if (it.length >= 2) out += it }
        }
        // Plurals and third person: cats, boxes, cities.
        val b = base
        if (!b.endsWith("ss")) suffix("s", b.dropLast(1))
        suffix("es", b.dropLast(2))
        suffix("ies", b.dropLast(3) + "y")
        // Past tense: walked, liked, studied, stopped.
        suffix("ed", b.dropLast(2), b.dropLast(1), if (doubled(b.dropLast(2))) b.dropLast(3) else "")
        suffix("ied", b.dropLast(3) + "y")
        // -ing: walking, making, running, lying.
        suffix("ing", b.dropLast(3), b.dropLast(3) + "e", if (doubled(b.dropLast(3))) b.dropLast(4) else "")
        suffix("ying", b.dropLast(4) + "ie")
        // Comparatives: faster, later, bigger, happier (and -est).
        for (end in listOf("er", "est")) {
            val stem = b.dropLast(end.length)
            suffix(end, stem, stem + "e", if (doubled(stem)) stem.dropLast(1) else "")
            suffix("i$end", b.dropLast(end.length + 1) + "y")
        }
        return out.toList()
    }

    /** The first candidate [known] accepts, or null. */
    fun lemma(word: String, known: (String) -> Boolean): String? = candidates(word).firstOrNull(known)

    /** Common irregular verbs, plurals and comparatives, mapped to their dictionary form. */
    val IRREGULAR: Map<String, String> = buildMap {
        fun verb(base: String, vararg forms: String) = forms.forEach { put(it, base) }
        verb("be", "am", "is", "are", "was", "were", "been", "being")
        verb("have", "has", "had", "having")
        verb("do", "does", "did", "done", "doing")
        verb("go", "goes", "went", "gone")
        verb("say", "says", "said")
        verb("make", "made")
        verb("get", "got", "gotten")
        verb("know", "knew", "known")
        verb("think", "thought")
        verb("take", "took", "taken")
        verb("see", "saw", "seen")
        verb("come", "came")
        verb("give", "gave", "given")
        verb("find", "found")
        verb("tell", "told")
        verb("become", "became")
        verb("leave", "left")
        verb("feel", "felt")
        verb("bring", "brought")
        verb("begin", "began", "begun")
        verb("keep", "kept")
        verb("hold", "held")
        verb("write", "wrote", "written")
        verb("stand", "stood")
        verb("hear", "heard")
        verb("mean", "meant")
        verb("meet", "met")
        verb("run", "ran")
        verb("pay", "paid")
        verb("sit", "sat")
        verb("speak", "spoke", "spoken")
        verb("lie", "lay", "lain")
        verb("lead", "led")
        verb("grow", "grew", "grown")
        verb("lose", "lost")
        verb("fall", "fell", "fallen")
        verb("send", "sent")
        verb("build", "built")
        verb("understand", "understood")
        verb("draw", "drew", "drawn")
        verb("break", "broke", "broken")
        verb("spend", "spent")
        verb("rise", "rose", "risen")
        verb("drive", "drove", "driven")
        verb("buy", "bought")
        verb("wear", "wore", "worn")
        verb("choose", "chose", "chosen")
        verb("seek", "sought")
        verb("throw", "threw", "thrown")
        verb("catch", "caught")
        verb("deal", "dealt")
        verb("win", "won")
        verb("forget", "forgot", "forgotten")
        verb("sell", "sold")
        verb("fight", "fought")
        verb("teach", "taught")
        verb("eat", "ate", "eaten")
        verb("drink", "drank", "drunk")
        verb("sing", "sang", "sung")
        verb("swim", "swam", "swum")
        verb("fly", "flew", "flown")
        verb("ride", "rode", "ridden")
        verb("shake", "shook", "shaken")
        verb("steal", "stole", "stolen")
        verb("wake", "woke", "woken")
        verb("freeze", "froze", "frozen")
        verb("hide", "hid", "hidden")
        verb("bite", "bit", "bitten")
        verb("feed", "fed")
        verb("sleep", "slept")
        verb("sweep", "swept")
        verb("lend", "lent")
        verb("bend", "bent")
        verb("shoot", "shot")
        verb("strike", "struck")
        verb("hang", "hung")
        verb("dig", "dug")
        verb("stick", "stuck")
        verb("light", "lit")
        verb("slide", "slid")
        verb("flee", "fled")
        verb("bleed", "bled")
        verb("swing", "swung")
        verb("tear", "tore", "torn")
        verb("ring", "rang", "rung")
        verb("show", "shown")
        verb("blow", "blew", "blown")
        verb("forgive", "forgave", "forgiven")
        verb("arise", "arose", "arisen")
        // Plurals.
        verb("child", "children")
        verb("person", "people")
        verb("man", "men")
        verb("woman", "women")
        verb("foot", "feet")
        verb("tooth", "teeth")
        verb("mouse", "mice")
        verb("goose", "geese")
        verb("life", "lives")
        verb("wife", "wives")
        verb("knife", "knives")
        verb("leaf", "leaves")
        verb("half", "halves")
        verb("shelf", "shelves")
        verb("wolf", "wolves")
        verb("thief", "thieves")
        verb("crisis", "crises")
        verb("analysis", "analyses")
        verb("phenomenon", "phenomena")
        verb("criterion", "criteria")
        // Comparatives.
        verb("good", "better", "best")
        verb("bad", "worse", "worst")
        verb("far", "farther", "further", "farthest", "furthest")
        verb("many", "more", "most")
        verb("little", "less", "least")
    }
}

/**
 * Word lookup for the news reader over the app's own vocabulary, plus online translation for whatever
 * is not in it. [entries] is a function so the 3000-word list is only parsed on first use.
 */
class Dictionary(private val entries: () -> List<DictEntry>) {

    private val index: Map<String, DictEntry> by lazy {
        // Vocabulary entries come first, so they win over the shorter topic entries for the same word.
        entries().reversed().associateBy { it.word.lowercase() }
    }

    fun lookup(word: String): DictEntry? = Lemmatizer.lemma(word) { it in index }?.let { index[it] }

    private val http = OkHttpClient.Builder()
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, String>()

    /** Vietnamese translation of [text]: Google's free endpoint first, then Gemini if a key is set. */
    suspend fun translate(text: String): String {
        val key = text.trim()
        if (key.isEmpty()) return ""
        cache[key]?.let { return it }
        val vi = withContext(Dispatchers.IO) {
            runCatching { google(key) }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: if (BuildConfig.GEMINI_API_KEY.isNotBlank()) gemini(key)
                else throw IOException("Không dịch được, kiểm tra mạng rồi thử lại.")
        }
        cache[key] = vi
        return vi
    }

    private fun google(text: String): String {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=vi&dt=t&q=" +
            URLEncoder.encode(text, "UTF-8")
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        val body = http.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            it.body?.string().orEmpty()
        }
        return parseGoogle(body)
    }

    private fun gemini(text: String): String {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", translatePrompt(text)) } }
                }
            }
            putJsonObject("generationConfig") { put("temperature", 0.2) }
        }.toString().toRequestBody(JSON_TYPE)
        for (model in AiTutor.MODELS) {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                .post(body)
                .build()
            val (code, response) = try {
                http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                continue
            }
            if (code in 200..299) return runCatching { AiTutor.replyText(response).trim() }.getOrNull() ?: continue
        }
        throw IOException("Không dịch được, thử lại sau nhé.")
    }

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        fun translatePrompt(text: String) =
            "Translate this English text into natural Vietnamese. Reply with only the translation, no quotes, notes or romanisation.\n\n$text"

        /** The endpoint answers `[[["Xin chào","Hello",...],[...]],...]`; the translation is every [0][i][0] joined. */
        fun parseGoogle(response: String): String {
            val segments = (json.parseToJsonElement(response) as? JsonArray)?.firstOrNull() as? JsonArray
                ?: throw IOException("Unexpected translate response")
            return segments.joinToString("") { seg ->
                ((seg as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
            }.trim()
        }
    }
}
