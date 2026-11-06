package com.speakeasy.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.DailyReminder
import com.speakeasy.app.data.Progress
import com.speakeasy.app.reminder.ReminderScheduler
import com.speakeasy.app.ui.components.VSpace
import com.speakeasy.app.ui.theme.Line
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.Sky

private fun needsPermission(context: Context) =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** "⏰ Nhắc học mỗi ngày": on/off and the time, kept in progress and applied to the daily alarm at once. */
@Composable
internal fun ReminderSettings(p: Progress) {
    val app = LocalApp.current
    val context = LocalContext.current
    fun update(on: Boolean, time: String) {
        app.progress.setReminder(on, time)
        ReminderScheduler.apply(context, on, time)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) update(true, p.reminderTime)
        else Toast.makeText(context, "Hãy cho phép SpeakEasy gửi thông báo trong Cài đặt để nhận lời nhắc.", Toast.LENGTH_LONG).show()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("⏰ Nhắc học mỗi ngày", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(
            checked = p.reminderOn,
            onCheckedChange = { on ->
                when {
                    !on -> update(false, p.reminderTime)
                    needsPermission(context) -> askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> update(true, p.reminderTime)
                }
            },
        )
    }
    if (p.reminderOn) {
        VSpace(6.dp)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp))
                .clickable {
                    val t = DailyReminder.parse(p.reminderTime)
                    TimePickerDialog(context, { _, h, m -> update(true, DailyReminder.format(h, m)) }, t.hour, t.minute, true).show()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Giờ nhắc", modifier = Modifier.weight(1f))
            Text(p.reminderTime, color = Sky, fontWeight = FontWeight.Bold)
        }
    }
    VSpace(4.dp)
    Text(
        "Nếu hôm đó bạn đã đạt mục tiêu thì sẽ không nhắc nữa.",
        color = Muted,
        style = MaterialTheme.typography.bodyMedium,
    )
}
