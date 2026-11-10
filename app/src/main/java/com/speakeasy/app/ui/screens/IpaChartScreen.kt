package com.speakeasy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Lavender
import com.speakeasy.app.ui.theme.LavenderLight
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Muted

private val ipaGroups = listOf("vowel" to "Nguyên âm đơn", "diphthong" to "Nguyên âm đôi", "consonant" to "Phụ âm")

/** The IPA chart: tap a symbol to hear a word with that sound and read how to make it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IpaChartScreen(onBack: () -> Unit) {
    val app = LocalApp.current
    val sounds = app.content.pronunciation.ipa
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val s = sounds.firstOrNull { it.symbol == picked }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "🔤 Bảng phiên âm IPA", subtitle = "${sounds.size} âm tiếng Anh · chạm để nghe", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            if (s != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LavenderLight).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("/${s.symbol}/", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Lavender)
                    HSpace(14.dp)
                    Column(Modifier.weight(1f)) {
                        Text(s.example, style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(s.hint, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    SpeakButton(s.example, size = 48.dp, tint = Lavender)
                    HSpace(8.dp)
                    SpeakButton(s.example, size = 48.dp, slow = true, tint = Lavender)
                }
            } else {
                Text("Chạm vào một ký hiệu để nghe từ ví dụ và mẹo phát âm.", color = Muted)
            }
            ipaGroups.forEach { (type, label) ->
                VSpace(18.dp)
                Text(label, style = MaterialTheme.typography.titleLarge)
                VSpace(8.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sounds.filter { it.type == type }.forEach { x ->
                        val on = x.symbol == picked
                        Column(
                            Modifier.width(72.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (on) LavenderLight else Color.White)
                                .border(2.dp, if (on) Lavender else Line, RoundedCornerShape(14.dp))
                                .clickable {
                                    picked = x.symbol
                                    app.speaker.say(x.example)
                                }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("/${x.symbol}/", fontWeight = FontWeight.Bold, color = Ink, fontSize = 18.sp)
                            Text(x.example, color = Muted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
