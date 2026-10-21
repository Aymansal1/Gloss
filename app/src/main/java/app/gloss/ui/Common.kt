package app.gloss.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.gloss.AppContainer
import app.gloss.GlossApp
import kotlinx.coroutines.flow.Flow
import java.util.Locale

@Composable
fun container(): AppContainer = (LocalContext.current.applicationContext as GlossApp).container

/** Scrolls [list] back to the top each time [reselect] fires (the tab was tapped again). */
@Composable
fun ScrollToTopOn(reselect: Flow<Unit>, list: LazyListState) {
    LaunchedEffect(reselect, list) { reselect.collect { list.animateScrollToItem(0) } }
}

// A no-break space keeps "60 KB" together when a line wraps.
fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f\u00A0GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.0f\u00A0MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f\u00A0KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes\u00A0B"
}

private val LANGS = mapOf(
    "JP" to "Japanese", "JA" to "Japanese", "EN" to "English", "FR" to "French", "DE" to "German",
    "ES" to "Spanish", "IT" to "Italian", "KO" to "Korean", "KR" to "Korean", "ZH" to "Chinese", "CN" to "Chinese",
    "PT" to "Portuguese", "RU" to "Russian",
)

fun languageName(code: String): String = LANGS[code.uppercase()] ?: code

/** "1997-10-31" -> "31 Oct 1997", "1992-10" -> "Oct 1992"; anything else unchanged. */
fun prettyDate(date: String): String {
    val m = Regex("""^(\d{4})(?:[-/](\d{1,2}))?(?:[-/](\d{1,2}))?$""").find(date.trim()) ?: return date
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val y = m.groupValues[1]
    val mo = m.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 }?.let { months[it - 1] }
    val d = m.groupValues[3].toIntOrNull()
    return listOfNotNull(d?.toString(), mo, y).joinToString(" ")
}
