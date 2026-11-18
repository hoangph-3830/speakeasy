package com.speakeasy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.AudioLine
import com.speakeasy.app.data.ExamItem
import com.speakeasy.app.speech.Speaker
import com.speakeasy.app.ui.TestTags
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.CoralLight
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import com.speakeasy.app.ui.theme.SunLight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val PITCH = mapOf("M" to 0.85f, "W" to 1.2f, "N" to 1f)
private const val LETTERS = "ABCD"

/** Reads listening lines one after another, a different pitch per speaker; a new play stops the old one. */
class LinePlayer(private val scope: CoroutineScope, private val speaker: Speaker) {
    private var job: Job? = null
    var playingId by mutableStateOf<String?>(null)
        private set

    fun play(id: String, lines: List<AudioLine>, onDone: () -> Unit = {}) {
        stop()
        playingId = id
        job = scope.launch {
            for (l in lines) speaker.sayAndWait(l.en, PITCH[l.who] ?: 1f)
            playingId = null
            onDone()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (playingId != null) speaker.stop()
        playingId = null
    }
}

/** A [LinePlayer] that stops when the screen using it goes away. */
@Composable
fun rememberLinePlayer(): LinePlayer {
    val speaker = LocalApp.current.speaker
    val scope = rememberCoroutineScope()
    val player = remember { LinePlayer(scope, speaker) }
    DisposableEffect(player) { onDispose { player.stop() } }
    return player
}

/** White rounded card used across the exam screens. */
@Composable
fun ExamCard(modifier: Modifier = Modifier, background: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(background).border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Play/stop button for a listening item; the script stays hidden until [reveal]. */
@Composable
fun AudioBlock(item: ExamItem, reveal: Boolean, player: LinePlayer, onPlayed: () -> Unit = {}) {
    if (item.audio.isEmpty()) return
    var script by remember(item.id) { mutableStateOf(false) }
    val playing = player.playingId == item.id
    ExamCard(background = SkyLight) {
        BigButton(
            if (playing) "⏹ Dừng" else "🔊 Nghe" + if (item.audio.size > 1) " (${item.audio.size} câu)" else "",
            onClick = { if (playing) player.stop() else player.play(item.id, item.audio, onPlayed) },
            color = if (playing) Coral else Sky,
            modifier = Modifier.testTag(TestTags.SPEAK),
        )
        Text("Nghe được nhiều lần khi luyện; khi thi thật chỉ nghe một lần.", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (reveal) {
            TextButton(onClick = { script = !script }) { Text(if (script) "▴ Ẩn lời thoại" else "📜 Xem lời thoại", fontWeight = FontWeight.Bold) }
            if (script) item.audio.forEach { l -> Text("${l.who}: ${l.en}", color = Ink, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
fun PassageBlock(item: ExamItem) {
    val passage = item.passage ?: return
    ExamCard(background = SunLight) {
        passage.split('\n').filter { it.isNotBlank() }.forEach { Text(it, color = Ink, style = MaterialTheme.typography.bodyLarge) }
    }
}

/**
 * Question [index] of [item]. [chosen] is the picked option; [reveal] shows right/wrong and the explanation.
 * Hidden options (TOEIC Part 2) show only their letters until revealed.
 */
@Composable
fun QuestionBlock(item: ExamItem, index: Int, chosen: Int?, reveal: Boolean, onChoose: (Int) -> Unit) {
    val q = item.questions[index]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text((if (item.questions.size > 1) "${index + 1}. " else "") + q.q, color = Ink, fontWeight = FontWeight.SemiBold)
        q.options.forEachIndexed { k, option ->
            val letter = LETTERS.getOrElse(k) { '•' }
            val (bg, edge) = when {
                reveal && k == q.answer -> MintLight to Mint
                reveal && k == chosen -> CoralLight to Coral
                !reveal && k == chosen -> SkyLight to Sky
                else -> Color.White to Line
            }
            Text(
                if (item.hideOptions && !reveal) "$letter" else "$letter. $option",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).border(1.5.dp, edge, RoundedCornerShape(14.dp))
                    .clickable(enabled = !reveal) { onChoose(k) }.padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("btn_answer_option"),
                color = Ink,
            )
        }
        if (reveal && !q.explain.isNullOrBlank()) Text("💡 ${q.explain}", color = Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Listening script, passage and every question of a question item. */
@Composable
fun QuestionItem(
    item: ExamItem,
    answers: Map<String, Int>,
    reveal: (Int) -> Boolean,
    scriptShown: Boolean,
    player: LinePlayer,
    onPlayed: () -> Unit = {},
    onChoose: (String, Int) -> Unit,
) {
    AudioBlock(item, scriptShown, player, onPlayed)
    PassageBlock(item)
    item.questions.indices.forEach { i ->
        val key = com.speakeasy.app.data.Exams.key(item, i)
        QuestionBlock(item, i, answers[key], reveal(i)) { onChoose(key, it) }
    }
}
