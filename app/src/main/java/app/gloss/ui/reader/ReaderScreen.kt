package app.gloss.ui.reader

import android.app.Activity
import android.util.Log
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.gloss.Nav
import app.gloss.data.IssueContent
import app.gloss.search.SearchText
import app.gloss.ui.components.GlossSwitch
import app.gloss.ui.components.Hairline
import app.gloss.ui.components.MarkdownBlock
import app.gloss.ui.components.RoundIconButton
import app.gloss.ui.components.SegmentedControl
import app.gloss.ui.components.glass
import app.gloss.ui.components.pressable
import app.gloss.ui.container
import app.gloss.ui.prettyDate
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GlossTheme
import app.gloss.ui.theme.GoldLeaf
import app.gloss.ui.theme.Motion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SheetTab(val label: String) { Translation("Translation"), Original("Original"), Summary("Summary") }

/** The reader is always dark: scans read best on a near-black ground, whatever the app theme. */
@Composable
fun ReaderScreen(id: String, startPage: Int?, query: String?, nav: Nav) {
    GlossTheme(dark = true) { Reader(id, startPage, query, nav) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Reader(id: String, startPage: Int?, query: String?, nav: Nav) {
    val c = container()
    val g = Gloss.colors
    val installedMap by c.library.issues.collectAsState()
    val issue = installedMap[id]
    if (issue == null) {
        Message("This issue isn't on your phone anymore.")
        return
    }

    val content by produceState<IssueContent?>(null, id, issue.stamp) { value = c.library.content(id) }
    // Page shapes come from the PDF; wait for them so the reader opens on the right page.
    val pageAspects by produceState<FloatArray?>(null, id, issue.hasScan) {
        value = if (issue.hasScan) c.pdf.aspects(issue.pdfFile) else FloatArray(0)
    }
    val rtl by c.reading.rightToLeft.collectAsState()
    val spreadsOn by c.reading.spreads.collectAsState()
    val showZones by c.reading.showZones.collectAsState()
    val textScale by c.reading.textScale.collectAsState()
    val bookmarks by c.reading.bookmarks.collectAsState()
    var mangaMode by rememberSaveable { mutableStateOf(false) }
    var chrome by rememberSaveable { mutableStateOf(true) }
    val zoomedPages = remember { mutableStateMapOf<Int, Boolean>() }
    var selection by remember { mutableStateOf<Selection?>(null) }
    var hitRegion by remember { mutableStateOf<Pair<Int, Int>?>(null) } // page to region
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var options by remember { mutableStateOf(false) }
    // The page the reader is "on"; survives rotation so a spread re-opens on the right half.
    var anchorPage by rememberSaveable { mutableIntStateOf(startPage ?: c.reading.lastPage(id)) }
    val terms = remember(query) { query?.let { SearchText.parseQuery(it).highlightTerms }.orEmpty() }

    val cnt = content
    val aspects = pageAspects
    if (cnt == null || aspects == null) {
        Box(Modifier.fillMaxSize().background(g.ground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = g.lapis)
        }
        return
    }
    val totalPages = maxOf(aspects.size, cnt.lastTextPage)
    if (totalPages == 0) {
        Message("This issue has no pages to show.")
        return
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useSpreads = spreadsOn && landscape && issue.hasScan
    val screens = remember(totalPages, useSpreads) { Spreads.layout(totalPages, useSpreads) }
    val models = remember(cnt) { ConcurrentHashMap<Int, PageModel>() }
    fun model(page: Int): PageModel = models.getOrPut(page) { PageModel(page, cnt.page(page), cnt.regionsFor(page)) }
    // Matching text to boxes on the scan is the slow part, so do it in the background ahead of
    // time, nearest pages first, instead of while a page is being swiped in.
    LaunchedEffect(cnt) {
        withContext(Dispatchers.Default) {
            val from = anchorPage
            (1..totalPages).sortedBy { abs(it - from) }.forEach { p ->
                ensureActive()
                runCatching { model(p) }.onFailure { Log.e("Reader", "Couldn't prepare page $p", it) }
            }
        }
    }

    val scope = rememberCoroutineScope()
    val haze = remember { HazeState() }
    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded),
    )
    BackHandler(enabled = sheetState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch { sheetState.bottomSheetState.partialExpand() }
    }

    // Switching between single pages and spreads rebuilds the pager on the same page.
    key(useSpreads) {
        val pager = rememberPagerState(initialPage = Spreads.indexOf(screens, anchorPage.coerceIn(1, totalPages))) { screens.size }
        val screen = screens.getOrElse(pager.currentPage) { listOf(1) }
        // In a spread, the page you touched last is the one the sheet talks about.
        val currentPage = selection?.page?.takeIf { it in screen } ?: screen.first()
        val current = model(currentPage)
        val zoomed = screen.any { zoomedPages[it] == true }

        LaunchedEffect(pager) {
            snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { i ->
                val pages = screens.getOrElse(i) { listOf(1) }
                anchorPage = pages.first()
                c.reading.recordPage(id, pages.first())
                if (selection?.page !in pages) selection = null
                zoomedPages.clear()
            }
        }

        // Arriving from search: gloss the matching passage on the target page.
        LaunchedEffect(query, startPage) {
            if (terms.isEmpty()) return@LaunchedEffect
            val page = startPage ?: currentPage
            val m = model(page)
            val block = m.firstBlockMatching(terms)
            val region = block?.let { m.regionForBlock(it) } ?: m.firstRegionMatching(terms)
            selection = Selection(page, block, region)
            if (region != null) hitRegion = page to region
        }

        ImmersiveMode(hidden = !chrome)

        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val peek = 148.dp + navInset
        val sheetPeek by animateDpAsState(if (chrome) peek else 0.dp, Motion.settle(), label = "peek")
        val isBookmarked = bookmarks.any { it.issueId == id && it.page == currentPage }

        BottomSheetScaffold(
            scaffoldState = sheetState,
            sheetPeekHeight = sheetPeek,
            sheetContainerColor = Color.Transparent,
            sheetContentColor = g.text,
            containerColor = Color.Black,
            sheetShadowElevation = 0.dp,
            sheetTonalElevation = 0.dp,
            sheetShape = SheetShape,
            sheetDragHandle = null,
            sheetContent = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.84f)
                        .glass(haze, SheetShape, tint = g.raised)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    Grabber()
                    PageBar(
                        label = Spreads.label(screen),
                        total = totalPages,
                        bookmarked = isBookmarked,
                        onBookmark = { c.reading.toggleBookmark(id, currentPage) },
                    )
                    Scrubber(
                        screens = screens,
                        current = pager.currentPage,
                        rtl = rtl,
                        thumb = { page, mod ->
                            if (issue.hasScan && page - 1 < aspects.size) PageThumb(issue.pdfFile, page - 1, mod)
                            else Box(mod.background(g.fill), contentAlignment = Alignment.Center) {
                                Text("$page", style = Gloss.type.caption, color = g.secondary)
                            }
                        },
                        onPick = { scope.launch { pager.animateScrollToPage(it) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        SegmentedControl(SheetTab.entries.map { it.label }, tab, { tab = it })
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
                    key = { screens[it].first() },
                    modifier = Modifier.fillMaxSize().hazeSource(haze),
                ) { index ->
                    val pages = screens[index]
                    val calloutBottom = if (chrome) peek + 8.dp else navInset + 16.dp
                    @Composable
                    fun Page(pageNo: Int, mod: Modifier) {
                        val m = model(pageNo)
                        val sel = selection?.takeIf { it.page == pageNo }
                        val note = sel?.region?.let { r ->
                            GlossNote(m.translationForRegion(r), m.blocksForRegion(r).mapNotNull { m.original.getOrNull(it) })
                        }
                        if (issue.hasScan && pageNo - 1 < aspects.size) {
                            ScanPage(
                                pdf = issue.pdfFile,
                                pageIndex = pageNo - 1,
                                aspect = aspects[pageNo - 1],
                                model = m,
                                selectedRegion = sel?.region,
                                hitRegion = hitRegion?.takeIf { it.first == pageNo }?.second,
                                showZones = showZones,
                                mangaMode = mangaMode,
                                gloss = note,
                                textScale = textScale,
                                calloutBottom = calloutBottom,
                                onTapRegion = { r ->
                                    selection = Selection(pageNo, m.blocksForRegion(r).firstOrNull(), r)
                                    hitRegion = null
                                },
                                onTapEmpty = {
                                    if (selection != null) selection = null else chrome = !chrome
                                },
                                onZoomChanged = { z -> zoomedPages[pageNo] = z },
                                onOpenGloss = {
                                    tab = SheetTab.Translation.ordinal
                                    scope.launch { sheetState.bottomSheetState.expand() }
                                },
                                modifier = mod,
                            )
                        } else {
                            TextOnlyPage(m, textScale, terms, onTap = { chrome = !chrome }, modifier = mod)
                        }
                    }
                    if (pages.size == 1) {
                        Page(pages[0], Modifier.fillMaxSize())
                    } else {
                        // Facing pages meet at the gutter, the way the magazine was bound.
                        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val a = aspects.getOrElse(pages[0] - 1) { 0.72f }.takeIf { it > 0f } ?: 0.72f
                            val pageW = minOf(maxWidth / 2, maxHeight * a)
                            val ordered = if (rtl) pages.reversed() else pages
                            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                                ordered.forEach { p -> Page(p, Modifier.width(pageW).fillMaxHeight().clipToBounds()) }
                            }
                        }
                    }
                }

                ReaderTopBar(
                    visible = chrome,
                    haze = haze,
                    title = issue.magazineName,
                    subtitle = prettyDate(issue.title),
                    hasScan = issue.hasScan,
                    mangaMode = mangaMode,
                    onBack = nav.back,
                    onManga = { mangaMode = !mangaMode },
                    onOptions = { options = true },
                )

                if (options) {
                    OptionsPopover(
                        textScale = textScale,
                        showZones = showZones,
                        rtl = rtl,
                        spreads = spreadsOn,
                        hasScan = issue.hasScan,
                        onTextScale = { c.reading.setTextScale(it) },
                        onZones = { c.reading.setShowZones(it) },
                        onRtl = { c.reading.setRightToLeft(it) },
                        onSpreads = { c.reading.setSpreads(it) },
                        onDismiss = { options = false },
                    )
                }
            }
        }
    }
}

private val SheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().background(Gloss.colors.ground).padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Gloss.type.body, color = Gloss.colors.secondary, textAlign = TextAlign.Center)
    }
}

/** Back, the issue in a glass capsule, and the two things you toggle while reading. */
@Composable
private fun ReaderTopBar(
    visible: Boolean,
    haze: HazeState,
    title: String,
    subtitle: String,
    hasScan: Boolean,
    mangaMode: Boolean,
    onBack: () -> Unit,
    onManga: () -> Unit,
    onOptions: () -> Unit,
) {
    val g = Gloss.colors
    AnimatedVisibility(
        visible,
        enter = fadeIn(Motion.settle()) + slideInVertically(Motion.offset()) { -it / 2 },
        exit = fadeOut(Motion.settle()) + slideOutVertically(Motion.offset()) { -it / 2 },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircle(haze, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back", onBack)
                Column(
                    Modifier
                        .weight(1f)
                        .glass(haze, CircleShape, tint = g.raised)
                        .padding(horizontal = 18.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, style = Gloss.type.subhead.copy(fontWeight = FontWeight(600)), color = g.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = Gloss.type.caption, color = g.secondary, maxLines = 1)
                }
                if (hasScan) {
                    GlassCircle(
                        haze, Icons.Rounded.Translate,
                        if (mangaMode) "Show the original page" else "Set the English on the page",
                        onManga, active = mangaMode,
                    )
                }
                GlassText(haze, "Aa", "Reading options", onOptions)
            }
        }
    }
}

@Composable
private fun GlassCircle(haze: HazeState, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, active: Boolean = false) {
    val g = Gloss.colors
    Box(
        Modifier
            .size(44.dp)
            .pressable(onClick, scaleTo = 0.88f, onClickLabel = label, role = Role.Button)
            .then(if (active) Modifier.clip(CircleShape).background(Brush.linearGradient(GoldLeaf.stops)) else Modifier.glass(haze, CircleShape, tint = g.raised)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Color(0xFF2A1D05) else g.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun GlassText(haze: HazeState, text: String, label: String, onClick: () -> Unit) {
    val g = Gloss.colors
    Box(
        Modifier
            .size(44.dp)
            .pressable(onClick, scaleTo = 0.88f, onClickLabel = label, role = Role.Button)
            .glass(haze, CircleShape, tint = g.raised),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Gloss.type.headline, color = g.text)
    }
}

/** A small panel under the "Aa" button, like an iOS popover. */
@Composable
private fun OptionsPopover(
    textScale: Float,
    showZones: Boolean,
    rtl: Boolean,
    spreads: Boolean,
    hasScan: Boolean,
    onTextScale: (Float) -> Unit,
    onZones: (Boolean) -> Unit,
    onRtl: (Boolean) -> Unit,
    onSpreads: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val g = Gloss.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 60.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(-10.dp.roundToPx(), top.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(
            shown,
            enter = fadeIn(Motion.settle()) + scaleIn(Motion.move(), initialScale = 0.86f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
            exit = fadeOut() + scaleOut(),
        ) {
            Column(
                Modifier
                    .width(272.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(g.raised)
                    .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp)),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(Icons.Rounded.Remove, "Smaller text", { onTextScale(textScale - 0.1f) }, tint = g.text, size = 36.dp)
                    Text(
                        "${(textScale * 100).roundToInt()}%",
                        style = Gloss.type.headline, color = g.text, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    RoundIconButton(Icons.Rounded.Add, "Larger text", { onTextScale(textScale + 0.1f) }, tint = g.text, size = 36.dp)
                }
                Hairline()
                if (hasScan) {
                    OptionRow("Outline text boxes", showZones, onZones)
                    Hairline(Modifier.padding(start = 16.dp))
                    OptionRow("Spreads sideways", spreads, onSpreads)
                    Hairline(Modifier.padding(start = 16.dp))
                }
                OptionRow("Right to left", rtl, onRtl)
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Gloss.type.body, color = Gloss.colors.text, modifier = Modifier.weight(1f))
        GlossSwitch(checked, onChange)
    }
}

@Composable
private fun Grabber() {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(36.dp, 5.dp).clip(CircleShape).background(Gloss.colors.tertiary))
    }
}

/** "12 of 180" in the gloss hand, and the bookmark. */
@Composable
private fun PageBar(label: String, total: Int, bookmarked: Boolean, onBookmark: () -> Unit) {
    val g = Gloss.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Gloss.type.glossTitle, color = GoldLeaf.Hi)
        Spacer(Modifier.width(8.dp))
        Text("of $total", style = Gloss.type.subhead, color = g.secondary, modifier = Modifier.weight(1f).offset(y = 2.dp))
        RoundIconButton(
            if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            if (bookmarked) "Remove bookmark" else "Bookmark this page",
            onBookmark,
            tint = if (bookmarked) g.vermilion else g.text,
        )
    }
}

/** Page thumbnails to slide through; the current one lifts and gets a gold edge. */
@Composable
private fun Scrubber(
    screens: List<List<Int>>,
    current: Int,
    rtl: Boolean,
    thumb: @Composable (page: Int, Modifier) -> Unit,
    onPick: (Int) -> Unit,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    LaunchedEffect(current) { list.animateScrollToItem((current - 3).coerceAtLeast(0)) }
    LazyRow(
        state = list,
        reverseLayout = rtl,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        itemsIndexed(screens, key = { _, s -> s.first() }) { i, pages ->
            val on = i == current
            val lift by animateDpAsState(if (on) 0.dp else 6.dp, Motion.press(), label = "lift")
            Row(
                Modifier
                    .padding(top = lift)
                    .pressable({ onPick(i) }, scaleTo = 0.92f, onClickLabel = "Go to page ${Spreads.label(pages)}")
                    .clip(RoundedCornerShape(4.dp))
                    .then(if (on) Modifier.border(1.5.dp, Brush.linearGradient(GoldLeaf.stops), RoundedCornerShape(4.dp)) else Modifier)
                    .graphicsLayer { alpha = if (on) 1f else 0.62f },
            ) {
                val ordered = if (rtl) pages.reversed() else pages
                ordered.forEach { p -> thumb(p, Modifier.size(width = 40.dp, height = 56.dp)) }
            }
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
    val g = Gloss.colors
    val blocks = when (tab) {
        SheetTab.Translation -> model.translation
        SheetTab.Original -> model.original
        SheetTab.Summary -> model.summary
    }
    val base = if (tab == SheetTab.Original) Gloss.type.callout else Gloss.type.body
    val style = base.copy(color = g.text, fontSize = base.fontSize * textScale, lineHeight = base.lineHeight * textScale)
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
            style = Gloss.type.subhead, color = g.secondary,
            modifier = Modifier.padding(20.dp),
        )
        return
    }
    LazyColumn(state = list, contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)) {
        itemsIndexed(blocks) { i, b ->
            val selectable = tab != SheetTab.Summary
            val selected = selectable && i == selectedBlock
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (selectable) Modifier.pressable({ onBlock(i) }, scaleTo = 0.99f, haptic = false) else Modifier)
                    .background(if (selected) g.goldWash else Color.Transparent)
                    .padding(vertical = 9.dp),
            ) {
                Box(
                    Modifier.padding(start = 10.dp).width(3.dp).height(if (selected) 22.dp else 0.dp)
                        .background(Brush.verticalGradient(GoldLeaf.stops), CircleShape),
                )
                MarkdownBlock(b, style, Modifier.padding(start = 7.dp, end = 20.dp), highlight = terms)
            }
        }
    }
}

/** Shown instead of a scan for text-only installs (or pages past the end of the PDF). */
@Composable
private fun TextOnlyPage(model: PageModel, textScale: Float, terms: List<String>, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val g = Gloss.colors
    val base = Gloss.type.body
    val style = base.copy(color = g.text, fontSize = base.fontSize * textScale, lineHeight = base.lineHeight * textScale)
    Column(
        modifier.fillMaxSize().background(g.ground).verticalScroll(rememberScrollState())
            .pressable(onTap, role = null, scaleTo = 1f)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 200.dp)
            .widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("${model.number}", style = Gloss.type.glossDisplay, color = GoldLeaf.Hi)
        val blocks = model.translation.ifEmpty { model.original }
        if (blocks.isEmpty()) Text("No text for this page.", style = Gloss.type.subhead, color = g.secondary)
        blocks.forEach { MarkdownBlock(it, style, highlight = terms) }
    }
}

/** Hides the system bars while reading with the chrome hidden; keeps their icons light on the dark reader. */
@Composable
private fun ImmersiveMode(hidden: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(controller) {
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNav
        }
    }
    DisposableEffect(hidden) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
