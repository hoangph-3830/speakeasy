package com.speakeasy.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Article
import com.speakeasy.app.data.NewsCache
import com.speakeasy.app.data.NewsItem
import com.speakeasy.app.data.NewsParser
import com.speakeasy.app.data.NewsSource
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.ReadingStats
import com.speakeasy.app.data.WordStatus
import com.speakeasy.app.data.WordStatuses
import com.speakeasy.app.speech.Sentences
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.Pill
import com.speakeasy.app.ui.components.SoftProgress
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TappableText
import com.speakeasy.app.ui.components.ToggleChip
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.components.WordSheet
import com.speakeasy.app.ui.components.rememberWordLookups
import com.speakeasy.app.ui.components.rememberWordMarks
import com.speakeasy.app.ui.components.underline
import com.speakeasy.app.ui.components.vocabCard
import com.speakeasy.app.ui.components.wordStyle
import com.speakeasy.app.ui.theme.CoralLight
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Peach
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val AUTO_REFRESH_MS = 5 * 60_000L
private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** Real headlines from eight publishers, read in English with a tap-to-translate dictionary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(openArticle: (String) -> Unit) {
    val app = LocalApp.current
    var source by rememberSaveable { mutableStateOf(app.news.lastSource) }
    val choose: (NewsSource) -> Unit = { source = it; app.news.lastSource = it }
    var bilingual by rememberSaveable { mutableStateOf(false) }
    var cache by remember(source) { mutableStateOf(app.news.cached(source)) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember(source) { mutableStateOf(false) }
    var refreshes by remember { mutableIntStateOf(0) }
    var manual by remember { mutableStateOf(false) }
    val refresh: () -> Unit = { manual = true; refreshes++ }

    // Refreshes every few minutes while the tab is open; leaving the tab cancels it.
    LaunchedEffect(source, refreshes) {
        // Coming back from an article should not refetch a list that is still fresh.
        val age = System.currentTimeMillis() - (cache?.fetchedAt ?: 0L)
        if (!manual && age in 0 until AUTO_REFRESH_MS) delay(AUTO_REFRESH_MS - age)
        manual = false
        while (true) {
            loading = true
            failed = try {
                cache = app.news.fetch(source)
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                true
            }
            loading = false
            delay(AUTO_REFRESH_MS)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Đọc báo 📰", style = MaterialTheme.typography.headlineSmall)
                Text(
                    cache?.let { "Cập nhật lúc ${clock.format(Instant.ofEpochMilli(it.fetchedAt).atZone(ZoneId.systemDefault()))}" }
                        ?: "Tin mới từ BBC, CNN, Reuters, The Guardian, NPR, Al Jazeera, ABC News & The Conversation.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ToggleChip("Song ngữ", bilingual) { bilingual = !bilingual }
            IconButton(onClick = refresh, enabled = !loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Làm mới")
            }
        }
        // Both rows keep their scroll position across refreshes and bring the chosen chip into view.
        val brands = remember { NewsSource.brands }
        val brandRow = rememberLazyListState()
        val sectionRow = rememberLazyListState()
        val sections = source.sections
        ChipRow(brandRow, brands, brands.indexOf(source.brand), label = { it }) { brand ->
            if (brand != source.brand) choose(NewsSource.entries.first { it.brand == brand })
        }
        if (sections.size > 1) {
            ChipRow(sectionRow, sections, sections.indexOf(source), label = { it.label }, small = true, onSelect = choose)
        }
        PullToRefreshBox(
            isRefreshing = loading && cache != null,
            onRefresh = refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            val items = cache?.items.orEmpty()
            when {
                items.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Sky)
                }
                items.isEmpty() -> NetworkError(
                    "Không tải được tin. Kiểm tra kết nối mạng rồi thử lại nhé.",
                    onRetry = refresh,
                )
                else -> NewsList(cache!!, failed, bilingual, openArticle)
            }
        }
    }
}

/** A horizontally scrolling row of chips; the [selected] one is scrolled into view whenever it changes. */
@Composable
private fun <T> ChipRow(
    state: LazyListState,
    options: List<T>,
    selected: Int,
    label: (T) -> String,
    small: Boolean = false,
    onSelect: (T) -> Unit,
) {
    LaunchedEffect(state, selected, options) { revealItem(state, selected) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = if (small) 4.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(options) { i, option ->
            val on = i == selected
            Text(
                label(option),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) (if (small) SkyLight else Sky) else Color.White)
                    .border(1.dp, if (on && small) Sky else Line, CircleShape)
                    .clickable { onSelect(option) }
                    .padding(horizontal = if (small) 12.dp else 14.dp, vertical = if (small) 7.dp else 9.dp),
                color = if (on) (if (small) Sky else Color.White) else Ink,
                fontWeight = FontWeight.SemiBold,
                style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** Scrolls just enough to show item [index] whole, leaving the row alone when it already is. */
internal suspend fun revealItem(state: LazyListState, index: Int) {
    if (index < 0) return
    val info = state.layoutInfo
    val shown = info.visibleItemsInfo.firstOrNull { it.index == index }
    when {
        shown == null -> state.animateScrollToItem(index)
        shown.offset < info.viewportStartOffset + info.beforeContentPadding ->
            state.animateScrollBy((shown.offset - info.viewportStartOffset - info.beforeContentPadding).toFloat())
        shown.offset + shown.size > info.viewportEndOffset - info.afterContentPadding ->
            state.animateScrollBy((shown.offset + shown.size - info.viewportEndOffset + info.afterContentPadding).toFloat())
    }
}

@Composable
private fun NewsList(cache: NewsCache, offline: Boolean, bilingual: Boolean, openArticle: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (offline) item {
            Text(
                "📴 Đang ngoại tuyến, hiển thị tin đã lưu.",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CoralLight).padding(10.dp),
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(cache.items, key = { it.link }) { item -> NewsCard(item, bilingual, onClick = { openArticle(item.link) }) }
    }
}

@Composable
private fun NewsCard(item: NewsItem, bilingual: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(item.source.tag, Sky, SkyLight)
                item.published?.let {
                    HSpace(8.dp)
                    Text(NewsParser.relativeTime(it), color = Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            VSpace(8.dp)
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = Ink)
            if (bilingual) Translation(item.title)
            if (item.summary.isNotBlank()) {
                VSpace(6.dp)
                Text(item.summary, color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            }
        }
    }
}

/** Vietnamese under an English line, fetched the first time the line scrolls into view. */
@Composable
internal fun Translation(text: String) {
    val app = LocalApp.current
    val vi by produceState<String?>(null, text) {
        value = try {
            app.dictionary.translate(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }
    val shown = vi
    Text(
        when {
            shown == null -> "Đang dịch…"
            shown.isEmpty() -> "Chưa dịch được (cần mạng)."
            else -> shown
        },
        modifier = Modifier.padding(top = 4.dp),
        color = Muted,
        fontStyle = if (shown.isNullOrEmpty()) FontStyle.Italic else FontStyle.Normal,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun NetworkError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📡", fontSize = 56.sp)
        VSpace(12.dp)
        Text(message, textAlign = TextAlign.Center, color = Ink)
        VSpace(16.dp)
        BigButton("Thử lại", onClick = onRetry)
    }
}

private sealed interface ArticleState {
    data object Loading : ArticleState
    data class Ready(val article: Article) : ArticleState
    data object Failed : ArticleState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(link: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val item = remember { app.news.item(link) }
    val progress by app.progress.progress.collectAsState()
    var attempt by remember { mutableIntStateOf(0) }
    var bilingual by rememberSaveable { mutableStateOf(false) }
    var word by remember { mutableStateOf<String?>(null) }
    var earned by remember { mutableStateOf<Int?>(null) }
    // Reuters blocks scripted visits, so its story is never fetched: the headline is studied here.
    val headlinesOnly = item?.source?.headlinesOnly == true || "news.google.com" in link
    val publisher = item?.source?.publisher ?: when {
        "cnn.com" in link -> "CNN"
        headlinesOnly -> "Reuters"
        "theguardian.com" in link -> "The Guardian"
        "npr.org" in link -> "NPR"
        "aljazeera.com" in link -> "Al Jazeera"
        "abc.net.au" in link -> "ABC News"
        "theconversation.com" in link -> "The Conversation"
        else -> "BBC News"
    }
    val state by produceState<ArticleState>(ArticleState.Loading, attempt) {
        if (headlinesOnly) {
            value = ArticleState.Ready(Article(item?.title.orEmpty(), emptyList()))
            return@produceState
        }
        value = ArticleState.Loading
        value = try {
            ArticleState.Ready(app.news.article(link, item?.title.orEmpty()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ArticleState.Failed
        }
    }
    val openOriginal = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } }
    val finished = "news:$link" in progress.done
    val article = (state as? ArticleState.Ready)?.article
    val title = article?.title?.ifBlank { null } ?: item?.title.orEmpty()
    val paragraphs = article?.paragraphs.orEmpty()
    val cards by app.review.cards.collectAsState()
    val deckIds = remember(cards) { cards.map { it.id }.toSet() }
    // Each distinct word on the page looked up once; the statuses follow the learned set and the deck.
    val shownTexts = listOf(title) + paragraphs.ifEmpty { listOfNotNull(item?.summary?.ifBlank { null }) }
    val lookups = rememberWordLookups(shownTexts)
    val marks = rememberWordMarks(lookups)
    val headwords = remember(lookups) { lookups.values.filterNotNull().distinctBy { it.word } }
    val stats = ReadingStats.of(headwords.map { it.word }) { WordStatuses.of(it, progress.learnedWords, deckIds) }
    val newWords = headwords.filter { WordStatuses.of(it.word, progress.learnedWords, deckIds) == WordStatus.NEW }

    val listState = rememberLazyListState()
    // Part being read aloud by "Đọc cả bài": 0 is the title, 1.. the paragraphs; null when not reading.
    var reading by remember { mutableStateOf<Int?>(null) }
    // Bumped per read-through, so a stopped one finishing late does not clear the next one.
    var readRun by remember { mutableIntStateOf(0) }
    val readAll = {
        val owners = mutableListOf<Int>()
        val chunks = (listOf(title) + paragraphs).flatMapIndexed { part, text ->
            Sentences.split(text).also { c -> repeat(c.size) { owners += part } }
        }
        val run = ++readRun
        reading = 0
        app.speaker.sayAll(
            chunks,
            onChunk = { if (run == readRun) reading = owners[it] },
            onDone = { if (run == readRun) reading = null },
        )
    }
    // The paragraphs follow the title item and, when shown, the reading summary card.
    val firstParagraphItem = if (stats.total > 0) 2 else 1
    LaunchedEffect(reading) {
        val part = reading ?: return@LaunchedEffect
        val index = if (part == 0) 0 else firstParagraphItem + part - 1
        val info = listState.layoutInfo
        val shown = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (shown == null || shown.offset < info.viewportStartOffset || shown.offset + shown.size > info.viewportEndOffset) {
            listState.animateScrollToItem(index)
        }
    }
    val stillReading by rememberUpdatedState(reading != null)
    DisposableEffect(Unit) { onDispose { if (stillReading) app.speaker.stop() } }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = publisher,
            subtitle = item?.published?.let { NewsParser.relativeTime(it) } ?: "Chạm vào từ bất kỳ để tra nghĩa",
            onBack = onBack,
        ) {
            Box(Modifier.padding(end = 12.dp)) { ToggleChip("Song ngữ", bilingual) { bilingual = !bilingual } }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                if (title.isNotBlank()) Column(Modifier.readingHighlight(reading == 0)) {
                    TappableText(title, MaterialTheme.typography.headlineSmall, Ink, marks, onWord = { word = it })
                    if (bilingual) Translation(title)
                }
                if (headlinesOnly && !item?.summary.isNullOrBlank()) {
                    VSpace(10.dp)
                    TappableText(item!!.summary, MaterialTheme.typography.bodyLarge, Ink, marks, onWord = { word = it })
                    if (bilingual) Translation(item.summary)
                }
                VSpace(6.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (headlinesOnly) "Nguồn: Reuters (tiêu đề qua Google News)" else "Nguồn: $publisher",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (!headlinesOnly) {
                        TextButton(onClick = { openOriginal() }) { Text("Mở bài gốc ↗", color = Sky, fontWeight = FontWeight.SemiBold) }
                    }
                }
                if (paragraphs.isNotEmpty()) {
                    VSpace(6.dp)
                    if (reading == null) BigButton("▶ Đọc cả bài", onClick = readAll)
                    else BigButton("⏸ Dừng đọc", color = Peach, onClick = { reading = null; app.speaker.stop() })
                }
            }
            if (state != ArticleState.Loading && stats.total > 0) item {
                ReadingSummary(stats, onAddNew = { app.review.add(newWords.map { vocabCard(it.word, it.vi, it) }) })
            }
            when {
                state == ArticleState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Sky) }
                }
                headlinesOnly -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "📰 Reuters chỉ cho đọc toàn bài trên trang của họ. Học từ trong tiêu đề ở đây, rồi bấm nút bên dưới để đọc tiếp.",
                            color = Muted,
                        )
                        BigButton("Đọc toàn bài trên Reuters ↗", onClick = { openOriginal() })
                    }
                }
                paragraphs.isEmpty() -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!item?.summary.isNullOrBlank()) {
                            TappableText(item!!.summary, MaterialTheme.typography.bodyLarge, Ink, marks, onWord = { word = it })
                            if (bilingual) Translation(item.summary)
                        }
                        Text(
                            if (state == ArticleState.Failed) "Không tải được bài báo. Kiểm tra mạng rồi thử lại, hoặc mở bài gốc trên trình duyệt."
                            else "Không lấy được nội dung bài này, bạn có thể đọc bài gốc trên trình duyệt.",
                            color = Muted,
                        )
                        if (state == ArticleState.Failed) BigButton("Thử lại", onClick = { attempt++ })
                        BigButton("Mở bài gốc", onClick = { openOriginal() }, color = Peach)
                    }
                }
                else -> itemsIndexed(paragraphs) { i, p ->
                    Row(Modifier.readingHighlight(reading == i + 1), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            TappableText(p, MaterialTheme.typography.bodyLarge, Ink, marks, onWord = { word = it })
                            if (bilingual) Translation(p)
                        }
                        HSpace(8.dp)
                        // Speaking one paragraph replaces "Đọc cả bài", whose onDone then resets its button.
                        SpeakButton(p, size = 34.dp, long = true)
                    }
                }
            }
            if (paragraphs.isNotEmpty()) item {
                VSpace(8.dp)
                if (finished) {
                    Text(
                        "✅ Bạn đã đọc xong bài này",
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MintLight).padding(14.dp),
                        color = Mint,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    BigButton("Đọc xong ✅", color = Mint, onClick = {
                        app.progress.markDone("news:$link")
                        app.progress.addXp(5)
                        app.progress.count(Quests.LISTEN)
                        earned = 5
                    })
                }
            }
        }
    }

    word?.let { WordSheet(it, onDismiss = { word = null }) }

    earned?.let { xp ->
        CompleteDialog(
            title = "Đọc xong bài báo!",
            message = "Mỗi bài báo tiếng Anh là thêm một chút vốn từ thật 📰",
            xp = xp,
            onDismiss = { earned = null },
        )
    }
}

/** Soft blue box around the part being read aloud, drawn outside the bounds so the text does not move. */
private fun Modifier.readingHighlight(on: Boolean) = drawBehind {
    if (!on) return@drawBehind
    val pad = 8.dp.toPx()
    drawRoundRect(SkyLight, Offset(-pad, -pad), Size(size.width + pad * 2, size.height + pad * 2), CornerRadius(12.dp.toPx()))
}

/** "Bạn đã biết X%" card above the article, with the colour legend and a bulk add of the new words. */
@Composable
private fun ReadingSummary(stats: ReadingStats, onAddNew: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text("Bạn đã biết ${stats.knownPercent}% từ trong bài", style = MaterialTheme.typography.titleMedium, color = Ink)
        VSpace(8.dp)
        SoftProgress(stats.known.toFloat() / stats.total, color = Mint)
        VSpace(8.dp)
        Text("${stats.new} từ mới · ${stats.learning} từ đang học", color = Muted, style = MaterialTheme.typography.bodyMedium)
        VSpace(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LegendChip("Mới", WordStatus.NEW)
            LegendChip("Đang học", WordStatus.LEARNING)
            LegendChip("Đã thuộc", WordStatus.KNOWN)
        }
        if (stats.new > 0) {
            VSpace(12.dp)
            OutlinedButton(
                onClick = onAddNew,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Sky),
            ) { Text("Thêm ${stats.new} từ mới vào ôn tập", color = Sky) }
        }
    }
}

@Composable
private fun LegendChip(label: String, status: WordStatus) {
    val line = underline(status)
    Text(
        buildAnnotatedString { withStyle(wordStyle(status) ?: SpanStyle()) { append(label) } },
        modifier = Modifier.clip(CircleShape).border(1.dp, Line, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)
            .drawBehind {
                if (line != null) drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.dp.toPx())
            },
        color = Ink,
        style = MaterialTheme.typography.labelMedium,
    )
}

