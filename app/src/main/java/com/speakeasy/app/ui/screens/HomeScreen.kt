package com.speakeasy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Activity
import com.speakeasy.app.data.Progress
import com.speakeasy.app.data.Quest
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.Topic
import com.speakeasy.app.ui.components.EmojiBadge
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.Pill
import com.speakeasy.app.ui.components.Ring
import com.speakeasy.app.ui.components.SectionTitle
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Lavender
import com.speakeasy.app.ui.theme.LavenderLight
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Peach
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import com.speakeasy.app.ui.theme.Sun
import com.speakeasy.app.ui.theme.SunLight
import com.speakeasy.app.ui.theme.categoryColors
import java.time.LocalTime

@Composable
fun HomeScreen(openTopic: (String) -> Unit, openReview: () -> Unit, openLab: () -> Unit, openStories: () -> Unit) {
    val app = LocalApp.current
    val progress by app.progress.progress.collectAsState()
    val cards by app.review.cards.collectAsState()
    val due = cards.count { it.due <= app.review.todayDay }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var askName by remember { mutableStateOf(progress.name.isEmpty()) }
    val today = remember { app.content.topicOfTheDay() }
    val topics = category?.let { app.content.topicsIn(it) } ?: app.content.topics

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Greeting(progress) }
        item { DailyGoalCard(progress) }
        if (progress.freezeUsedDay == app.review.todayDay) item {
            Text(
                "🧊 Hôm qua bạn nghỉ, một lượt đóng băng đã giữ chuỗi ${progress.streak} ngày cho bạn!",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SkyLight).padding(12.dp),
                color = Ink,
            )
        }
        item { QuestsCard(progress, onClaim = { app.progress.claim(it) }) }
        if (cards.isNotEmpty()) item { ReviewCard(due, cards.size, onClick = openReview) }
        item {
            SectionTitle("Luyện nghe nói như người bản xứ")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureTile(
                    "🎯", "Phòng phát âm",
                    progress.speakingScore?.let { "Điểm phát âm $it" } ?: "Sửa âm người Việt hay sai",
                    LavenderLight, Modifier.weight(1f), openLab,
                )
                FeatureTile("📖", "Truyện nghe", "Nghe chuyện, trả lời câu hỏi", MintLight, Modifier.weight(1f), openStories)
            }
        }
        item {
            SectionTitle("Chủ đề hôm nay ✨")
            TodayCard(today, progress, onClick = { openTopic(today.id) })
        }
        item {
            SectionTitle("Khám phá chủ đề")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryChip("🌈 Tất cả", category == null, Sky) { category = null }
                app.content.categories.forEach { c ->
                    CategoryChip("${c.emoji} ${c.name}", category == c.id, categoryColors(c.id).first) { category = c.id }
                }
            }
        }
        items(topics, key = { it.id }) { t -> TopicRow(t, progress, onClick = { openTopic(t.id) }) }
    }

    if (askName) {
        NameDialog(onDone = { name ->
            if (name.isNotBlank()) app.progress.setName(name)
            askName = false
        })
    }
}

@Composable
private fun Greeting(p: Progress) {
    val hello = when (LocalTime.now().hour) {
        in 5..10 -> "Good morning"
        in 11..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$hello${if (p.name.isNotEmpty()) ", ${p.name}" else ""}! 👋", style = MaterialTheme.typography.headlineSmall)
            Text("Hôm nay mình nói tiếng Anh nhé?", color = Muted)
        }
        Row(
            Modifier.clip(CircleShape).background(SunLight).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔥", fontSize = 18.sp)
            HSpace(4.dp)
            Text("${p.streak}", fontWeight = FontWeight.ExtraBold, color = Color(0xFF9A6200), fontSize = 18.sp)
            if (p.freezes > 0) {
                HSpace(8.dp)
                Text("🧊${p.freezes}", fontWeight = FontWeight.Bold, color = Sky, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun DailyGoalCard(p: Progress) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF4FB8EC), Color(0xFF55D3AE))))
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ring(p.goalProgress, 74.dp, Color.White) {
                Text("${(p.goalProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
            }
            HSpace(16.dp)
            Column(Modifier.weight(1f)) {
                Text("Mục tiêu hôm nay", color = Color.White.copy(alpha = 0.85f))
                Text("${p.todayXp} / ${p.dailyGoal} XP", color = Color.White, style = MaterialTheme.typography.titleLarge)
                VSpace(6.dp)
                Text(
                    if (p.goalProgress >= 1f) "Đạt mục tiêu rồi, giỏi quá! 🎯" else "Cấp ${p.level} · còn ${100 - p.xp % 100} XP để lên cấp",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TodayCard(t: Topic, p: Progress, onClick: () -> Unit) {
    val (strong, light) = categoryColors(t.category)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = light),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Pill(t.level, strong, Color.White)
                VSpace(8.dp)
                Text(t.title, style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(t.titleVi, color = Muted)
                VSpace(10.dp)
                SoftProgress(p.doneCount(t.id) / Activity.entries.size.toFloat(), color = strong, track = Color.White)
            }
            HSpace(12.dp)
            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(14.dp)) {
                Text(t.emoji, fontSize = 44.sp)
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) color else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        color = if (selected) Color.White else Ink,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun TopicRow(t: Topic, p: Progress, onClick: () -> Unit) {
    val (strong, light) = categoryColors(t.category)
    val done = p.doneCount(t.id)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(t.emoji, light, 54.dp)
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium)
                Text(t.titleVi, color = Muted, style = MaterialTheme.typography.bodyMedium)
                VSpace(8.dp)
                SoftProgress(done / Activity.entries.size.toFloat(), color = strong)
            }
            HSpace(12.dp)
            if (done == Activity.entries.size) Text("⭐", fontSize = 22.sp)
            else Pill(t.level, strong, light)
        }
    }
}

@Composable
private fun NameDialog(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone("") },
        icon = { Text("👋", fontSize = 40.sp) },
        title = { Text("Chào bạn mới!") },
        text = {
            Column {
                Text("Mình nên gọi bạn là gì nhỉ?", color = Muted)
                VSpace(12.dp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    singleLine = true,
                    placeholder = { Text("Tên của bạn") },
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(name) }) { Text("Bắt đầu học", color = Mint, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { onDone("") }) { Text("Để sau", color = Peach) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun ReviewCard(due: Int, total: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = LavenderLight),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge("🔁", Color.White, 50.dp)
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    if (due > 0) "Ôn tập hôm nay: $due thẻ" else "Đã ôn xong hôm nay ✅",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                Text("Bộ nhớ có $total thẻ · ôn đúng lúc để nhớ lâu", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (due > 0) Pill("Ôn ngay", Color.White, Lavender)
        }
    }
}

/** Duolingo-style daily quests: finish one, then tap to open its chest. */
@Composable
private fun QuestsCard(p: Progress, onClaim: (Quest) -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📜 Nhiệm vụ hôm nay", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("🧊 đạt mục tiêu = +1 đóng băng", color = Muted, style = MaterialTheme.typography.labelMedium)
            }
            Quests.today(p).forEach { q ->
                val n = minOf(p.count(q.id), q.target)
                val done = n >= q.target
                val claimed = q.id in p.claimed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q.emoji, fontSize = 22.sp)
                    HSpace(10.dp)
                    Column(Modifier.weight(1f)) {
                        Text(q.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        VSpace(4.dp)
                        SoftProgress(n / q.target.toFloat(), color = if (done) Mint else Sun)
                    }
                    HSpace(10.dp)
                    when {
                        claimed -> Text("✅", fontSize = 20.sp)
                        done -> Text(
                            "🎁 +${q.reward}",
                            modifier = Modifier.clip(CircleShape).background(Sun).clickable { onClaim(q) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6B4500),
                        )
                        else -> Text("$n/${q.target}", color = Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTile(emoji: String, title: String, subtitle: String, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(emoji, fontSize = 32.sp)
            VSpace(6.dp)
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}
