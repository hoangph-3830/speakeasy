package com.speakeasy.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.ExamSection
import com.speakeasy.app.data.Exams
import com.speakeasy.app.data.Pronunciation
import com.speakeasy.app.data.Quests
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sky

/** Practice one exam section, one item per page, with the answer and explanation shown right away. */
@Composable
fun ExamPracticeScreen(examId: String, sectionId: String, onBack: () -> Unit) {
    val section = rememberExams()?.firstOrNull { it.id == examId }?.section(sectionId)
    if (section == null || section.items.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Luyện thi", onBack = onBack)
            Text("Đang tải…", color = Muted, modifier = Modifier.padding(20.dp))
        }
        return
    }
    SectionPractice(section, onBack)
}

@Composable
private fun SectionPractice(section: ExamSection, onBack: () -> Unit) {
    val app = LocalApp.current
    val player = rememberLinePlayer()
    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<String, Int>() }
    val tasks = remember { mutableMapOf<String, TaskState>() }
    var finished by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val item = section.items[index]
    val last = index == section.items.lastIndex

    fun finish() {
        val items = section.items.filter { it.isQuestionItem }
        val total = items.sumOf { it.questions.size }
        val right = items.sumOf { x -> x.questions.indices.count { i -> answers[Exams.key(x, i)] == x.questions[i].answer } }
        var message = "Bạn đã làm xong ${section.items.size} bài."
        if (total > 0) {
            val score = Pronunciation.percent(right, total)
            app.progress.recordExamBest(section.id, score)
            message = "Đúng $right/$total câu ($score%)."
        }
        val xp = maxOf(5, right)
        app.progress.addXp(xp)
        finished = message to xp
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(section.title, "Bài ${index + 1}/${section.items.size}", onBack = onBack)
        SoftProgress((index + 1f) / section.items.size, modifier = Modifier.padding(horizontal = 20.dp), color = Sky)
        Column(
            Modifier.weight(1f).verticalScroll(remember(index) { ScrollState(0) }).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item.title?.let { Text(it, color = Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
            when (item.kind) {
                "speak" -> SpeakTask(item, tasks.getOrPut(item.id) { TaskState() })
                "write" -> WriteTask(item, tasks.getOrPut(item.id) { TaskState() })
                else -> {
                    val done = item.questions.indices.all { answers[Exams.key(item, it)] != null }
                    QuestionItem(
                        item, answers,
                        reveal = { i -> answers[Exams.key(item, i)] != null },
                        scriptShown = done,
                        player = player,
                        onPlayed = { app.progress.count(Quests.LISTEN) },
                        onChoose = { key, k -> if (answers[key] == null) answers[key] = k },
                    )
                }
            }
        }
        Row(Modifier.padding(20.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (index > 0) OutlinedButton(
                onClick = { player.stop(); index-- },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("← Bài trước") }
            Box(Modifier.weight(1f)) {
                BigButton(
                    if (last) "Hoàn thành" else "Bài tiếp →",
                    onClick = { player.stop(); if (last) finish() else index++ },
                    color = if (last) Mint else Sky,
                )
            }
        }
    }

    finished?.let { (message, xp) ->
        CompleteDialog(
            title = "Xong phần luyện! 🎓",
            message = message,
            xp = xp,
            onDismiss = { finished = null; onBack() },
            onAgain = { finished = null; index = 0; answers.clear() },
        )
    }
}
