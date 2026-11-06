package com.speakeasy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Vocab
import com.speakeasy.app.data.VocabGroup
import com.speakeasy.app.data.VocabWord
import com.speakeasy.app.data.Word
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.EmojiBadge
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.Pill
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.CoralLight
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import androidx.compose.ui.platform.testTag
import com.speakeasy.app.ui.TestTags

/** The 3000 most common words, grouped by theme, with search across all of them. */
@Composable
fun VocabHubScreen(onBack: () -> Unit, openGroup: (String) -> Unit) {
    val app = LocalApp.current
    val progress by app.progress.progress.collectAsState()
    val groups = remember { app.content.vocab.groups }
    val all = remember { app.content.vocabWords }
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query) { Vocab.search(all, query) }
    val learned = all.count { it.w in progress.learnedWords }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "3000 từ vựng thông dụng", subtitle = "${all.size} từ · đã thuộc $learned", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SkyLight)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📚 Đã thuộc", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("$learned / ${all.size}", fontWeight = FontWeight.Bold, color = Sky)
                        }
                        VSpace(8.dp)
                        SoftProgress(learned / maxOf(1, all.size).toFloat(), color = Sky, track = Color.White)
                        VSpace(6.dp)
                        Text("Từ đánh dấu “đã thuộc” ở bài học chủ đề cũng được tính ở đây.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.VOCAB_SEARCH),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Tìm từ tiếng Anh hoặc nghĩa tiếng Việt") },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
                )
            }
            if (query.isNotBlank()) {
                if (hits.isEmpty()) item { Text("Không tìm thấy từ nào khớp “$query”.", color = Muted) }
                items(hits, key = { "hit:${it.w}" }) { w -> WordRow(w, w.w in progress.learnedWords, onToggle = { app.progress.toggleWord(w.w) }) }
            } else {
                items(groups.chunked(2), key = { row -> row.first().id }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { g ->
                            GroupTile(g, g.words.count { it.w in progress.learnedWords }, Modifier.weight(1f), onClick = { openGroup(g.id) })
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupTile(group: VocabGroup, learned: Int, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(14.dp)) {
            EmojiBadge(group.emoji, SkyLight, 44.dp)
            VSpace(8.dp)
            Text(group.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 2)
            Text("$learned/${group.words.size} từ", color = Muted, style = MaterialTheme.typography.bodyMedium)
            VSpace(8.dp)
            SoftProgress(learned / maxOf(1, group.words.size).toFloat(), color = if (learned == group.words.size) Mint else Sky)
        }
    }
}

@Composable
private fun WordRow(w: VocabWord, known: Boolean, onToggle: () -> Unit, showExample: Boolean = false) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.w, style = MaterialTheme.typography.titleMedium, color = Ink)
                        HSpace(8.dp)
                        if (w.pos.isNotBlank()) Pill(Vocab.posLabel(w.pos), Sky, SkyLight)
                    }
                    if (w.ipa.isNotBlank()) Text(w.ipa, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Text(w.vi, color = Ink)
                }
                HSpace(8.dp)
                SpeakButton(w.w)
            }
            if (showExample && w.ex.isNotBlank()) {
                VSpace(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("“${w.ex}”", fontStyle = FontStyle.Italic, color = Ink, style = MaterialTheme.typography.bodyMedium)
                        if (w.exVi.isNotBlank()) Text(w.exVi, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    HSpace(8.dp)
                    SpeakButton(w.ex, size = 34.dp)
                }
            }
            VSpace(8.dp)
            Text(
                if (known) "✅ Đã thuộc" else "○ Đánh dấu đã thuộc",
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (known) MintLight else Color.Transparent)
                    .border(1.dp, if (known) Mint else Line, CircleShape)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (known) Mint else Muted,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private enum class VocabMode { LIST, CARDS, QUIZ }

@Composable
fun VocabGroupScreen(groupId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val group = remember { app.content.vocabGroup(groupId) } ?: return
    val progress by app.progress.progress.collectAsState()
    val cards by app.review.cards.collectAsState()
    var mode by remember { mutableStateOf(VocabMode.LIST) }
    var notice by remember { mutableStateOf<String?>(null) }
    val learned = group.words.count { it.w in progress.learnedWords }
    val deck = remember(cards) { cards.map { it.id }.toSet() }
    val inDeck = group.words.count { "v:${it.w}" in deck }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "${group.emoji} ${group.title}",
            subtitle = "$learned/${group.words.size} đã thuộc",
            onBack = { if (mode == VocabMode.LIST) onBack() else mode = VocabMode.LIST },
        )
        when (mode) {
            VocabMode.LIST -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("🃏 Học thẻ", Modifier.weight(1f)) { mode = VocabMode.CARDS }
                        ActionChip("🎯 Kiểm tra", Modifier.weight(1f)) { mode = VocabMode.QUIZ }
                        ActionChip("🔁 Thêm vào ôn tập", Modifier.weight(1f)) {
                            val added = app.review.add(group.words.map(Vocab::card))
                            notice = if (added > 0) "Đã thêm $added thẻ vào Ôn tập 🔁" else "Các từ này đã có trong Ôn tập rồi"
                        }
                    }
                    (notice ?: "$inDeck/${group.words.size} từ đang trong bộ Ôn tập".takeIf { inDeck > 0 })?.let {
                        VSpace(8.dp)
                        Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(group.words, key = { it.w }) { w ->
                    WordRow(w, w.w in progress.learnedWords, onToggle = { app.progress.toggleWord(w.w) }, showExample = true)
                }
            }
            VocabMode.CARDS -> {
                // Study what is not learned yet; once everything is, go over the whole list again.
                val words = remember {
                    group.words.filter { it.w !in progress.learnedWords }.ifEmpty { group.words }
                        .map { Word(it.w, it.ipa, it.vi, it.ex) }
                }
                Box(Modifier.weight(1f)) {
                    Flashcards(words, Sky, SkyLight, onFinish = { mode = VocabMode.LIST })
                }
            }
            VocabMode.QUIZ -> Box(Modifier.weight(1f)) {
                Quiz(group, onExit = { mode = VocabMode.LIST })
            }
        }
    }
}

@Composable
private fun ActionChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SkyLight)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        color = Sky,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** Ten English words, pick the Vietnamese meaning; one XP per right answer. */
@Composable
private fun Quiz(group: VocabGroup, onExit: () -> Unit) {
    val app = LocalApp.current
    var round by remember { mutableIntStateOf(0) }
    val questions = remember(round) { Vocab.quiz(group.words, app.content.vocabWords) }
    var index by remember(round) { mutableIntStateOf(0) }
    var correct by remember(round) { mutableIntStateOf(0) }
    var picked by remember(round, index) { mutableStateOf<Int?>(null) }
    var earned by remember(round) { mutableStateOf<Int?>(null) }
    val q = questions.getOrNull(index) ?: return

    Column(
        Modifier.fillMaxSize().padding(20.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SoftProgress((index + 1f) / questions.size)
        VSpace(6.dp)
        Text("Câu ${index + 1} / ${questions.size} · đúng $correct", color = Muted)
        VSpace(16.dp)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(SkyLight).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(q.word.w, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Ink, textAlign = TextAlign.Center)
            if (q.word.ipa.isNotBlank()) Text(q.word.ipa, color = Muted, fontSize = 17.sp)
            VSpace(12.dp)
            SpeakButton(q.word.w, size = 52.dp)
        }
        VSpace(16.dp)
        Text("Nghĩa tiếng Việt là gì?", color = Muted)
        VSpace(10.dp)
        q.options.forEachIndexed { i, option ->
            val color = when {
                picked == null -> Line
                i == q.answer -> Mint
                i == picked -> Coral
                else -> Line
            }
            OutlinedButton(
                onClick = {
                    if (picked == null) {
                        picked = i
                        if (i == q.answer) correct++
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, color),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = when (color) {
                        Mint -> MintLight
                        Coral -> CoralLight
                        else -> Color.White
                    },
                ),
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
            ) { Text(option, color = Ink, textAlign = TextAlign.Center) }
        }
        Spacer(Modifier.weight(1f))
        if (picked != null) {
            Text(
                if (picked == q.answer) "Chính xác! 🎉" else "Đáp án: ${q.options[q.answer]}",
                color = if (picked == q.answer) Mint else Coral,
                fontWeight = FontWeight.Bold,
            )
            VSpace(10.dp)
            if (index < questions.lastIndex) BigButton("Tiếp →", onClick = { index++ })
            else BigButton("Xem kết quả", color = Mint, onClick = {
                app.progress.addXp(correct)
                earned = correct
            })
        }
    }

    earned?.let { xp ->
        CompleteDialog(
            title = "Kiểm tra xong!",
            message = "Bạn trả lời đúng $correct/${questions.size} câu.",
            xp = xp,
            onDismiss = { earned = null; onExit() },
            onAgain = { round++ },
        )
    }
}
