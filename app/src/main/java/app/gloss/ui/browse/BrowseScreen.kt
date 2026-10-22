package app.gloss.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gloss.Nav
import app.gloss.data.CatalogItem
import app.gloss.data.CatalogRepository
import app.gloss.data.InstalledIssue
import app.gloss.data.RecentRead
import app.gloss.data.compareVersions
import app.gloss.download.DownloadMode
import app.gloss.ui.ScrollToTopOn
import app.gloss.ui.components.CoverInfo
import app.gloss.ui.components.CoverShape
import app.gloss.ui.components.CoverTile
import app.gloss.ui.components.EmptyState
import app.gloss.ui.components.IssueCover
import app.gloss.ui.components.LargeTitleScreen
import app.gloss.ui.components.PillButton
import app.gloss.ui.components.PillStyle
import app.gloss.ui.components.RoundIconButton
import app.gloss.ui.components.SearchField
import app.gloss.ui.components.TileAction
import app.gloss.ui.components.Wordmark
import app.gloss.ui.components.coverShadow
import app.gloss.ui.components.goldLeafUnderline
import app.gloss.ui.components.pressable
import app.gloss.ui.container
import app.gloss.ui.formatBytes
import app.gloss.ui.prettyDate
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GoldLeaf
import app.gloss.ui.theme.Motion
import app.gloss.ui.theme.Pigment
import app.gloss.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Series(val name: String, val issues: List<CatalogItem>) {
    val years: String
        get() {
            val ys = issues.mapNotNull { it.year.toIntOrNull() }
            if (ys.isEmpty()) return ""
            val lo = ys.min(); val hi = ys.max()
            return if (lo == hi) "$lo" else "$lo–$hi"
        }
}

fun groupSeries(items: List<CatalogItem>, showAdult: Boolean, filter: String): List<Series> {
    val f = filter.trim().lowercase()
    return items
        .filter { showAdult || !it.adult }
        .filter {
            f.isEmpty() || it.magazineName.lowercase().contains(f) || it.publisher.lowercase().contains(f) ||
                it.date.contains(f) || it.issueName.lowercase().contains(f) || it.tags.any { t -> t.lowercase().contains(f) }
        }
        .groupBy { it.magazineName }
        .map { (name, list) -> Series(name, list.sortedWith(compareBy({ it.date }, { it.issueName }))) }
        .sortedBy { it.name.lowercase() }
}

@Composable
fun DiscoverScreen(nav: Nav, reselect: Flow<Unit> = emptyFlow()) {
    val c = container()
    val items by c.catalog.items.collectAsState()
    val status by c.catalog.status.collectAsState()
    val installed by c.library.issues.collectAsState()
    val recents by c.reading.recents.collectAsState()
    val showAdult by c.reading.showAdult.collectAsState()
    val update by c.updates.state.collectAsState()
    var filter by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val uri = LocalUriHandler.current
    ScrollToTopOn(reselect, list)

    val series = remember(items, showAdult, filter) { groupSeries(items, showAdult, filter) }
    val reading = remember(recents, installed) { recents.filter { installed.containsKey(it.issueId) } }
    val updates = remember(items, installed) {
        installed.values.filter { inst ->
            val cat = items.firstOrNull { it.id == inst.id } ?: return@filter false
            cat.version.isNotBlank() && inst.version.isNotBlank() && compareVersions(cat.version, inst.version) > 0
        }
    }
    val loading = status is CatalogRepository.Status.Loading

    LargeTitleScreen(
        title = "Discover",
        state = list,
        titleContent = { Wordmark() },
        header = {
            Spacer(Modifier.height(12.dp))
            SearchField(filter, { filter = it }, "Magazines, publishers, years")
        },
        actions = {
            if (loading) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp, color = Gloss.colors.lapis)
            } else {
                RoundIconButton(Icons.Rounded.Refresh, "Refresh catalog", { scope.launch { c.catalog.refresh() } })
            }
            RoundIconButton(Icons.Rounded.Settings, "Settings", nav.settings)
        },
    ) {
        val app = update
        if (app is UpdateChecker.State.Available && filter.isBlank()) {
            item(key = "app-update") {
                Notice(
                    icon = Icons.Rounded.SystemUpdate,
                    text = "Gloss ${app.release.version} is out.",
                    action = "Get it" to { uri.openUri(app.release.apkUrl ?: app.release.pageUrl) },
                )
            }
        }

        (status as? CatalogRepository.Status.Offline)?.let { s ->
            item(key = "offline") {
                if (items.isEmpty()) {
                    EmptyState(
                        title = "Can't reach the archive",
                        body = "The catalog didn't load (${s.reason}). Your downloaded issues still open from Library.",
                        icon = Icons.Rounded.CloudOff,
                    ) {
                        PillButton("Try again", { scope.launch { c.catalog.refresh() } }, style = PillStyle.Tinted)
                    }
                } else {
                    Notice(Icons.Rounded.CloudOff, "Showing the saved catalog. The archive didn't answer (${s.reason}).")
                }
            }
        }

        if (reading.isNotEmpty() && filter.isBlank()) {
            val first = reading.first()
            installed[first.issueId]?.let { issue ->
                item(key = "continue") {
                    ContinueCard(issue, first, onClick = { nav.read(issue.id, c.reading.lastPage(issue.id), null) })
                }
            }
            if (reading.size > 1) {
                item(key = "recent") {
                    SectionHeader("Recently read")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(reading.drop(1).take(12), key = { "r-${it.issueId}" }) { r ->
                            val issue = installed[r.issueId] ?: return@items
                            CoverTile(
                                CoverInfo.of(issue), "Page ${c.reading.lastPage(issue.id)}",
                                Modifier.width(92.dp),
                                actions = listOf(TileAction("Issue details") { nav.issue(issue.id) }),
                                onClick = { nav.read(issue.id, c.reading.lastPage(issue.id), null) },
                            )
                        }
                    }
                }
            }
        }

        if (updates.isNotEmpty() && filter.isBlank()) {
            item(key = "translation-updates") {
                Notice(
                    icon = Icons.Rounded.SystemUpdate,
                    text = if (updates.size == 1) "A newer translation of ${updates[0].magazineName} ${prettyDate(updates[0].title)} is ready."
                    else "Newer translations are ready for ${updates.size} of your issues.",
                    action = "View" to { nav.issue(updates[0].id) },
                )
            }
        }

        if (items.isEmpty() && loading) {
            item(key = "loading") {
                EmptyState(title = "Opening the archive", body = "Fetching the catalog from Gaming Alexandria.") {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = Gloss.colors.lapis)
                }
            }
        }

        if (series.isEmpty() && items.isNotEmpty()) {
            item(key = "nomatch") {
                EmptyState(title = "No magazines match", body = "Nothing in the catalog matches “$filter”. Try a shorter name or a year.")
            }
        }

        items(series, key = { "s-${it.name}" }, contentType = { "shelf" }) { s ->
            Shelf(
                s, installed,
                onSeries = { nav.series(s.name) },
                onIssue = { nav.issue(it) },
                onRead = { nav.read(it, c.reading.lastPage(it), null) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick, scaleTo = 0.98f) else Modifier)
            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text, style = Gloss.type.title2, color = Gloss.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String, action: Pair<String, () -> Unit>? = null) {
    val c = Gloss.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = c.lapis, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = Gloss.type.subhead, color = c.text, modifier = Modifier.weight(1f))
        action?.let { (label, onClick) ->
            Spacer(Modifier.width(8.dp))
            PillButton(label, onClick, style = PillStyle.Tinted, height = 34.dp)
        }
    }
}

/**
 * The first thing on screen: the page you were last on, on a lapis card, with how far
 * through the issue you are drawn as a gold-leaf rule.
 */
@Composable
private fun ContinueCard(issue: InstalledIssue, recent: RecentRead, onClick: () -> Unit) {
    val c = container()
    val page = c.reading.lastPage(issue.id)
    val thumb by produceState<android.graphics.Bitmap?>(null, issue.id, page, issue.hasScan) {
        value = if (issue.hasScan) c.pdf.render(issue.pdfFile, page - 1, 360) else null
    }
    val total by produceState(0, issue.id, issue.hasScan) {
        value = if (issue.hasScan) c.pdf.pageCount(issue.pdfFile) else (c.library.content(issue.id)?.lastTextPage ?: 0)
    }
    val fraction = if (total > 0) (page.toFloat() / total).coerceIn(0f, 1f) else 0f
    var shown by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fraction) { shown = fraction }
    val progress by animateFloatAsState(shown, Motion.move(), label = "progress")
    val lapis = Pigment.Lapis
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
                .pressable(onClick, scaleTo = 0.97f, haptic = true)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3149DD), lapis, Color(0xFF141E78))))
                .padding(16.dp),
        ) {
            Box(Modifier.fillMaxHeight().aspectRatio(0.72f).coverShadow().clip(CoverShape).background(Color.White)) {
                val t = thumb
                if (t != null) {
                    Image(t.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxHeight())
                } else {
                    IssueCover(CoverInfo.of(issue), Modifier.fillMaxHeight(), shared = app.gloss.ui.components.CoverSharing.None)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    "Continue reading", style = Gloss.type.caption.copy(fontWeight = FontWeight(600)),
                    color = GoldLeaf.Hi,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    issue.magazineName, style = Gloss.type.title2, color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(prettyDate(issue.title), style = Gloss.type.subhead, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("p.", style = Gloss.type.glossBody, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(4.dp))
                    Text("$page", style = Gloss.type.glossTitle, color = Color.White)
                    if (total > 0) {
                        Text(" of $total", style = Gloss.type.footnote, color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth().height(4.dp).goldLeafUnderline(progress))
                }
            }
        }
    }
}

@Composable
private fun Shelf(
    series: Series,
    installed: Map<String, InstalledIssue>,
    onSeries: () -> Unit,
    onIssue: (String) -> Unit,
    onRead: (String) -> Unit,
) {
    val c = Gloss.colors
    val pigment = Pigment.forSeries(series.name)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(onSeries, scaleTo = 0.98f)
                .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(pigment))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(series.name, style = Gloss.type.title3, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val count = if (series.issues.size == 1) "1 issue" else "${series.issues.size} issues"
                Text(
                    listOf(count, series.years, series.issues.first().publisher).filter { it.isNotBlank() }.joinToString(" · "),
                    style = Gloss.type.footnote, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (series.issues.size > 3) Text("See all", style = Gloss.type.subhead, color = c.lapis)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(series.issues, key = { it.id }) { item ->
                ShelfIssue(
                    item, installed[item.id],
                    onClick = { onIssue(item.id) },
                    onRead = { onRead(item.id) },
                    modifier = Modifier.width(112.dp),
                )
            }
        }
    }
}

@Composable
fun ShelfIssue(
    item: CatalogItem,
    installed: InstalledIssue?,
    onClick: () -> Unit,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = container()
    val badge: (@Composable () -> Unit)? = installed?.let { inst -> { SavedBadge(textOnly = !inst.hasScan) } }
    var removing by remember { mutableStateOf(false) }
    val downloading by remember(item.id) { c.downloads.progress(item.id) }.collectAsState(null)
    val actions = buildList {
        if (installed != null) {
            add(TileAction("Read", run = onRead))
            add(TileAction("Issue details", run = onClick))
            add(TileAction("Remove from phone", danger = true) { removing = true })
        } else if (downloading == null) {
            add(TileAction(if (item.hasScan) "Download" else "Download text") { c.downloads.start(item.id, DownloadMode.FULL) })
            if (item.hasScan && item.hasData) add(TileAction("Download text only") { c.downloads.start(item.id, DownloadMode.TEXT) })
            add(TileAction("Issue details", run = onClick))
        }
    }
    CoverTile(
        CoverInfo.of(item),
        prettyDate(item.displayTitle),
        modifier,
        badge = badge,
        actions = actions,
        onClick = onClick,
    )
    if (removing && installed != null) {
        RemoveIssueDialog(installed, onDismiss = { removing = false })
    }
}

/** Confirms before deleting a downloaded issue, its bookmarks and reading place. */
@Composable
fun RemoveIssueDialog(issue: InstalledIssue, onDismiss: () -> Unit) {
    val c = container()
    val scope = rememberCoroutineScope()
    val size by produceState(0L, issue.id) { value = withContext(Dispatchers.IO) { issue.sizeBytes() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove this issue?") },
        text = { Text("This deletes ${issue.magazineName} ${prettyDate(issue.title)} (${formatBytes(size)}) from your phone. Bookmarks for it go too. You can download it again later.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                scope.launch { c.library.delete(issue.id); c.search.removeIssue(issue.id); c.reading.forget(issue.id) }
            }) { Text("Remove", color = Gloss.colors.danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}

/** A small gold seal on covers you've downloaded. */
@Composable
fun SavedBadge(textOnly: Boolean) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(GoldLeaf.stops))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            if (textOnly) "Text" else "Saved",
            style = Gloss.type.caption2.copy(fontWeight = FontWeight(700)),
            color = Color(0xFF2A1D00),
        )
    }
}
