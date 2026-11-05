package com.speakeasy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.BookPage
import com.speakeasy.app.data.Levels
import com.speakeasy.app.data.PictureBook
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.WordStatus
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.CompleteDialog
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.Pill
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TappableText
import com.speakeasy.app.ui.components.ToggleChip
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.components.WordSheet
import com.speakeasy.app.ui.components.rememberWordLookups
import com.speakeasy.app.ui.components.rememberWordMarks
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import com.speakeasy.app.ui.theme.levelColors
import kotlinx.coroutines.launch

/** The "Truyện tranh" tab of the stories hub: every picture book, grouped by level. */
@Composable
internal fun PictureBookList(done: Set<String>, openBook: (String) -> Unit) {
    val app = LocalApp.current
    // Books come sorted by level, then length.
    val byLevel = remember { app.content.books.books.groupBy { it.level }.toList().sortedBy { Levels.rank(it.first) } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "note") {
            Text(
                "Truyện tranh có sẵn từ thư viện mở African Storybook & Global Storybooks, bản dịch tiếng Việt do người dịch thật.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        byLevel.forEach { (level, books) ->
            item(key = "level:$level") { LevelHeader(level) }
            items(books, key = { it.id }) { b -> BookCard(b, done = "book:${b.id}" in done, onClick = { openBook(b.id) }) }
        }
    }
}

@Composable
private fun BookCard(b: PictureBook, done: Boolean, onClick: () -> Unit) {
    val (levelStrong, levelLight) = levelColors(b.level)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val cover = Modifier.size(76.dp).clip(RoundedCornerShape(16.dp))
            if (b.cover == null) {
                // A few books have no pictures at all.
                Box(cover.background(MintLight), contentAlignment = Alignment.Center) { Text("📖", fontSize = 30.sp) }
            } else {
                AsyncImage(
                    model = b.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = cover.background(Line),
                )
            }
            HSpace(14.dp)
            Column(Modifier.weight(1f)) {
                Text(b.title, style = MaterialTheme.typography.titleMedium)
                Text(b.titleVi, color = Muted)
                VSpace(6.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(b.level, levelStrong, levelLight)
                    HSpace(8.dp)
                    Text("${b.pages.size} trang", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(if (done) "⭐" else "▶", color = levelStrong, fontSize = 20.sp)
        }
    }
}

/** A picture book, one swipeable page at a time, ending on the licence credits. */
@Composable
fun BookScreen(bookId: String, onBack: () -> Unit) {
    val app = LocalApp.current
    val book = remember(bookId) { app.content.book(bookId) } ?: return
    val progress by app.progress.progress.collectAsState()
    var bilingual by rememberSaveable { mutableStateOf(true) }
    var autoRead by remember { mutableStateOf(false) }
    var word by remember { mutableStateOf<String?>(null) }
    var earned by remember { mutableStateOf<Int?>(null) }
    // The last page is the credits, which the CC licences require.
    val pager = rememberPagerState { book.pages.size + 1 }
    val scope = rememberCoroutineScope()
    val lookups = rememberWordLookups(remember(book) { book.pages.map { it.en } })
    val marks = rememberWordMarks(lookups)

    // "Tự đọc": read the settled page, then turn to the next one, which settles and reads in turn.
    LaunchedEffect(autoRead, pager.settledPage) {
        if (!autoRead) return@LaunchedEffect
        val i = pager.settledPage
        val page = book.pages.getOrNull(i) ?: run { autoRead = false; return@LaunchedEffect }
        app.speaker.sayLongAndWait(page.en)
        // Something else took over the voice (a word or the 🔊 button): stop turning pages.
        if (app.speaker.speaking.value != null) {
            autoRead = false
            return@LaunchedEffect
        }
        if (i == book.pages.lastIndex) autoRead = false
        pager.animateScrollToPage(i + 1)
    }
    // A swipe by hand stops both the reading and the automatic page turns.
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) {
                autoRead = false
                app.speaker.stop()
            }
        }
    }
    DisposableEffect(Unit) { onDispose { app.speaker.stop() } }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = book.title, subtitle = book.titleVi, onBack = onBack) {
            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleChip("Song ngữ", bilingual) { bilingual = !bilingual }
                ToggleChip("▶ Tự đọc", autoRead) {
                    if (!autoRead && pager.currentPage > book.pages.lastIndex) scope.launch { pager.scrollToPage(0) }
                    if (autoRead) app.speaker.stop()
                    autoRead = !autoRead
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { i ->
            val page = book.pages.getOrNull(i)
            if (page == null) {
                BookCredits(book, done = "book:${book.id}" in progress.done, onFinish = {
                    app.progress.markDone("book:${book.id}")
                    app.progress.addXp(5)
                    app.progress.count(Quests.LISTEN)
                    earned = 5
                })
            } else {
                BookPageView(page, bilingual, marks, onWord = { autoRead = false; word = it })
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(pager.pageCount, pager.currentPage)
            VSpace(6.dp)
            Text(
                if (pager.currentPage <= book.pages.lastIndex) "Trang ${pager.currentPage + 1} / ${book.pages.size}" else "Thông tin sách",
                color = Muted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    word?.let { WordSheet(it, onDismiss = { word = null }) }

    earned?.let { xp ->
        CompleteDialog(
            title = "Đọc xong truyện tranh!",
            message = "${book.title} · ${book.titleVi}",
            xp = xp,
            onDismiss = { earned = null },
        )
    }
}

@Composable
private fun BookPageView(page: BookPage, bilingual: Boolean, marks: Map<String, WordStatus>, onWord: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The picture fits the width but never takes more than ~45% of the height, so the text stays in view.
        val imageHeight = min(maxHeight * 0.45f, maxWidth * 0.8f)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            // Pages whose illustration is missing on the CDN show only their text.
            page.img?.let {
                PageImage(it, Modifier.fillMaxWidth().height(imageHeight))
                VSpace(16.dp)
            }
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    TappableText(page.en, MaterialTheme.typography.headlineSmall, Ink, marks, onWord = onWord)
                    if (bilingual) {
                        VSpace(8.dp)
                        Text(page.vi, color = Muted, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HSpace(10.dp)
                SpeakButton(page.en, size = 44.dp, long = true)
            }
        }
    }
}

/** The page illustration on a light placeholder; Coil's disk cache keeps it for offline re-reading. */
@Composable
private fun PageImage(url: String, modifier: Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(SkyLight.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { failed = false },
            onError = { failed = true },
        )
        if (failed) Text("📡 Cần mạng để tải tranh", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The attribution the CC BY / CC BY-NC licences require, with the once-per-book "Đọc xong" reward. */
@Composable
private fun BookCredits(book: PictureBook, done: Boolean, onFinish: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("📚", fontSize = 44.sp)
        Text(book.title, style = MaterialTheme.typography.headlineSmall, color = Ink)
        Text(book.titleVi, color = Muted, style = MaterialTheme.typography.titleMedium)
        VSpace(4.dp)
        CreditLine("Tác giả", book.text)
        CreditLine("Tranh", book.illustration)
        CreditLine("Dịch", book.translation)
        CreditLine("Giấy phép", book.license)
        CreditLine("Nguồn", "African Storybook · Global Storybooks (globalstorybooks.net)")
        VSpace(12.dp)
        if (done) {
            Text(
                "✅ Bạn đã đọc xong truyện này",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MintLight).padding(14.dp),
                color = Mint,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        } else {
            BigButton("Đọc xong ✅ +5 XP", color = Mint, onClick = onFinish)
        }
    }
}

@Composable
private fun CreditLine(label: String, value: String) {
    Text(
        "$label: $value",
        color = Ink,
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** One dot per page (the credits included), the current one long and blue. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 18.dp else 7.dp, height = 7.dp)
                    .clip(CircleShape)
                    .background(if (i == current) Sky else Line),
            )
        }
    }
}
