package com.speakeasy.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.speakeasy.app.data.Challenge
import com.speakeasy.app.data.Challenges
import com.speakeasy.app.data.ChoiceQuestion
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.SpeedState
import com.speakeasy.app.data.VocabGroup
import com.speakeasy.app.data.WordChallenges
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.Pill
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.CoralLight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Strong and light accent per challenge, shared by the hub cards and the game screens. */
internal fun challengeColors(c: Challenge): Pair<Color, Color> = when (c) {
    Challenge.SPEED -> Peach to PeachLight
    Challenge.MEMORY -> Sky to SkyLight
    Challenge.HANGMAN -> Lavender to LavenderLight
    Challenge.TRUEFALSE -> Mint to MintLight
    Challenge.CLOZE -> Peach to PeachLight
    Challenge.ANAGRAM -> Sky to SkyLight
    Challenge.SPELLING -> Mint to MintLight
}

private data class ChallengeResult(val score: Int, val best: Int, val newRecord: Boolean, val xp: Int, val detail: String?)

/** One vocabulary challenge, then its result: score, best, XP, "Chơi lại" / "Về". */
@Composable
fun ChallengeScreen(id: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val challenge = Challenge.from(id) ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val groups = remember { app.content.vocab.groups }
    val words = remember { groups.flatMap { it.words } }
    val accent = challengeColors(challenge).first
    var session by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<ChallengeResult?>(null) }

    fun finish(score: Int, detail: String?) {
        if (result != null) return
        val record = app.progress.recordGameBest(challenge.id, score, challenge.lowerIsBetter)
        val xp = Challenges.xp(challenge, score)
        app.progress.addXp(xp)
        app.progress.count(Quests.LISTEN)
        result = ChallengeResult(score, app.progress.progress.value.gameBest[challenge.id] ?: score, record, xp, detail)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "${challenge.emoji} ${challenge.title}", onBack = onBack)
        val r = result
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (r != null) {
                ChallengeEnd(challenge, r, accent, onAgain = { result = null; session++ }, onBack = onBack)
            } else key(session) {
                when (challenge) {
                    Challenge.SPEED -> SpeedGame(groups, accent, ::finish)
                    Challenge.MEMORY -> MemoryGame(words, accent, ::finish)
                    Challenge.HANGMAN -> HangmanGame(words, accent, ::finish)
                    Challenge.TRUEFALSE -> TrueFalseGame(groups, accent, ::finish)
                    Challenge.CLOZE -> ClozeGame(groups, accent, ::finish)
                    Challenge.ANAGRAM -> AnagramGame(groups, accent, ::finish)
                    Challenge.SPELLING -> SpellingGame(groups, accent, ::finish)
                }
            }
        }
    }
}

private fun scoreLabel(c: Challenge, score: Int) = when (c) {
    Challenge.MEMORY -> "$score lượt"
    Challenge.HANGMAN -> "$score/${Challenges.HANGMAN_ROUNDS} từ"
    Challenge.CLOZE -> "Đúng $score/${WordChallenges.CLOZE_ROUNDS}"
    Challenge.ANAGRAM -> "Đúng $score/${WordChallenges.ANAGRAM_ROUNDS}"
    Challenge.SPELLING -> "Đúng $score/${WordChallenges.SPELLING_ROUNDS}"
    else -> "$score điểm"
}

@Composable
private fun ChallengeEnd(c: Challenge, r: ChallengeResult, accent: Color, onAgain: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(c.emoji, fontSize = 56.sp)
        VSpace(8.dp)
        Text(scoreLabel(c, r.score), style = MaterialTheme.typography.headlineMedium, color = accent)
        if (r.detail != null) Text(r.detail, color = Muted)
        VSpace(8.dp)
        if (r.newRecord) Text("🏆 Kỷ lục mới!", fontWeight = FontWeight.ExtraBold, color = Peach, fontSize = 20.sp)
        else Text("Kỷ lục: ${r.best}", color = Muted)
        VSpace(12.dp)
        Pill("+${r.xp} XP", color = Color(0xFF8A5A00), background = Sun.copy(alpha = 0.35f))
        VSpace(28.dp)
        BigButton("Chơi lại", onClick = onAgain, color = accent)
        VSpace(10.dp)
        BigButton("Về", onClick = onBack, color = Muted)
    }
}

/** Countdown bar with the seconds left; turns coral in the last ten seconds. */
@Composable
private fun TimeBar(leftMs: Long, totalSeconds: Int, accent: Color) {
    val seconds = ((leftMs + 999) / 1000).toInt()
    val color = if (seconds <= 10) Coral else accent
    SoftProgress(leftMs / (totalSeconds * 1000f), color = color)
    VSpace(4.dp)
    Text("⏱ $seconds giây", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
}

/** Ticks [leftMs] down every 100 ms (penalties may cut it meanwhile) and calls [onOver] once it hits zero. */
@Composable
private fun Countdown(leftMs: () -> Long, setLeft: (Long) -> Unit, onOver: () -> Unit) {
    LaunchedEffect(Unit) {
        while (leftMs() > 0) {
            delay(100)
            setLeft((leftMs() - 100).coerceAtLeast(0))
        }
        onOver()
    }
}

@Composable
private fun SpeedGame(groups: List<VocabGroup>, accent: Color, onFinish: (Int, String?) -> Unit) {
    val speaker = LocalApp.current.speaker
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SpeedState()) }
    var question by remember { mutableStateOf(Challenges.choice(groups)) }
    // The wrong option just tapped, while the right one is flashed before the next word.
    var wrongPick by remember { mutableStateOf<String?>(null) }
    fun next(after: ChoiceQuestion) = generateSequence { Challenges.choice(groups) }.first { it.word.w != after.word.w }

    Countdown({ state.timeLeftMs }, { state = state.copy(timeLeftMs = it) }, onOver = { onFinish(state.score, null) })
    LaunchedEffect(question) { speaker.say(question.word.w) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeBar(state.timeLeftMs, Challenges.SPEED_SECONDS, accent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Điểm: ${state.score}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.doubled) Text("🔥 x2", color = Peach, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Text("Chuỗi đúng: ${state.streak} · Sai bị trừ ${Challenges.WRONG_PENALTY_MS / 1000} giây", color = Muted)
        WordPrompt(question.word.w, question.word.ipa, accent)
        question.options.forEach { option ->
            val look = when {
                wrongPick == null -> 0
                option == question.answer -> 1
                option == wrongPick -> 2
                else -> 0
            }
            ChallengeOption(option, look, enabled = wrongPick == null) {
                if (option == question.answer) {
                    state = Challenges.speedAnswer(state, correct = true)
                    question = next(question)
                } else {
                    state = Challenges.speedAnswer(state, correct = false)
                    wrongPick = option
                    scope.launch {
                        delay(600)
                        wrongPick = null
                        question = next(question)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrueFalseGame(groups: List<VocabGroup>, accent: Color, onFinish: (Int, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var score by remember { mutableIntStateOf(0) }
    var left by remember { mutableLongStateOf(Challenges.TRUEFALSE_SECONDS * 1000L) }
    var question by remember { mutableStateOf(Challenges.trueFalse(groups)) }
    // True while the real meaning is shown after a wrong answer.
    var reveal by remember { mutableStateOf(false) }

    Countdown({ left }, { left = it }, onOver = { onFinish(score, null) })

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeBar(left, Challenges.TRUEFALSE_SECONDS, accent)
        Text("Điểm: $score", style = MaterialTheme.typography.titleLarge)
        WordPrompt(question.word.w, question.word.ipa, accent)
        Text("nghĩa là", color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            question.shown,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                .border(2.dp, Line, RoundedCornerShape(18.dp)).padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (reveal) Text(
            "😅 Sai rồi! “${question.word.w}” nghĩa là: ${question.word.vi}",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CoralLight).padding(12.dp),
            color = Ink,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(true to "✅ Đúng", false to "❌ Sai").forEach { (saysTrue, label) ->
                BigButton(
                    label,
                    modifier = Modifier.weight(1f),
                    color = if (saysTrue) Mint else Coral,
                    enabled = !reveal,
                    onClick = {
                        if (saysTrue == question.isTrue) {
                            score++
                            question = Challenges.trueFalse(groups)
                        } else {
                            reveal = true
                            scope.launch {
                                delay(1200)
                                reveal = false
                                question = Challenges.trueFalse(groups)
                            }
                        }
                    },
                )
            }
        }
    }
}

/** The English word large, with its IPA and a speaker button. */
@Composable
internal fun WordPrompt(word: String, ipa: String, accent: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(accent.copy(alpha = 0.1f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(word, style = MaterialTheme.typography.headlineMedium, color = Ink)
            if (ipa.isNotBlank()) Text(ipa, color = Muted)
        }
        HSpace(8.dp)
        SpeakButton(word, size = 48.dp, tint = accent)
    }
}

/** A multiple-choice option; [state] 1 marks the right answer, 2 a wrong pick. */
@Composable
internal fun ChallengeOption(text: String, state: Int, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        when (state) { 1 -> MintLight; 2 -> CoralLight; else -> Color.White },
        label = "option",
    )
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(2.dp, when (state) { 1 -> Mint; 2 -> Coral; else -> Line }, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
    )
}
