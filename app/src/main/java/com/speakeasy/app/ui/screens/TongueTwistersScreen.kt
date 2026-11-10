package com.speakeasy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Pronunciation
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.Twister
import com.speakeasy.app.speech.MatchResult
import com.speakeasy.app.speech.rememberSpeechAlternatives
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.MicButton
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Lavender
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Muted

private val twisterLevels = listOf("easy" to "🟢 Dễ", "medium" to "🟡 Vừa", "hard" to "🔴 Khó")

/** Tongue twisters by level: listen normal or slow, then say it and keep the best score per twister. */
@Composable
fun TongueTwistersScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val p by app.progress.progress.collectAsState()
    val list = app.content.pronunciation.twisters
    var level by rememberSaveable { mutableStateOf("easy") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<MatchResult?>(null) }
    val listen = rememberSpeechAlternatives(onCancel = {}) { heard ->
        val t = list.firstOrNull { it.id == open } ?: return@rememberSpeechAlternatives
        app.progress.count(Quests.SPEAK)
        val r = Pronunciation.bestSentenceScore(t.en, heard)
        result = r
        app.progress.recordTwister(t.id, r.score)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "👅 Câu líu lưỡi", subtitle = "Nói nhanh mà vẫn rõ từng âm", onBack = onBack)
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            twisterLevels.forEach { (id, label) ->
                FilterChip(
                    selected = level == id,
                    onClick = { level = id; open = null; result = null },
                    label = { Text(label) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list.filter { it.level == level }, key = { it.id }) { t ->
                TwisterCard(
                    t,
                    best = p.twisterBest[t.id],
                    open = open == t.id,
                    result = result.takeIf { open == t.id },
                    onToggle = { open = if (open == t.id) null else t.id; result = null },
                    onSpeak = { listen(t.en) },
                )
            }
        }
    }
}

@Composable
private fun TwisterCard(t: Twister, best: Int?, open: Boolean, result: MatchResult?, onToggle: () -> Unit, onSpeak: () -> Unit) {
    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (open) Lavender else Line),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.en, style = MaterialTheme.typography.titleMedium)
                    Text(t.focus, color = Muted, style = MaterialTheme.typography.labelMedium)
                }
                HSpace(10.dp)
                if (best != null) Text("$best", color = scoreColor(best), fontWeight = FontWeight.ExtraBold)
                else Text("Mới", color = Muted, style = MaterialTheme.typography.labelMedium)
            }
            if (open) {
                VSpace(12.dp)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (result != null) {
                        ScoredText(result)
                        Text("${result.score}", color = scoreColor(result.score), fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                    }
                    Text(t.vi, color = Muted, textAlign = TextAlign.Center)
                    VSpace(10.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpeakButton(t.en, size = 52.dp, tint = Lavender)
                        HSpace(12.dp)
                        SpeakButton(t.en, size = 52.dp, slow = true, tint = Lavender)
                        HSpace(16.dp)
                        MicButton(onClick = onSpeak, size = 64.dp)
                    }
                    VSpace(6.dp)
                    Text("Nghe chậm trước, nói chậm cho đúng rồi mới tăng tốc.", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
