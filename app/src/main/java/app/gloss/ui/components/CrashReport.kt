package app.gloss.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.gloss.ui.theme.Gloss

/** Offered on the launch after a crash, so the report can be sent along instead of lost. */
@Composable
fun CrashReportDialog(report: String, onDismiss: () -> Unit) {
    val g = Gloss.colors
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(g.raised, RoundedCornerShape(22.dp))
                .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Gloss closed unexpectedly", style = Gloss.type.title2, color = g.text)
            Text(
                "Sharing this report shows exactly where it went wrong, so it can be fixed.",
                style = Gloss.type.subhead, color = g.secondary,
            )
            Text(
                report,
                style = Gloss.type.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp),
                color = g.secondary,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .background(g.fill, RoundedCornerShape(10.dp))
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            )
            PillButton("Share report", { share(context, report) }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Copy", { copy(context, report) }, Modifier.weight(1f), style = PillStyle.Tinted)
                PillButton("Close", onDismiss, Modifier.weight(1f), style = PillStyle.Plain)
            }
        }
    }
}

private fun share(context: Context, report: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Gloss crash report")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(send, "Share crash report"))
}

private fun copy(context: Context, report: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Gloss crash report", report))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
