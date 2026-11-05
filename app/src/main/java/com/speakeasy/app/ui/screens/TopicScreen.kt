package com.speakeasy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Activity
import com.speakeasy.app.ui.components.EmojiBadge
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.Pill
import com.speakeasy.app.ui.components.SectionTitle
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.categoryColors

@Composable
fun TopicScreen(topicId: String, onBack: () -> Unit, open: (String) -> Unit) {
    val app = LocalApp.current
    val topic = app.content.topic(topicId) ?: return
    val category = app.content.category(topic.category)
    val progress by app.progress.progress.collectAsState()
    val (strong, light) = categoryColors(topic.category)

    Column(Modifier.fillMaxSize()) {
        TopBar(title = topic.title, subtitle = topic.titleVi, onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(light).padding(22.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill("${category?.emoji} ${category?.name}", strong, Color.White)
                        HSpace(8.dp)
                        Pill("Trình độ ${topic.level}", strong, Color.White)
                    }
                    VSpace(14.dp)
                    Text(topic.emoji, fontSize = 56.sp)
                    VSpace(6.dp)
                    Text(
                        "${topic.phrases.size} mẫu câu · ${topic.words.size} từ vựng · ${topic.dialogue.lines.size} lượt hội thoại",
                        color = Muted,
                    )
                }
            }

            SectionTitle("Lộ trình học")
            Text(
                "Làm lần lượt 4 bước: học mẫu câu → nghe & nhập vai hội thoại → luyện nói → chơi game ôn tập.",
                color = Muted,
            )
            Activity.entries.forEachIndexed { i, a ->
                StepCard(
                    index = i + 1,
                    activity = a,
                    done = progress.isDone(topic.id, a),
                    accent = strong,
                    accentLight = light,
                    onClick = {
                        open(
                            when (a) {
                                Activity.PHRASES -> "topic/${topic.id}/phrases"
                                Activity.DIALOGUE -> "topic/${topic.id}/dialogue"
                                Activity.SPEAK -> "topic/${topic.id}/speak"
                                Activity.GAME -> "game/${topic.id}/mix"
                            }
                        )
                    },
                )
            }

            // Extra practice, not one of the four tracked steps.
            Card(
                onClick = { open("topic/${topic.id}/shadow") },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Line),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge("🎙️", light, 52.dp)
                    HSpace(14.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Luyện nhại (shadowing)", style = MaterialTheme.typography.titleMedium)
                        Text("Nghe mẫu, ghi âm giọng bạn rồi so với người bản xứ", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    HSpace(8.dp)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Muted)
                }
            }
            VSpace(12.dp)
        }
    }
}

private val descriptions = mapOf(
    Activity.PHRASES to "Nghe mẫu câu thông dụng & lật thẻ từ vựng",
    Activity.DIALOGUE to "Nghe đoạn hội thoại, rồi tự nói vai của bạn",
    Activity.SPEAK to "Nói từng câu, app chấm điểm phát âm",
    Activity.GAME to "Ghép từ, xếp câu, nghe chọn đáp án",
)

@Composable
private fun StepCard(
    index: Int,
    activity: Activity,
    done: Boolean,
    accent: Color,
    accentLight: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (done) Mint.copy(alpha = 0.5f) else Line),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(activity.emoji, accentLight, 52.dp)
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Text("Bước $index", color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(activity.label, style = MaterialTheme.typography.titleMedium)
                Text(descriptions.getValue(activity), color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            HSpace(8.dp)
            if (done) Icon(Icons.Filled.CheckCircle, contentDescription = "Đã xong", tint = Mint)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Muted)
                Text("+${activity.xp}", color = Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
