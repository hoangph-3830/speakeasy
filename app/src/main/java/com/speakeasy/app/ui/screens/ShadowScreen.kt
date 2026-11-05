package com.speakeasy.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Quests
import com.speakeasy.app.speech.Recorder
import com.speakeasy.app.speech.VoiceAnalysis
import com.speakeasy.app.speech.VoiceTake
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.CoralLight
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.categoryColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val MAX_RECORD_MS = 10_000L

private val ratings = listOf("😕 Chưa giống" to 1, "🙂 Khá giống" to 2, "😎 Rất giống" to 3)

/** Shadowing: hear the model, record yourself, then compare the two by ear and on a pitch curve. */
@Composable
fun ShadowScreen(topicId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val topic = app.content.topic(topicId) ?: return
    // Same sentences as the speaking step: phrases, native expressions, the learner's dialogue lines.
    val sentences = remember(topic) {
        (topic.phrases.map { it.en to it.vi } +
            topic.native.map { it.natural to it.vi } +
            topic.dialogue.lines.filter { it.isUser }.map { it.en to it.vi })
            .distinctBy { it.first }
    }
    val (strong, light) = categoryColors(topic.category)
    val recorder = remember { Recorder(context) }
    val scope = rememberCoroutineScope()
    var index by remember { mutableIntStateOf(0) }
    val takes = remember { mutableStateMapOf<Int, ShortArray>() }
    val stars = remember { mutableStateMapOf<Int, Int>() }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var looping by remember(index) { mutableStateOf(false) }
    var listened by remember(index) { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var earned by remember { mutableStateOf<Int?>(null) }
    val (en, vi) = sentences[index]
    val take = takes[index]
    val intonation = remember(en) { VoiceAnalysis.intonation(en) }
    val analysis by produceState<VoiceTake?>(null, take) {
        value = null
        value = take?.let { withContext(Dispatchers.Default) { VoiceAnalysis.analyze(it) } }
    }

    // The model sentence on repeat with a one-second gap; turning it off cancels and stops speech.
    LaunchedEffect(looping, index) {
        if (looping) while (true) {
            app.speaker.sayAndWait(en)
            delay(1000)
        }
    }

    val startRecording: () -> Unit = {
        val at = index
        looping = false
        playJob?.cancel()
        app.speaker.stop()
        denied = false
        recording = true
        elapsed = 0
        scope.launch {
            val samples = try {
                recorder.record(MAX_RECORD_MS) { elapsed = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            recording = false
            // Anything under a fifth of a second is a mis-tap, not a take.
            if (samples != null && samples.size > VoiceAnalysis.RATE / 5) {
                takes[at] = samples
                listened = false
            }
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else denied = true
    }
    val play: (suspend () -> Unit) -> Unit = { block ->
        looping = false
        playJob?.cancel()
        playJob = scope.launch {
            block()
            listened = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Luyện nhại", subtitle = topic.title, onBack = onBack)
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
                    Text(sEn, style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center)
                    VSpace(8.dp)
                    Text(sVi, color = Muted, textAlign = TextAlign.Center)
                    VSpace(10.dp)
                    Text(VoiceAnalysis.intonation(sEn).hint, color = strong, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
            }
            VSpace(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction("🔊 Nghe mẫu", Modifier.weight(1f)) { looping = false; app.speaker.say(en) }
                SmallAction("🐢 Chậm", Modifier.weight(1f)) { looping = false; app.speaker.say(en, slow = true) }
                SmallAction(if (looping) "🔁 Đang lặp" else "🔁 Lặp", Modifier.weight(1f), selected = looping, accent = strong) {
                    looping = !looping
                    if (!looping) app.speaker.stop()
                }
            }
            VSpace(16.dp)

            if (recording) {
                Text(
                    "⏺ Đang ghi âm… ${seconds(elapsed / 1000.0)} / ${MAX_RECORD_MS / 1000} giây",
                    color = Coral,
                    fontWeight = FontWeight.Bold,
                )
                VSpace(10.dp)
                BigButton("⏹ Dừng", onClick = { recorder.stop() }, color = Coral)
            } else {
                BigButton(if (take == null) "⏺ Ghi âm" else "⏺ Ghi lại", color = Coral, onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) startRecording() else askMic.launch(Manifest.permission.RECORD_AUDIO)
                })
            }
            if (denied) {
                VSpace(10.dp)
                Text(
                    "Cần quyền micro để ghi âm giọng bạn. Hãy cho phép SpeakEasy dùng micro trong Cài đặt rồi thử lại nhé.",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CoralLight).padding(12.dp),
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (take != null && !recording) {
                VSpace(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("▶ Nghe lại giọng bạn", Modifier.weight(1f)) { play { recorder.play(take) } }
                    SmallAction("🎧 Mẫu → Bạn", Modifier.weight(1f)) {
                        play {
                            app.speaker.sayAndWait(en)
                            delay(300)
                            recorder.play(take)
                        }
                    }
                }
                analysis?.let { a ->
                    VSpace(16.dp)
                    VoiceCharts(a, strong)
                    VSpace(10.dp)
                    val model = VoiceAnalysis.modelSeconds(en, app.speaker.rate)
                    Text("Bạn: ${seconds(a.seconds)} giây · Người bản xứ: ~${seconds(model)} giây", color = Ink)
                    Text(VoiceAnalysis.paceFeedback(a.seconds, model), color = Muted, textAlign = TextAlign.Center)
                    a.trend?.let { t ->
                        val feedback = VoiceAnalysis.trendFeedback(t, intonation)
                        Text(feedback, color = if ("✓" in feedback) Mint else Coral, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (listened || stars[index] != null) {
                    VSpace(16.dp)
                    Text("Giọng bạn giống mẫu đến đâu?", color = Muted)
                    VSpace(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ratings.forEach { (label, n) ->
                            SmallAction(label, Modifier.weight(1f), selected = stars[index] == n, accent = Mint) { stars[index] = n }
                        }
                    }
                }
            } else if (!recording) {
                VSpace(12.dp)
                Text("Nghe mẫu vài lần, rồi ghi âm và nói theo đúng nhịp, đúng ngữ điệu.", color = Muted, textAlign = TextAlign.Center)
            }
            VSpace(20.dp)
        }

        Row(Modifier.padding(20.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { index-- },
                enabled = index > 0 && !recording,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("← Trước") }
            Box(Modifier.weight(1f)) {
                if (index < sentences.lastIndex) {
                    BigButton(if (take == null) "Bỏ qua →" else "Tiếp →", onClick = { playJob?.cancel(); index++ }, color = strong, enabled = !recording)
                } else BigButton("Hoàn thành", onClick = {
                    playJob?.cancel()
                    val xp = 5 + stars.values.sum()
                    if (takes.isNotEmpty()) app.progress.count(Quests.SPEAK, takes.size)
                    app.progress.addXp(xp)
                    earned = xp
                }, color = Mint, enabled = !recording)
            }
        }
    }

    earned?.let { xp ->
        CompleteDialog(
            title = "Nhại giọng xong!",
            message = "Đã ghi âm ${takes.size}/${sentences.size} câu · ${stars.values.sum()} ⭐ tự chấm.",
            xp = xp,
            onDismiss = { earned = null; onBack() },
        )
    }
}

private fun seconds(s: Double) = String.format(Locale.US, "%.1f", s)

@Composable
private fun SmallAction(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color = Ink,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, if (selected) accent else Line),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
    ) { Text(label, color = if (selected) accent else Ink, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge) }
}

/** Waveform (loudness per 20 ms) above the pitch curve (Hz per 40 ms, gaps where unvoiced). */
@Composable
private fun VoiceCharts(take: VoiceTake, accent: Color) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp)) {
        Text("Sóng âm", color = Muted, style = MaterialTheme.typography.labelMedium)
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val peak = (take.rms.maxOrNull() ?: 0f).coerceAtLeast(0.01f)
            val step = size.width / take.rms.size.coerceAtLeast(1)
            take.rms.forEachIndexed { i, v ->
                val h = (v / peak) * size.height
                val x = i * step + step / 2
                drawLine(accent, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = (step * 0.7f).coerceAtLeast(1f))
            }
        }
        VSpace(8.dp)
        Text("Cao độ giọng (ngữ điệu)", color = Muted, style = MaterialTheme.typography.labelMedium)
        val voiced = take.pitch.filterNotNull()
        if (voiced.isEmpty()) {
            Text("Chưa nhận được giọng rõ, thử nói to hơn nhé.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        } else Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            val lo = (voiced.min() - 20f).coerceAtLeast(VoiceAnalysis.MIN_HZ)
            val hi = (voiced.max() + 20f).coerceAtMost(VoiceAnalysis.MAX_HZ)
            val step = size.width / take.pitch.size.coerceAtLeast(1)
            val path = Path()
            var drawing = false
            take.pitch.forEachIndexed { i, hz ->
                if (hz == null) {
                    drawing = false
                    return@forEachIndexed
                }
                val x = i * step + step / 2
                val y = size.height - (hz - lo) / (hi - lo).coerceAtLeast(1f) * size.height
                if (drawing) path.lineTo(x, y) else path.moveTo(x, y)
                drawing = true
                drawCircle(Mint, radius = 3f, center = Offset(x, y))
            }
            drawPath(path, Mint, style = Stroke(width = 4f))
        }
    }
}
