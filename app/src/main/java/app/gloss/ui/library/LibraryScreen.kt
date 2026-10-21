package app.gloss.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gloss.Nav
import app.gloss.data.Bookmark
import app.gloss.data.InstalledIssue
import app.gloss.data.compareVersions
import app.gloss.ui.ScrollToTopOn
import app.gloss.ui.components.Cell
import app.gloss.ui.components.CoverInfo
import app.gloss.ui.components.EmptyState
import app.gloss.ui.components.IssueCover
import app.gloss.ui.components.LargeTitleScreen
import app.gloss.ui.components.Section
import app.gloss.ui.components.SegmentedControl
import app.gloss.ui.components.SharedCover
import app.gloss.ui.container
import app.gloss.ui.formatBytes
import app.gloss.ui.prettyDate
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GoldLeaf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(nav: Nav, reselect: Flow<Unit> = emptyFlow()) {
    val c = container()
    val installed by c.library.issues.collectAsState()
    val catalog by c.catalog.items.collectAsState()
    val bookmarks by c.reading.bookmarks.collectAsState()
    var view by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var removing by remember { mutableStateOf<InstalledIssue?>(null) }
    val scope = rememberCoroutineScope()

    val issues = remember(installed) {
        installed.values.sortedWith(compareBy({ it.magazineName.lowercase() }, { it.date }))
    }
    val sizes by produceState(emptyMap<String, Long>(), installed) {
        value = withContext(Dispatchers.IO) { installed.mapValues { it.value.sizeBytes() } }
    }
    val total = sizes.values.sum()
    val marks = remember(bookmarks, installed) { bookmarks.filter { installed.containsKey(it.issueId) } }
    val list = rememberLazyListState()
    ScrollToTopOn(reselect, list)

    LargeTitleScreen(
        title = "Library",
        state = list,
        header = {
            if (issues.isNotEmpty()) {
                Text(
                    "${issues.size} ${if (issues.size == 1) "issue" else "issues"} · ${formatBytes(total)} on this phone",
                    style = Gloss.type.subhead, color = Gloss.colors.secondary,
                )
            }
            Spacer(Modifier.height(14.dp))
            SegmentedControl(listOf("Issues", "Bookmarks"), view, { view = it })
        },
    ) {
        if (view == 0) {
            if (issues.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Your shelf is empty",
                        body = "Issues you download from Discover are kept here, ready to read offline.",
                        icon = Icons.Rounded.Inventory2,
                    )
                }
            } else {
                item(key = "issues") {
                    Section(Modifier.padding(top = 8.dp)) {
                        issues.forEachIndexed { i, issue ->
                            val cat = catalog.firstOrNull { it.id == issue.id }
                            val hasUpdate = cat != null && cat.version.isNotBlank() && issue.version.isNotBlank() &&
                                compareVersions(cat.version, issue.version) > 0
                            IssueRow(
                                issue, sizes[issue.id], hasUpdate,
                                last = i == issues.lastIndex,
                                onClick = { SharedCover.target = issue.id; nav.issue(issue.id) },
                                onRemove = { removing = issue },
                            )
                        }
                    }
                }
            }
        } else {
            if (marks.isEmpty()) {
                item(key = "nomarks") {
                    EmptyState(
                        title = "No bookmarks yet",
                        body = "Tap the bookmark in the reader to keep a page. You can add a note to remember why.",
                        icon = Icons.Rounded.BookmarkBorder,
                    )
                }
            } else {
                // One section per issue, newest bookmark first.
                val byIssue = marks.groupBy { it.issueId }
                byIssue.forEach { (issueId, bs) ->
                    val issue = installed[issueId] ?: return@forEach
                    item(key = "m-$issueId") {
                        Section(
                            Modifier.padding(top = 18.dp),
                            header = "${issue.magazineName} · ${prettyDate(issue.title)}",
                        ) {
                            bs.sortedBy { it.page }.forEachIndexed { i, b ->
                                BookmarkRow(
                                    b, last = i == bs.lastIndex,
                                    onOpen = { nav.read(b.issueId, b.page, null) },
                                    onEdit = { editing = b },
                                    onRemove = { c.reading.removeBookmark(b.issueId, b.page) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { b -> NoteDialog(b, onDismiss = { editing = null }, onSave = { c.reading.setNote(b.issueId, b.page, it); editing = null }) }

    removing?.let { issue ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this issue?") },
            text = { Text("This deletes ${issue.magazineName} ${prettyDate(issue.title)} (${formatBytes(sizes[issue.id] ?: 0)}) from your phone. Bookmarks for it go too. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = issue.id
                    removing = null
                    scope.launch { c.library.delete(id); c.search.removeIssue(id); c.reading.forget(id) }
                }) { Text("Remove", color = Gloss.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun IssueRow(
    issue: InstalledIssue,
    size: Long?,
    hasUpdate: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val c = Gloss.colors
    Box {
        Cell(
            title = issue.magazineName,
            subtitle = listOfNotNull(
                prettyDate(issue.title),
                if (issue.hasScan) "Scan and text" else "Text only",
                size?.let { formatBytes(it) },
            ).joinToString(" · "),
            leading = { IssueCover(CoverInfo.of(issue), Modifier.width(44.dp)) },
            trailing = if (hasUpdate) @Composable { UpdateDot() } else null,
            chevron = true,
            onClick = onClick,
            onLongClick = { menu = true },
            divider = !last,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Remove from phone", color = c.danger) }, onClick = { menu = false; onRemove() })
        }
    }
}

@Composable
private fun UpdateDot() {
    Box(
        Modifier.clip(CircleShape).background(Gloss.colors.lapis).padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text("Update", style = Gloss.type.caption2.copy(fontWeight = FontWeight(700)), color = Gloss.colors.onLapis) }
}

@Composable
private fun BookmarkRow(b: Bookmark, last: Boolean, onOpen: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val c = Gloss.colors
    Box {
        Cell(
            title = b.note.ifBlank { "Page ${b.page}" },
            titleMaxLines = 3,
            subtitle = if (b.note.isBlank()) "Hold to add a note" else "Page ${b.page}",
            leading = {
                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                    Text("${b.page}", style = Gloss.type.glossTitle, color = if (c.isDark) GoldLeaf.Hi else c.goldInk)
                }
            },
            onClick = onOpen,
            onLongClick = { menu = true },
            divider = !last,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (b.note.isBlank()) "Add note" else "Edit note") }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("Remove bookmark", color = c.danger) }, onClick = { menu = false; onRemove() })
        }
    }
}

@Composable
private fun NoteDialog(b: Bookmark, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(b) { mutableStateOf(b.note) }
    val c = Gloss.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note for page ${b.page}") },
        text = {
            Column {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    textStyle = Gloss.type.body.copy(color = c.text),
                    cursorBrush = SolidColor(c.lapis),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.fill)
                        .padding(12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text("What's on this page?", style = Gloss.type.body, color = c.secondary)
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Text("${text.length}/500", style = Gloss.type.caption2, color = c.tertiary, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
