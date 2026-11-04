package com.speakeasy.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Activity
import com.speakeasy.app.data.Quests
import com.speakeasy.app.speech.MatchResult
import com.speakeasy.app.speech.SpeechMatch
import com.speakeasy.app.speech.rememberSpeechInput
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.MicButton
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.categoryColors

/** Shadowing practice: hear a phrase, say it, get a word-by-word score. */
@Composable
fun SpeakScreen(topicId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val topic = app.content.topic(topicId) ?: return
    // Phrases, then native expressions, then the learner's own dialogue lines.
    val sentences = remember(topic) {
        (topic.phrases.map { it.en to it.vi } +
            topic.native.map { it.natural to it.vi } +
            topic.dialogue.lines.filter { it.isUser }.map { it.en to it.vi })
            .distinctBy { it.first }
    }
    val (strong, light) = categoryColors(topic.category)
    var index by remember { mutableIntStateOf(0) }
    val best = remember { mutableStateMapOf<Int, MatchResult>() }
    var last by remember(index) { mutableStateOf<MatchResult?>(null) }
    var heardText by remember(index) { mutableStateOf("") }
    var earned by remember { mutableStateOf<Int?>(null) }
    val (en, vi) = sentences[index]

    val listen = rememberSpeechInput { heard ->
        app.progress.count(Quests.SPEAK)
        val r = SpeechMatch.compare(en, heard)
        last = r
        heardText = heard
        if ((best[index]?.score ?: -1) < r.score) best[index] = r
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Luyện nói", subtitle = topic.title, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SoftProgress((index + 1f) / sentences.size, color = strong)
            VSpace(6.dp)
            Text("Câu ${index + 1} / ${sentences.size}", color = Muted)
            VSpace(18.dp)
            AnimatedContent(targetState = index, label = "sentence") { i ->
                val (sEn, sVi) = sentences[i]
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(light).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shown = last
                    if (shown != null && i == index) ScoredText(shown)
                    else Text(sEn, style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center)
                    VSpace(8.dp)
                    Text(sVi, color = Muted, textAlign = TextAlign.Center)
                    VSpace(16.dp)
                    Row {
                        SpeakButton(sEn, size = 48.dp, tint = strong)
                        HSpace(12.dp)
                        SpeakButton(sEn, size = 48.dp, slow = true, tint = strong)
                    }
                }
            }
            VSpace(20.dp)
            val r = last
            if (r != null) {
                Text("⭐".repeat(r.stars).ifEmpty { "☆" }, fontSize = 34.sp)
                Text("${r.score}%", style = MaterialTheme.typography.headlineMedium, color = if (r.stars >= 2) Mint else strong)
                Text(r.feedback, color = Ink, textAlign = TextAlign.Center)
                VSpace(6.dp)
                Text("Máy nghe được: “$heardText”", color = Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            } else {
                Text("Nghe mẫu 🔊 rồi chạm 🎤 và đọc to câu trên.", color = Muted, textAlign = TextAlign.Center)
                Text("Từ xanh = nói đúng, từ đỏ = cần luyện thêm.", color = Muted, textAlign = TextAlign.Center)
            }
            VSpace(20.dp)
        }

        Column(Modifier.padding(20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            MicButton(onClick = { listen(en) }, size = 80.dp)
            VSpace(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { index-- },
                    enabled = index > 0,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("← Trước") }
                Box(Modifier.weight(1f)) {
                    if (index < sentences.lastIndex) BigButton(if (last == null) "Bỏ qua →" else "Tiếp →", onClick = { index++ }, color = strong)
                    else BigButton("Hoàn thành", onClick = {
                        earned = app.progress.complete(topic.id, Activity.SPEAK, bonus = best.values.sumOf { it.stars } / 2)
                    }, color = Mint)
                }
            }
        }
    }

    earned?.let { xp ->
        val avg = if (best.isEmpty()) 0 else best.values.sumOf { it.score } / best.size
        CompleteDialog(
            title = "Bạn nói hay lắm!",
            message = "Đã luyện ${best.size}/${sentences.size} câu · điểm trung bình $avg%.",
            xp = xp,
            onDismiss = { earned = null; onBack() },
        )
    }
}
