package com.speakeasy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.speakeasy.app.data.Grade
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.Srs
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
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
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Peach
import com.speakeasy.app.ui.theme.Sky
import kotlinx.coroutines.delay

private val gradeColors = mapOf(Grade.AGAIN to Coral, Grade.HARD to Peach, Grade.GOOD to Sky, Grade.EASY to Mint)

/** Daily spaced-repetition session over everything learned so far. */
@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val store = app.review
    // Ids still to show this session; a forgotten card goes back to the end.
    val queue = remember { mutableStateListOf(*store.due().shuffled().take(30).map { it.id }.toTypedArray()) }
    val total = remember { queue.size }
    var done by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var listenOnly by remember { mutableStateOf(false) }
    var earned by remember { mutableStateOf<Int?>(null) }
    val card = queue.firstOrNull()?.let { id -> store.cards.value.firstOrNull { it.id == id } }

    LaunchedEffect(card?.id, flipped) {
        if (card != null && !flipped) { delay(250); app.speaker.say(card.front) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Ôn tập hôm nay", subtitle = "Lặp lại ngắt quãng · nhớ lâu hơn", onBack = onBack) {
            Text(
                if (listenOnly) "🎧 Chỉ nghe ✓" else "🎧 Chỉ nghe",
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clip(CircleShape)
                    .background(if (listenOnly) LavenderLight else Color.Transparent)
                    .border(1.dp, Line, CircleShape)
                    .clickable { listenOnly = !listenOnly }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Lavender,
                fontWeight = FontWeight.Bold,
            )
        }

        if (card == null) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (total == 0) "🌤️" else "🏁", fontSize = 64.sp)
                VSpace(12.dp)
                Text(
                    if (total == 0) "Chưa có thẻ nào cần ôn" else "Xong phần ôn hôm nay!",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (total == 0) "Học xong bước “Cụm từ & từ vựng” của một chủ đề để thêm thẻ vào bộ ôn tập."
                    else "Mai quay lại nhé, app sẽ nhắc đúng lúc bạn sắp quên.",
                    color = Muted,
                    textAlign = TextAlign.Center,
                )
                VSpace(20.dp)
                BigButton("Về trang chủ", onClick = onBack, color = Lavender)
            }
            return@Column
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            SoftProgress(done.toFloat() / maxOf(1, total), color = Lavender)
            VSpace(4.dp)
            Text("Còn ${queue.size} thẻ · đã ôn $done", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(LavenderLight)
                .clickable { flipped = true }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (listenOnly && !flipped) {
                Text("🎧", fontSize = 48.sp)
                Text("Nghe và tự nhắc lại, rồi lật thẻ", color = Muted, textAlign = TextAlign.Center)
            } else {
                Text(card.front, style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center)
            }
            VSpace(16.dp)
            SpeakButton(card.front, size = 56.dp, tint = Lavender)
            if (flipped) {
                VSpace(20.dp)
                Text(card.back, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Lavender, textAlign = TextAlign.Center)
                if (card.extra != null) {
                    VSpace(8.dp)
                    Text(card.extra, color = Muted, textAlign = TextAlign.Center)
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            if (!flipped) {
                BigButton("Lật thẻ", onClick = { flipped = true }, color = Lavender)
            } else {
                Text("Bạn nhớ câu này thế nào?", color = Muted, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Grade.entries.forEach { g ->
                        val color = gradeColors.getValue(g)
                        OutlinedButton(
                            onClick = {
                                val next = store.grade(card, g)
                                app.progress.count(Quests.REVIEW)
                                queue.removeAt(0)
                                if (next.due <= store.todayDay) queue.add(card.id) else done++
                                flipped = false
                                if (queue.isEmpty()) {
                                    app.progress.addXp(done)
                                    earned = done
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, color),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${g.emoji} ${g.label}", color = color, fontWeight = FontWeight.Bold)
                                Text(Srs.preview(card, g, store.todayDay), color = Muted, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    earned?.let { xp ->
        CompleteDialog(
            title = "Ôn tập xong!",
            message = "Bạn đã ôn $xp thẻ. Trí nhớ dài hạn +1 💪",
            xp = xp,
            onDismiss = { earned = null },
        )
    }
}
