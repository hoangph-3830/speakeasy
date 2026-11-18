package com.speakeasy.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speakeasy.app.data.ExamItem
import com.speakeasy.app.data.Exams
import com.speakeasy.app.data.Pronunciation
import com.speakeasy.app.speech.MatchResult
import com.speakeasy.app.speech.rememberSpeechAlternatives
import com.speakeasy.app.ui.components.MicButton
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.SkyLight

/** What the learner did on one speaking or writing task; kept while they move between pages. */
class TaskState {
    var heard by mutableStateOf<String?>(null)
    var result by mutableStateOf<MatchResult?>(null)
    var draft by mutableStateOf("")
    var sample by mutableStateOf(false)
    val checked = mutableStateListOf<Int>()
}

@Composable
private fun Prompt(item: ExamItem) {
    Text(item.prompt.orEmpty(), color = Ink, fontWeight = FontWeight.SemiBold)
    item.promptVi?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun SampleToggle(state: TaskState, label: String) {
    TextButton(onClick = { state.sample = !state.sample }) {
        Text(if (state.sample) "▴ Ẩn bài mẫu" else label, fontWeight = FontWeight.Bold)
    }
}

/**
 * Speaking task: say the answer and see what was heard. A read-aloud task is scored against its sample
 * with the sentence scorer; other tasks show the words heard and their count. Then the sample and tips.
 */
@Composable
fun SpeakTask(item: ExamItem, state: TaskState) {
    val sample = item.sample.orEmpty()
    val listen = rememberSpeechAlternatives(onCancel = {}) { alts ->
        state.heard = alts.first()
        if (item.readAloud) state.result = Pronunciation.bestSentenceScore(sample, alts)
    }
    ExamCard {
        Prompt(item)
        Text(
            "⏱️ Chuẩn bị ${item.prepareSeconds} giây · nói ${item.speakSeconds} giây. Bấm 🎤 rồi nói liền một mạch.",
            color = Muted, style = MaterialTheme.typography.bodySmall,
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MicButton(onClick = { listen(if (item.readAloud) sample else item.prompt.orEmpty().lineSequence().first()) })
        }
        val heard = state.heard
        val result = state.result
        if (heard != null) ExamCard(background = SkyLight) {
            if (item.readAloud && result != null) {
                ScoredText(result)
                Text("${result.score}%", style = MaterialTheme.typography.headlineSmall, color = Ink)
            } else {
                Text("Máy nghe được (${Exams.wordCount(heard)} từ):", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(heard, color = Ink)
            }
        }
        SampleToggle(state, "📄 Xem bài mẫu & mẹo")
        if (state.sample) ExamCard(background = MintLight) {
            SpeakButton(sample, long = true, tint = Mint)
            Text(sample, color = Ink)
            item.tips.forEach { Text("✅ $it", color = Ink, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Writing task: a draft box with a live word count, then the model answer and a self-check list. */
@Composable
fun WriteTask(item: ExamItem, state: TaskState) {
    val sample = item.sample.orEmpty()
    ExamCard {
        Prompt(item)
        OutlinedTextField(
            value = state.draft,
            onValueChange = { state.draft = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 8,
            placeholder = { Text("Viết bài của bạn ở đây…") },
        )
        val words = remember(state.draft) { Exams.wordCount(state.draft) }
        Text(
            "$words / tối thiểu ${item.minWords} từ",
            color = if (words >= item.minWords) Mint else Muted, style = MaterialTheme.typography.bodySmall,
        )
        SampleToggle(state, "📄 Xem bài mẫu & tự chấm")
        if (state.sample) ExamCard(background = MintLight) {
            Text("Bài mẫu (${Exams.wordCount(sample)} từ)", color = Muted, style = MaterialTheme.typography.bodySmall)
            sample.split('\n').filter { it.isNotBlank() }.forEach { Text(it, color = Ink) }
            if (item.checklist.isNotEmpty()) {
                Text("Tự kiểm tra bài của bạn", color = Ink, fontWeight = FontWeight.SemiBold)
                item.checklist.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Checkbox(checked = i in state.checked, onCheckedChange = { on -> if (on) state.checked += i else state.checked -= i })
                        Text(c, color = Ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
