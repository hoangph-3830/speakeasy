package com.speakeasy.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.DictEntry
import com.speakeasy.app.data.Lemmatizer
import com.speakeasy.app.data.ReviewCard
import com.speakeasy.app.data.Vocab
import com.speakeasy.app.data.WordStatus
import com.speakeasy.app.data.WordStatuses
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import com.speakeasy.app.ui.theme.Sun
import com.speakeasy.app.ui.theme.SunLight
import kotlinx.coroutines.CancellationException

// Tap-to-look-up reading shared by the news articles and the picture books: words coloured by
// status (new, learning, known), each one opening the dictionary sheet.

@Composable
fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        if (on) "$label ✓" else label,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) SkyLight else Color.Transparent)
            .border(1.dp, Line, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Sky,
        fontWeight = FontWeight.Bold,
    )
}

/** Background for a word: light yellow while learning; new, known and unknown words have none. */
fun wordStyle(status: WordStatus?): SpanStyle? =
    if (status == WordStatus.LEARNING) SpanStyle(background = SunLight) else null

/** A thin underline marks words still to learn: blue for new, yellow for learning. */
fun underline(status: WordStatus?): Color? = when (status) {
    WordStatus.NEW -> Sky.copy(alpha = 0.55f)
    WordStatus.LEARNING -> Sun
    else -> null
}

/** The review card a dictionary word becomes, the same "v:" format the vocabulary cards use. */
fun vocabCard(headword: String, vi: String, entry: DictEntry?): ReviewCard {
    val extra = listOfNotNull(entry?.ipa?.ifBlank { null }, entry?.example?.ifBlank { null }).joinToString(" · ")
    return ReviewCard("v:$headword", headword, vi, extra.ifEmpty { null })
}

val wordPattern = Regex("[A-Za-z]+(?:['’][A-Za-z]+)*")

/**
 * Text where every English word is its own link, so a tap opens it in the dictionary.
 * [marks] maps normalized words to their status, which sets the highlight.
 */
@Composable
fun TappableText(text: String, style: TextStyle, color: Color, marks: Map<String, WordStatus>, onWord: (String) -> Unit) {
    val onTap by rememberUpdatedState(onWord)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Word ranges with their underline colour; drawn by hand so the line can be thin and coloured.
    val lines = remember(text, marks) {
        wordPattern.findAll(text).mapNotNull { m -> underline(marks[Lemmatizer.normalize(m.value)])?.let { m.range to it } }.toList()
    }
    val annotated = remember(text, marks) {
        buildAnnotatedString {
            var last = 0
            wordPattern.findAll(text).forEach { m ->
                append(text.substring(last, m.range.first))
                val link = LinkAnnotation.Clickable(
                    tag = m.value,
                    styles = TextLinkStyles(
                        style = wordStyle(marks[Lemmatizer.normalize(m.value)]),
                        pressedStyle = SpanStyle(background = Sky.copy(alpha = 0.25f)),
                    ),
                ) { onTap(m.value) }
                withLink(link) { append(m.value) }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
    Text(
        annotated,
        style = style,
        color = color,
        onTextLayout = { layout = it },
        modifier = Modifier.drawBehind {
            val l = layout ?: return@drawBehind
            val stroke = 2.dp.toPx()
            for ((range, c) in lines) {
                if (range.last >= l.layoutInput.text.length) continue
                val line = l.getLineForOffset(range.first)
                val y = l.getLineBaseline(line) + stroke * 1.5f
                drawLine(c, Offset(l.getBoundingBox(range.first).left, y), Offset(l.getBoundingBox(range.last).right, y), strokeWidth = stroke)
            }
        },
    )
}

/** Dictionary card for a tapped word: offline entry when there is one, online translation otherwise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(raw: String, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val progress by app.progress.progress.collectAsState()
    val cards by app.review.cards.collectAsState()
    val entry = remember(raw) { app.dictionary.lookup(raw) }
    val headword = entry?.word ?: Lemmatizer.normalize(raw)
    val vi by produceState(entry?.vi, raw) {
        if (entry == null) value = try {
            app.dictionary.translate(headword)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }
    val meaning = vi
    val inDeck = cards.any { it.id == "v:$headword" }
    val known = headword in progress.learnedWords

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(headword, style = MaterialTheme.typography.headlineMedium, color = Ink)
                    val details = listOfNotNull(entry?.ipa?.ifBlank { null }, entry?.pos?.ifBlank { null }?.let(Vocab::posLabel))
                    if (details.isNotEmpty()) Text(details.joinToString(" · "), color = Muted)
                }
                SpeakButton(headword, size = 52.dp)
            }
            VSpace(12.dp)
            Text(
                when {
                    meaning == null -> "Đang dịch…"
                    meaning.isEmpty() -> "Chưa tra được nghĩa, kiểm tra mạng rồi thử lại nhé."
                    else -> meaning
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (meaning.isNullOrEmpty()) Muted else Sky,
            )
            if (entry == null && !meaning.isNullOrEmpty()) {
                Text("Dịch tự động · từ này chưa có trong từ điển của app", color = Muted, style = MaterialTheme.typography.labelMedium)
            }
            if (!entry?.example.isNullOrBlank()) {
                VSpace(12.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("“${entry!!.example}”", fontStyle = FontStyle.Italic, color = Ink, modifier = Modifier.weight(1f))
                    HSpace(8.dp)
                    SpeakButton(entry.example, size = 34.dp)
                }
            }
            VSpace(20.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { app.review.add(listOf(vocabCard(headword, meaning.orEmpty(), entry))) },
                    enabled = !inDeck && !meaning.isNullOrEmpty(),
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, if (inDeck) Line else Sky),
                ) { Text(if (inDeck) "✓ Đã có trong Ôn tập" else "＋ Ôn tập", color = if (inDeck) Muted else Sky) }
                OutlinedButton(
                    onClick = { app.progress.toggleWord(headword) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, if (known) Mint else Line),
                ) { Text(if (known) "✅ Đã thuộc" else "Đã thuộc", color = if (known) Mint else Ink) }
            }
        }
    }
}

/**
 * Each distinct word in [texts], normalized and looked up once; null when the dictionary has no
 * entry worth colouring. Pair with [rememberWordMarks] for the statuses.
 */
@Composable
fun rememberWordLookups(texts: List<String>): Map<String, DictEntry?> {
    val dictionary = LocalApp.current.dictionary
    return remember(texts) {
        texts.flatMap { t -> wordPattern.findAll(t).map { Lemmatizer.normalize(it.value) } }
            .distinct()
            .associateWith { WordStatuses.colourable(dictionary.lookup(it)) }
    }
}

/** The status of every looked-up word, following the learned set and the review deck as they change. */
@Composable
fun rememberWordMarks(lookups: Map<String, DictEntry?>): Map<String, WordStatus> {
    val app = LocalApp.current
    val progress by app.progress.progress.collectAsState()
    val cards by app.review.cards.collectAsState()
    val deckIds = remember(cards) { cards.map { it.id }.toSet() }
    return remember(lookups, progress.learnedWords, deckIds) {
        lookups.mapValues { (_, e) -> WordStatuses.of(e?.word, progress.learnedWords, deckIds) }
    }
}
