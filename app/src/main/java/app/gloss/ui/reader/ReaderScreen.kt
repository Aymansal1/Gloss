package app.gloss.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.gloss.Nav
import app.gloss.data.IssueContent
import app.gloss.search.SearchText
import app.gloss.ui.components.MarkdownBlock
import app.gloss.ui.container
import app.gloss.ui.prettyDate
import app.gloss.ui.theme.Ink
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class SheetTab(val label: String) { Translation("Translation"), Original("Original"), Summary("Summary") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(id: String, startPage: Int?, query: String?, nav: Nav) {
    val c = container()
    val installedMap by c.library.issues.collectAsState()
    val issue = installedMap[id]
    if (issue == null) {
        Box(Modifier.fillMaxSize().background(Ink.Night), contentAlignment = Alignment.Center) {
            Text("This issue isn't on your phone anymore.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val content by produceState<IssueContent?>(null, id, issue.stamp) { value = c.library.content(id) }
    val aspects by produceState(FloatArray(0), id, issue.hasScan) {
        value = if (issue.hasScan) c.pdf.aspects(issue.pdfFile) else FloatArray(0)
    }
    val rtl by c.reading.rightToLeft.collectAsState()
    val showZones by c.reading.showZones.collectAsState()
    val textScale by c.reading.textScale.collectAsState()
    val bookmarks by c.reading.bookmarks.collectAsState()
    var mangaMode by rememberSaveable { mutableStateOf(false) }
    var chrome by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var hitRegion by remember { mutableStateOf<Pair<Int, Int>?>(null) } // page to region
    var jumpOpen by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val terms = remember(query) { query?.let { SearchText.parseQuery(it).highlightTerms }.orEmpty() }

    val cnt = content
    val totalPages = maxOf(aspects.size, cnt?.lastTextPage ?: 0)
    if (cnt == null || totalPages == 0) {
        Box(Modifier.fillMaxSize().background(Ink.Night), contentAlignment = Alignment.Center) {
            if (cnt == null) androidx.compose.material3.CircularProgressIndicator()
            else Text("This issue has no pages to show.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val initial = ((startPage ?: c.reading.lastPage(id)).coerceIn(1, totalPages)) - 1
    val pager = rememberPagerState(initialPage = initial) { totalPages }
    val scope = rememberCoroutineScope()
    val models = remember(cnt) { HashMap<Int, PageModel>() }
    fun model(page: Int): PageModel = models.getOrPut(page) { PageModel(page, cnt.page(page), cnt.regionsFor(page)) }

    val currentPage = pager.currentPage + 1
    val current = model(currentPage)

    // Remember position; clear per-page selection on page change.
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { p ->
            c.reading.recordPage(id, p + 1)
            if (selection?.page != p + 1) selection = null
            zoomed = false
        }
    }

    // Arriving from search: select the matching passage on the target page.
    LaunchedEffect(query, startPage) {
        if (terms.isEmpty()) return@LaunchedEffect
        val m = model(currentPage)
        val block = m.firstBlockMatching(terms)
        val region = block?.let { m.regionForBlock(it) } ?: m.firstRegionMatching(terms)
        selection = Selection(currentPage, block, region)
        if (region != null) hitRegion = currentPage to region
    }

    ImmersiveMode(hidden = !chrome)

    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded),
    )
    BackHandler(enabled = sheetState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch { sheetState.bottomSheetState.partialExpand() }
    }

    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val isBookmarked = bookmarks.any { it.issueId == id && it.page == currentPage }

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = if (chrome) 168.dp else 0.dp,
        sheetContainerColor = Ink.Surface,
        sheetContentColor = Ink.Text,
        containerColor = Color.Black,
        sheetShadowElevation = 12.dp,
        sheetContent = {
            Column(Modifier.heightIn(max = screenH * 0.82f).windowInsetsPadding(WindowInsets.navigationBars)) {
                SelectionCard(current, selection, textScale, terms,
                    onExpand = { scope.launch { sheetState.bottomSheetState.expand() } })
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Page $currentPage of $totalPages",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { jumpOpen = true }
                            .padding(vertical = 8.dp, horizontal = 4.dp).weight(1f),
                    )
                    TextButton(onClick = { c.reading.setTextScale(textScale - 0.1f) }) { Text("A−") }
                    TextButton(onClick = { c.reading.setTextScale(textScale + 0.1f) }) { Text("A+") }
                    IconButton(onClick = { c.reading.toggleBookmark(id, currentPage) }) {
                        Icon(
                            if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            if (isBookmarked) "Remove bookmark" else "Bookmark this page",
                            tint = if (isBookmarked) Ink.Vermilion else Ink.Text,
                        )
                    }
                }
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Ink.Surface,
                    contentColor = Ink.Text,
                    indicator = { pos ->
                        TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = Ink.Vermilion)
                    },
                    divider = { Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line)) },
                ) {
                    SheetTab.entries.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.label) })
                    }
                }
                PageText(
                    model = current,
                    tab = SheetTab.entries[tab],
                    selectedBlock = selection?.takeIf { it.page == currentPage }?.block,
                    textScale = textScale,
                    terms = terms,
                    onBlock = { b ->
                        selection = Selection(currentPage, b, current.regionForBlock(b))
                        hitRegion = null
                    },
                )
            }
        },
    ) { _ ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pager,
                reverseLayout = rtl,
                beyondViewportPageCount = 1,
                userScrollEnabled = !zoomed,
                key = { it },
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val pageNo = index + 1
                val m = model(pageNo)
                val hasScanPage = issue.hasScan && index < aspects.size
                if (hasScanPage) {
                    ScanPage(
                        pdf = issue.pdfFile,
                        pageIndex = index,
                        aspect = aspects[index],
                        model = m,
                        selectedRegion = selection?.takeIf { it.page == pageNo }?.region,
                        hitRegion = hitRegion?.takeIf { it.first == pageNo }?.second,
                        showZones = showZones,
                        mangaMode = mangaMode,
                        onTapRegion = { r ->
                            val blocks = m.blocksForRegion(r)
                            selection = Selection(pageNo, blocks.firstOrNull(), r)
                            hitRegion = null
                            chrome = true
                        },
                        onTapEmpty = {
                            if (selection != null) selection = null else chrome = !chrome
                        },
                        onZoomChanged = { z -> if (index == pager.currentPage) zoomed = z },
                    )
                } else {
                    TextOnlyPage(m, textScale, terms, onTap = { chrome = !chrome })
                }
            }

            AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
                TopBar(
                    title = issue.magazineName,
                    subtitle = prettyDate(issue.title),
                    mangaMode = mangaMode,
                    showZones = showZones,
                    rtl = rtl,
                    hasScan = issue.hasScan,
                    onBack = nav.back,
                    onManga = { mangaMode = it },
                    onZones = { c.reading.setShowZones(it) },
                    onRtl = { c.reading.setRightToLeft(it) },
                )
            }
        }
    }

    if (jumpOpen) {
        var target by remember { mutableStateOf(currentPage.toFloat()) }
        AlertDialog(
            onDismissRequest = { jumpOpen = false },
            title = { Text("Go to page ${target.toInt()}") },
            text = {
                Slider(value = target, onValueChange = { target = it }, valueRange = 1f..totalPages.toFloat(),
                    steps = (totalPages - 2).coerceAtLeast(0))
            },
            confirmButton = {
                TextButton(onClick = {
                    jumpOpen = false
                    scope.launch { pager.scrollToPage(target.toInt() - 1) }
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { jumpOpen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    mangaMode: Boolean,
    showZones: Boolean,
    rtl: Boolean,
    hasScan: Boolean,
    onBack: () -> Unit,
    onManga: (Boolean) -> Unit,
    onZones: (Boolean) -> Unit,
    onRtl: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        }
        if (hasScan) {
            IconToggleButton(checked = mangaMode, onCheckedChange = onManga) {
                Icon(Icons.Outlined.Translate, if (mangaMode) "Show original page" else "Show translation on the page",
                    tint = if (mangaMode) Ink.Vermilion else Color.White)
            }
            IconToggleButton(checked = showZones, onCheckedChange = onZones) {
                Icon(Icons.Outlined.CropFree, if (showZones) "Hide text zones" else "Show text zones",
                    tint = if (showZones) Ink.Blue else Color.White)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Reader options", tint = Color.White) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (rtl) "Swipe left to right instead" else "Swipe right to left (Japanese order)") },
                    onClick = { menu = false; onRtl(!rtl) },
                )
            }
        }
    }
}

/** The peeking part of the sheet: the passage for whatever box is selected. */
@Composable
private fun SelectionCard(model: PageModel, selection: Selection?, textScale: Float, terms: List<String>, onExpand: () -> Unit) {
    val base = MaterialTheme.typography.bodyLarge
    val style = base.copy(fontSize = base.fontSize * textScale, lineHeight = base.lineHeight * textScale)
    val sel = selection?.takeIf { it.page == model.number }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 260.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        val block = sel?.block
        when {
            block != null -> {
                val en = model.translation.getOrNull(block)
                val jp = model.original.getOrNull(block)
                if (en != null) MarkdownBlock(en, style, highlight = terms)
                if (jp != null) {
                    Spacer(Modifier.height(6.dp))
                    MarkdownBlock(jp, MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize * textScale),
                        highlight = terms)
                }
            }
            sel?.region != null -> {
                Text("No translation is linked to this box.", style = MaterialTheme.typography.bodyMedium, color = Ink.Muted)
                Spacer(Modifier.height(4.dp))
                Text(model.regions.getOrNull(sel.region)?.text.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
            model.summary.isNotEmpty() -> {
                Text(
                    model.summary.first().text.replace(Regex("""[*_#`]"""), ""),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Ink.Muted),
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onExpand),
                )
            }
            model.regions.isNotEmpty() -> Text("Tap any text on the page to translate it.", style = MaterialTheme.typography.bodyMedium, color = Ink.Muted)
            model.hasText -> Text("Pull up to read this page's translation.", style = MaterialTheme.typography.bodyMedium, color = Ink.Muted)
            else -> Text("No text for this page.", style = MaterialTheme.typography.bodyMedium, color = Ink.Muted)
        }
    }
}

@Composable
private fun PageText(
    model: PageModel,
    tab: SheetTab,
    selectedBlock: Int?,
    textScale: Float,
    terms: List<String>,
    onBlock: (Int) -> Unit,
) {
    val blocks = when (tab) {
        SheetTab.Translation -> model.translation
        SheetTab.Original -> model.original
        SheetTab.Summary -> model.summary
    }
    val base = if (tab == SheetTab.Original) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    val style = base.copy(fontSize = base.fontSize * textScale, lineHeight = base.lineHeight * textScale)
    val list = rememberLazyListState()
    LaunchedEffect(selectedBlock, tab) {
        if (selectedBlock != null && tab != SheetTab.Summary && selectedBlock < blocks.size) list.animateScrollToItem(selectedBlock)
    }
    if (blocks.isEmpty()) {
        Text(
            when (tab) {
                SheetTab.Translation -> "No translation for this page."
                SheetTab.Original -> "No transcription for this page."
                SheetTab.Summary -> "No summary for this page."
            },
            style = MaterialTheme.typography.bodyMedium, color = Ink.Muted,
            modifier = Modifier.padding(20.dp),
        )
        return
    }
    LazyColumn(state = list, contentPadding = PaddingValues(vertical = 12.dp)) {
        itemsIndexed(blocks) { i, b ->
            val selected = tab != SheetTab.Summary && i == selectedBlock
            Row(
                Modifier.fillMaxWidth()
                    .then(if (tab != SheetTab.Summary) Modifier.clickable { onBlock(i) } else Modifier)
                    .background(if (selected) Ink.Vermilion.copy(alpha = 0.10f) else Color.Transparent)
                    .padding(vertical = 8.dp),
            ) {
                Box(Modifier.padding(start = 10.dp).width(3.dp).height(if (selected) 22.dp else 0.dp).background(Ink.Vermilion))
                MarkdownBlock(b, style, Modifier.padding(start = 7.dp, end = 20.dp), highlight = terms)
            }
        }
    }
}

/** Shown instead of a scan for text-only installs (or pages past the end of the PDF). */
@Composable
private fun TextOnlyPage(model: PageModel, textScale: Float, terms: List<String>, onTap: () -> Unit) {
    val base = MaterialTheme.typography.bodyLarge
    val style = base.copy(fontSize = base.fontSize * textScale, lineHeight = base.lineHeight * textScale)
    Column(
        Modifier.fillMaxSize().background(Ink.Night).verticalScroll(rememberScrollState())
            .clickable(onClick = onTap)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Page ${model.number}", style = MaterialTheme.typography.headlineSmall)
        val blocks = model.translation.ifEmpty { model.original }
        if (blocks.isEmpty()) Text("No text for this page.", style = MaterialTheme.typography.bodyMedium, color = Ink.Muted)
        blocks.forEach { MarkdownBlock(it, style, highlight = terms) }
    }
}

/** Hides status and navigation bars while reading with the toolbar hidden. */
@Composable
private fun ImmersiveMode(hidden: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(hidden) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
