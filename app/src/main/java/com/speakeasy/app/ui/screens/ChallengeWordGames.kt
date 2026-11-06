package com.speakeasy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.speakeasy.app.data.VocabGroup
import com.speakeasy.app.data.WordChallenges
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.CoralLight
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.SunLight

/** "Câu 3/10 · đúng 2" above every round-based word game. */
@Composable
internal fun RoundLine(index: Int, total: Int, score: Int) =
    Text("Câu ${index + 1}/$total · đúng $score", color = Muted, fontWeight = FontWeight.SemiBold)

/** Right or wrong after an answer, what to learn from it, and the button to the next round or the result. */
@Composable
internal fun RoundVerdict(ok: Boolean, last: Boolean, onNext: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (ok) MintLight else CoralLight).padding(16.dp)) {
        Text(if (ok) "🎉 Chính xác!" else "😅 Chưa đúng", fontWeight = FontWeight.ExtraBold, color = if (ok) Mint else Coral, fontSize = 18.sp)
        VSpace(4.dp)
        body()
        VSpace(10.dp)
        BigButton(if (last) "Xem kết quả" else "Tiếp theo →", onClick = onNext, color = if (ok) Mint else Coral)
    }
}

/** Ten example sentences with one word missing; pick it from four words of the same kind. */
@Composable
internal fun ClozeGame(groups: List<VocabGroup>, accent: Color, onFinish: (Int, String?) -> Unit) {
    val speaker = LocalApp.current.speaker
    val rounds = remember { List(WordChallenges.CLOZE_ROUNDS) { WordChallenges.clozeRound(groups) } }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    val q = rounds[index]

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundLine(index, rounds.size, score)
        Text(
            q.sentence,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = 0.1f)).padding(18.dp),
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
        )
        q.options.forEach { option ->
            val look = when {
                picked == null -> 0
                option == q.answer -> 1
                option == picked -> 2
                else -> 0
            }
            ChallengeOption(option, look, enabled = picked == null) {
                picked = option
                if (option == q.answer) score++
                speaker.say(q.word.w)
            }
        }
        picked?.let { choice ->
            RoundVerdict(choice == q.answer, last = index == rounds.lastIndex, onNext = {
                if (index == rounds.lastIndex) onFinish(score, null)
                else { index++; picked = null }
            }) {
                Text("${q.word.w} ${q.word.ipa} · ${q.word.vi}", color = Ink, fontWeight = FontWeight.SemiBold)
                if (q.translation.isNotBlank()) Text(q.translation, color = Muted)
            }
        }
    }
}

/** Eight words with their letters scrambled; tap the tiles into the tray in the right order. */
@Composable
internal fun AnagramGame(groups: List<VocabGroup>, accent: Color, onFinish: (Int, String?) -> Unit) {
    val speaker = LocalApp.current.speaker
    val words = remember { WordChallenges.anagramWords(groups) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    if (words.isEmpty()) {
        LaunchedEffect(Unit) { onFinish(0, null) }
        return
    }
    val word = words[index]
    val letters = remember(index) { WordChallenges.scrambleLetters(word.w) }
    // Indices into [letters], in the order they were tapped into the tray.
    var placed by remember(index) { mutableStateOf(listOf<Int>()) }
    var checked by remember(index) { mutableStateOf<Boolean?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RoundLine(index, words.size, score)
        Text(
            "💡 Nghĩa: ${word.vi}" + if (word.ipa.isNotBlank()) " · ${word.ipa}" else "",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SunLight).padding(12.dp),
            color = Ink,
        )
        LetterRow(letters.size) { slot ->
            val i = placed.getOrNull(slot)
            LetterTile(i?.let { letters[it] }, filled = true, accent, Modifier.weight(1f), enabled = checked == null && i != null) {
                placed = placed - i!!
            }
        }
        LetterRow(letters.size) { i ->
            val used = i in placed
            LetterTile(if (used) null else letters[i], filled = false, accent, Modifier.weight(1f), enabled = checked == null && !used) {
                placed = placed + i
            }
        }
        val result = checked
        if (result == null) {
            BigButton("Kiểm tra", enabled = placed.size == letters.size, color = accent, onClick = {
                val ok = placed.map { letters[it] }.joinToString("") == word.w
                if (ok) score++
                checked = ok
                speaker.say(word.w)
            })
            Text(
                "Bỏ qua",
                color = Muted,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { checked = false; speaker.say(word.w) }.padding(10.dp),
            )
        } else {
            RoundVerdict(result, last = index == words.lastIndex, onNext = {
                if (index == words.lastIndex) onFinish(score, null) else index++
            }) {
                Text("Từ đúng: ${word.w}", color = Ink, fontWeight = FontWeight.SemiBold)
                if (word.ex.isNotBlank()) Text(word.ex, color = Muted)
            }
        }
    }
}

@Composable
private fun LetterRow(count: Int, tile: @Composable androidx.compose.foundation.layout.RowScope.(Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { tile(it) }
    }
}

/** One letter tile; an empty slot when [letter] is null. Tray tiles are [filled] with the accent colour. */
@Composable
private fun LetterTile(letter: Char?, filled: Boolean, accent: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(50.dp).clip(shape)
            .background(if (letter == null) Color(0xFFF1F4F9) else if (filled) accent.copy(alpha = 0.15f) else Color.White)
            .border(2.dp, if (letter != null && filled) accent else Line, shape)
            .clickable(enabled = enabled && letter != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (letter != null) Text(letter.uppercase(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}
