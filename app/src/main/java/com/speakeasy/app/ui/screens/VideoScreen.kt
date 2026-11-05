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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Quests
import com.speakeasy.app.data.Video
import com.speakeasy.app.data.VideoChannel
import com.speakeasy.app.data.Videos
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.Pill
import com.speakeasy.app.ui.components.SectionTitle
import com.speakeasy.app.ui.components.TappableText
import com.speakeasy.app.ui.components.ToggleChip
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.components.WordSheet
import com.speakeasy.app.ui.components.YouTubePlayer
import com.speakeasy.app.ui.components.openOnYouTube
import com.speakeasy.app.ui.components.rememberWordLookups
import com.speakeasy.app.ui.components.rememberWordMarks
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.MintLight
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import com.speakeasy.app.ui.theme.SunLight
import com.speakeasy.app.ui.theme.levelColors

/** "Học qua video": official YouTube channels, one chip each, and a grid of the chosen channel's videos. */
@Composable
fun VideoHubScreen(onBack: () -> Unit, openVideo: (String) -> Unit) {
    val app = LocalApp.current
    val p by app.progress.progress.collectAsState()
    val channels = remember { app.content.videos.channels }
    // Remembered across visits, so the learner lands back on the channel they were watching.
    var handle by rememberSaveable { mutableStateOf(app.content.lastVideoChannel) }
    val selected = channels.indexOfFirst { it.handle == handle }.coerceAtLeast(0)
    val channel = channels.getOrNull(selected)
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Học qua video", subtitle = "Video từ kênh chính thức trên YouTube", onBack = onBack)
        val chipRow = rememberLazyListState()
        LaunchedEffect(chipRow, selected) { revealItem(chipRow, selected) }
        LazyRow(
            state = chipRow,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(channels, key = { _, c -> c.handle }) { i, c ->
                ChannelChip(c, on = i == selected) { handle = c.handle; app.content.lastVideoChannel = c.handle }
            }
        }
        if (channel == null) return@Column
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "about", span = { GridItemSpan(maxLineSpan) }) {
                Text(channel.about, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            items(channel.videos, key = { it.id }) { v ->
                VideoCard(v, watched = Videos.doneKey(v.id) in p.done, onClick = { openVideo(v.id) })
            }
        }
    }
}

/** Channel name with its level, plus a "Thiếu nhi" badge for channels made for children. */
@Composable
private fun ChannelChip(c: VideoChannel, on: Boolean, onClick: () -> Unit) {
    val (strong, light) = levelColors(c.level)
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (on) Sky else Color.White)
            .border(1.dp, if (on) Sky else Line, CircleShape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.name, color = if (on) Color.White else Ink, fontWeight = FontWeight.SemiBold)
        HSpace(8.dp)
        Pill(c.level, strong, if (on) Color.White else light)
        if (c.kids) {
            HSpace(4.dp)
            Pill("👶 Thiếu nhi", Ink, SunLight)
        }
    }
}

@Composable
private fun VideoCard(v: Video, watched: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Line),
    ) {
        Box {
            Thumbnail(v.id, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            if (watched) Text("✅", fontSize = 18.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            v.title,
            modifier = Modifier.padding(10.dp),
            color = Ink,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** YouTube's own thumbnail for the video, kept in Coil's disk cache once seen. */
@Composable
private fun Thumbnail(id: String, modifier: Modifier) {
    AsyncImage(
        model = Videos.thumbnail(id),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(Line),
    )
}

/** One video: YouTube's player on top, then its title to read word by word and the channel's other videos. */
@Composable
fun VideoScreen(videoId: String, onBack: () -> Unit, openVideo: (String) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val found = remember(videoId) { app.content.video(videoId) }
    val progress by app.progress.progress.collectAsState()
    var bilingual by rememberSaveable { mutableStateOf(false) }
    var word by remember { mutableStateOf<String?>(null) }
    if (found == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Học qua video", onBack = onBack)
            Text("Không tìm thấy video này.", color = Muted, modifier = Modifier.padding(20.dp))
        }
        return
    }
    val (channel, video) = found
    val (strong, light) = levelColors(channel.level)
    val watched = Videos.doneKey(video.id) in progress.done
    val marks = rememberWordMarks(rememberWordLookups(listOf(video.title)))

    Column(Modifier.fillMaxSize()) {
        TopBar(title = channel.name, subtitle = "Chạm vào từ bất kỳ để tra nghĩa", onBack = onBack) {
            Box(Modifier.padding(end = 12.dp)) { ToggleChip("Song ngữ", bilingual) { bilingual = !bilingual } }
        }
        // Outside the list, so the player stays in view (and keeps playing) while the list scrolls.
        YouTubePlayer(video.id, Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                TappableText(video.title, MaterialTheme.typography.titleLarge, Ink, marks, onWord = { word = it })
                if (bilingual) Translation(video.title)
                VSpace(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.name, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    HSpace(8.dp)
                    Pill(channel.level, strong, light)
                    if (channel.kids) {
                        HSpace(6.dp)
                        Pill("👶 Thiếu nhi", Ink, SunLight)
                    }
                }
            }
            item(key = "tip") {
                Text(
                    "💡 Bật phụ đề tiếng Anh (CC) trong trình phát để vừa nghe vừa đọc.",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SkyLight).padding(12.dp),
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { openOnYouTube(context, video.id) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, Sky),
                    ) { Text("Xem trên YouTube ↗", color = Sky, fontWeight = FontWeight.SemiBold) }
                    if (watched) {
                        Text(
                            "✅ Bạn đã xem video này",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MintLight).padding(14.dp),
                            color = Mint,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        BigButton("Đã xem ✅ +3 XP", color = Mint, onClick = {
                            app.progress.markDone(Videos.doneKey(video.id))
                            app.progress.addXp(3)
                            app.progress.count(Quests.LISTEN)
                        })
                    }
                }
            }
            val others = channel.videos.filter { it.id != video.id }
            if (others.isNotEmpty()) item(key = "others") { SectionTitle("Video khác của kênh") }
            items(others, key = { it.id }) { v ->
                OtherVideoRow(v, watched = Videos.doneKey(v.id) in progress.done, onClick = { openVideo(v.id) })
            }
        }
    }

    word?.let { WordSheet(it, onDismiss = { word = null }) }
}

@Composable
private fun OtherVideoRow(v: Video, watched: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(v.id, Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
        HSpace(12.dp)
        Text(
            v.title,
            modifier = Modifier.weight(1f),
            color = Ink,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (watched) {
            HSpace(8.dp)
            Text("✅", fontSize = 16.sp)
        }
    }
}
