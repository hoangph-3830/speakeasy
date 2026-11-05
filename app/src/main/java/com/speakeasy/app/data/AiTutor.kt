package com.speakeasy.app.data

import android.content.Context
import android.util.Log
import com.speakeasy.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What the AI partner plays and what the learner plays. */
data class Scenario(
    val id: String,
    val title: String,
    val setting: String,
    val aiRole: String,
    val learnerRole: String,
    val emoji: String,
)

enum class AiLevel(val label: String, val prompt: String) {
    EASY("Dễ", "Beginner (A2): simple everyday words, short sentences, speak slowly and clearly."),
    MEDIUM("Vừa", "Intermediate (B1): normal everyday English with common phrasal verbs."),
    NATIVE("Như bản xứ", "Advanced: talk exactly like a native speaker, with idioms, slang and fillers (you know, kind of, I mean), still kind."),
}

@Serializable
data class Correction(
    @SerialName("has_issue") val hasIssue: Boolean,
    val corrected: String = "",
    @SerialName("explanation_vi") val explanationVi: String = "",
)

@Serializable
data class TutorReply(
    val reply: String,
    @SerialName("reply_vi") val replyVi: String,
    val correction: Correction,
    val suggestions: List<String> = emptyList(),
)

/** One message in the chat. For the AI, [raw] is the JSON sent back as history. */
data class ChatTurn(
    val fromUser: Boolean,
    val text: String,
    val raw: String = text,
    val reply: TutorReply? = null,
    val correction: Correction? = null,
    /** App-generated cue (e.g. "start the conversation"); sent to the model but not shown. */
    val hidden: Boolean = false,
)

class AiException(message: String) : Exception(message)

/** Chat preferences, kept in app-private storage on the device. */
class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)
    private val _level = MutableStateFlow(AiLevel.entries.getOrElse(prefs.getInt("level", 1)) { AiLevel.MEDIUM })
    val level: StateFlow<AiLevel> = _level.asStateFlow()
    private val _autoSpeak = MutableStateFlow(prefs.getBoolean("autoSpeak", true))
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    fun setLevel(level: AiLevel) {
        _level.value = level
        prefs.edit().putInt("level", level.ordinal).apply()
    }

    fun setAutoSpeak(on: Boolean) {
        _autoSpeak.value = on
        prefs.edit().putBoolean("autoSpeak", on).apply()
    }
}

/** Google Gemini (free tier) as a role-play partner that also corrects every learner line. */
class AiTutor {

    // Flash models can think for well over OkHttp's default 10 s before the first byte arrives.
    private val http = OkHttpClient.Builder()
        // One slow model must not freeze the chat for minutes: give up on it and try the next.
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var workingModel: String? = null

    suspend fun reply(scenario: Scenario, level: AiLevel, history: List<ChatTurn>): TutorReply =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) throw AiException("Chưa cấu hình GEMINI_API_KEY trong local.properties.")
            val body = requestBody(scenario, level, history).toString().toRequestBody(JSON_TYPE)
            var lastError = "Gemini đang quá tải, thử lại sau ít phút nhé."
            // Free-tier models get overloaded (503) or retired for new keys (404), so try a short list.
            // Start with the model that answered last time; overloaded ones tend to stay overloaded.
            for (model in listOfNotNull(workingModel) + (MODELS - workingModel)) {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    .header("x-goog-api-key", apiKey)
                    .post(body)
                    .build()
                val (code, text) = try {
                    http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
                } catch (e: IOException) {
                    Log.w("AiTutor", "$model failed", e)
                    // A timeout means Gemini is slow, not that the phone is offline.
                    lastError = if (e is java.io.InterruptedIOException) "Gemini đang phản hồi rất chậm (bản miễn phí hay bị quá tải). Đợi một chút rồi thử lại nhé."
                    else "Không gọi được Gemini. Kiểm tra mạng rồi thử lại."
                    continue
                }
                if (code !in 200..299) Log.w("AiTutor", "$model -> HTTP $code: ${text.take(300)}")
                when {
                    code == 400 || code == 403 ->
                        if (text.contains("api key", ignoreCase = true)) throw AiException("Gemini API key không hợp lệ.")
                        else throw AiException("Gemini từ chối yêu cầu ($code). Thử lại sau nhé.")
                    code == 429 -> lastError = "Đã hết lượt miễn phí của Gemini, thử lại sau ít phút."
                    code in 200..299 -> {
                        workingModel = model
                        return@withContext parse(replyText(text))
                    }
                }
            }
            throw AiException(lastError)
        }

    companion object {
        /** Google's rolling Flash aliases first, then a pinned fallback; same list as NhàTrọ+ uses. */
        val MODELS = listOf("gemini-flash-latest", "gemini-3.5-flash", "gemini-flash-lite-latest")

        private val JSON_TYPE = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(reply: TutorReply): String = json.encodeToString(TutorReply.serializer(), reply)

        fun parse(text: String): TutorReply = try {
            json.decodeFromString(TutorReply.serializer(), text)
        } catch (e: Exception) {
            throw AiException("AI trả lời không đúng định dạng. Thử lại nhé.")
        }

        /** Pulls the answer text out of a generateContent response; a blocked prompt has no candidates. */
        fun replyText(response: String): String {
            val root = runCatching { json.parseToJsonElement(response).jsonObject }.getOrNull()
                ?: throw AiException("AI trả lời không đúng định dạng. Thử lại nhé.")
            val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            val text = parts?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            if (text.isNullOrBlank()) throw AiException("AI không trả lời câu này. Thử nói theo cách khác nhé.")
            return text
        }

        /** Gemini wants alternating user/model turns; the AI's turns are replayed as the JSON it produced. */
        fun requestBody(scenario: Scenario, level: AiLevel, history: List<ChatTurn>): JsonObject = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", systemPrompt(scenario, level)) } }
            }
            putJsonArray("contents") {
                history.forEach { turn ->
                    addJsonObject {
                        put("role", if (turn.fromUser) "user" else "model")
                        putJsonArray("parts") { addJsonObject { put("text", turn.raw) } }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
                put("temperature", 0.8)
            }
        }

        /** Cue that makes the AI open the conversation, since the first message must come from the user. */
        const val START_CUE = "[The learner has just joined. Open the conversation with your first line.]"

        fun systemPrompt(s: Scenario, level: AiLevel) = """
            You are a friendly native English speaker helping a Vietnamese learner practise spoken English for work, daily life, travel and socialising.
            Situation: ${s.setting}
            You play: ${s.aiRole}. The learner plays: ${s.learnerRole}.

            How to talk (the "reply" field):
            - Stay in character and keep the conversation moving like a real one. Usually end with a question or a natural prompt so the learner has something to answer.
            - 1 to 3 short sentences, the way people actually speak (contractions, everyday phrasing). No lists, no emojis: the reply is read aloud by text-to-speech.
            - Level: ${level.prompt}
            - Never teach inside "reply"; corrections only go in "correction".

            Correcting the learner (the "correction" field), for the learner's latest message:
            - Their messages come from speech recognition, so ignore capitalisation, punctuation and obvious transcription slips.
            - If it was natural, correct English for this situation, set has_issue to false and leave the other fields empty.
            - Otherwise (grammar, wrong word, translated too literally from Vietnamese, unnatural or too blunt), set has_issue to true, put what a native speaker would say in "corrected", and explain the key point in one short Vietnamese sentence in "explanation_vi".
            - If they wrote Vietnamese or got stuck, give the English they could say in "corrected", explain briefly, and carry on the role-play.
            - A message in square brackets is a cue from the app, not the learner: never correct it.

            "reply_vi": a natural Vietnamese translation of your reply.
            "suggestions": 2 short, natural things the learner could say next (under 12 words each), different in meaning.
        """.trimIndent()

        private fun type(t: String) = buildJsonObject { put("type", t) }

        /** Gemini's OpenAPI-style response schema for [TutorReply]. */
        private val schema: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                put("reply", type("STRING"))
                put("reply_vi", type("STRING"))
                putJsonObject("correction") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        put("has_issue", type("BOOLEAN"))
                        put("corrected", type("STRING"))
                        put("explanation_vi", type("STRING"))
                    }
                    put("required", JsonArray(listOf("has_issue", "corrected", "explanation_vi").map(::JsonPrimitive)))
                }
                putJsonObject("suggestions") {
                    put("type", "ARRAY")
                    put("items", type("STRING"))
                }
            }
            put("required", buildJsonArray { listOf("reply", "reply_vi", "correction", "suggestions").forEach { add(it) } })
        }

        /** Free talk plus one role-play per lesson topic, so chat practice follows what was just learned. */
        fun scenarios(content: Content): List<Scenario> =
            listOf(
                Scenario(
                    "free", "Trò chuyện tự do",
                    "A relaxed chat between new friends at a café. Talk about anything the learner brings up: work, hobbies, travel, food, plans.",
                    "Alex, a friendly 28-year-old from Canada", "themselves", "☕",
                ),
                Scenario(
                    "smalltalk", "Small talk với đồng nghiệp",
                    "Small talk with a colleague in the office kitchen on a Monday morning.",
                    "Sam, a cheerful colleague from London", "a colleague", "💬",
                ),
            ) + content.topics.map { t ->
                Scenario(
                    "topic:${t.id}", t.titleVi,
                    "${t.title}. Use situations and phrases like these: ${t.phrases.joinToString(" / ") { it.en }}",
                    t.dialogue.roleA, "the person talking to ${t.dialogue.roleA}", t.emoji,
                )
            }
    }
}
