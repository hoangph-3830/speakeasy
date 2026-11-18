package com.speakeasy.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The exams of "Luyện thi": the built-in TOEIC and IELTS from assets, then the ones the learner imported.
 * Imported exams are kept in app-private storage only (one JSON file each), never uploaded.
 */
class ExamLibrary(private val context: Context) {

    private val dir = File(context.filesDir, "exams")

    private val builtIn: List<Exam> by lazy {
        BUILT_IN.map { id -> context.assets.open("exam-$id.json").bufferedReader().use { parse(it.readText()) } }
    }

    private val _exams = MutableStateFlow<List<Exam>?>(null)

    /** Every exam, built-in first; null until first loaded. */
    val exams: StateFlow<List<Exam>?> = _exams.asStateFlow()

    /** Loads the list if not loaded yet; call off the main thread. */
    fun load(): List<Exam> = _exams.value ?: refresh()

    fun exam(id: String): Exam? = load().firstOrNull { it.id == id }

    private fun refresh(): List<Exam> {
        val local = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.lastModified() }
            .mapNotNull { f -> runCatching { parse(f.readText()) }.getOrNull()?.takeIf { Exams.problems(it).isEmpty() } }
            .map { it.copy(local = true) }
        return (builtIn + local).also { _exams.value = it }
    }

    /**
     * Checks and saves an imported exam file; returns the saved exam, or throws with a message the learner can read.
     * An imported exam reusing a built-in id gets a fresh one so it never hides TOEIC or IELTS.
     */
    fun import(text: String): Exam {
        val parsed = runCatching { parse(text) }.getOrNull()
        val problems = Exams.problems(parsed)
        require(problems.isEmpty()) { "File đề chưa đúng định dạng: ${problems.take(2).joinToString("; ")}" }
        var exam = parsed!!
        if (exam.id in BUILT_IN) exam = exam.copy(id = "${exam.id}-${System.currentTimeMillis()}")
        dir.mkdirs()
        // Saved re-encoded rather than as given, so the id change above sticks.
        fileOf(exam.id).writeText(json.encodeToString(Exam.serializer(), exam))
        refresh()
        return exam.copy(local = true)
    }

    /** Removes an imported exam from this device; built-in exams cannot be deleted. */
    fun delete(id: String) {
        if (id in BUILT_IN) return
        fileOf(id).delete()
        refresh()
    }

    /** One file per exam, named after a safe form of its id. */
    private fun fileOf(id: String) = File(dir, id.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("") + ".json")

    companion object {
        val BUILT_IN = listOf("toeic", "ielts")
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        /** Parses exam JSON; throws on text that is not an exam-shaped JSON object. */
        fun parse(text: String): Exam = json.decodeFromString(Exam.serializer(), text)
    }
}
