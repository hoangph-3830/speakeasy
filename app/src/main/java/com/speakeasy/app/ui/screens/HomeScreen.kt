package com.speakeasy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Activity
import com.speakeasy.app.data.LearningPath
import com.speakeasy.app.data.Levels
import com.speakeasy.app.data.PathStep
import com.speakeasy.app.data.Progress
import com.speakeasy.app.data.Quest
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.Stage
import com.speakeasy.app.data.Topic
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.EmojiBadge
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.Pill
import com.speakeasy.app.ui.components.Ring
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.CoralLight
import com.speakeasy.app.ui.theme.Ink
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
import com.speakeasy.app.ui.theme.levelColors
import java.time.LocalTime

@Composable
fun HomeScreen(
    openTopic: (String) -> Unit,
    openReview: () -> Unit,
    openLab: () -> Unit,
    openStories: () -> Unit,
    openVocab: () -> Unit,
    openNews: () -> Unit,
) {
    val app = LocalApp.current
    val progress by app.progress.progress.collectAsState()
    val cards by app.review.cards.collectAsState()
    val due = cards.count { it.due <= app.review.todayDay }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var byTopic by rememberSaveable { mutableStateOf(false) }
    var questsOpen by rememberSaveable { mutableStateOf(false) }
    // Stage ids the learner toggled away from the default (only the current stage open).
    var toggled by rememberSaveable { mutableStateOf(listOf<String>()) }
    var askName by remember { mutableStateOf(progress.name.isEmpty()) }
    val path = app.content.path
    val doneCount: (String) -> Int = progress::doneCount
    val next = LearningPath.next(path, doneCount)
    val topics = (category?.let { app.content.topicsIn(it) } ?: app.content.topics).sortedBy { Levels.rank(it.level) }

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
        item {
            ContinueCard(
                next, next?.let { app.content.topic(it.topicId) }, progress,
                onOpen = { next?.let { openTopic(it.topicId) } },
                onReview = openReview,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Shortcut("🔁", "Ôn tập", LavenderLight, Modifier.weight(1f), badge = due, onClick = openReview)
                Shortcut("📚", "Từ vựng", SkyLight, Modifier.weight(1f), onClick = openVocab)
                Shortcut("🎯", "Phát âm", CoralLight, Modifier.weight(1f), onClick = openLab)
                Shortcut("📖", "Truyện", MintLight, Modifier.weight(1f), onClick = openStories)
                Shortcut("📰", "Đọc báo", SunLight, Modifier.weight(1f), onClick = openNews)
            }
        }
        item { QuestsCard(progress, questsOpen, onToggle = { questsOpen = !questsOpen }, onClaim = { app.progress.claim(it) }) }
        item {
            VSpace(4.dp)
            Segmented("🧭 Lộ trình", "🗂️ Theo chủ đề", second = byTopic, onPick = { byTopic = it })
        }
        if (!byTopic) {
            itemsIndexed(path, key = { _, s -> "stage:${s.id}" }) { i, stage ->
                StageCard(
                    i, stage, progress,
                    expanded = (stage.id == next?.stage?.id) != (stage.id in toggled),
                    onToggle = { toggled = if (stage.id in toggled) toggled - stage.id else toggled + stage.id },
                    openTopic = openTopic,
                )
            }
        } else {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryChip("🌈 Tất cả", category == null, Sky) { category = null }
                    app.content.categories.forEach { c ->
                        CategoryChip("${c.emoji} ${c.name}", category == c.id, categoryColors(c.id).first) { category = c.id }
                    }
                }
            }
            items(topics, key = { it.id }) { t -> TopicRow(t, progress, onClick = { openTopic(t.id) }) }
        }
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF4FB8EC), Color(0xFF55D3AE))))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ring(p.goalProgress, 56.dp, Color.White) {
                Text("${(p.goalProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mục tiêu hôm nay", color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                    Text("${p.todayXp} / ${p.dailyGoal} XP", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                VSpace(2.dp)
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

/** Duolingo-style daily quests, folded into one row: finish one, then tap to open its chest. */
@Composable
private fun QuestsCard(p: Progress, open: Boolean, onToggle: () -> Unit, onClaim: (Quest) -> Unit) {
    val quests = Quests.today(p)
    val finished = quests.filter { p.count(it.id) >= it.target }
    val claimable = finished.any { it.id !in p.claimed }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "📜 Nhiệm vụ hôm nay · ${finished.size}/${quests.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (claimable) Text("🎁", fontSize = 20.sp)
            HSpace(8.dp)
            Text(if (open) "▴" else "▾", color = Muted, fontSize = 18.sp)
        }
        if (open) Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🧊 đạt mục tiêu = +1 đóng băng", color = Muted, style = MaterialTheme.typography.labelMedium)
            quests.forEach { q ->
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

/** "Học tiếp": the next unfinished topic along the path, or a pointer to review once it is all done. */
@Composable
private fun ContinueCard(next: PathStep?, topic: Topic?, p: Progress, onOpen: () -> Unit, onReview: () -> Unit) {
    val (strong, light) = levelColors(next?.stage?.level ?: "A1")
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = light),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (next == null || topic == null) {
                Text("Bạn đã học hết lộ trình 🎉", style = MaterialTheme.typography.titleLarge, color = Ink)
                Text("Ôn lại thẻ ghi nhớ để không quên nhé.", color = Muted)
                VSpace(12.dp)
                BigButton("🔁 Ôn tập", onClick = onReview, color = strong)
                return@Column
            }
            val step = Activity.entries.first { !p.isDone(topic.id, it) }
            Text(
                "Chặng ${next.stageIndex + 1} · ${next.stage.level} ${Levels.name(next.stage.level)} · ${next.stage.title}",
                color = strong,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            VSpace(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(topic.emoji, Color.White, 56.dp)
                HSpace(14.dp)
                Column(Modifier.weight(1f)) {
                    Text(topic.title, style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(topic.titleVi, color = Muted)
                    VSpace(4.dp)
                    Text(
                        "Bước ${step.ordinal + 1}: ${step.label}",
                        color = Ink,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            VSpace(12.dp)
            BigButton("Học tiếp →", onClick = onOpen, color = strong)
        }
    }
}

/** One of the five small square shortcuts; [badge] shows a count in the corner when above zero. */
@Composable
private fun Shortcut(emoji: String, label: String, bg: Color, modifier: Modifier, badge: Int = 0, onClick: () -> Unit) {
    Box(modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bg),
        ) {
            Column(
                Modifier.fillMaxSize().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(emoji, fontSize = 22.sp)
                VSpace(2.dp)
                Text(label, color = Ink, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
        if (badge > 0) Text(
            if (badge > 99) "99+" else "$badge",
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).clip(CircleShape).background(Coral)
                .padding(horizontal = 6.dp, vertical = 1.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Two-option segmented control; [second] is true when the right-hand option is picked. */
@Composable
private fun Segmented(firstLabel: String, secondLabel: String, second: Boolean, onPick: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(Line).padding(4.dp)) {
        listOf(firstLabel to false, secondLabel to true).forEach { (label, value) ->
            val selected = second == value
            Text(
                label,
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onPick(value) }
                    .padding(vertical = 9.dp),
                color = if (selected) Ink else Muted,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A path stage: header with level and progress; tap it to show or hide its topics in order. */
@Composable
private fun StageCard(
    index: Int,
    stage: Stage,
    p: Progress,
    expanded: Boolean,
    onToggle: () -> Unit,
    openTopic: (String) -> Unit,
) {
    val app = LocalApp.current
    val (strong, light) = levelColors(stage.level)
    val doneCount: (String) -> Int = p::doneCount
    val complete = LearningPath.isComplete(stage, doneCount)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (expanded) strong.copy(alpha = 0.5f) else Line),
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chặng ${index + 1}", fontWeight = FontWeight.ExtraBold, color = strong)
                HSpace(8.dp)
                Pill("${stage.level} · ${Levels.name(stage.level)}", strong, light)
                Box(Modifier.weight(1f))
                Text(if (expanded) "▴" else "▾", color = Muted, fontSize = 18.sp)
            }
            VSpace(4.dp)
            Text(stage.title, style = MaterialTheme.typography.titleMedium, color = Ink)
            if (stage.subtitle.isNotEmpty()) Text(stage.subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
            VSpace(8.dp)
            SoftProgress(LearningPath.progress(stage, doneCount), color = strong)
            VSpace(4.dp)
            Text(
                if (complete) "✅ Hoàn thành" else "${LearningPath.topicsDone(stage, doneCount)}/${stage.topics.size} chủ đề xong",
                color = if (complete) Mint else Muted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (complete) FontWeight.Bold else FontWeight.Medium,
            )
        }
        if (expanded) Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
            stage.topics.mapNotNull { app.content.topic(it) }.forEach { t ->
                PathTopicRow(t, p, light, strong, onClick = { openTopic(t.id) })
            }
        }
    }
}

@Composable
private fun PathTopicRow(t: Topic, p: Progress, light: Color, strong: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(t.emoji, light, 40.dp)
        HSpace(12.dp)
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(t.titleVi, color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        HSpace(8.dp)
        // One dot per activity, filled once it is done.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Activity.entries.forEach { a ->
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (p.isDone(t.id, a)) strong else Line))
            }
        }
        HSpace(8.dp)
        Text(
            if (p.doneCount(t.id) == Activity.entries.size) "✓" else "",
            color = Mint,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.width(14.dp),
        )
    }
}
