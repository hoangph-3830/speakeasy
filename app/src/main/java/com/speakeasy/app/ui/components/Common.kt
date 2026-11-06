package com.speakeasy.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.ui.TestTags
import com.speakeasy.app.ui.theme.Coral
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sky
import com.speakeasy.app.ui.theme.SkyLight
import com.speakeasy.app.ui.theme.Sun

@Composable
fun TopBar(title: String, subtitle: String? = null, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag(TestTags.BACK)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 1)
        }
        trailing()
    }
}

/**
 * Speaker button that pulses while its own [text] is being read aloud.
 * [long] reads it sentence by sentence, for paragraphs too long for one utterance.
 */
@Composable
fun SpeakButton(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    slow: Boolean = false,
    tint: Color = Sky,
    long: Boolean = false,
) {
    val speaker = LocalApp.current.speaker
    val speaking by speaker.speaking.collectAsState()
    val active = speaking == text
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.15f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "scale")
    FilledIconButton(
        onClick = { if (long) speaker.sayLong(text) else speaker.say(text, slow) },
        modifier = modifier.size(size).scale(if (active) scale else 1f).testTag(if (slow) TestTags.SPEAK_SLOW else TestTags.SPEAK),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) tint else tint.copy(alpha = 0.14f),
            contentColor = if (active) Color.White else tint,
        ),
    ) {
        if (slow) Text("🐢", fontSize = (size.value * 0.4f).sp)
        else Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Nghe", modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun MicButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(size).testTag(TestTags.MIC),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Coral, contentColor = Color.White),
    ) {
        Icon(Icons.Filled.Mic, contentDescription = "Nói", modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Sky, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun Pill(text: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun SoftProgress(progress: Float, modifier: Modifier = Modifier, color: Color = Sky, track: Color = Line) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
        color = color,
        trackColor = track,
        strokeCap = StrokeCap.Round,
        drawStopIndicator = {},
    )
}

@Composable
fun Ring(progress: Float, size: Dp, color: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(size),
            color = color.copy(alpha = 0.18f),
            strokeWidth = 8.dp,
        )
        // A round cap would draw a dot at 0%, so skip the arc until there is progress.
        if (progress > 0f) CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = 8.dp,
            strokeCap = StrokeCap.Round,
            trackColor = Color.Transparent,
        )
        content()
    }
}

/** Celebration shown when an activity is finished. */
@Composable
fun CompleteDialog(title: String, message: String, xp: Int, onDismiss: () -> Unit, onAgain: (() -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🎉", fontSize = 44.sp) },
        title = { Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Pill("+$xp XP", color = Color(0xFF8A5A00), background = Sun.copy(alpha = 0.35f))
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp), modifier = Modifier.testTag(TestTags.DIALOG_CONTINUE)) { Text("Tiếp tục") }
        },
        dismissButton = onAgain?.let { { TextButton(onClick = it, modifier = Modifier.testTag(TestTags.DIALOG_AGAIN)) { Text("Làm lại") } } },
        modifier = Modifier.dialogTestTag(TestTags.DIALOG_COMPLETE),
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
    )
}

/**
 * Tags a dialog's root. A dialog is its own window, outside the app root that turns tags into
 * resource-ids, so it has to switch that on again for itself and its buttons.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.dialogTestTag(tag: String) = semantics { testTagsAsResourceId = true }.testTag(tag)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmojiBadge(emoji: String, background: Color = SkyLight, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(background), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

val ScreenPadding = Arrangement.spacedBy(12.dp)
