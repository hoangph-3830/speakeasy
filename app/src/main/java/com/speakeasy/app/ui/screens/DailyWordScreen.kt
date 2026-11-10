package com.speakeasy.app.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.DailyStats
import com.speakeasy.app.data.DailyWord
import com.speakeasy.app.data.VocabWord
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.CoralLight
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted

/** "Từ của ngày": a five-letter word puzzle, the same word for everyone each day (and the same as the web). */
@Composable
fun DailyWordScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val p by app.progress.progress.collectAsState()
    val day = remember { todayEpochDay() }
    val answer = remember { DailyWord.wordOfDay(DailyWord.puzzleWords(app.content.vocab.groups), day) }
    var typed by remember { mutableStateOf("") }
    val guesses = p.daily?.takeIf { it.day == day }?.guesses.orEmpty()
    val over = DailyWord.isOver(guesses, answer.w)

    fun submit() {
        if (over) return
        if (!DailyWord.validGuess(typed)) {
            Toast.makeText(context, "Cần đủ ${DailyWord.WORD_LENGTH} chữ cái.", Toast.LENGTH_SHORT).show()
            return
        }
        val next = guesses + typed
        typed = ""
        app.progress.saveDaily(day, next, answer.w)
        if (DailyWord.isOver(next, answer.w)) app.speaker.say(answer.w)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "🔠 Từ của ngày",
            subtitle = "Đoán từ tiếng Anh ${DailyWord.WORD_LENGTH} chữ cái trong ${DailyWord.MAX_GUESSES} lần",
            onBack = onBack,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DailyWordGrid(guesses, typed, answer.w)
            Text("🟩 đúng chỗ · 🟨 có trong từ nhưng sai chỗ · ⬜ không có", color = Muted, fontSize = 12.sp)
            if (over) {
                DailyResult(guesses, answer, p.dailyStats, onShare = { share(context, DailyWord.shareText(guesses, answer.w, day)) })
            } else {
                DailyKeyboard(
                    DailyWord.keyColours(guesses, answer.w),
                    onKey = { if (typed.length < DailyWord.WORD_LENGTH) typed += it },
                    onEnter = ::submit,
                    onBack = { typed = typed.dropLast(1) },
                )
            }
        }
    }
}

@Composable
private fun DailyResult(guesses: List<String>, answer: VocabWord, stats: DailyStats, onShare: () -> Unit) {
    val won = DailyWord.isWon(guesses, answer.w)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (won) MintLight else CoralLight).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (won) "🎉 Giải được sau ${guesses.size} lần!" else "😅 Hôm nay chưa đoán ra",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = if (won) Mint else Coral,
        )
        Text("Từ hôm nay: ${answer.w} ${answer.ipa} · ${answer.vi}", color = Ink, fontWeight = FontWeight.SemiBold)
        if (answer.ex.isNotBlank()) Text(answer.ex, color = Muted, fontStyle = FontStyle.Italic)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val rate = if (stats.played > 0) stats.won * 100 / stats.played else 0
            listOf("${stats.played}" to "đã chơi", "$rate%" to "thắng", "🔥 ${stats.streak}" to "chuỗi", "${stats.best}" to "kỷ lục")
                .forEach { (value, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, fontWeight = FontWeight.ExtraBold, color = Ink)
                        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
        }
        VSpace(4.dp)
        BigButton("📤 Chia sẻ kết quả", onClick = onShare, color = if (won) Mint else Coral)
        Text("Ngày mai có từ mới. Quay lại để giữ chuỗi nhé!", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

/** Opens Android's share sheet with the coloured squares. */
private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Chia sẻ kết quả"))
}
