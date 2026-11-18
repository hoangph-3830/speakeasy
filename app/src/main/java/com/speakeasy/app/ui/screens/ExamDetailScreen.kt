package com.speakeasy.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.speakeasy.app.data.Exams
import com.speakeasy.app.ui.components.SectionTitle
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.CoralLight
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Peach

/** One exam: the timed test card, then its sections grouped by skill with the best practice %. */
@Composable
fun ExamDetailScreen(examId: String, onBack: () -> Unit, openTest: () -> Unit, openSection: (String) -> Unit) {
    val app = LocalApp.current
    val progress by app.progress.progress.collectAsState()
    val exams = rememberExams()
    val exam = exams?.firstOrNull { it.id == examId }
    var confirmDelete by remember { mutableStateOf(false) }
    if (exam == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Luyện thi", onBack = onBack)
            Text(if (exams == null) "Đang tải…" else "Không tìm thấy đề này.", color = Muted, modifier = Modifier.padding(20.dp))
        }
        return
    }
    val sections = exam.sections.orEmpty()
    val last = progress.examHistory.lastOrNull { it.exam == exam.id }
    Column(Modifier.fillMaxSize()) {
        TopBar("${exam.emoji} ${exam.title}", exam.about.ifBlank { null }, onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            exam.test?.let { test ->
                item {
                    Card(
                        onClick = openTest,
                        modifier = Modifier.fillMaxWidth().testTag("card_exam_test"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CoralLight),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("LUYỆN ĐỀ CÓ BẤM GIỜ · ${test.minutes} PHÚT", color = Coral, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text(test.title ?: "Đề luyện", color = Ink, fontWeight = FontWeight.Bold)
                            Text(
                                last?.results?.joinToString(" · ", prefix = "Lần gần nhất: ") { "${Exams.SKILLS[it.skill]?.title ?: it.skill} ${Exams.formatScore(it.score)}" }
                                    ?: "Mỗi lần làm là một đề mới, chấm điểm và quy đổi ước lượng.",
                                color = Muted, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Exams.SKILLS.forEach { (skill, s) ->
                val ofSkill = sections.filter { it.skill == skill }
                if (ofSkill.isEmpty()) return@forEach
                item(key = "skill:$skill") { SectionTitle("${s.emoji} ${s.title}") }
                items(ofSkill, key = { it.id }) { x ->
                    val best = progress.examBest[x.id]
                    Card(
                        onClick = { openSection(x.id) },
                        modifier = Modifier.fillMaxWidth().testTag("section_${x.id}"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(x.title, color = Ink, fontWeight = FontWeight.Bold)
                                if (x.about.isNotBlank()) Text(x.about, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                                Text(if (x.questionCount > 0) "${x.questionCount} câu hỏi" else "${x.items.size} bài", color = Muted, style = MaterialTheme.typography.labelSmall)
                                if (best != null) SoftProgress(best / 100f, color = if (best >= 80) Mint else if (best >= 50) Peach else Coral)
                            }
                            Text(if (best != null) "$best%" else "→", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
            if (exam.local) item {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("btn_exam_delete")) {
                    Text("Xoá đề này khỏi máy", color = Coral)
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Xoá đề này?") },
        text = { Text("“${exam.title}” sẽ bị xoá khỏi máy này.") },
        confirmButton = {
            TextButton(onClick = { confirmDelete = false; app.content.exams.delete(exam.id); onBack() }) { Text("Xoá", color = Coral, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Huỷ") } },
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
    )
}
