package app.gloss.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.gloss.BuildConfig
import app.gloss.Nav
import app.gloss.R
import app.gloss.data.ReadingCodec
import app.gloss.ui.components.Cell
import app.gloss.ui.components.GlossSwitch
import app.gloss.ui.components.LargeTitleScreen
import app.gloss.ui.components.RoundIconButton
import app.gloss.ui.components.Section
import app.gloss.ui.components.SegmentedControl
import app.gloss.ui.components.Wordmark
import app.gloss.ui.container
import app.gloss.ui.theme.Appearance
import app.gloss.ui.theme.Gloss
import app.gloss.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(nav: Nav) {
    val c = container()
    val g = Gloss.colors
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val appearance by c.reading.appearance.collectAsState()
    val rtl by c.reading.rightToLeft.collectAsState()
    val spreads by c.reading.spreads.collectAsState()
    val zones by c.reading.showZones.collectAsState()
    val textScale by c.reading.textScale.collectAsState()
    val adult by c.reading.showAdult.collectAsState()
    val update by c.updates.state.collectAsState()
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { dest ->
        if (dest == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    val json = ReadingCodec.encodeBackup(c.reading.export(), System.currentTimeMillis(), BuildConfig.VERSION_NAME)
                    context.contentResolver.openOutputStream(dest)?.use { it.write(json.toByteArray()) }
                        ?: error("Couldn't open the file")
                }
                "Backup saved" to "Your bookmarks, notes and the page you're on in every issue are in that file. Restore it on any phone with Gloss."
            }.getOrElse { "Backup failed" to (it.message ?: "Something went wrong writing the file.") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { src ->
        if (src == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                val backup = withContext(Dispatchers.IO) {
                    val raw = context.contentResolver.openInputStream(src)?.use { it.readBytes().decodeToString() }
                        ?: error("Couldn't open the file")
                    ReadingCodec.decodeBackup(raw)
                }
                c.reading.import(backup)
                "Restored" to "Added ${backup.bookmarks.size} bookmarks and your place in ${backup.pages.size} issues. Anything already on this phone was kept."
            }.getOrElse { "Couldn't restore" to (it.message ?: "That file couldn't be read.") }
        }
    }

    LargeTitleScreen(title = "Settings", onBack = nav.back, backLabel = "Discover") {
        item(key = "appearance") {
            Section(Modifier.padding(top = 8.dp), header = "Appearance") {
                Column(Modifier.padding(12.dp)) {
                    SegmentedControl(
                        Appearance.entries.map { it.label },
                        Appearance.entries.indexOf(appearance),
                        { c.reading.setAppearance(Appearance.entries[it]) },
                    )
                }
            }
        }
        item(key = "reading") {
            Section(
                Modifier.padding(top = 26.dp),
                header = "Reading",
                footer = "Japanese magazines bind on the right, so pages turn right to left unless you change it.",
            ) {
                Cell("Turn pages right to left", trailing = { GlossSwitch(rtl, { c.reading.setRightToLeft(it) }) })
                Cell("Two-page spreads sideways", subtitle = "Show facing pages together in landscape",
                    trailing = { GlossSwitch(spreads, { c.reading.setSpreads(it) }) })
                Cell("Outline text on scans", subtitle = "Show where every translated box is",
                    trailing = { GlossSwitch(zones, { c.reading.setShowZones(it) }) })
                Cell(
                    "Translation text size",
                    divider = false,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoundIconButton(Icons.Rounded.Remove, "Smaller text", { c.reading.setTextScale(textScale - 0.1f) }, size = 32.dp, background = g.fill)
                            Text("${(textScale * 100).roundToInt()}%", style = Gloss.type.subhead, color = g.text,
                                textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
                            RoundIconButton(Icons.Rounded.Add, "Larger text", { c.reading.setTextScale(textScale + 0.1f) }, size = 32.dp, background = g.fill)
                        }
                    },
                )
            }
        }
        item(key = "catalog") {
            Section(Modifier.padding(top = 26.dp), header = "Catalog", footer = "Issues tagged adult in the catalog stay hidden unless this is on.") {
                Cell("Show 18+ issues", divider = false, trailing = { GlossSwitch(adult, { c.reading.setShowAdult(it) }) })
            }
        }
        item(key = "data") {
            Section(
                Modifier.padding(top = 26.dp),
                header = "Your reading",
                footer = "A backup holds bookmarks, notes, your page in every issue and these settings. Downloaded issues aren't included; get them again from Discover.",
            ) {
                Cell("Save a backup…", titleColor = g.lapis, chevron = false, onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exporter.launch("gloss-backup-$stamp.json")
                })
                Cell("Restore from a backup…", titleColor = g.lapis, chevron = false, divider = false, onClick = {
                    importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                })
            }
        }
        item(key = "updates") {
            val u = update
            Section(Modifier.padding(top = 26.dp), header = "Updates") {
                Cell("Version", trailing = { Text(BuildConfig.VERSION_NAME, style = Gloss.type.body, color = g.secondary) })
                when (u) {
                    is UpdateChecker.State.Available -> Cell(
                        "Get Gloss ${u.release.version}", titleColor = g.lapis, divider = false,
                        subtitle = "Opens the download on GitHub. Your library and bookmarks stay.",
                        onClick = { uri.openUri(u.release.apkUrl ?: u.release.pageUrl) },
                    )
                    UpdateChecker.State.Checking -> Cell(
                        "Checking…", divider = false,
                        trailing = { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = g.lapis) },
                    )
                    else -> Cell(
                        "Check for updates", titleColor = g.lapis, chevron = false, divider = false,
                        subtitle = when (u) {
                            UpdateChecker.State.UpToDate -> "You have the latest version."
                            is UpdateChecker.State.Failed -> "Couldn't check: ${u.reason}"
                            else -> null
                        },
                        onClick = { scope.launch { c.updates.check(force = true) } },
                    )
                }
            }
        }
        item(key = "about") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Image(painterResource(R.drawable.gloss_mark), contentDescription = null, modifier = Modifier.size(56.dp))
                Wordmark(style = Gloss.type.glossTitle)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Catalog, scans and AI translations come from Gaming Alexandria, read in the same format as their Rosetta Magazine Researcher. Gloss is an independent, open-source reader and isn't affiliated with them.",
                    style = Gloss.type.footnote, color = g.secondary, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    LinkText("Gaming Alexandria") { uri.openUri("https://www.gamingalexandria.com") }
                    LinkText("Source code") { uri.openUri("https://github.com/Aymansal1/Gloss") }
                }
                Text("AGPL-3.0 · Fonts under the SIL Open Font License", style = Gloss.type.caption, color = g.tertiary, textAlign = TextAlign.Center)
            }
        }
    }

    message?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, style = Gloss.type.subhead, color = Gloss.colors.lapis) }
}
