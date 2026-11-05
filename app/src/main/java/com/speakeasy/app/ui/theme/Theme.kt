package com.speakeasy.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Sky = Color(0xFF2B9FDB)
val SkyLight = Color(0xFFE3F4FD)
val Mint = Color(0xFF3CC497)
val MintLight = Color(0xFFE2F8EF)
val Peach = Color(0xFFFF9466)
val PeachLight = Color(0xFFFFEDE4)
val Lavender = Color(0xFF8C73F0)
val LavenderLight = Color(0xFFEFEAFE)
val Sun = Color(0xFFFFC23D)
val SunLight = Color(0xFFFFF5DB)
val Coral = Color(0xFFF2667A)
val CoralLight = Color(0xFFFDE8EB)
val Ink = Color(0xFF1F2A44)
val Muted = Color(0xFF6B7590)
val Canvas = Color(0xFFF7FBFF)
val Line = Color(0xFFE6ECF5)

private val colors = lightColorScheme(
    primary = Sky,
    onPrimary = Color.White,
    primaryContainer = SkyLight,
    onPrimaryContainer = Ink,
    secondary = Mint,
    onSecondary = Color.White,
    secondaryContainer = MintLight,
    onSecondaryContainer = Ink,
    tertiary = Peach,
    tertiaryContainer = PeachLight,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F5FB),
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Line,
    error = Coral,
)

private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

/** Category accent colors: strong for text/icons, light for card backgrounds. */
fun categoryColors(categoryId: String): Pair<Color, Color> = when (categoryId) {
    "work" -> Sky to SkyLight
    "life" -> Mint to MintLight
    "travel" -> Peach to PeachLight
    "social" -> Lavender to LavenderLight
    "home" -> Sun to SunLight
    "health" -> Coral to CoralLight
    else -> Sky to SkyLight
}

/** Level accent colors, easy to hard: A1 mint, A2 sky, B1 lavender, B2 peach. */
fun levelColors(level: String): Pair<Color, Color> = when (level) {
    "A1" -> Mint to MintLight
    "A2" -> Sky to SkyLight
    "B1" -> Lavender to LavenderLight
    "B2" -> Peach to PeachLight
    else -> Sky to SkyLight
}

@Composable
fun SpeakEasyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
