package com.speakeasy.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.LearningPlan
import com.speakeasy.app.data.Levels
import com.speakeasy.app.data.PlacementAnswer
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.Pill
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.levelColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Kiểm tra trình độ": three sentences per level, easiest first; stops at the first level failed.
 * The level passed is saved so "continue learning" skips what the learner already knows.
 */
@Composable
fun PlacementScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val quiz = remember { LearningPlan.placementQuiz(app.content.topics) }
    var index by remember { mutableIntStateOf(0) }
    var answers by remember { mutableStateOf(listOf<PlacementAnswer>()) }
    var picked by remember { mutableStateOf<String?>(null) }
    var placed by remember { mutableStateOf<String?>(null) }

    fun finish(all: List<PlacementAnswer>) {
        val result = LearningPlan.placementResult(all)
        app.progress.setPlacedLevel(result)
        placed = result
    }

    if (quiz.isEmpty() && placed == null) LaunchedEffect(Unit) { finish(emptyList()) }

    fun answer(option: String) {
        if (picked != null) return
        val q = quiz[index]
        val all = answers + PlacementAnswer(q.level, option == q.answer)
        picked = option
        answers = all
        scope.launch {
            delay(700)
            picked = null
            val levelDone = index == quiz.lastIndex || quiz[index + 1].level != q.level
            if (index == quiz.lastIndex || (levelDone && LearningPlan.levelFailed(all, q.level))) finish(all)
            else index++
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "🧭 Kiểm tra trình độ", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val result = placed
            if (result != null) PlacementResult(result, onContinue)
            else if (quiz.isNotEmpty()) {
                val q = quiz[index]
                val (strong, light) = levelColors(q.level)
                SoftProgress(index / quiz.size.toFloat(), color = strong)
                Pill("Cấp ${q.level} · ${Levels.name(q.level)}", strong, light)
                Text("Câu này nghĩa là gì?", color = Muted)
                Text(q.prompt, style = MaterialTheme.typography.headlineSmall, color = Ink)
                q.options.forEach { option ->
                    val look = when {
                        picked == null -> 0
                        option == q.answer -> 1
                        option == picked -> 2
                        else -> 0
                    }
                    ChallengeOption(option, look, enabled = picked == null) { answer(option) }
                }
                Text(
                    "Không biết thì cứ chọn đại, bài kiểm tra sẽ dừng khi tới cấp vừa sức bạn.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PlacementResult(placed: String, onContinue: () -> Unit) {
    val study = LearningPlan.studyLevel(placed)
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🧭", fontSize = 56.sp)
        VSpace(8.dp)
        Text(
            "Trình độ của bạn: " + if (placed.isEmpty()) "Mới bắt đầu" else "$placed · ${Levels.name(placed)}",
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
            textAlign = TextAlign.Center,
        )
        VSpace(8.dp)
        Text(
            "Bắt đầu học từ cấp $study · ${Levels.name(study)}",
            color = Sky,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        VSpace(6.dp)
        Text(
            "Mục \"Học tiếp\" sẽ bỏ qua các chặng dễ hơn. Bạn vẫn có thể mở lại chúng bất cứ lúc nào.",
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        VSpace(24.dp)
        BigButton("Học tiếp →", onClick = onContinue, color = Sky)
        VSpace(8.dp)
        Text("Kết quả đã được lưu.", color = Muted, style = MaterialTheme.typography.labelMedium)
    }
}
