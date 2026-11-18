package com.speakeasy.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakeasy.app.LocalApp
import com.speakeasy.app.data.Exam
import com.speakeasy.app.ui.TestTags
import com.speakeasy.app.ui.components.EmojiBadge
import com.speakeasy.app.ui.components.TopBar
import com.speakeasy.app.ui.theme.Ink
import com.speakeasy.app.ui.theme.Muted
import com.speakeasy.app.ui.theme.SkyLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Imported exam files are small; anything bigger is not an exam file. */
private const val MAX_IMPORT_BYTES = 5_000_000

/** Every exam (null while loading); loads the built-in ones off the main thread on first use. */
@Composable
fun rememberExams(): List<Exam>? {
    val library = LocalApp.current.content.exams
    val exams by library.exams.collectAsState()
    LaunchedEffect(library) { withContext(Dispatchers.IO) { library.load() } }
    return exams
}

/** The "🎓 Luyện thi" entry on Home, under the small shortcuts. */
@Composable
fun ExamShortcut(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(TestTags.shortcut("exam")),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SkyLight),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎓", fontSize = 22.sp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Luyện thi", color = Ink, fontWeight = FontWeight.Bold)
                Text("TOEIC · IELTS · đề của bạn", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text("→", color = Ink)
        }
    }
}

/** "Luyện thi": the built-in TOEIC and IELTS, the learner's imported exams, and importing a new one. */
@Composable
fun ExamListScreen(onBack: () -> Unit, openExam: (String) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exams = rememberExams()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readNBytesCompat(MAX_IMPORT_BYTES + 1) }
                    require(bytes.size <= MAX_IMPORT_BYTES) { "File quá lớn, không phải file đề." }
                    "Đã nhập “${app.content.exams.import(bytes.decodeToString()).title}” ✅"
                }.getOrElse { it.message ?: "Không đọc được file đề." }
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar("🎓 Luyện thi", "TOEIC · IELTS · đề của bạn", onBack = onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (exams == null) item { Text("Đang tải…", color = Muted) }
            else items(exams, key = { it.id }) { e ->
                Card(
                    onClick = { openExam(e.id) },
                    modifier = Modifier.fillMaxWidth().testTag("exam_${e.id}"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(e.emoji, SkyLight, 56.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(e.title + if (e.local) " (đề của bạn)" else "", color = Ink, fontWeight = FontWeight.Bold)
                            Text(e.about, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        }
                        Text("→", color = Ink)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { pick.launch(arrayOf("application/json", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth().testTag("btn_exam_import"),
                ) { Text("📥 Nhập đề riêng (file .json)") }
            }
            item {
                Text(
                    "Đề nhập chỉ lưu trong máy này, không đăng lên mạng. Câu hỏi ở đây là bài soạn mới theo dạng đề thi, " +
                        "không phải đề thi chính thức của ETS hay IELTS.",
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Reads at most [limit] bytes, so a huge file picked by mistake is not loaded whole. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
