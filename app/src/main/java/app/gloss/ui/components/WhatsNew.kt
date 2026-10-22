package app.gloss.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.gloss.BuildConfig
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GoldLeaf

/**
 * A short note shown once after an update. Edit [notes] with each release; an empty list
 * shows nothing. A fresh install never sees it, only people coming from an older version.
 */
object WhatsNew {
    val notes = listOf(
        "Learn now reads people's names as names, using the translation to tell how they're said: 萩原雪歩 is Yukiho Hagiwara, not \"reedy field\".",
        "Sentences split the way a reader would, so たくさん stays one word and で, な and くん mean what they mean in context.",
        "Every word in Learn opens when tapped, the little ones like の and を included, and a kanji on its own opens its stroke order.",
        "From the archive's history notes always say when it happened.",
    )

    private const val PREFS = "whats-new"
    private const val SEEN = "seen-version"

    /** Whether to show the notes on this launch. Records the version either way. */
    fun pending(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(SEEN, null)
        prefs.edit().putString(SEEN, BuildConfig.VERSION_NAME).apply()
        // No record yet: an update from a version before this note existed, or a fresh install.
        // The package's install and update times tell the two apart.
        val updated = if (seen == null) {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).let { it.firstInstallTime != it.lastUpdateTime }
            }.getOrDefault(false)
        } else seen != BuildConfig.VERSION_NAME
        return updated && notes.isNotEmpty()
    }
}

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val g = Gloss.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(g.raised, RoundedCornerShape(22.dp))
                .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("What's new in ${BuildConfig.VERSION_NAME}", style = Gloss.type.title2, color = g.text)
            WhatsNew.notes.forEach { note ->
                Row {
                    Box(
                        Modifier.padding(top = 10.dp, end = 10.dp).width(12.dp).height(2.dp)
                            .background(Brush.horizontalGradient(GoldLeaf.stops), RoundedCornerShape(1.dp)),
                    )
                    Text(note, style = Gloss.type.subhead, color = g.secondary)
                }
            }
            PillButton("Got it", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
