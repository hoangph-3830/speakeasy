package com.speakeasy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speakeasy.app.data.LearningPlan
import com.speakeasy.app.data.Progress
import com.speakeasy.app.ui.components.HSpace
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Mint
import com.speakeasy.app.ui.theme.Muted
import java.time.LocalDate

private const val WEEKS = 12

/** Grey for a day without study, then increasingly strong mint up to twice the daily goal. */
private fun heatColor(level: Int): Color = when (level) {
    0 -> Color(0xFFEDF1F7)
    1 -> Mint.copy(alpha = 0.25f)
    2 -> Mint.copy(alpha = 0.45f)
    3 -> Mint.copy(alpha = 0.7f)
    else -> Mint
}

/** The last twelve weeks as a GitHub-style grid (one column per week, seven rows), plus this week vs last. */
@Composable
internal fun StudyCalendar(p: Progress, today: Long = LocalDate.now().toEpochDay()) {
    val weeks = LearningPlan.calendarDays(p.history, today, WEEKS).chunked(7)
    val totals = LearningPlan.weekTotals(p.history, today)
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text("📅 Lịch học 12 tuần", fontWeight = FontWeight.SemiBold, color = Ink)
            VSpace(10.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                weeks.forEach { week ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day ->
                            HeatSquare(heatColor(LearningPlan.heat(day.xp, p.dailyGoal)), Modifier.fillMaxWidth().aspectRatio(1f))
                        }
                    }
                }
            }
            VSpace(10.dp)
            Text(
                "Tuần này ${totals.thisWeek} XP · tuần trước ${totals.lastWeek} XP",
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            VSpace(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ít", color = Muted, style = MaterialTheme.typography.labelMedium)
                HSpace(6.dp)
                (0..4).forEach {
                    HeatSquare(heatColor(it), Modifier.size(12.dp))
                    HSpace(3.dp)
                }
                HSpace(3.dp)
                Text("Nhiều", color = Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HeatSquare(color: Color, modifier: Modifier) =
    Box(modifier.clip(RoundedCornerShape(3.dp)).background(color))
