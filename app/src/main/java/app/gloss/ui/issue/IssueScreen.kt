package app.gloss.ui.issue

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.gloss.Nav
import app.gloss.data.CatalogItem
import app.gloss.data.InstalledIssue
import app.gloss.data.compareVersions
import app.gloss.download.DownloadMode
import app.gloss.download.DownloadProgress
import app.gloss.ui.components.Cell
import app.gloss.ui.components.CoverInfo
import app.gloss.ui.components.CoverSharing
import app.gloss.ui.components.IssueCover
import app.gloss.ui.components.LargeTitleScreen
import app.gloss.ui.components.PillButton
import app.gloss.ui.components.PillStyle
import app.gloss.ui.components.RoundIconButton
import app.gloss.ui.components.Section
import app.gloss.ui.components.coverShadow
import app.gloss.ui.components.pressable
import app.gloss.ui.container
import app.gloss.ui.formatBytes
import app.gloss.ui.languageName
import app.gloss.ui.prettyDate
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.Motion
import app.gloss.ui.theme.Pigment
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssueScreen(id: String, nav: Nav) {
    val c = container()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by c.catalog.items.collectAsState()
    val installedMap by c.library.issues.collectAsState()
    val item = remember(items, id) { items.firstOrNull { it.id == id } }
    val installed = installedMap[id]
    val progress by remember(id) { c.downloads.progress(id) }.collectAsState(initial = null)
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeleteScan by remember { mutableStateOf(false) }

    var pendingMode by remember { mutableStateOf<DownloadMode?>(null) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingMode?.let { c.downloads.start(id, it) }
        pendingMode = null
    }
    fun download(mode: DownloadMode) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) c.downloads.start(id, mode)
        else { pendingMode = mode; notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    val info = when {
        item != null -> CoverInfo.of(item)
        installed != null -> CoverInfo.of(installed)
        else -> null
    }
    val name = item?.magazineName ?: installed?.magazineName.orEmpty()
    val pigment = Pigment.forSeries(name)
    val g = Gloss.colors
    val list = rememberLazyListState()
    val density = LocalDensity.current
    // The pigment wash behind the cover scrolls away with it.
    val washShift by remember {
        derivedStateOf { if (list.firstVisibleItemIndex > 0) -10_000f else -list.firstVisibleItemScrollOffset.toFloat() }
    }

    Box(Modifier.fillMaxSize().background(g.ground)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(460.dp)
                .graphicsLayer { translationY = washShift }
                .background(Brush.verticalGradient(listOf(pigment.copy(alpha = if (g.isDark) 0.45f else 0.30f), g.ground))),
        )
        LargeTitleScreen(
            title = name,
            state = list,
            onBack = nav.back,
            background = Color.Transparent,
            titlePadding = false,
            actions = {
                if (installed != null) {
                    Box {
                        var open by remember { mutableStateOf(false) }
                        RoundIconButton(Icons.Rounded.MoreHoriz, "More options", { open = true })
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            if (installed.hasScan && installed.hasData) {
                                DropdownMenuItem(
                                    text = { Text("Delete scan, keep text (${formatBytes(installed.pdfFile.length())})") },
                                    onClick = { open = false; confirmDeleteScan = true },
                                )
                            }
                            DropdownMenuItem(text = { Text("Remove from phone", color = g.danger) }, onClick = { open = false; confirmDelete = true })
                        }
                    }
                }
            },
            titleContent = {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (info != null) {
                        IssueCover(info, Modifier.width(188.dp).coverShadow(), shared = CoverSharing.Destination)
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(name, style = Gloss.type.title1, color = g.text, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                    // Many issues are named by their date; don't say it twice.
                    val date = item?.date ?: installed?.date.orEmpty()
                    val issueName = (item?.issueName ?: installed?.issueName.orEmpty())
                        .takeUnless { it == date || prettyDate(it) == prettyDate(date) }.orEmpty()
                    val subtitle = listOf(prettyDate(date), issueName)
                        .filter { it.isNotBlank() }.distinct().joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(subtitle, style = Gloss.type.subhead, color = g.secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Actions(
                        item, installed, progress,
                        lastPage = c.reading.lastPage(id),
                        onRead = { nav.read(id, null, null) },
                        onDownload = ::download,
                        onCancel = { c.downloads.cancel(id) },
                    )
                }
            },
        ) {
            item(key = "facts") { Facts(item, installed) }

            item?.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                item(key = "tags") {
                    FlowRow(
                        Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tags.forEach { t ->
                            Text(
                                t, style = Gloss.type.footnote, color = g.text,
                                modifier = Modifier.clip(CircleShape).background(g.surface).padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            val notes = item?.notes.orEmpty().ifBlank { item?.description.orEmpty() }
            if (notes.isNotBlank()) {
                item(key = "notes") {
                    Section(Modifier.padding(top = 8.dp), header = "Notes") {
                        Text(notes, style = Gloss.type.subhead, color = g.text, modifier = Modifier.padding(16.dp))
                    }
                }
            }

            item(key = "fineprint") {
                Column(Modifier.padding(horizontal = 32.dp, vertical = 22.dp)) {
                    Text(
                        "Transcriptions and translations are AI-generated by Gaming Alexandria and can contain mistakes. " +
                            "Check important passages against the scan.",
                        style = Gloss.type.footnote, color = g.secondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Catalog ID $id", style = Gloss.type.caption, color = g.tertiary)
                }
            }
        }
    }

    if (confirmDelete && installed != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this issue?") },
            text = { Text("This deletes the scan and translation (${formatBytes(installed.sizeBytes())}) from your phone. Bookmarks for it go too. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { c.library.delete(id); c.search.removeIssue(id); c.reading.forget(id) }
                }) { Text("Remove", color = g.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }

    if (confirmDeleteScan && installed != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteScan = false },
            title = { Text("Delete the scan?") },
            text = { Text("This frees ${formatBytes(installed.pdfFile.length())}. The translation stays, and you can get the scan again later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteScan = false
                    scope.launch { c.library.deleteScan(id) }
                }) { Text("Delete scan", color = g.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteScan = false }) { Text("Keep") } },
        )
    }
}

private sealed interface Primary {
    data object Unavailable : Primary
    data class Get(val label: String, val mode: DownloadMode) : Primary
    data class Downloading(val progress: DownloadProgress) : Primary
    data class Read(val page: Int) : Primary
}

@Composable
private fun Actions(
    item: CatalogItem?,
    installed: InstalledIssue?,
    progress: DownloadProgress?,
    lastPage: Int,
    onRead: () -> Unit,
    onDownload: (DownloadMode) -> Unit,
    onCancel: () -> Unit,
) {
    val g = Gloss.colors
    val primary: Primary = when {
        progress?.running == true -> Primary.Downloading(progress)
        installed != null -> Primary.Read(lastPage)
        item == null || !(item.hasScan || item.hasData) -> Primary.Unavailable
        else -> Primary.Get(if (item.hasScan) "Download" else "Download text", DownloadMode.FULL)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MorphButton(primary, onRead = onRead, onDownload = onDownload, onCancel = onCancel)
        Spacer(Modifier.height(10.dp))
        when (primary) {
            is Primary.Downloading -> {
                val p = primary.progress
                val pct = p.fraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
                val bytes = if (p.total > 0) " · ${formatBytes(p.done)} of ${formatBytes(p.total)}" else ""
                Text(p.stage + pct + bytes, style = Gloss.type.footnote, color = g.secondary, textAlign = TextAlign.Center)
            }
            is Primary.Read -> if (primary.page > 1) Text("You're on page ${primary.page}", style = Gloss.type.footnote, color = g.secondary)
            is Primary.Get -> Text(
                if (item?.hasScan == true && item.hasData) "The scan and its translation, to read offline" else "To read offline",
                style = Gloss.type.footnote, color = g.secondary,
            )
            Primary.Unavailable -> Text(
                if (item == null) "This issue is no longer in the catalog." else "No download is listed for this issue yet.",
                style = Gloss.type.footnote, color = g.secondary,
            )
        }
        progress?.error?.let {
            Spacer(Modifier.height(6.dp))
            Text("Download stopped: $it. Tap Download to pick up where it left off.", style = Gloss.type.footnote, color = g.danger, textAlign = TextAlign.Center)
        }

        // Secondary choices.
        val secondary = buildList<Pair<String, () -> Unit>> {
            if (primary is Primary.Get && item?.hasScan == true && item.hasData) add("Text only, smaller" to { onDownload(DownloadMode.TEXT) })
            if (installed != null && item != null && !installed.hasScan && item.hasScan && primary !is Primary.Downloading) {
                add("Get the scan" to { onDownload(DownloadMode.SCAN) })
            }
            val newer = item != null && installed != null && item.version.isNotBlank() && installed.version.isNotBlank() &&
                compareVersions(item.version, installed.version) > 0
            if (newer && item!!.hasData && primary !is Primary.Downloading) add("Update translation to v${item.version}" to { onDownload(DownloadMode.TEXT) })
        }
        if (secondary.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            secondary.forEach { (label, action) ->
                PillButton(label, action, style = PillStyle.Tinted, height = 44.dp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

/**
 * The App Store "Get" button: a lapis capsule that, once tapped, draws itself in to a ring
 * that fills as the issue downloads (tap the ring to stop), then opens back out to Read.
 */
@Composable
private fun MorphButton(
    state: Primary,
    onRead: () -> Unit,
    onDownload: (DownloadMode) -> Unit,
    onCancel: () -> Unit,
) {
    val g = Gloss.colors
    val downloading = state is Primary.Downloading
    val width by animateDpAsState(if (downloading) 54.dp else 220.dp, Motion.move(), label = "morph")
    Box(
        Modifier
            .width(width)
            .height(54.dp)
            .clip(CircleShape)
            .background(if (downloading) g.lapisTint else if (state is Primary.Unavailable) g.fill else g.lapis)
            .pressable(
                onClick = {
                    when (state) {
                        is Primary.Get -> onDownload(state.mode)
                        is Primary.Downloading -> onCancel()
                        is Primary.Read -> onRead()
                        Primary.Unavailable -> Unit
                    }
                },
                enabled = state !is Primary.Unavailable,
                haptic = true,
                onClickLabel = when (state) {
                    is Primary.Get -> "Download"
                    is Primary.Downloading -> "Stop download"
                    is Primary.Read -> "Read"
                    Primary.Unavailable -> null
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state::class,
            transitionSpec = { (fadeIn(tween(180)) + scaleIn(Motion.move(), initialScale = 0.92f)) togetherWith fadeOut(tween(120)) },
            label = "morphContent",
        ) { kind ->
            when (kind) {
                Primary.Downloading::class -> {
                    val p = (state as? Primary.Downloading)?.progress
                    ProgressRing(p?.fraction, Modifier.size(54.dp).semantics { contentDescription = "Downloading" })
                }
                Primary.Read::class -> Text("Read", style = Gloss.type.headline, color = g.onLapis)
                Primary.Get::class -> Text((state as? Primary.Get)?.label ?: "Download", style = Gloss.type.headline, color = g.onLapis)
                else -> Text("Unavailable", style = Gloss.type.headline, color = g.secondary)
            }
        }
    }
}

/** A lapis ring that fills clockwise, with a stop square in the middle; spins when the size is unknown. */
@Composable
fun ProgressRing(fraction: Float?, modifier: Modifier = Modifier) {
    val g = Gloss.colors
    val shown by animateFloatAsState(fraction ?: 0.25f, Motion.settle(), label = "ring")
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Canvas(modifier.padding(8.dp)) {
        val stroke = 3.5.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(g.lapis.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        val start = if (fraction == null) angle - 90f else -90f
        drawArc(g.lapis, start, 360f * shown, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val sq = size.minDimension * 0.28f
        drawRoundRect(
            g.lapis,
            topLeft = Offset((size.width - sq) / 2, (size.height - sq) / 2),
            size = Size(sq, sq),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
        )
    }
}

@Composable
private fun Facts(item: CatalogItem?, installed: InstalledIssue?) {
    val uri = LocalUriHandler.current
    val rows = buildList {
        (item?.publisher ?: installed?.publisher)?.takeIf { it.isNotBlank() }?.let { add(Triple("Publisher", it, "")) }
        if (item != null && (item.originalLanguage.isNotBlank() || item.translatedLanguage.isNotBlank())) {
            add(Triple("Language", listOf(item.originalLanguage, item.translatedLanguage).filter { it.isNotBlank() }
                .joinToString(" → ") { languageName(it) }, ""))
        }
        val v = installed?.version?.takeIf { it.isNotBlank() } ?: item?.version
        if (!v.isNullOrBlank()) add(Triple(if (installed != null) "Translation version" else "Version", v, ""))
        if (installed != null) {
            add(Triple("On this phone", (if (installed.hasScan) "Scan and text · " else "Text only · ") + formatBytes(installed.sizeBytes()), ""))
        }
        item?.scanner?.takeIf { it.isNotBlank() }?.let { add(Triple("Scanned by", it, item.scannerUrl)) }
        item?.editor?.takeIf { it.isNotBlank() }?.let { add(Triple("Edited by", it, item.editorUrl)) }
    }
    if (rows.isEmpty()) return
    Section(Modifier.padding(top = 26.dp), header = "Details") {
        rows.forEachIndexed { i, (label, value, url) ->
            val link = url.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else "https://$it" }
            Cell(
                title = label,
                titleColor = Gloss.colors.secondary,
                trailing = {
                    Text(
                        value, style = Gloss.type.body,
                        color = if (link != null) Gloss.colors.lapis else Gloss.colors.text,
                        textAlign = TextAlign.End, maxLines = 2,
                    )
                },
                onClick = link?.let { l -> { uri.openUri(l) } },
                chevron = false,
                divider = i != rows.lastIndex,
            )
        }
    }
}
