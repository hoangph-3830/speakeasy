package com.speakeasy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Games
import com.speakeasy.app.data.Round
import com.speakeasy.app.speech.MatchResult
import com.speakeasy.app.speech.SpeechMatch
import com.speakeasy.app.ui.TestTags
import com.speakeasy.app.ui.components.BigButton
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Muted

/** Writing: the Vietnamese meaning is shown, the learner writes the English; small slips pass. */
@Composable
internal fun WriteRound(round: Round.Write, accent: Color, onDone: (Boolean) -> Unit) {
    val speaker = LocalApp.current.speaker
    var typed by remember(round) { mutableStateOf("") }
    var showHint by remember(round) { mutableStateOf(false) }
    var result by remember(round) { mutableStateOf<MatchResult?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Viết câu này bằng tiếng Anh", style = MaterialTheme.typography.titleLarge)
        Text(
            round.prompt,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(accent.copy(alpha = 0.1f)).padding(20.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        if (showHint) {
            Text("💡 Bắt đầu bằng: ${round.answer.split(" ").take(2).joinToString(" ")}…", color = Muted)
        } else {
            Text(
                "💡 Gợi ý vài chữ đầu",
                color = accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showHint = true }.padding(4.dp),
            )
        }
        OutlinedTextField(
            value = typed,
            onValueChange = { if (result == null) typed = it },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.GAME_INPUT),
            placeholder = { Text("Gõ câu tiếng Anh…") },
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            minLines = 2,
        )
        val r = result
        if (r == null) {
            BigButton(
                "Kiểm tra",
                enabled = typed.isNotBlank(),
                onClick = {
                    result = SpeechMatch.compare(round.answer, typed)
                    speaker.say(round.answer)
                },
                color = accent,
            )
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .border(1.dp, Line, RoundedCornerShape(18.dp)).padding(16.dp),
            ) {
                Text("Câu mẫu · khớp ${r.score}%", color = Muted, style = MaterialTheme.typography.labelMedium)
                VSpace(4.dp)
                ScoredText(r)
            }
            val ok = r.score >= Games.WRITE_PASS
            AnswerFeedback(ok, correctAnswer = null, onNext = { onDone(ok) })
        }
    }
}
