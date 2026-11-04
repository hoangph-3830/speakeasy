package com.speakeasy.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Activity
import com.speakeasy.app.data.Topic
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Peach
import com.speakeasy.app.ui.theme.SunLight
import com.speakeasy.app.ui.theme.categoryColors

@Composable
fun PhrasesScreen(topicId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val topic = app.content.topic(topicId) ?: return
    var tab by remember { mutableIntStateOf(0) }
    var earned by remember { mutableStateOf<Int?>(null) }
    val (strong, light) = categoryColors(topic.category)

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Cụm từ & từ vựng", subtitle = topic.title, onBack = onBack)
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(CircleShape).background(light).padding(4.dp),
        ) {
            listOf("💬 Mẫu câu", "🗣️ Bản xứ", "🃏 Từ vựng").forEachIndexed { i, label ->
                Text(
                    label,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (tab == i) Color.White else Color.Transparent)
                        .clickable { tab = i }
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tab == i) strong else Muted,
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> PhraseList(topic, strong, light, onNext = { tab = 1 })
                1 -> NativeList(topic, strong, light, onNext = { tab = 2 })
                else -> Flashcards(topic, strong, light, onFinish = {
                    earned = app.progress.complete(topic.id, Activity.PHRASES)
                    app.review.enroll(topic)
                })
            }
        }
    }

    earned?.let { xp ->
        CompleteDialog(
            title = "Hoàn thành bài học!",
            message = "Bạn đã học ${topic.phrases.size} mẫu câu và ${topic.words.size} từ mới. Các thẻ đã được thêm vào Ôn tập hôm nay 🔁",
            xp = xp,
            onDismiss = { earned = null; onBack() },
        )
    }
}

@Composable
private fun PhraseList(topic: Topic, strong: Color, light: Color, onNext: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Chạm 🔊 để nghe, 🐢 để nghe chậm. Đọc to theo nhé!", color = Muted)
        }
        itemsIndexed(topic.phrases) { i, p ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Line),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("${i + 1}", color = strong, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 2.dp))
                        HSpace(10.dp)
                        Column(Modifier.weight(1f)) {
                            Text(p.en, style = MaterialTheme.typography.titleMedium, color = Ink)
                            VSpace(4.dp)
                            Text(p.vi, color = Muted)
                        }
                        HSpace(8.dp)
                        SpeakButton(p.en, tint = strong)
                        HSpace(6.dp)
                        SpeakButton(p.en, slow = true, tint = strong)
                    }
                    if (p.tip != null) {
                        VSpace(10.dp)
                        Text(
                            "💡 ${p.tip}",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SunLight).padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            VSpace(4.dp)
            BigButton("Tiếp: Nói như người bản xứ →", onClick = onNext, color = strong)
        }
    }
}

@Composable
private fun Flashcards(topic: Topic, strong: Color, light: Color, onFinish: () -> Unit) {
    val app = LocalApp.current
    val progress by app.progress.progress.collectAsState()
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember(index) { mutableStateOf(false) }
    val word = topic.words[index]
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(400), label = "flip")
    val known = word.word in progress.learnedWords

    Column(
        Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SoftProgress((index + 1f) / topic.words.size, color = strong)
        VSpace(6.dp)
        Text("${index + 1} / ${topic.words.size} · chạm thẻ để lật", color = Muted)
        VSpace(16.dp)
        // Flip animation adapted from Engrisk's flashcard animator, rebuilt with graphicsLayer.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .clip(RoundedCornerShape(28.dp))
                .background(if (rotation <= 90f) light else Color.White)
                .clickable { flipped = !flipped }
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (rotation <= 90f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(word.word, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Ink, textAlign = TextAlign.Center)
                    VSpace(6.dp)
                    Text(word.ipa, color = Muted, fontSize = 18.sp)
                    VSpace(18.dp)
                    SpeakButton(word.word, size = 56.dp, tint = strong)
                }
            } else {
                Column(
                    Modifier.graphicsLayer { rotationY = 180f },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(word.vi, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = strong, textAlign = TextAlign.Center)
                    VSpace(16.dp)
                    Text("“${word.example}”", fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, color = Ink)
                    VSpace(12.dp)
                    SpeakButton(word.example, tint = strong)
                }
            }
        }
        VSpace(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { if (known) app.progress.toggleWord(word.word) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, if (!known) Peach else Line),
            ) { Text("🤔 Chưa thuộc", color = Peach) }
            OutlinedButton(
                onClick = { if (!known) app.progress.toggleWord(word.word) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, if (known) Mint else Line),
            ) { Text("✅ Đã thuộc", color = Mint) }
        }
        VSpace(12.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { if (index > 0) index-- },
                enabled = index > 0,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("← Trước") }
            Box(Modifier.weight(1f)) {
                if (index < topic.words.lastIndex) BigButton("Tiếp →", onClick = { index++ }, color = strong)
                else BigButton("Hoàn thành", onClick = onFinish, color = Mint)
            }
        }
    }
}

/** Textbook English next to what natives actually say, so learners stop sounding translated. */
@Composable
private fun NativeList(topic: Topic, strong: Color, light: Color, onNext: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Câu “sách vở” nghe vẫn đúng nhưng cứng. Đây là cách người bản xứ nói hằng ngày:", color = Muted)
        }
        items(topic.native) { n ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Line),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "📖 ${n.plain}",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.LineThrough,
                    )
                    VSpace(8.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🗣️ ${n.natural}", style = MaterialTheme.typography.titleMedium, color = strong)
                            VSpace(4.dp)
                            Text(n.vi, color = Ink)
                        }
                        HSpace(8.dp)
                        SpeakButton(n.natural, tint = strong)
                        HSpace(6.dp)
                        SpeakButton(n.natural, slow = true, tint = strong)
                    }
                    if (n.note != null) {
                        VSpace(10.dp)
                        Text(
                            "💡 ${n.note}",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(light).padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            VSpace(4.dp)
            BigButton("Tiếp: Thẻ từ vựng →", onClick = onNext, color = strong)
        }
    }
}
