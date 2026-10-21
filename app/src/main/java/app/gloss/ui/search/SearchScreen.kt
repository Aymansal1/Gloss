package app.gloss.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gloss.Nav
import app.gloss.search.SearchHit
import app.gloss.ui.ScrollToTopOn
import app.gloss.ui.components.CoverInfo
import app.gloss.ui.components.CoverSharing
import app.gloss.ui.components.EmptyState
import app.gloss.ui.components.Hairline
import app.gloss.ui.components.IssueCover
import app.gloss.ui.components.LargeTitleScreen
import app.gloss.ui.components.SearchField
import app.gloss.ui.components.Section
import app.gloss.ui.components.rowPress
import app.gloss.ui.container
import app.gloss.ui.prettyDate
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GoldLeaf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun SearchScreen(nav: Nav, reselect: Flow<Unit> = emptyFlow()) {
    val c = container()
    val installed by c.library.issues.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    ScrollToTopOn(reselect, list)

    // Opening Search puts the cursor in the field, like iOS.
    LaunchedEffect(Unit) { if (query.isEmpty() && installed.isNotEmpty()) runCatching { focus.requestFocus() } }

    LaunchedEffect(query, installed.size) {
        if (query.isBlank()) { hits = emptyList(); searched = false; return@LaunchedEffect }
        delay(220) // debounce typing
        hits = c.search.search(query)
        searched = true
    }

    val grouped = remember(hits) { hits.groupBy { it.issueId } }

    LargeTitleScreen(
        title = "Search",
        state = list,
        header = {
            Spacer(Modifier.height(12.dp))
            SearchField(
                query, { query = it }, "English or Japanese, e.g. マリオ",
                modifier = Modifier.focusRequester(focus),
                onSearch = { keyboard?.hide() },
            )
            Spacer(Modifier.height(8.dp))
            val hint = when {
                installed.isEmpty() || (searched && hits.isEmpty()) -> ""
                query.isBlank() -> "\"Exact phrase\", -exclude, prefix*"
                searched -> "${hits.size} ${if (hits.size == 1) "page" else "pages"} in ${grouped.size} ${if (grouped.size == 1) "issue" else "issues"}"
                else -> ""
            }
            if (hint.isNotEmpty()) Text(hint, style = Gloss.type.footnote, color = Gloss.colors.secondary)
        },
    ) {
        if (installed.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "Nothing to search yet",
                    body = "Search reads inside the issues on your phone, in English and Japanese. Download one from Discover to start.",
                    icon = Icons.Rounded.Inventory2,
                )
            }
        } else if (searched && hits.isEmpty()) {
            item(key = "nohits") {
                EmptyState(
                    title = "No pages match",
                    body = "Nothing inside your issues matches “$query”. Try fewer words, or the Japanese term.",
                    icon = Icons.Rounded.SearchOff,
                )
            }
        }
        grouped.forEach { (issueId, issueHits) ->
            val issue = installed[issueId] ?: return@forEach
            item(key = "h-$issueId") {
                Column(Modifier.padding(top = 18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IssueCover(CoverInfo.of(issue), Modifier.width(30.dp), shared = CoverSharing.None)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(issue.magazineName, style = Gloss.type.headline, color = Gloss.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(prettyDate(issue.title), style = Gloss.type.footnote, color = Gloss.colors.secondary)
                        }
                    }
                    Section {
                        issueHits.forEachIndexed { i, hit ->
                            HitRow(hit, last = i == issueHits.lastIndex, onClick = { nav.read(hit.issueId, hit.page, query) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, last: Boolean, onClick: () -> Unit) {
    val c = Gloss.colors
    Row(Modifier.fillMaxWidth().rowPress(onClick)) {
        Box(Modifier.padding(start = 16.dp, top = 10.dp).width(40.dp), contentAlignment = Alignment.TopCenter) {
            Text("${hit.page}", style = Gloss.type.glossTitle, color = if (c.isDark) GoldLeaf.Hi else c.goldInk)
        }
        Box(Modifier.weight(1f).padding(start = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(end = 16.dp, top = 11.dp, bottom = 11.dp)) {
                val section = when (hit.section) {
                    SearchHit.Section.TRANSLATION -> "Translation"
                    SearchHit.Section.ORIGINAL -> "Original"
                    SearchHit.Section.SUMMARY -> "Summary"
                }
                Text(section, style = Gloss.type.caption, color = c.secondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    buildAnnotatedString {
                        append(hit.snippet)
                        hit.highlights.forEach { r ->
                            if (r.first >= 0 && r.last < hit.snippet.length) {
                                addStyle(SpanStyle(background = c.goldWash, color = c.text), r.first, r.last + 1)
                            }
                        }
                    },
                    style = Gloss.type.subhead,
                    color = c.text,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!last) Hairline(Modifier.align(Alignment.BottomStart))
        }
    }
}
