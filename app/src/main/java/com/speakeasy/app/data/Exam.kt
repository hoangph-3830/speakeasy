package com.speakeasy.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A practice exam (TOEIC, IELTS or one the learner imported), the shape of assets/exam-*.json.
 * Every field has a default so a broken imported file still parses and [Exams.problems] can name what is wrong.
 */
@Serializable
data class Exam(
    val id: String = "",
    val title: String = "",
    val emoji: String = "📝",
    val about: String = "",
    val sections: List<ExamSection>? = null,
    val test: ExamTest? = null,
    /** True for an exam the learner imported; those can be deleted. */
    @Transient val local: Boolean = false,
) {
    fun section(id: String): ExamSection? = sections.orEmpty().firstOrNull { it.id == id }
}

@Serializable
data class ExamSection(
    val id: String = "",
    val skill: String = "",
    val title: String = "",
    val about: String = "",
    val items: List<ExamItem> = emptyList(),
) {
    val questionCount: Int get() = items.filter { it.isQuestionItem }.sumOf { it.questions.size }
}

/** Listening and reading items carry [questions]; speaking and writing items ([kind] "speak"/"write") are open tasks. */
@Serializable
data class ExamItem(
    val id: String = "",
    val title: String? = null,
    val kind: String? = null,
    /** TOEIC Part 2: only the option letters show until the answer is revealed. */
    val hideOptions: Boolean = false,
    val audio: List<AudioLine> = emptyList(),
    val passage: String? = null,
    val questions: List<ExamQuestion> = emptyList(),
    val prompt: String? = null,
    val promptVi: String? = null,
    val sample: String? = null,
    val tips: List<String> = emptyList(),
    val checklist: List<String> = emptyList(),
    val minWords: Int = 0,
    val prepareSeconds: Int = 0,
    val speakSeconds: Int = 45,
    val readAloud: Boolean = false,
) {
    val isQuestionItem: Boolean get() = kind == null
}

/** One spoken line; [who] is "M" (man), "W" (woman) or "N" (narrator). */
@Serializable
data class AudioLine(val who: String = "N", val en: String = "")

@Serializable
data class ExamQuestion(val q: String = "", val options: List<String> = emptyList(), val answer: Int = -1, val explain: String? = null)

/** The timed test: [pick] items drawn from each section id, answered within [minutes]. */
@Serializable
data class ExamTest(val title: String? = null, val minutes: Int = 0, val pick: Map<String, Int> = emptyMap())

data class Skill(val title: String, val emoji: String)

data class TestEntry(val section: ExamSection, val item: ExamItem)

data class SkillScore(val correct: Int, val total: Int)

/** A finished test's result for one skill: TOEIC 5–495 or an IELTS band. */
@Serializable
data class SkillResult(val skill: String, val correct: Int, val total: Int, val score: Double)

/** One timed test kept in progress history. */
@Serializable
data class ExamRecord(val exam: String, val day: Long, val results: List<SkillResult>)

/** Rules shared by exam practice and the timed test, mirroring web/js/exam.js. */
object Exams {
    val SKILLS = linkedMapOf(
        "listening" to Skill("Nghe", "🎧"),
        "reading" to Skill("Đọc", "📖"),
        "speaking" to Skill("Nói", "🎤"),
        "writing" to Skill("Viết", "✍️"),
    )
    const val HISTORY_SIZE = 20

    /** The items of a timed test: `pick` items drawn at random from each section, in section order. */
    fun buildTest(exam: Exam, random: kotlin.random.Random = kotlin.random.Random): List<TestEntry> =
        exam.sections.orEmpty().flatMap { s ->
            s.items.filter { it.isQuestionItem }.shuffled(random).take(exam.test?.pick?.get(s.id) ?: 0).map { TestEntry(s, it) }
        }

    /** Key of a question in an answers map: "itemId:questionIndex". */
    fun key(item: ExamItem, index: Int) = "${item.id}:$index"

    /** Correct answers per skill, in the order skills first appear. */
    fun scoreBySkill(entries: List<TestEntry>, answers: Map<String, Int>): Map<String, SkillScore> {
        val out = LinkedHashMap<String, SkillScore>()
        for ((section, item) in entries) item.questions.forEachIndexed { i, q ->
            val s = out[section.skill] ?: SkillScore(0, 0)
            out[section.skill] = SkillScore(s.correct + if (answers[key(item, i)] == q.answer) 1 else 0, s.total + 1)
        }
        return out
    }

    /** TOEIC listening or reading, 5–495 in steps of 5, scaled linearly from the share answered right. */
    fun toeicEstimate(correct: Int, total: Int): Int {
        if (total == 0) return 5
        return (Math.round((5 + 490.0 * correct / total) / 5) * 5).toInt().coerceIn(5, 495)
    }

    // Raw score out of 40 → band, from the commonly published IELTS conversion tables.
    private val LISTENING_BANDS = listOf(
        39 to 9.0, 37 to 8.5, 35 to 8.0, 32 to 7.5, 30 to 7.0, 26 to 6.5, 23 to 6.0,
        18 to 5.5, 16 to 5.0, 13 to 4.5, 10 to 4.0, 8 to 3.5, 6 to 3.0, 4 to 2.5,
    )
    private val READING_BANDS = listOf(
        39 to 9.0, 37 to 8.5, 35 to 8.0, 33 to 7.5, 30 to 7.0, 27 to 6.5, 23 to 6.0,
        19 to 5.5, 15 to 5.0, 13 to 4.5, 10 to 4.0, 8 to 3.5, 6 to 3.0, 4 to 2.5,
    )

    /** IELTS band for a part, scaling a short test up to the real 40 questions first. */
    fun ieltsBand(skill: String, correct: Int, total: Int): Double {
        if (total == 0) return 0.0
        val raw40 = Math.round(40.0 * correct / total).toInt()
        val table = if (skill == "reading") READING_BANDS else LISTENING_BANDS
        return table.firstOrNull { raw40 >= it.first }?.second ?: if (raw40 > 0) 2.0 else 0.0
    }

    /** Estimated result of a finished test, per skill, in the exam's own scale. */
    fun estimate(examId: String, bySkill: Map<String, SkillScore>): List<SkillResult> = bySkill.map { (skill, s) ->
        val score = if (examId == "ielts") ieltsBand(skill, s.correct, s.total) else toeicEstimate(s.correct, s.total).toDouble()
        SkillResult(skill, s.correct, s.total, score)
    }

    /** "7" for whole scores, "6.5" for half bands. */
    fun formatScore(score: Double): String = if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()

    private val WORD = Regex("[A-Za-z0-9'’-]+")

    fun wordCount(text: String): Int = WORD.findAll(text.trim()).count()

    /** Problems in an exam file, empty when it can be used. Also guards files learners import. */
    fun problems(exam: Exam?): List<String> {
        if (exam == null) return listOf("not an object")
        val sections = exam.sections
        if (exam.id.isBlank() || exam.title.isBlank() || sections == null) return listOf("needs id, title and sections")
        val problems = mutableListOf<String>()
        val ids = mutableSetOf<String>()
        for (s in sections) {
            if (s.skill !in SKILLS) problems += "${s.id}: unknown skill ${s.skill}"
            for (item in s.items) {
                if (!ids.add(item.id)) problems += "duplicate item id ${item.id}"
                if (item.kind == "speak" || item.kind == "write") {
                    if (item.prompt.isNullOrEmpty() || item.sample.isNullOrEmpty()) problems += "${item.id}: needs prompt and sample"
                    continue
                }
                if (item.questions.isEmpty()) problems += "${item.id}: no questions"
                for (q in item.questions)
                    if (q.q.isEmpty() || q.answer !in q.options.indices) problems += "${item.id}: bad question \"${q.q}\""
            }
        }
        for ((id, n) in exam.test?.pick.orEmpty()) {
            val s = sections.firstOrNull { it.id == id }
            if (s == null) problems += "test picks unknown section $id"
            else if (n > s.items.count { it.isQuestionItem }) problems += "test picks $n from $id, which has fewer"
        }
        return problems
    }

    /** Keeps the newest [HISTORY_SIZE] test records. */
    fun addRecord(history: List<ExamRecord>, record: ExamRecord) = (history + record).takeLast(HISTORY_SIZE)

    private val historySerializer = ListSerializer(ExamRecord.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeHistory(history: List<ExamRecord>): String = json.encodeToString(historySerializer, history)

    /** Stored history back as a list; missing or unreadable data is an empty history. */
    fun decodeHistory(text: String?): List<ExamRecord> =
        text?.let { runCatching { json.decodeFromString(historySerializer, it) }.getOrNull() }.orEmpty()
}
