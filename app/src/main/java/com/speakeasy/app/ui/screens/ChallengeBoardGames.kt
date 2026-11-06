package com.speakeasy.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Challenges
import com.speakeasy.app.data.MemoryCard
import com.speakeasy.app.data.VocabWord
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.CoralLight
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Twelve face-down cards in a 3×4 grid; find the six word/meaning pairs in as few moves as possible. */
@Composable
internal fun MemoryGame(words: List<VocabWord>, accent: Color, onFinish: (Int, String?) -> Unit) {
    val speaker = LocalApp.current.speaker
    val scope = rememberCoroutineScope()
    val deck = remember { Challenges.memoryDeck(words) }
    val matched = remember { mutableStateListOf<Int>() } // pair ids
    val open = remember { mutableStateListOf<MemoryCard>() } // face up, not yet matched (two at most)
    var moves by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }
    // A wrong pair stays visible for a moment; taps wait until it flips back.
    var busy by remember { mutableStateOf(false) }
    val done = matched.size == deck.size / 2

    LaunchedEffect(done) {
        if (done) {
            delay(700)
            onFinish(moves, "trong $seconds giây")
        } else while (true) {
            delay(1000)
            seconds++
        }
    }

    fun tap(card: MemoryCard) {
        if (busy || done || card.pairId in matched || open.any { it.id == card.id }) return
        open += card
        if (open.size < 2) return
        moves++
        val (a, b) = open.toList()
        if (Challenges.isPair(a, b)) {
            matched += a.pairId
            speaker.say(if (a.english) a.text else b.text)
            open.clear()
        } else {
            busy = true
            scope.launch {
                delay(800)
                open.clear()
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Lật hai thẻ để tìm từ và nghĩa của nó.", color = Muted)
        Text("🔁 $moves lượt · ⏱ $seconds giây · ${matched.size}/${deck.size / 2} cặp", fontWeight = FontWeight.Bold, color = Ink)
        deck.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { card ->
                    val solved = card.pairId in matched
                    MemoryTile(
                        card,
                        faceUp = solved || open.any { it.id == card.id },
                        solved = solved,
                        wrong = busy && open.any { it.id == card.id },
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = { tap(card) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryTile(card: MemoryCard, faceUp: Boolean, solved: Boolean, wrong: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(
        when {
            !faceUp -> accent
            solved -> MintLight
            wrong -> CoralLight
            else -> Color.White
        },
        label = "card",
    )
    Box(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(2.dp, if (solved) Mint else if (wrong) Coral else if (faceUp) accent else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(enabled = !faceUp, onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!faceUp) Text("❓", fontSize = 26.sp)
        else Text(
            card.text,
            textAlign = TextAlign.Center,
            fontWeight = if (card.english) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = if (card.english) 16.sp else 13.sp,
            lineHeight = 16.sp,
            maxLines = 4,
            color = if (solved) Mint else Ink,
        )
    }
}

/** Five words to guess letter by letter from the meaning and IPA, with six lives per word. */
@Composable
internal fun HangmanGame(words: List<VocabWord>, accent: Color, onFinish: (Int, String?) -> Unit) {
    val speaker = LocalApp.current.speaker
    val rounds = remember { Challenges.hangmanWords(words) }
    var index by remember { mutableIntStateOf(0) }
    var solvedCount by remember { mutableIntStateOf(0) }
    var guessed by remember { mutableStateOf(setOf<Char>()) }
    if (rounds.isEmpty()) {
        LaunchedEffect(Unit) { onFinish(0, null) }
        return
    }
    val word = rounds[index]
    val won = Challenges.isSolved(word.w, guessed)
    val lost = Challenges.isLost(word.w, guessed)
    val lives = Challenges.livesLeft(word.w, guessed).coerceAtLeast(0)
    LaunchedEffect(index, won) { if (won) speaker.say(word.w) }

    fun next() {
        val total = solvedCount + if (won) 1 else 0
        if (index == rounds.lastIndex) onFinish(total, null)
        else {
            solvedCount = total
            index++
            guessed = emptySet()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Từ ${index + 1}/${rounds.size} · đúng $solvedCount", color = Muted, modifier = Modifier.weight(1f))
            Text("❤️".repeat(lives) + "🤍".repeat(Challenges.HANGMAN_LIVES - lives), fontSize = 16.sp)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = 0.1f)).padding(16.dp)) {
            Text("💬 ${word.vi}", style = MaterialTheme.typography.titleMedium, color = Ink)
            if (word.ipa.isNotBlank()) Text("🔊 ${word.ipa}", color = Muted)
        }
        Text(
            // A lost round shows the whole word.
            Challenges.masked(word.w, if (lost) word.w.toSet() else guessed).uppercase(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            color = if (lost) Coral else if (won) Mint else Ink,
        )
        if (won || lost) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (won) MintLight else CoralLight).padding(16.dp),
            ) {
                Text(if (won) "🎉 Chính xác!" else "😅 Hết mạng rồi", fontWeight = FontWeight.ExtraBold, color = if (won) Mint else Coral, fontSize = 18.sp)
                if (lost) Text("Đáp án: ${word.w}", color = Ink)
                VSpace(10.dp)
                BigButton(if (index == rounds.lastIndex) "Xem kết quả" else "Từ tiếp theo", onClick = ::next, color = if (won) Mint else Coral)
            }
        } else {
            ('a'..'z').chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { c -> LetterKey(c, guessed, word.w, Modifier.weight(1f)) { guessed = guessed + c } }
                    // Keep the short last row's keys the same width as the others.
                    repeat(7 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun LetterKey(c: Char, guessed: Set<Char>, word: String, modifier: Modifier, onClick: () -> Unit) {
    val used = c in guessed
    val (bg, fg) = when {
        !used -> Color.White to Ink
        c in word -> MintLight to Mint
        else -> CoralLight to Coral
    }
    Text(
        c.uppercaseChar().toString(),
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.5.dp, if (used) fg else Line, RoundedCornerShape(12.dp))
            .clickable(enabled = !used, onClick = onClick)
            .padding(top = 11.dp),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = fg,
    )
}
