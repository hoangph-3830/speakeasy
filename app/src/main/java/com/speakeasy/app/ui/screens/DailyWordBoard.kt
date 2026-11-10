package com.speakeasy.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.data.DailyWord
import com.speakeasy.app.data.LetterState
import com.speakeasy.app.data.Progress
import com.speakeasy.app.ui.components.EmojiBadge
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sun
import java.time.LocalDate

private val keyRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val Grey = Color(0xFF9AA3B5)

private fun fill(state: LetterState?): Color = when (state) {
    LetterState.HIT -> Mint
    LetterState.NEAR -> Sun
    LetterState.MISS -> Grey
    null -> Color.White
}

/** The daily puzzle sits on top of the games hub: one word a day keeps people coming back. */
@Composable
internal fun DailyWordCard(p: Progress, onOpen: () -> Unit) {
    val playedToday = p.daily?.day == todayEpochDay()
    val streak = p.dailyStats.streak
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge("🔠", MintLight, 56.dp)
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Text("MỖI NGÀY MỘT TỪ", color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Từ của ngày", style = MaterialTheme.typography.titleMedium)
                Text("Đoán từ 5 chữ cái trong 6 lần. Ai cũng giải cùng một từ.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            HSpace(8.dp)
            Text(
                if (streak > 0) "🔥 $streak" else if (playedToday) "Xem →" else "Chơi →",
                color = Mint,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** Six rows of five tiles: the guesses so far, the word being typed, then empty rows. */
@Composable
internal fun DailyWordGrid(guesses: List<String>, typed: String, answer: String) {
    val over = DailyWord.isOver(guesses, answer)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(DailyWord.MAX_GUESSES) { row ->
            val guess = guesses.getOrNull(row)
            val states = guess?.let { DailyWord.scoreGuess(it, answer) }
            val letters = guess ?: if (row == guesses.size && !over) typed else ""
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(DailyWord.WORD_LENGTH) { i ->
                    val state = states?.get(i)
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(fill(state))
                            .border(2.dp, if (state != null) fill(state) else if (i < letters.length) Ink else Line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            letters.getOrNull(i)?.uppercase() ?: "",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (state != null) Color.White else Ink,
                        )
                    }
                }
            }
        }
    }
}

/** On-screen keyboard coloured with the best hint seen for each letter. */
@Composable
internal fun DailyKeyboard(keys: Map<Char, LetterState>, onKey: (Char) -> Unit, onEnter: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keyRows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (r == 2) Key("Đoán", Modifier.weight(1.6f), null, onEnter)
                row.forEach { ch -> Key(ch.toString(), Modifier.weight(1f), keys[ch]) { onKey(ch) } }
                if (r == 2) Key("⌫", Modifier.weight(1.6f), null, onBack)
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, state: LetterState?, onClick: () -> Unit) {
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(8.dp)).background(if (state == null) Line else fill(state)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = if (state == null) Ink else Color.White)
    }
}

/** Today's epoch day, the same day number the web app uses (local calendar date). */
internal fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
