package com.speakeasy.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.Story
import com.speakeasy.app.data.StoryItem
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.EmojiBadge
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.categoryColors

@Composable
fun StoriesHubScreen(onBack: () -> Unit, openStory: (String) -> Unit) {
    val app = LocalApp.current
    val p by app.progress.progress.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Truyện nghe", subtitle = "Nghe chuyện đời thường, trả lời câu hỏi", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(app.content.stories.stories, key = { it.id }) { s ->
                val (strong, light) = categoryColors(s.category)
                Card(
                    onClick = { openStory(s.id) },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Line),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(s.emoji, light, 60.dp)
                        HSpace(14.dp)
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleMedium)
                            Text(s.titleVi, color = Muted)
                            Text(
                                s.characters.filter { it.name != "Narrator" }.joinToString(" · ") { it.name },
                                color = strong,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Text(if ("story:${s.id}" in p.done) "⭐" else "▶", color = strong, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StoryScreen(storyId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val story = app.content.stories.stories.firstOrNull { it.id == storyId } ?: return
    val (strong, light) = categoryColors(story.category)
    var pos by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<Int, Int>() }
    var listenOnly by remember { mutableStateOf(false) }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }
    var earned by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val current = story.items[pos]

    // Each new line is read in its character's voice.
    LaunchedEffect(pos) {
        listState.animateScrollToItem(pos)
        if (!current.isQuestion) app.speaker.say(current.en, pitch = story.pitchOf(current.speaker))
    }

    fun advance() {
        if (pos < story.items.lastIndex) pos++
        else {
            val correct = answers.count { (i, a) -> story.items[i].answer == a }
            app.progress.count(Quests.LISTEN)
            app.progress.markDone("story:${story.id}")
            val xp = 10 + correct * 2
            app.progress.addXp(xp)
            earned = xp
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = story.title, subtitle = story.titleVi, onBack = { app.speaker.stop(); onBack() }) {
            Text(
                if (listenOnly) "🎧 ✓" else "🎧",
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clip(CircleShape)
                    .background(if (listenOnly) light else Color.Transparent)
                    .border(1.dp, Line, CircleShape)
                    .clickable { listenOnly = !listenOnly }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = strong,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.padding(horizontal = 20.dp)) { SoftProgress((pos + 1f) / story.items.size, color = strong) }
        if (listenOnly) {
            Text("Chế độ chỉ nghe: chữ bị ẩn, chạm vào câu để xem.", color = Muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(story.items.take(pos + 1)) { i, item ->
                AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 3 }) {
                    if (item.isQuestion) {
                        QuestionCard(item, picked = answers[i], accent = strong, onPick = { if (answers[i] == null) answers[i] = it })
                    } else {
                        StoryLine(
                            story, item,
                            hidden = listenOnly && revealed[i] != true,
                            accent = strong,
                            onReveal = { revealed[i] = true },
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(20.dp).navigationBarsPadding()) {
            val waiting = current.isQuestion && answers[pos] == null
            BigButton(
                when {
                    waiting -> "Chọn một đáp án"
                    pos == story.items.lastIndex -> "Hoàn thành 🎉"
                    else -> "Tiếp tục"
                },
                onClick = ::advance,
                enabled = !waiting,
                color = if (pos == story.items.lastIndex) Mint else strong,
            )
        }
    }

    earned?.let { xp ->
        val correct = answers.count { (i, a) -> story.items[i].answer == a }
        CompleteDialog(
            title = "Hết truyện rồi!",
            message = "Bạn trả lời đúng $correct/${story.questionCount} câu hỏi.",
            xp = xp,
            onDismiss = { earned = null; onBack() },
        )
    }
}

@Composable
private fun StoryLine(story: Story, item: StoryItem, hidden: Boolean, accent: Color, onReveal: () -> Unit) {
    val narrator = item.speaker == "Narrator"
    Column(Modifier.fillMaxWidth()) {
        if (!narrator) Text(item.speaker.orEmpty(), color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (narrator) Color.Transparent else Color.White)
                // Narration sits on the page; character lines get a bubble.
                .then(if (narrator) Modifier else Modifier.border(1.dp, Line, RoundedCornerShape(18.dp)))
                .clickable(enabled = hidden, onClick = onReveal)
                .padding(if (narrator) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (hidden) Text("🎧 • • •  chạm để xem chữ", color = Muted)
                else {
                    Text(
                        item.en,
                        color = Ink,
                        style = if (narrator) MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        else MaterialTheme.typography.bodyLarge,
                    )
                    Text(item.vi, color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            HSpace(8.dp)
            StorySpeak(item.en, story.pitchOf(item.speaker), accent)
        }
    }
}

/** Replays a line in the same character voice. */
@Composable
private fun StorySpeak(text: String, pitch: Float, accent: Color) {
    val speaker = LocalApp.current.speaker
    Text(
        "🔊",
        modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.12f)).clickable { speaker.say(text, pitch = pitch) }
            .padding(10.dp),
        fontSize = 16.sp,
    )
}

@Composable
private fun QuestionCard(item: StoryItem, picked: Int?, accent: Color, onPick: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = 0.08f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("❓ ${item.q}", fontWeight = FontWeight.Bold, color = Ink)
        item.options.forEachIndexed { k, option ->
            val state = when {
                picked == null -> 0
                k == item.answer -> 1
                k == picked -> 2
                else -> 0
            }
            Text(
                option,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(when (state) { 1 -> MintLight; 2 -> Coral.copy(alpha = 0.12f); else -> Color.White })
                    .border(1.5.dp, when (state) { 1 -> Mint; 2 -> Coral; else -> Line }, RoundedCornerShape(14.dp))
                    .clickable(enabled = picked == null) { onPick(k) }
                    .padding(12.dp),
                color = Ink,
            )
        }
        if (picked != null) {
            VSpace(2.dp)
            Text(
                if (picked == item.answer) "🎉 Chính xác!" else "😅 Chưa đúng, đáp án được tô xanh.",
                color = if (picked == item.answer) Mint else Coral,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
