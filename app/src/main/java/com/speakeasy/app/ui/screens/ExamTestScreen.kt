package com.speakeasy.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Exam
import com.speakeasy.app.data.Exams
import com.speakeasy.app.data.SkillResult
import com.speakeasy.app.data.TestEntry
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Muted
import kotlinx.coroutines.delay

/**
 * Timed practice test ("Luyện đề"): a fresh draw of listening and reading items, no feedback until
 * submitting or time-out, then an estimated TOEIC score or IELTS band per skill and a full review.
 */
@Composable
fun ExamTestScreen(examId: String, onBack: () -> Unit) {
    val exam = rememberExams()?.firstOrNull { it.id == examId }
    if (exam?.test == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Luyện đề", onBack = onBack)
            Text("Đang tải…", color = Muted, modifier = Modifier.padding(20.dp))
        }
        return
    }
    TimedTest(exam, onBack)
}

@Composable
private fun TimedTest(exam: Exam, onBack: () -> Unit) {
    val app = LocalApp.current
    val test = exam.test!!
    val player = rememberLinePlayer()
    val list = rememberLazyListState()
    var round by remember { mutableStateOf(0) }
    val entries = remember(round) { Exams.buildTest(exam) }
    val answers = remember(round) { mutableStateMapOf<String, Int>() }
    var results by remember(round) { mutableStateOf<List<SkillResult>?>(null) }
    val endAt = remember(round) { System.currentTimeMillis() + test.minutes * 60_000L }
    var left by remember(round) { mutableLongStateOf(test.minutes * 60_000L) }
    var confirm by remember { mutableStateOf(false) }
    val total = entries.sumOf { it.item.questions.size }
    val submitted = results != null

    fun submit() {
        player.stop()
        val r = Exams.estimate(exam.id, Exams.scoreBySkill(entries, answers))
        results = r
        app.progress.recordExamTest(exam.id, r)
        app.progress.addXp(10 + r.sumOf { it.correct })
    }

    LaunchedEffect(round, submitted) {
        while (!submitted) {
            left = (endAt - System.currentTimeMillis()).coerceAtLeast(0)
            if (left == 0L) { submit(); break }
            delay(1_000)
        }
    }
    LaunchedEffect(submitted) { if (submitted) list.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            test.title ?: "Luyện đề",
            if (submitted) "Xem lại bài làm" else "$total câu · ${test.minutes} phút",
            onBack = onBack,
        ) {
            if (!submitted) Text(
                "⏱️ ${left / 60_000}:${(left / 1000 % 60).toString().padStart(2, '0')}",
                color = Coral, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp).testTag("text_exam_clock"),
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            state = list,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val r = results
                if (r != null) ResultCard(exam.id, r, onAgain = { round++ })
                else Text("Bài nghe chỉ nên bấm nghe một lần, như khi thi thật. Nộp bài khi xong hoặc khi hết giờ.", color = Muted)
            }
            itemsIndexed(entries, key = { _, e -> "$round:${e.item.id}" }) { n, e -> EntryCard(n, e, answers, submitted, player) }
            if (!submitted) item {
                BigButton("Nộp bài (${answers.size}/$total)", onClick = {
                    if (answers.size < total) confirm = true else submit()
                }, color = Coral, modifier = Modifier.testTag("btn_exam_submit"))
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Nộp bài?") },
        text = { Text("Còn ${total - answers.size} câu chưa làm.") },
        confirmButton = {
            TextButton(onClick = { confirm = false; if (!submitted) submit() }) { Text("Nộp luôn", color = Coral, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Làm tiếp") } },
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun EntryCard(n: Int, e: TestEntry, answers: MutableMap<String, Int>, submitted: Boolean, player: LinePlayer) {
    ExamCard {
        Text("${n + 1}. ${e.section.title}", color = Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        QuestionItem(
            e.item, answers,
            reveal = { submitted },
            scriptShown = submitted,
            player = player,
            onChoose = { key, k -> if (!submitted) answers[key] = k },
        )
    }
}

/** Estimated result per skill: TOEIC 5–495 each plus the total out of 990, or an IELTS band. */
@Composable
private fun ResultCard(examId: String, results: List<SkillResult>, onAgain: () -> Unit) {
    ExamCard {
        Text("Kết quả ước lượng", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            results.forEach { r ->
                val skill = Exams.SKILLS[r.skill]
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${skill?.emoji.orEmpty()} ${skill?.title ?: r.skill}", color = Muted)
                    Text(
                        if (examId == "ielts") "Band ${Exams.formatScore(r.score)}" else Exams.formatScore(r.score),
                        color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    )
                    Text("${r.correct}/${r.total} câu đúng", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (examId == "toeic") Text("Tổng ước lượng: ${results.sumOf { it.score.toInt() }} / 990", color = Ink, fontWeight = FontWeight.SemiBold)
        Text(
            "Đây là quy đổi gần đúng từ đề rút gọn, chỉ để theo dõi tiến bộ, không thay cho điểm thi chính thức.",
            color = Muted, style = MaterialTheme.typography.bodySmall,
        )
        BigButton("Làm đề mới", onClick = onAgain, color = Coral, modifier = Modifier.testTag("btn_exam_again"))
    }
}
