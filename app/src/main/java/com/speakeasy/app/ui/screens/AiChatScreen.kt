package com.speakeasy.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.AiException
import com.speakeasy.app.data.AiLevel
import com.speakeasy.app.data.AiTutor
import com.speakeasy.app.data.ChatTurn
import com.speakeasy.app.data.Scenario
import com.speakeasy.app.data.Quests
import com.speakeasy.app.speech.rememberSpeechAlternatives
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.EmojiBadge
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.MicButton
import com.speakeasy.app.ui.components.SectionTitle
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import com.speakeasy.app.ui.theme.Sun
import com.speakeasy.app.ui.theme.SunLight
import com.speakeasy.app.ui.theme.categoryColors
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import com.speakeasy.app.ui.TestTags

/** Picks who the AI should be and how hard it should talk. */
@Composable
fun AiHubScreen(openChat: (String) -> Unit) {
    val app = LocalApp.current
    val level by app.ai.level.collectAsState()
    val scenarios = remember { AiTutor.scenarios(app.content.content) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Trò chuyện với AI 🤖", style = MaterialTheme.typography.headlineSmall)
            Text("Nói gì cũng được. AI đáp như người bản xứ và sửa từng câu cho bạn.", color = Muted)
        }
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Line),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Độ khó khi AI nói", fontWeight = FontWeight.SemiBold)
                    VSpace(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiLevel.entries.forEach { l ->
                            FilterChip(
                                selected = level == l,
                                onClick = { app.ai.setLevel(l) },
                                label = { Text(l.label) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        item { SectionTitle("Chọn tình huống") }
        items(scenarios, key = { it.id }) { s ->
            val topicId = s.id.removePrefix("topic:")
            val (strong, light) = app.content.topic(topicId)?.let { categoryColors(it.category) } ?: (Sun to SunLight)
            Card(
                onClick = { openChat(s.id) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Line),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(s.emoji, light, 50.dp)
                    HSpace(14.dp)
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium)
                        Text("AI đóng vai: ${s.aiRole}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("▶", color = strong, fontSize = 18.sp)
                }
            }
        }
        item {
            Text(
                "Câu bạn nói được gửi tới Google Gemini để AI trả lời. Các phần học khác vẫn chạy offline.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AiChatScreen(scenarioId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val scenario = remember { AiTutor.scenarios(app.content.content).firstOrNull { it.id == scenarioId } } ?: return
    val level by app.ai.level.collectAsState()
    val autoSpeak by app.ai.autoSpeak.collectAsState()
    val tutor = remember { AiTutor() }
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf(ChatTurn(fromUser = true, text = AiTutor.START_CUE, hidden = true)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var showVi by remember { mutableStateOf(false) }
    // Voice call (Duolingo Max style): hands-free, the mic opens by itself after the AI speaks.
    var callMode by remember { mutableStateOf(false) }
    var listenTick by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    fun ask() {
        loading = true
        error = null
        scope.launch {
            try {
                val r = tutor.reply(scenario, level, turns.toList())
                // Attach the correction to the learner line it was about.
                val lastUser = turns.indexOfLast { it.fromUser }
                if (lastUser >= 0 && !turns[lastUser].hidden) turns[lastUser] = turns[lastUser].copy(correction = r.correction)
                turns += ChatTurn(fromUser = false, text = r.reply, raw = AiTutor.encode(r), reply = r)
                loading = false
                if (callMode) {
                    app.speaker.sayAndWait(r.reply)
                    if (callMode) listenTick++
                } else if (autoSpeak) app.speaker.say(r.reply)
            } catch (e: AiException) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || loading) return
        turns += ChatTurn(fromUser = true, text = t)
        input = ""
        app.progress.addXp(2)
        app.progress.count(Quests.SPEAK)
        ask()
    }

    // Cancelling the recognizer pauses the call; the mic button resumes it.
    val listen = rememberSpeechAlternatives(onCancel = {}) { heard -> send(heard.first()) }
    LaunchedEffect(listenTick) { if (listenTick > 0 && callMode) listen("Đến lượt bạn nói") }
    LaunchedEffect(Unit) { ask() }
    LaunchedEffect(turns.size, loading) { listState.animateScrollToItem(maxOf(0, turns.size)) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(title = scenario.title, subtitle = "AI đóng vai: ${scenario.aiRole}", onBack = onBack) {
            Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleChip("📞", callMode) {
                    callMode = !callMode
                    // Only open the mic straight away if the AI has had its turn.
                    if (callMode && !loading && error == null && !turns.last().fromUser) listenTick++
                    if (!callMode) app.speaker.stop()
                }
                if (!callMode) ToggleChip("🔊", autoSpeak) { app.ai.setAutoSpeak(!autoSpeak) }
                if (!callMode) ToggleChip("VI", showVi) { showVi = !showVi }
            }
        }
        if (callMode) {
            CallView(
                scenario = scenario,
                lastAi = turns.lastOrNull { !it.fromUser },
                lastLearner = turns.lastOrNull { it.fromUser && !it.hidden },
                loading = loading,
                error = error,
                onRetry = ::ask,
                onMic = { listen("Đến lượt bạn nói") },
                onHangUp = { callMode = false; app.speaker.stop() },
            )
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(turns.filter { !it.hidden }) { _, turn ->
                if (turn.fromUser) LearnerBubble(turn) else AiBubble(turn, showVi)
            }
            item {
                if (loading) TypingBubble()
                error?.let { msg ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Coral.copy(alpha = 0.1f)).padding(14.dp),
                    ) {
                        Text("😕 $msg", color = Ink)
                        TextButton(onClick = ::ask) { Text("Thử lại", color = Coral, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        val suggestions = turns.lastOrNull { !it.fromUser }?.reply?.suggestions.orEmpty()
        if (suggestions.isNotEmpty() && !loading) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("💡", fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
                suggestions.forEach { s ->
                    Text(
                        s,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SkyLight)
                            .clickable {
                                // Hear it, then say it yourself: the hint fills the box rather than sending.
                                app.speaker.say(s)
                                input = s
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = Ink,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicButton(onClick = { listen("Nói câu trả lời bằng tiếng Anh") }, size = 56.dp)
            HSpace(10.dp)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).testTag(TestTags.CHAT_INPUT),
                placeholder = { Text("Hoặc gõ tiếng Anh…") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
            )
            HSpace(8.dp)
            FilledIconButton(
                onClick = { send(input) },
                enabled = input.isNotBlank() && !loading,
                modifier = Modifier.size(48.dp).testTag(TestTags.CHAT_SEND),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Sky),
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi") }
        }
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) SkyLight else Color.Transparent)
            .border(1.dp, Line, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (on) Sky else Muted,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun AiBubble(turn: ChatTurn, showVi: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .widthIn(max = 310.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                .background(Color.White)
                .border(1.dp, Line, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(turn.text, color = Ink, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                HSpace(8.dp)
                SpeakButton(turn.text, size = 34.dp, tint = Sky)
            }
            val vi = turn.reply?.replyVi
            if (showVi && !vi.isNullOrBlank()) {
                VSpace(4.dp)
                Text(vi, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LearnerBubble(turn: ChatTurn) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Text(
            turn.text,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(SkyLight)
                .padding(14.dp),
            color = Ink,
            style = MaterialTheme.typography.bodyLarge,
        )
        val c = turn.correction ?: return@Column
        VSpace(6.dp)
        if (!c.hasIssue) {
            Text("✅ Tự nhiên lắm!", color = Mint, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        } else {
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MintLight)
                    .padding(12.dp),
            ) {
                Text("✨ Người bản xứ sẽ nói:", color = Mint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                VSpace(4.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.corrected, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    HSpace(8.dp)
                    SpeakButton(c.corrected, size = 32.dp, tint = Mint)
                }
                if (c.explanationVi.isNotBlank()) {
                    VSpace(4.dp)
                    Text("💡 ${c.explanationVi}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    val pulse = rememberInfiniteTransition(label = "typing")
    val a by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "alpha")
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text("• • •", color = Muted, fontSize = 20.sp, modifier = Modifier.alpha(a))
    }
}

/** Hands-free call screen: big avatar and no text unless asked, so the learner has to listen. */
@Composable
private fun CallView(
    scenario: Scenario,
    lastAi: ChatTurn?,
    lastLearner: ChatTurn?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onMic: () -> Unit,
    onHangUp: () -> Unit,
) {
    val speaker = LocalApp.current.speaker
    val speaking by speaker.speaking.collectAsState()
    var showText by remember(lastAi) { mutableStateOf(false) }
    val pulse = rememberInfiniteTransition(label = "call")
    val scale by pulse.animateFloat(1f, 1.12f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    val talking = speaking != null && speaking == lastAi?.text
    val name = scenario.aiRole.substringBefore(",")

    Column(
        Modifier.fillMaxSize().padding(24.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VSpace(16.dp)
        Box(
            Modifier
                .size(170.dp)
                .scale(if (talking) scale else 1f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(SkyLight, MintLight))),
            contentAlignment = Alignment.Center,
        ) { Text(scenario.emoji, fontSize = 72.sp) }
        VSpace(14.dp)
        Text(name, style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center)
        Text(
            when {
                error != null -> "Mất kết nối"
                loading -> "Đang nghĩ…"
                talking -> "Đang nói… 🎧"
                else -> "Đến lượt bạn 🎤"
            },
            color = Muted,
        )
        VSpace(16.dp)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (error != null) {
                Text("😕 $error", color = Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Thử lại", color = Coral, fontWeight = FontWeight.Bold)
                }
            }
            if (lastAi != null) {
                if (showText) AiBubble(lastAi, showVi = true)
                else TextButton(onClick = { showText = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("👀 Không nghe kịp? Xem chữ", color = Sky)
                }
            }
            val c = lastLearner?.correction
            if (c != null && c.hasIssue) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MintLight).padding(12.dp)) {
                    Text("✨ Câu vừa rồi, người bản xứ sẽ nói:", color = Mint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.corrected, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        SpeakButton(c.corrected, size = 32.dp, tint = Mint)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            MicButton(onClick = onMic, size = 72.dp)
            FilledIconButton(
                onClick = onHangUp,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Coral),
            ) { Text("📵", fontSize = 28.sp) }
        }
        VSpace(6.dp)
        Text("🎤 Nói    ·    📵 Kết thúc gọi", color = Muted, style = MaterialTheme.typography.labelMedium)
    }
}
