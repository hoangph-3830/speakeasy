package com.speakeasy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Activity
import com.speakeasy.app.data.Progress
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.SectionTitle
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.Lavender
import com.speakeasy.app.ui.theme.LavenderLight
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.PeachLight
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import com.speakeasy.app.ui.theme.categoryColors

private data class Badge(val emoji: String, val title: String, val hint: String, val earned: (Progress, Int) -> Boolean)

private val badges = listOf(
    Badge("🌱", "Khởi đầu", "Hoàn thành 1 bài", { p, _ -> p.done.isNotEmpty() }),
    Badge("🔥", "Chăm chỉ", "Chuỗi 3 ngày", { p, _ -> p.bestStreak >= 3 }),
    Badge("🗓️", "Một tuần", "Chuỗi 7 ngày", { p, _ -> p.bestStreak >= 7 }),
    Badge("📚", "Mọt từ", "Thuộc 30 từ", { p, _ -> p.learnedWords.size >= 30 }),
    Badge("🎤", "Mạnh dạn", "Luyện nói 5 chủ đề", { p, _ -> p.done.count { it.endsWith(Activity.SPEAK.name) } >= 5 }),
    Badge("⭐", "Toàn diện", "Xong trọn 1 chủ đề", { _, full -> full >= 1 }),
    Badge("✈️", "Sẵn sàng đi xa", "Xong 4 chủ đề", { _, full -> full >= 4 }),
    Badge("🏆", "Bậc thầy", "Đạt 500 XP", { p, _ -> p.xp >= 500 }),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen() {
    val app = LocalApp.current
    val p by app.progress.progress.collectAsState()
    val fullTopics = app.content.topics.count { p.doneCount(it.id) == Activity.entries.size }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tiến độ của ${p.name.ifEmpty { "bạn" }} 📈", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("🔥", "${p.streak}", "ngày liên tiếp", PeachLight, Modifier.weight(1f))
            Stat("⚡", "${p.xp}", "tổng XP", SkyLight, Modifier.weight(1f))
            Stat("📖", "${p.learnedWords.size}", "từ đã thuộc", MintLight, Modifier.weight(1f))
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = LavenderLight)) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cấp ${p.level}", style = MaterialTheme.typography.titleLarge, color = Lavender, modifier = Modifier.weight(1f))
                    Text("${p.xp % 100}/100 XP", color = Muted)
                }
                VSpace(8.dp)
                SoftProgress(p.levelProgress, color = Lavender, track = Color.White)
            }
        }

        SectionTitle("Theo chủ đề")
        app.content.categories.forEach { c ->
            val topics = app.content.topicsIn(c.id)
            val done = topics.sumOf { p.doneCount(it.id) }
            val total = topics.size * Activity.entries.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.emoji, fontSize = 22.sp)
                HSpace(10.dp)
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(c.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("$done/$total", color = Muted)
                    }
                    VSpace(4.dp)
                    SoftProgress(done / total.toFloat(), color = categoryColors(c.id).first)
                }
            }
        }

        SectionTitle("Huy hiệu")
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 4,
        ) {
            badges.forEach { b ->
                val earned = b.earned(p, fullTopics)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (earned) Color(0xFFFFF5DB) else Color(0xFFF1F4F9))
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                        .alpha(if (earned) 1f else 0.45f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(b.emoji, fontSize = 28.sp)
                    Text(b.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(b.hint, style = MaterialTheme.typography.labelMedium, color = Muted, textAlign = TextAlign.Center, fontSize = 10.sp)
                }
            }
        }

        SectionTitle("Cài đặt")
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Line),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Mục tiêu mỗi ngày", fontWeight = FontWeight.SemiBold)
                VSpace(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20 to "Nhẹ nhàng", 30 to "Vừa phải", 50 to "Chăm chỉ", 80 to "Siêu tốc").forEach { (g, label) ->
                        FilterChip(
                            selected = p.dailyGoal == g,
                            onClick = { app.progress.setDailyGoal(g) },
                            label = { Text("$label\n$g XP", textAlign = TextAlign.Center, fontSize = 11.sp) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                VSpace(16.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tốc độ giọng đọc: ${"%.1f".format(p.speechRate)}x", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    SpeakButton("Nice to meet you. How are you today?", tint = Sky)
                }
                Slider(
                    value = p.speechRate,
                    onValueChange = {
                        app.progress.setSpeechRate(it)
                        app.speaker.rate = it
                    },
                    valueRange = 0.5f..1.3f,
                    steps = 7,
                )
                VSpace(8.dp)
                Text("Giọng bản xứ", fontWeight = FontWeight.SemiBold)
                VSpace(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("US" to "🇺🇸 Anh - Mỹ", "UK" to "🇬🇧 Anh - Anh").forEach { (code, label) ->
                        FilterChip(
                            selected = p.accent == code,
                            onClick = {
                                app.progress.setAccent(code)
                                app.speaker.accent = code
                                app.speaker.say("Hello! Lovely to meet you.")
                            },
                            label = { Text(label) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                VSpace(4.dp)
                TextButton(onClick = { confirmReset = true }) { Text("Xoá tiến độ & học lại từ đầu", color = Coral) }
            }
        }
        Text(
            "SpeakEasy dựa trên mã nguồn mở Engrisk (MIT). Hoạt động offline, dữ liệu chỉ lưu trên máy bạn.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(8.dp)
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Xoá toàn bộ tiến độ?") },
            text = { Text("XP, chuỗi ngày, từ đã thuộc và huy hiệu sẽ bị xoá. Không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = { app.progress.reset(); app.review.reset(); confirmReset = false }) { Text("Xoá", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Huỷ") } },
            containerColor = Color.White,
        )
    }
}

@Composable
private fun Stat(emoji: String, value: String, label: String, bg: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}
