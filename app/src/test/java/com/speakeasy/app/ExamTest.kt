package com.speakeasy.app

import com.speakeasy.app.data.AudioLine
import com.speakeasy.app.data.Exam
import com.speakeasy.app.data.ExamItem
import com.speakeasy.app.data.ExamLibrary
import com.speakeasy.app.data.ExamQuestion
import com.speakeasy.app.data.ExamRecord
import com.speakeasy.app.data.ExamSection
import com.speakeasy.app.data.ExamTest
import com.speakeasy.app.data.Exams
import com.speakeasy.app.data.SkillResult
import com.speakeasy.app.data.SkillScore
import com.speakeasy.app.data.TestEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Port of the web tests "exam engine: …" and "built-in TOEIC and IELTS files are valid…". */
class ExamTest {

    private fun q(answer: Int = 0) = ExamQuestion("Q?", listOf("a", "b", "c", "d"), answer, "x")

    private val exam = Exam(
        id = "toeic", title = "TOEIC",
        sections = listOf(
            ExamSection("l", "listening", items = listOf(
                ExamItem("l1", audio = listOf(AudioLine("M", "Hi")), questions = listOf(q(1), q(2))),
                ExamItem("l2", questions = listOf(q())),
            )),
            ExamSection("r", "reading", items = listOf(ExamItem("r1", passage = "Text", questions = listOf(q(3))), ExamItem("r2", questions = listOf(q())))),
            ExamSection("s", "speaking", items = listOf(ExamItem("s1", kind = "speak", prompt = "Say", sample = "Hello"))),
        ),
        test = ExamTest(minutes = 10, pick = mapOf("l" to 1, "r" to 2)),
    )

    @Test
    fun drawsATestInSectionOrderFromQuestionItemsOnly() {
        assertEquals(emptyList<String>(), Exams.problems(exam))
        repeat(5) { seed ->
            val entries = Exams.buildTest(exam, Random(seed))
            assertEquals(listOf("l", "r", "r"), entries.map { it.section.id })
            assertTrue(entries.all { it.item.isQuestionItem })
            assertEquals(2, entries.drop(1).map { it.item.id }.toSet().size)
        }
    }

    @Test
    fun scoresBySkill() {
        val s = exam.sections!!
        val full = listOf(TestEntry(s[0], s[0].items[0]), TestEntry(s[1], s[1].items[0]))
        assertEquals(
            mapOf("listening" to SkillScore(1, 2), "reading" to SkillScore(1, 1)),
            Exams.scoreBySkill(full, mapOf("l1:0" to 1, "l1:1" to 0, "r1:0" to 3)),
        )
        assertEquals(mapOf("listening" to SkillScore(0, 2), "reading" to SkillScore(0, 1)), Exams.scoreBySkill(full, emptyMap()))
    }

    @Test
    fun toeicAndIeltsEstimates() {
        assertEquals(listOf(5, 495, 250, 5), listOf(Exams.toeicEstimate(0, 10), Exams.toeicEstimate(10, 10), Exams.toeicEstimate(5, 10), Exams.toeicEstimate(0, 0)))
        assertEquals(
            listOf(9.0, 7.0, 7.0, 6.0, 0.0),
            listOf(
                Exams.ieltsBand("listening", 40, 40), Exams.ieltsBand("listening", 30, 40), Exams.ieltsBand("reading", 30, 40),
                Exams.ieltsBand("reading", 26, 40), Exams.ieltsBand("listening", 0, 40),
            ),
        )
        assertEquals(7.0, Exams.ieltsBand("listening", 15, 20), 0.0) // a short test is scaled up to 40 questions
        assertEquals(2.0, Exams.ieltsBand("listening", 3, 40), 0.0) // under the table but not zero
        assertEquals(0.0, Exams.ieltsBand("reading", 0, 0), 0.0)
        // Reading and listening tables differ at the same raw score.
        assertEquals(7.5, Exams.ieltsBand("listening", 32, 40), 0.0)
        assertEquals(7.0, Exams.ieltsBand("reading", 32, 40), 0.0)
        assertEquals(listOf(SkillResult("reading", 20, 20, 9.0)), Exams.estimate("ielts", mapOf("reading" to SkillScore(20, 20))))
        assertEquals(listOf(SkillResult("listening", 5, 10, 250.0)), Exams.estimate("toeic", mapOf("listening" to SkillScore(5, 10))))
        assertEquals(listOf("7", "6.5", "495"), listOf(Exams.formatScore(7.0), Exams.formatScore(6.5), Exams.formatScore(495.0)))
    }

    @Test
    fun countsWords() {
        assertEquals(6, Exams.wordCount("It's a well-known fact, isn't it?"))
        assertEquals(0, Exams.wordCount("   "))
        assertEquals(3, Exams.wordCount("I don’t know"))
    }

    @Test
    fun namesEveryProblemInABadFile() {
        val bad = exam.copy(
            sections = listOf(ExamSection("x", "cooking", items = listOf(
                ExamItem("a", questions = listOf(ExamQuestion("Q", listOf("a"), 2))),
                ExamItem("a", kind = "write", prompt = "P"),
            ))),
            test = ExamTest(pick = mapOf("y" to 1)),
        )
        assertEquals(5, Exams.problems(bad).size)
        assertTrue(Exams.problems(null).isNotEmpty())
        assertTrue(Exams.problems(Exam()).isNotEmpty())
        val tooMany = exam.copy(test = ExamTest(pick = mapOf("l" to 3)))
        assertEquals(listOf("test picks 3 from l, which has fewer"), Exams.problems(tooMany))
        assertEquals(listOf("e: no questions"), Exams.problems(exam.copy(sections = listOf(ExamSection("e", "reading", items = listOf(ExamItem("e")))), test = null)))
    }

    @Test
    fun importedJsonParsesOrIsRejected() {
        val parsed = ExamLibrary.parse("""{"id":"mine","title":"Mine","sections":[{"id":"r","skill":"reading","items":[{"id":"i","questions":[{"q":"Q","options":["a","b"],"answer":1}]}]}],"extra":true}""")
        assertEquals(emptyList<String>(), Exams.problems(parsed))
        assertTrue(Exams.problems(ExamLibrary.parse("{}")).isNotEmpty())
        assertTrue(runCatching { ExamLibrary.parse("[1,2]") }.isFailure)
        assertTrue(runCatching { ExamLibrary.parse("not json") }.isFailure)
    }

    @Test
    fun historyKeepsTheLastTwentyAndSurvivesStorage() {
        var history = emptyList<ExamRecord>()
        repeat(25) { history = Exams.addRecord(history, ExamRecord("toeic", it.toLong(), listOf(SkillResult("reading", 1, 2, 250.0)))) }
        assertEquals(20, history.size)
        assertEquals(5L, history.first().day)
        assertEquals(history, Exams.decodeHistory(Exams.encodeHistory(history)))
        assertEquals(emptyList<ExamRecord>(), Exams.decodeHistory(null))
        assertEquals(emptyList<ExamRecord>(), Exams.decodeHistory("garbage"))
    }

    @Test
    fun builtInToeicAndIeltsFilesAreValid() {
        for (id in ExamLibrary.BUILT_IN) {
            val exam = ExamLibrary.parse(File("src/main/assets/exam-$id.json").readText())
            assertEquals(id, exam.id)
            assertEquals(id, emptyList<String>(), Exams.problems(exam))
            assertEquals(id, Exams.SKILLS.keys, exam.sections!!.map { it.skill }.toSet())
            assertTrue(id, Exams.buildTest(exam).size >= 5)
            // Hidden options are only letters, so they must fit A–D.
            assertTrue(id, exam.sections!!.flatMap { it.items }.flatMap { it.questions }.all { it.options.size <= 4 })
        }
    }
}
