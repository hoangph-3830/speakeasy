package com.speakeasy.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.LearningPath
import com.speakeasy.app.data.Progress
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Muted

/** "👪 Lộ trình dành cho": adults see the general path; children get the kids' stages first. */
@Composable
internal fun AudienceSettings(p: Progress) {
    val app = LocalApp.current
    Column {
        Text("👪 Lộ trình dành cho", fontWeight = FontWeight.SemiBold)
        VSpace(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("adult" to "🧑 Người lớn", LearningPath.KIDS to "🧸 Trẻ em").forEach { (id, label) ->
                FilterChip(
                    selected = p.audience == id,
                    onClick = { app.progress.setAudience(id) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "Trẻ em học các chủ đề thiếu nhi trước (trường lớp, con vật, sinh nhật…), rồi mới sang lộ trình chung.",
            color = Muted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
