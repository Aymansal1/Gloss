@file:OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalSharedTransitionApi::class)

package app.gloss.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.Motion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

/** Space the floating tab bar takes at the bottom of the home tabs (0 elsewhere). */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/** Shared-element plumbing: the app's transition scope and the current destination's visibility. */
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** True once the list has been scrolled during a push or pop, so covers stop flying and stay put. */
private val LocalCoversLanded = compositionLocalOf { false }

/**
 * Provides [scope] as the destination's [AnimatedVisibilityScope] for shared covers.
 *
 * Scrolling while a cover flies back used to move its landing spot every frame, so it chased
 * the list around and drew over the header. Now the first scroll during the transition lets
 * go of the flight and every cover sits in its own place in the list.
 */
@Composable
fun ProvideNavScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    val running = scope.transition.isRunning
    var landed by remember(running) { mutableStateOf(false) }
    val watch = remember(running) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (running && available != Offset.Zero) landed = true
                return Offset.Zero
            }
        }
    }
    CompositionLocalProvider(LocalNavScope provides scope, LocalCoversLanded provides landed) {
        Box(Modifier.fillMaxSize().nestedScroll(watch)) { content() }
    }
}

/** Ties this element to the same [key] on the next screen, so it flies across the transition. */
@Composable
fun Modifier.sharedCover(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    if (LocalCoversLanded.current) return this
    return with(shared) {
        this@sharedCover.sharedElement(rememberSharedContentState(key), nav)
    }
}

/**
 * Frosted glass over whatever [state]'s source draws: the iOS bar material. A hairline on the
 * edge catches the light the way iOS's floating controls do.
 */
@Composable
fun Modifier.glass(state: HazeState, shape: Shape, tint: Color = Gloss.colors.surface, edge: Boolean = true): Modifier {
    val c = Gloss.colors
    val edgeColor = if (c.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.65f)
    return this
        .clip(shape)
        .hazeEffect(state = state, style = CupertinoMaterials.regular(containerColor = tint))
        .then(if (edge) Modifier.border(0.6.dp, edgeColor, shape) else Modifier)
}

/**
 * A screen with an iOS large title that scrolls away under a frosted bar, where a small centred
 * title fades in. [onBack] adds a lapis back chevron with [backLabel].
 */
@Composable
fun LargeTitleScreen(
    title: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    background: Color = Gloss.colors.ground,
    titlePadding: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val c = Gloss.colors
    val haze = remember { HazeState() }
    val density = LocalDensity.current
    val threshold = with(density) { 36.dp.toPx() }
    val collapsed by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > threshold }
    }
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, Motion.settle(), label = "bar")
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + LocalBottomBarPadding.current

    Box(modifier.fillMaxSize().background(background)) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze),
            state = state,
            contentPadding = PaddingValues(top = top + 44.dp, bottom = bottom + 28.dp),
        ) {
            item(key = "__large_title", contentType = "title") {
                Column(Modifier.fillMaxWidth().then(if (titlePadding) Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp) else Modifier)) {
                    if (titleContent != null) titleContent()
                    else Text(title, style = Gloss.type.largeTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    header?.invoke()
                }
            }
            content()
        }
        // The bar: transparent at rest, frosted once content slides under it.
        Box(
            Modifier
                .fillMaxWidth()
                .height(top + 44.dp)
                .graphicsLayer { alpha = barAlpha }
                .glass(haze, RoundedCornerShape(0.dp), edge = false),
        ) {
            Hairline(Modifier.align(Alignment.BottomStart))
        }
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(44.dp)) {
            if (onBack != null) BackChevron(backLabel, onBack, Modifier.align(Alignment.CenterStart))
            Text(
                title,
                style = Gloss.type.headline,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 96.dp)
                    .graphicsLayer { alpha = barAlpha; translationY = (1f - barAlpha) * 8f },
            )
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** "‹ Back" in lapis, iOS style. System Back and the predictive gesture still work as usual. */
@Composable
fun BackChevron(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Gloss.colors
    Row(
        modifier
            .height(48.dp)
            .pressable(onClick, scaleTo = 0.94f, onClickLabel = "Back")
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null, tint = c.lapis, modifier = Modifier.size(34.dp))
        Text(label, style = Gloss.type.body, color = c.lapis, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(x = (-4).dp))
    }
}

data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * The floating tab bar: a frosted capsule of tabs with a lapis-tinted pill that springs to the
 * selected one, and Search as its own round button beside it, the way iOS 26 lays it out.
 */
@Composable
fun GlassTabBar(
    haze: HazeState,
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    search: TabItem,
    searchSelected: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Gloss.colors
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(TabBarHeight)
                .shadow(18.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .glass(haze, CircleShape)
                .padding(4.dp),
        ) {
            val w = maxWidth / tabs.size
            val pillX by animateDpAsState(w * selected.coerceAtLeast(0), Motion.move(), label = "pill")
            val pillAlpha by animateFloatAsState(if (selected >= 0) 1f else 0f, Motion.settle(), label = "pillAlpha")
            Box(
                Modifier
                    .offset(x = pillX)
                    .width(w)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = pillAlpha }
                    .clip(CircleShape)
                    .background(c.lapis.copy(alpha = if (c.isDark) 0.28f else 0.12f)),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, tab ->
                    val on = i == selected
                    val tint by animateColorAsState(if (on) c.lapis else c.text, Motion.settle(), label = "tab")
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressable({ onSelect(i) }, scaleTo = 0.9f, haptic = !on, role = Role.Tab)
                            .semantics { this.selected = on; role = Role.Tab },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(if (on) tab.selectedIcon else tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(1.dp))
                        Text(tab.label, style = Gloss.type.caption2, color = tint, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        val searchTint by animateColorAsState(if (searchSelected) c.onLapis else c.text, Motion.settle(), label = "search")
        val searchBg by animateColorAsState(if (searchSelected) c.lapis else Color.Transparent, Motion.settle(), label = "searchBg")
        Box(
            Modifier
                .size(TabBarHeight)
                .shadow(18.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .glass(haze, CircleShape)
                .background(searchBg)
                .pressable(onSearch, scaleTo = 0.88f, haptic = !searchSelected, role = Role.Tab)
                .semantics { contentDescription = search.label; this.selected = searchSelected },
            contentAlignment = Alignment.Center,
        ) {
            Icon(search.icon, contentDescription = null, tint = searchTint, modifier = Modifier.size(26.dp))
        }
    }
}

val TabBarHeight: Dp = 62.dp

/** Bottom padding the home tabs need so their last row clears the floating tab bar. */
val TabBarClearance: Dp = TabBarHeight + 20.dp
