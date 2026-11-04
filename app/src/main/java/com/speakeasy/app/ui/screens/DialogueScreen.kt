package com.speakeasy.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Activity
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.Line
import com.speakeasy.app.speech.MatchResult
import com.speakeasy.app.speech.SpeechMatch
import com.speakeasy.app.speech.rememberSpeechInput
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.MicButton
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line as LineColor
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.categoryColors
import kotlinx.coroutines.delay

private enum class Mode(val label: String) {
    LISTEN("🎧 Nghe"),
    ROLEPLAY("🎭 Nhập vai"),
    /** No English on screen: understand by ear, answer from the Vietnamese cue, mic opens by itself. */
    REFLEX("⚡ Phản xạ"),
}

@Composable
fun DialogueScreen(topicId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val topic = app.content.topic(topicId) ?: return
    val lines = topic.dialogue.lines
    val (strong, light) = categoryColors(topic.category)
    val speaker = app.speaker
    val speaking by speaker.speaking.collectAsState()

    var mode by remember { mutableStateOf(Mode.LISTEN) }
    var showVi by remember { mutableStateOf(true) }
    var playingAll by remember { mutableStateOf(false) }
    // Role-play state: how many lines are revealed, and scores for the learner's lines.
    var round by remember { mutableIntStateOf(0) }
    var turn by remember(mode, round) { mutableIntStateOf(0) }
    val scores = remember(mode, round) { mutableStateMapOf<Int, MatchResult>() }
    val revealed = remember(mode, round) { mutableStateListOf<Int>() }
    var earned by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    val listen = rememberSpeechInput { heard ->
        app.progress.count(Quests.SPEAK)
        scores[turn] = SpeechMatch.compare(lines[turn].en, heard)
    }

    // Listen mode: play every line in order.
    LaunchedEffect(playingAll) {
        if (!playingAll) return@LaunchedEffect
        for (i in lines.indices) {
            turn = i
            listState.animateScrollToItem(i)
            speaker.sayAndWait(lines[i].en)
            delay(350)
        }
        app.progress.count(Quests.LISTEN)
        playingAll = false
    }

    // Role-play and reflex: the app voices the other person, then waits for the learner.
    LaunchedEffect(mode, round, turn) {
        if (mode == Mode.LISTEN || turn >= lines.size) return@LaunchedEffect
        listState.animateScrollToItem(turn)
        if (!lines[turn].isUser) {
            delay(300)
            speaker.sayAndWait(lines[turn].en)
            turn++
        } else if (mode == Mode.REFLEX && scores[turn] == null) {
            // Answer straight away, like a real conversation.
            delay(400)
            listen(lines[turn].vi)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Hội thoại", subtitle = topic.title, onBack = onBack) {
            Text(
                if (showVi) "VI ✓" else "VI",
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clip(CircleShape)
                    .background(if (showVi) light else Color.Transparent)
                    .border(1.dp, LineColor, CircleShape)
                    .clickable { showVi = !showVi }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = strong,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(CircleShape).background(light).padding(4.dp)) {
            Mode.entries.forEach { m ->
                Text(
                    m.label,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (mode == m) Color.White else Color.Transparent)
                        .clickable {
                            speaker.stop()
                            playingAll = false
                            mode = m
                        }
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = if (mode == m) strong else Muted,
                )
            }
        }
        Text(
            when (mode) {
                Mode.LISTEN -> "Nghe ${topic.dialogue.roleA} nói chuyện với bạn. Chạm từng câu để nghe lại."
                Mode.ROLEPLAY -> "Bạn là “${topic.dialogue.roleB}”. Khi đến lượt, chạm 🎤 và nói câu của bạn."
                Mode.REFLEX -> "Không có chữ tiếng Anh! Nghe ${topic.dialogue.roleA} nói, nhìn gợi ý tiếng Việt và trả lời ngay."
            },
            color = Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        val visible = if (mode == Mode.LISTEN) lines.size else minOf(turn + 1, lines.size)
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(lines.take(visible)) { i, line ->
                AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 2 }) {
                    Bubble(
                        line = line,
                        name = if (line.isUser) topic.dialogue.roleB else topic.dialogue.roleA,
                        showVi = showVi,
                        active = speaking == line.en,
                        accent = strong,
                        accentLight = light,
                        result = scores[i],
                        awaiting = mode != Mode.LISTEN && i == turn && line.isUser,
                        hidden = mode == Mode.REFLEX && i !in revealed && scores[i] == null,
                        onReveal = { revealed += i },
                    )
                }
            }
        }

        Column(Modifier.padding(20.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                mode == Mode.LISTEN -> {
                    BigButton(
                        if (playingAll) "⏸ Dừng" else "▶ Phát cả đoạn hội thoại",
                        onClick = {
                            if (playingAll) speaker.stop()
                            playingAll = !playingAll
                        },
                        color = strong,
                    )
                    VSpace(10.dp)
                    OutlinedButton(
                        onClick = { speaker.stop(); playingAll = false; mode = Mode.ROLEPLAY },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) { Text("Sẵn sàng? Thử nhập vai 🎭") }
                }

                turn >= lines.size -> BigButton("Hoàn thành 🎉", onClick = {
                    val stars = scores.values.sumOf { it.stars }
                    earned = app.progress.complete(topic.id, Activity.DIALOGUE, bonus = stars)
                }, color = Mint)

                lines[turn].isUser -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { turn++ }, shape = RoundedCornerShape(16.dp)) { Text("Bỏ qua") }
                    HSpace(20.dp)
                    MicButton(onClick = { listen(if (mode == Mode.REFLEX) lines[turn].vi else lines[turn].en) })
                    HSpace(20.dp)
                    OutlinedButton(
                        onClick = { turn++ },
                        enabled = scores[turn] != null,
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Tiếp →") }
                }

                else -> Text("${topic.dialogue.roleA} đang nói… 🎧", color = Muted, modifier = Modifier.padding(vertical = 18.dp))
            }
        }
    }

    earned?.let { xp ->
        val said = scores.size
        CompleteDialog(
            title = if (mode == Mode.REFLEX) "Phản xạ cực nhanh! ⚡" else "Nhập vai xuất sắc!",
            message = "Bạn đã nói $said/${lines.count { it.isUser }} câu trong vai ${topic.dialogue.roleB}.",
            xp = xp,
            onDismiss = { earned = null; onBack() },
            onAgain = { earned = null; round++ },
        )
    }
}

@Composable
private fun Bubble(
    line: Line,
    name: String,
    showVi: Boolean,
    active: Boolean,
    accent: Color,
    accentLight: Color,
    result: MatchResult?,
    awaiting: Boolean,
    hidden: Boolean,
    onReveal: () -> Unit,
) {
    val mine = line.isUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(horizontal = 6.dp))
            val shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (mine) 20.dp else 6.dp,
                bottomEnd = if (mine) 6.dp else 20.dp,
            )
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(if (mine) accentLight else Color.White)
                    .border(
                        BorderStroke(if (active || awaiting) 2.dp else 1.dp, if (awaiting) Coral else if (active) accent else LineColor),
                        shape,
                    )
                    .padding(14.dp),
            ) {
                Column {
                    if (hidden && mine) {
                        Text("🇻🇳 ${line.vi}", color = Ink, style = MaterialTheme.typography.bodyLarge)
                        VSpace(4.dp)
                        Text("Nói câu này bằng tiếng Anh · chạm để xem đáp án", color = Muted,
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable(onClick = onReveal))
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        if (result != null) ScoredText(result, Modifier.weight(1f, fill = false))
                        else if (hidden) Text(
                            "🎧 • • •  chạm để xem chữ",
                            color = Muted,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f, fill = false).clickable(onClick = onReveal),
                        )
                        else Text(line.en, color = Ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                        HSpace(8.dp)
                        SpeakButton(line.en, size = 34.dp, tint = accent)
                    }
                    if (showVi && !hidden) {
                        VSpace(4.dp)
                        Text(line.vi, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (result != null) {
                        VSpace(6.dp)
                        Text("${"⭐".repeat(result.stars)}  ${result.score}% · ${result.feedback}", style = MaterialTheme.typography.labelMedium, color = Ink)
                    } else if (awaiting) {
                        VSpace(6.dp)
                        Text("🎤 Đến lượt bạn!", color = Coral, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Sentence with each word coloured by whether the recognizer heard it. */
@Composable
fun ScoredText(result: MatchResult, modifier: Modifier = Modifier) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            result.words.forEachIndexed { i, (w, ok) ->
                if (i > 0) append(" ")
                pushStyle(androidx.compose.ui.text.SpanStyle(color = if (ok) Mint else Coral, fontWeight = FontWeight.SemiBold))
                append(w)
                pop()
            }
        },
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
    )
}
