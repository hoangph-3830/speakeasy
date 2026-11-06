package com.speakeasy.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.VocabGroup
import com.speakeasy.app.data.WordChallenges
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.SpeakButton
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Muted
import kotlinx.coroutines.delay

/** Ten words only heard, never shown: type each one back letter for letter. */
@Composable
internal fun SpellingGame(groups: List<VocabGroup>, accent: Color, onFinish: (Int, String?) -> Unit) {
    val speaker = LocalApp.current.speaker
    val words = remember { WordChallenges.spellingWords(groups) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    if (words.isEmpty()) {
        LaunchedEffect(Unit) { onFinish(0, null) }
        return
    }
    val word = words[index]
    var typed by remember(index) { mutableStateOf("") }
    var result by remember(index) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(index) { delay(300); speaker.say(word.w) }

    fun check() {
        if (result != null || typed.isBlank()) return
        val ok = WordChallenges.spelledRight(typed, word.w)
        if (ok) score++
        result = ok
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RoundLine(index, words.size, score)
        Text("Nghe rồi gõ lại từ tiếng Anh. Nghe bao nhiêu lần cũng được.", color = Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            SpeakButton(word.w, size = 72.dp, tint = accent)
            HSpace(16.dp)
            SpeakButton(word.w, size = 72.dp, slow = true, tint = accent)
        }
        Text("🔊 Nghe lại · 🐢 Nghe chậm", color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedTextField(
            value = typed,
            onValueChange = { if (result == null) typed = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Gõ từ bạn nghe được…") },
            singleLine = true,
            readOnly = result != null,
            shape = RoundedCornerShape(18.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { check() }),
        )
        val r = result
        if (r == null) {
            BigButton("Kiểm tra", enabled = typed.isNotBlank(), color = accent, onClick = ::check)
        } else {
            RoundVerdict(r, last = index == words.lastIndex, onNext = {
                if (index == words.lastIndex) onFinish(score, null) else index++
            }) {
                Text("${word.w} ${word.ipa} · ${word.vi}", color = Ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
