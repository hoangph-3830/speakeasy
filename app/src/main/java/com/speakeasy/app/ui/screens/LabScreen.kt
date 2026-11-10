package com.speakeasy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.speakeasy.app.data.Pronunciation
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.SoundLesson
import com.speakeasy.app.data.WordVerdict
import com.speakeasy.app.speech.MatchResult
import com.speakeasy.app.speech.rememberSpeechAlternatives
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.EmojiBadge
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.MicButton
import com.speakeasy.app.ui.components.Ring
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Lavender
import com.speakeasy.app.ui.theme.LavenderLight
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Peach
import com.speakeasy.app.ui.theme.PeachLight
import com.speakeasy.app.ui.theme.SunLight
import kotlin.random.Random

/** Colour for a 0-100 pronunciation score, ELSA-style: green / amber / red. */
fun scoreColor(score: Int): Color = when {
    score >= 80 -> Mint
    score >= 50 -> Peach
    else -> Coral
}

@Composable
fun LabHubScreen(onBack: () -> Unit, openLesson: (String) -> Unit, openIpa: () -> Unit, openTwisters: () -> Unit) {
    val app = LocalApp.current
    val p by app.progress.progress.collectAsState()
    val lessons = app.content.pronunciation.lessons

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Phòng phát âm", subtitle = "Sửa đúng những âm người Việt hay sai", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LavenderLight).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val score = p.speakingScore
                    Ring((score ?: 0) / 100f, 76.dp, if (score == null) Lavender else scoreColor(score)) {
                        Text(score?.toString() ?: "–", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Ink)
                    }
                    HSpace(16.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Điểm phát âm", style = MaterialTheme.typography.titleLarge, color = Ink)
                        Text(
                            if (score == null) "Làm một bài để biết điểm của bạn."
                            else "Trung bình ${p.soundScores.size}/${lessons.size} bài. Luyện âm có điểm đỏ trước nhé!",
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        Triple("🔤 Bảng phiên âm IPA", LavenderLight, openIpa),
                        Triple("👅 Câu líu lưỡi", PeachLight, openTwisters),
                    ).forEach { (label, bg, open) ->
                        Text(
                            label,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = open)
                                .padding(vertical = 16.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                        )
                    }
                }
            }
            items(lessons, key = { it.id }) { l ->
                val score = p.soundScores[l.id]
                Card(
                    onClick = { openLesson(l.id) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Line),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(l.emoji, LavenderLight, 50.dp)
                        HSpace(14.dp)
                        Column(Modifier.weight(1f)) {
                            Text(l.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (l.pairs.isNotEmpty()) "${l.ipa} · ${l.pairs.joinToString(", ") { "${it[0]}/${it[1]}" }.take(34)}…"
                                else "${l.sentences.size} câu nói nhanh như người bản xứ",
                                color = Muted,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            if (score != null) {
                                VSpace(6.dp)
                                SoftProgress(score / 100f, color = scoreColor(score))
                            }
                        }
                        HSpace(10.dp)
                        Text(score?.let { "$it" } ?: "Mới", color = score?.let(::scoreColor) ?: Lavender, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

private enum class LabStep { TIP, LISTEN, WORDS, SENTENCES }

@Composable
fun LabLessonScreen(lessonId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val lesson = app.content.pronunciation.lessons.firstOrNull { it.id == lessonId } ?: return
    val steps = remember(lesson) {
        LabStep.entries.filter { lesson.pairs.isNotEmpty() || (it != LabStep.LISTEN && it != LabStep.WORDS) }
    }
    var step by remember { mutableIntStateOf(0) }
    // Score parts, each 0-100; averaged when the lesson ends.
    val parts = remember { mutableListOf<Int>() }
    var earned by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun next(partScore: Int?) {
        partScore?.let { parts += it }
        if (step < steps.lastIndex) step++
        else {
            val score = if (parts.isEmpty()) 0 else parts.average().toInt()
            app.progress.recordSound(lesson.id, score)
            val xp = 10 + score / 10
            app.progress.addXp(xp)
            earned = score to xp
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = lesson.title, subtitle = "Bước ${step + 1}/${steps.size}", onBack = onBack)
        Column(Modifier.padding(horizontal = 20.dp)) { SoftProgress((step + 1f) / steps.size, color = Lavender) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            when (steps[step]) {
                LabStep.TIP -> TipStep(lesson, onNext = { next(null) })
                LabStep.LISTEN -> ListenStep(lesson, onDone = { next(it) })
                LabStep.WORDS -> WordsStep(lesson, onDone = { next(it) })
                LabStep.SENTENCES -> SentencesStep(lesson, onDone = { next(it) })
            }
        }
    }

    earned?.let { (score, xp) ->
        CompleteDialog(
            title = if (score >= 80) "Phát âm chuẩn lắm! 🎯" else "Tiến bộ rồi đó!",
            message = "Điểm bài “${lesson.title}”: $score/100. Luyện lại mỗi ngày để giữ điểm xanh.",
            xp = xp,
            onDismiss = { earned = null; onBack() },
        )
    }
}

@Composable
private fun TipStep(lesson: SoundLesson, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LavenderLight).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(lesson.emoji, fontSize = 48.sp)
            Text(lesson.ipa, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Lavender)
        }
        Text("👄 Cách đặt miệng", style = MaterialTheme.typography.titleMedium)
        Text(lesson.tip, color = Ink, style = MaterialTheme.typography.bodyLarge)
        if (lesson.pairs.isNotEmpty()) {
            Text("Nghe sự khác nhau", style = MaterialTheme.typography.titleMedium)
            lesson.pairs.take(3).forEach { (a, b) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a, fontWeight = FontWeight.Bold, color = Mint, modifier = Modifier.weight(1f))
                    SpeakButton(a, tint = Mint)
                    HSpace(20.dp)
                    Text(b, color = Muted, modifier = Modifier.weight(1f))
                    SpeakButton(b, tint = Muted)
                }
            }
        }
        BigButton("Bắt đầu luyện →", onClick = onNext, color = Lavender)
    }
}

/** Minimal-pair discrimination: hear one of two look-alike words, pick which. */
@Composable
private fun ListenStep(lesson: SoundLesson, onDone: (Int) -> Unit) {
    val app = LocalApp.current
    val rounds = remember(lesson) { List(6) { lesson.pairs.random() to Random.nextInt(2) } }
    var i by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var picked by remember(i) { mutableStateOf<Int?>(null) }
    val (pair, answer) = rounds[i]
    LaunchedEffect(i) { app.speaker.say(pair[answer]) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎧 Bạn nghe thấy từ nào?", style = MaterialTheme.typography.titleLarge)
        Text("Câu ${i + 1}/${rounds.size}", color = Muted)
        Row {
            SpeakButton(pair[answer], tint = Lavender, size = 64.dp)
            HSpace(16.dp)
            SpeakButton(pair[answer], tint = Lavender, size = 64.dp, slow = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEachIndexed { k, w ->
                val bg = when {
                    picked == null -> Color.White
                    k == answer -> MintLight
                    k == picked -> Coral.copy(alpha = 0.12f)
                    else -> Color.White
                }
                Text(
                    w,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .border(2.dp, if (picked != null && k == answer) Mint else Line, RoundedCornerShape(20.dp))
                        .clickable(enabled = picked == null) {
                            picked = k
                            if (k == answer) correct++
                        }
                        .padding(20.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        picked?.let { k ->
            Text(
                if (k == answer) "🎉 Đúng rồi! Tai bạn tinh lắm." else "😅 Đó là “${pair[answer]}”. Nghe lại cả hai từ nhé.",
                color = if (k == answer) Mint else Coral,
                fontWeight = FontWeight.SemiBold,
            )
            BigButton(if (i < rounds.lastIndex) "Tiếp →" else "Sang phần nói →", onClick = {
                if (i < rounds.lastIndex) i++
                else {
                    app.progress.count(Quests.LISTEN)
                    onDone(Pronunciation.percent(correct, rounds.size))
                }
            }, color = Lavender)
        }
    }
}

/** Say the target word of each pair; the recognizer acts as the native listener. */
@Composable
private fun WordsStep(lesson: SoundLesson, onDone: (Int) -> Unit) {
    val app = LocalApp.current
    var i by remember { mutableIntStateOf(0) }
    var clear by remember { mutableIntStateOf(0) }
    var verdict by remember(i) { mutableStateOf<WordVerdict?>(null) }
    var gotIt by remember(i) { mutableStateOf(false) }
    val (target, lookAlike) = lesson.pairs[i]
    val listen = rememberSpeechAlternatives(onCancel = {}) { heard ->
        app.progress.count(Quests.SPEAK)
        val v = Pronunciation.judgeWord(target, lookAlike, heard)
        verdict = v
        if (v == WordVerdict.Clear && !gotIt) { gotIt = true; clear++ }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎤 Nói từ này", style = MaterialTheme.typography.titleLarge)
        Text("Từ ${i + 1}/${lesson.pairs.size}", color = Muted)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LavenderLight).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(target, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Text("đừng để thành “$lookAlike”", color = Muted)
            VSpace(12.dp)
            Row {
                SpeakButton(target, tint = Lavender, size = 52.dp)
                HSpace(12.dp)
                SpeakButton(target, tint = Lavender, size = 52.dp, slow = true)
            }
        }
        MicButton(onClick = { listen("Say: $target") }, size = 80.dp)
        when (val v = verdict) {
            null -> Text("Nghe mẫu rồi chạm 🎤 và nói rõ từ này.", color = Muted)
            WordVerdict.Clear -> Text("✅ Chuẩn! Người bản xứ sẽ nghe đúng “$target”.", color = Mint, fontWeight = FontWeight.Bold)
            is WordVerdict.Confused -> Text(
                "❌ Nghe thành “${v.heardAs}” rồi. Xem lại mẹo đặt miệng và thử lại.",
                color = Coral, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            is WordVerdict.Unclear -> Text(
                "🤔 Máy nghe được “${v.heard}”. Nói chậm và rõ hơn nhé.",
                color = Peach, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
        }
        BigButton(
            when {
                i < lesson.pairs.lastIndex && verdict == null -> "Bỏ qua →"
                i < lesson.pairs.lastIndex -> "Tiếp →"
                else -> "Sang phần câu →"
            },
            onClick = { if (i < lesson.pairs.lastIndex) i++ else onDone(Pronunciation.percent(clear, lesson.pairs.size)) },
            color = Lavender,
        )
    }
}

@Composable
private fun SentencesStep(lesson: SoundLesson, onDone: (Int) -> Unit) {
    val app = LocalApp.current
    var i by remember { mutableIntStateOf(0) }
    val best = remember { mutableMapOf<Int, Int>() }
    var result by remember(i) { mutableStateOf<MatchResult?>(null) }
    val s = lesson.sentences[i]
    val listen = rememberSpeechAlternatives(onCancel = {}) { heard ->
        app.progress.count(Quests.SPEAK)
        val r = Pronunciation.bestSentenceScore(s.en, heard)
        result = r
        if ((best[i] ?: -1) < r.score) best[i] = r.score
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🗣️ Nói cả câu", style = MaterialTheme.typography.titleLarge)
        Text("Câu ${i + 1}/${lesson.sentences.size}", color = Muted)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LavenderLight).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val r = result
            if (r != null) ScoredText(r)
            else Text(s.en, style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center)
            if (s.sounds != null) {
                VSpace(8.dp)
                Text(
                    "🔊 Nghe như: ${s.sounds}",
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(SunLight).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            VSpace(6.dp)
            Text(s.vi, color = Muted, textAlign = TextAlign.Center)
            VSpace(12.dp)
            Row {
                SpeakButton(s.en, tint = Lavender, size = 52.dp)
                HSpace(12.dp)
                SpeakButton(s.en, tint = Lavender, size = 52.dp, slow = true)
            }
        }
        MicButton(onClick = { listen(s.en) }, size = 80.dp)
        result?.let { r ->
            Text("${r.score}", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = scoreColor(r.score))
            Text(r.feedback, color = Ink, textAlign = TextAlign.Center)
        } ?: Text("Nghe mẫu, chú ý cách nối âm và nhấn giọng, rồi nói theo.", color = Muted, textAlign = TextAlign.Center)
        BigButton(
            if (i < lesson.sentences.lastIndex) (if (result == null) "Bỏ qua →" else "Tiếp →") else "Hoàn thành",
            onClick = {
                if (i < lesson.sentences.lastIndex) i++
                else onDone(lesson.sentences.indices.sumOf { best[it] ?: 0 } / lesson.sentences.size)
            },
            color = if (i < lesson.sentences.lastIndex) Lavender else Mint,
        )
    }
}
