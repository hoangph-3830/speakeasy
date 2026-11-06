package com.speakeasy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Peach
import com.speakeasy.app.ui.theme.PeachLight
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight

/** Shown on Home until the placement test is taken: lets a learner skip what they already know. */
@Composable
internal fun PlacementCard(onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SkyLight).clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("🧭 Kiểm tra trình độ · 3 phút", style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("Bỏ qua phần đã biết", color = Muted)
        }
        Text("▶", color = Sky, fontSize = 20.sp)
    }
}

/** The evening warning that today's streak will break without a lesson. */
@Composable
internal fun StreakRiskCard(streak: Int) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PeachLight)
            .border(2.dp, Peach, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text("🔥 Chuỗi $streak ngày sắp mất!", fontWeight = FontWeight.ExtraBold, color = Color(0xFFC2410C), fontSize = 17.sp)
        Text("Học 1 bài ngắn để giữ chuỗi", color = Ink)
    }
}
