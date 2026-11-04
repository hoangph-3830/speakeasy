package com.speakeasy.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Activity
import com.speakeasy.app.data.GameMode
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.Games
import com.speakeasy.app.data.Round
import com.speakeasy.app.speech.MatchResult
import com.speakeasy.app.speech.SpeechMatch
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.EmojiBadge
import com.speakeasy.app.ui.components.HSpace
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
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import com.speakeasy.app.ui.theme.Sun
import com.speakeasy.app.ui.theme.SunLight
import com.speakeasy.app.ui.theme.categoryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val modeColors = mapOf(
    GameMode.MIX to (Sun to SunLight),
    GameMode.MATCH to (Sky to SkyLight),
    GameMode.SCRAMBLE to (Peach to PeachLight),
    GameMode.LISTEN to (Mint to MintLight),
    GameMode.MEANING to (Lavender to LavenderLight),
    GameMode.DICTATION to (Coral to Coral.copy(alpha = 0.12f)),
)

@Composable
fun GameHubScreen(openGame: (String) -> Unit, openLab: () -> Unit, openStories: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Vừa chơi vừa học 🎮", style = MaterialTheme.typography.headlineSmall)
            Text("Ôn lại từ vựng & mẫu câu của tất cả chủ đề.", color = Muted)
            VSpace(8.dp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple("🎯 Phòng phát âm", LavenderLight, openLab),
                    Triple("📖 Truyện nghe", MintLight, openStories),
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
        items(GameMode.entries) { m ->
            val (strong, light) = modeColors.getValue(m)
            Card(
                onClick = { openGame(m.id) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Line),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(m.emoji, light, 58.dp)
                    HSpace(14.dp)
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = MaterialTheme.typography.titleMedium)
                        Text(m.description, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("▶", color = strong, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun GameScreen(topicId: String, mode: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val topic = app.content.topic(topicId)
    val gameMode = GameMode.from(mode)
    var session by remember { mutableIntStateOf(0) }
    val rounds = remember(session) { Games.build(topic?.let { listOf(it) } ?: app.content.topics, gameMode) }
    var index by remember(session) { mutableIntStateOf(0) }
    var correct by remember(session) { mutableIntStateOf(0) }
    var combo by remember(session) { mutableIntStateOf(0) }
    var earned by remember { mutableStateOf<Int?>(null) }
    val accent = topic?.let { categoryColors(it.category).first } ?: modeColors.getValue(gameMode).first

    fun answered(ok: Boolean) {
        if (rounds[index] is Round.Listen || rounds[index] is Round.Dictation) app.progress.count(Quests.LISTEN)
        if (ok) { correct++; combo++ } else combo = 0
        if (index < rounds.lastIndex) index++
        else earned = if (topic != null) app.progress.complete(topic.id, Activity.GAME, bonus = correct / 2)
        else correct.also { app.progress.addXp(it) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = gameMode.title, subtitle = topic?.title ?: "Tất cả chủ đề", onBack = onBack) {
            if (combo >= 2) Text("🔥 x$combo", color = Peach, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 16.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            SoftProgress(index.toFloat() / rounds.size, color = accent)
            VSpace(4.dp)
            Text("Vòng ${index + 1}/${rounds.size} · đúng $correct", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        AnimatedContent(
            targetState = index,
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "round",
        ) { i ->
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                when (val r = rounds[i]) {
                    is Round.Match -> MatchRound(r, accent, onDone = ::answered)
                    is Round.Scramble -> ScrambleRound(r, accent, onDone = ::answered)
                    is Round.Listen -> ChoiceRound(
                        title = "Nghe và chọn câu đúng",
                        prompt = null,
                        audio = r.audio,
                        options = r.options,
                        answer = r.answer,
                        speakAnswer = true,
                        accent = accent,
                        onDone = ::answered,
                    )
                    is Round.Meaning -> ChoiceRound(
                        title = if (r.speakPrompt) "Câu này nghĩa là gì?" else "Nói câu này bằng tiếng Anh thế nào?",
                        prompt = r.prompt,
                        audio = if (r.speakPrompt) r.prompt else null,
                        options = r.options,
                        answer = r.answer,
                        speakAnswer = !r.speakPrompt,
                        accent = accent,
                        onDone = ::answered,
                    )
                    is Round.Dictation -> DictationRound(r, accent, onDone = ::answered)
                }
            }
        }
    }

    earned?.let { xp ->
        CompleteDialog(
            title = when {
                correct == rounds.size -> "Hoàn hảo! 🏆"
                correct * 2 >= rounds.size -> "Làm tốt lắm!"
                else -> "Cố lên nhé!"
            },
            message = "Bạn trả lời đúng $correct/${rounds.size} vòng.",
            xp = xp,
            onDismiss = { earned = null; onBack() },
            onAgain = { earned = null; session++ },
        )
    }
}

/** Feedback banner + continue button shown after answering a question. */
@Composable
private fun AnswerFeedback(ok: Boolean, correctAnswer: String?, onNext: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (ok) MintLight else Coral.copy(alpha = 0.12f)).padding(16.dp),
    ) {
        Text(if (ok) "🎉 Chính xác!" else "😅 Chưa đúng rồi", fontWeight = FontWeight.ExtraBold, color = if (ok) Mint else Coral, fontSize = 18.sp)
        if (!ok && correctAnswer != null) {
            VSpace(4.dp)
            Text("Đáp án: $correctAnswer", color = Ink)
        }
        VSpace(10.dp)
        BigButton("Tiếp tục", onClick = onNext, color = if (ok) Mint else Coral)
    }
}

@Composable
private fun ChoiceRound(
    title: String,
    prompt: String?,
    audio: String?,
    options: List<String>,
    answer: String,
    speakAnswer: Boolean,
    accent: Color,
    onDone: (Boolean) -> Unit,
) {
    val speaker = LocalApp.current.speaker
    var picked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(audio) { if (audio != null) { delay(300); speaker.say(audio) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (prompt != null) {
            Text(
                prompt,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(accent.copy(alpha = 0.1f)).padding(20.dp),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        if (audio != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SpeakButton(audio, size = 72.dp, tint = accent)
                HSpace(16.dp)
                SpeakButton(audio, size = 72.dp, slow = true, tint = accent)
            }
        }
        VSpace(4.dp)
        options.forEach { option ->
            val state = when {
                picked == null -> 0
                option == answer -> 1
                option == picked -> 2
                else -> 0
            }
            val bg by animateColorAsState(
                when (state) { 1 -> MintLight; 2 -> Coral.copy(alpha = 0.12f); else -> Color.White },
                label = "option",
            )
            Text(
                option,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .border(2.dp, when (state) { 1 -> Mint; 2 -> Coral; else -> Line }, RoundedCornerShape(18.dp))
                    .clickable(enabled = picked == null) {
                        picked = option
                        if (option == answer && speakAnswer) speaker.say(answer)
                    }
                    .padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        picked?.let { p -> AnswerFeedback(p == answer, answer, onNext = { onDone(p == answer) }) }
    }
}

@Composable
private fun MatchRound(round: Round.Match, accent: Color, onDone: (Boolean) -> Unit) {
    val speaker = LocalApp.current.speaker
    val left = remember(round) { round.pairs.map { it.first }.shuffled() }
    val right = remember(round) { round.pairs.map { it.second }.shuffled() }
    val solved = remember(round) { mutableStateListOf<String>() }
    var selLeft by remember { mutableStateOf<String?>(null) }
    var selRight by remember { mutableStateOf<String?>(null) }
    var mistakes by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf<Pair<String, String>?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun check() {
        val l = selLeft ?: return
        val r = selRight ?: return
        if (round.pairs.any { it.first == l && it.second == r }) {
            solved += l
            speaker.say(l)
        } else {
            mistakes++
            wrong = l to r
            scope.launch {
                for (x in listOf(14f, -14f, 8f, -8f, 0f)) shake.animateTo(x, spring(stiffness = 3000f))
                wrong = null
            }
        }
        selLeft = null
        selRight = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ghép từ với nghĩa đúng", style = MaterialTheme.typography.titleLarge)
        Text("Chạm một từ bên trái rồi chọn nghĩa bên phải.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                left.forEach { w ->
                    MatchTile(w, solved = w in solved, selected = selLeft == w, wrong = wrong?.first == w, accent = accent,
                        shake = if (wrong?.first == w) shake.value else 0f) {
                        selLeft = w; check()
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                right.forEach { m ->
                    val isSolved = round.pairs.any { it.second == m && it.first in solved }
                    MatchTile(m, solved = isSolved, selected = selRight == m, wrong = wrong?.second == m, accent = accent,
                        shake = if (wrong?.second == m) shake.value else 0f) {
                        selRight = m; check()
                    }
                }
            }
        }
        if (solved.size == round.pairs.size) {
            AnswerFeedback(ok = mistakes <= 1, correctAnswer = null, onNext = { onDone(mistakes <= 1) })
            if (mistakes > 1) Text("Bạn ghép sai $mistakes lần — vòng sau cẩn thận hơn nhé!", color = Muted)
        }
    }
}

@Composable
private fun MatchTile(text: String, solved: Boolean, selected: Boolean, wrong: Boolean, accent: Color, shake: Float, onClick: () -> Unit) {
    val bg by animateColorAsState(
        when {
            solved -> MintLight
            wrong -> Coral.copy(alpha = 0.15f)
            selected -> accent.copy(alpha = 0.15f)
            else -> Color.White
        },
        label = "tile",
    )
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(shake.dp.roundToPx(), 0) }
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(2.dp, if (solved) Mint else if (wrong) Coral else if (selected) accent else Line, RoundedCornerShape(16.dp))
            .clickable(enabled = !solved, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
        color = if (solved) Mint else Ink,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScrambleRound(round: Round.Scramble, accent: Color, onDone: (Boolean) -> Unit) {
    val speaker = LocalApp.current.speaker
    // Positions into round.tiles, so repeated words stay distinct.
    val placed = remember(round) { mutableStateListOf<Int>() }
    var checked by remember(round) { mutableStateOf<Boolean?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Xếp thành câu hoàn chỉnh", style = MaterialTheme.typography.titleLarge)
        Text("💬 ${round.hint}", color = Muted)
        FlowRow(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(accent.copy(alpha = 0.08f))
                .border(2.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            placed.forEach { i ->
                WordTile(round.tiles[i], accent, filled = true) { if (checked == null) placed.remove(i) }
            }
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            round.tiles.forEachIndexed { i, w ->
                if (i !in placed) WordTile(w, accent, filled = false) { if (checked == null) placed += i }
            }
        }
        val c = checked
        if (c == null) {
            BigButton(
                "Kiểm tra",
                enabled = placed.size == round.tiles.size,
                onClick = {
                    val ok = Games.isSolved(round, placed.map { round.tiles[it] })
                    checked = ok
                    speaker.say(round.sentence)
                },
                color = accent,
            )
        } else {
            AnswerFeedback(c, round.sentence, onNext = { onDone(c) })
        }
    }
}

@Composable
private fun WordTile(word: String, accent: Color, filled: Boolean, onClick: () -> Unit) {
    Text(
        word,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (filled) Color.White else accent.copy(alpha = 0.12f))
            .border(1.5.dp, if (filled) accent else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        fontWeight = FontWeight.SemiBold,
        color = Ink,
    )
}

/** Dictation: the sentence is only heard, never shown, until the learner submits what they typed. */
@Composable
private fun DictationRound(round: Round.Dictation, accent: Color, onDone: (Boolean) -> Unit) {
    val speaker = LocalApp.current.speaker
    var typed by remember(round) { mutableStateOf("") }
    var showHint by remember(round) { mutableStateOf(false) }
    var result by remember(round) { mutableStateOf<MatchResult?>(null) }
    LaunchedEffect(round) { delay(300); speaker.say(round.sentence) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Nghe và chép lại", style = MaterialTheme.typography.titleLarge)
        Text("Nghe bao nhiêu lần cũng được. Dấu câu và viết hoa không tính.", color = Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            SpeakButton(round.sentence, size = 72.dp, tint = accent)
            HSpace(16.dp)
            SpeakButton(round.sentence, size = 72.dp, slow = true, tint = accent)
        }
        if (showHint) Text("💬 ${round.hint}", color = Muted)
        else Text(
            "💡 Xem gợi ý nghĩa",
            color = accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showHint = true }.padding(4.dp),
        )
        OutlinedTextField(
            value = typed,
            onValueChange = { if (result == null) typed = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Gõ câu bạn nghe được…") },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            minLines = 2,
        )
        val r = result
        if (r == null) {
            BigButton(
                "Kiểm tra",
                enabled = typed.isNotBlank(),
                onClick = { result = SpeechMatch.compare(round.sentence, typed) },
                color = accent,
            )
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .border(1.dp, Line, RoundedCornerShape(18.dp)).padding(16.dp),
            ) {
                Text("Câu gốc · đúng ${r.score}%", color = Muted, style = MaterialTheme.typography.labelMedium)
                VSpace(4.dp)
                ScoredText(r)
            }
            // Small typos are fine; the goal is catching every word by ear. The sentence is already shown above.
            AnswerFeedback(r.score >= 80, correctAnswer = null, onNext = { onDone(r.score >= 80) })
        }
    }
}
