@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.gloss

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.gloss.ui.browse.DiscoverScreen
import app.gloss.ui.browse.SeriesScreen
import app.gloss.ui.components.GlassTabBar
import app.gloss.ui.components.LocalBottomBarPadding
import app.gloss.ui.components.LocalSharedScope
import app.gloss.ui.components.ProvideNavScope
import app.gloss.ui.components.TabBarClearance
import app.gloss.ui.components.TabItem
import app.gloss.ui.container
import app.gloss.ui.issue.IssueScreen
import app.gloss.ui.library.LibraryScreen
import app.gloss.ui.reader.ReaderScreen
import app.gloss.ui.search.SearchScreen
import app.gloss.ui.settings.SettingsScreen
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GlossTheme
import app.gloss.ui.theme.Motion
import app.gloss.ui.theme.isGlossDark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by container().reading.appearance.collectAsState()
            val dark = isGlossDark(appearance)
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            GlossTheme(dark = dark) { AppNav() }
        }
    }
}

/** Navigation callbacks shared by every screen. */
class Nav(
    val issue: (String) -> Unit,
    val series: (String) -> Unit,
    val read: (id: String, page: Int?, query: String?) -> Unit,
    val settings: () -> Unit,
    val back: () -> Unit,
)

// iOS-style push: the new screen slides in over the old one, which drifts a quarter-width behind.
private val push: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(Motion.offset()) { it } }
private val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(Motion.offset()) { -it / 4 } + fadeOut(tween(300), targetAlpha = 0.7f) }
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { slideInHorizontally(Motion.offset()) { -it / 4 } + fadeIn(tween(300), initialAlpha = 0.7f) }
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { slideOutHorizontally(Motion.offset()) { it } }

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val actions = remember(nav) {
        Nav(
            issue = { nav.navigate("issue/${Uri.encode(it)}") },
            series = { nav.navigate("series/${Uri.encode(it)}") },
            read = { id, page, q ->
                nav.navigate("read/${Uri.encode(id)}?page=${page ?: -1}&q=${Uri.encode(q ?: "")}")
            },
            settings = { nav.navigate("settings") },
            back = { nav.popBackStack() },
        )
    }
    SharedTransitionLayout(Modifier.fillMaxSize().background(Gloss.colors.ground)) {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                nav,
                startDestination = "home",
                enterTransition = push,
                exitTransition = pushExit,
                popEnterTransition = popEnter,
                popExitTransition = popExit,
            ) {
                composable("home") { ProvideNavScope(this) { Home(actions) } }
                composable("settings") { ProvideNavScope(this) { SettingsScreen(actions) } }
                composable("series/{name}") { e ->
                    ProvideNavScope(this) { SeriesScreen(Uri.decode(e.arguments?.getString("name").orEmpty()), actions) }
                }
                composable("issue/{id}") { e ->
                    ProvideNavScope(this) { IssueScreen(Uri.decode(e.arguments?.getString("id").orEmpty()), actions) }
                }
                // Opening an issue zooms up into the page rather than sliding sideways.
                composable(
                    "read/{id}?page={page}&q={q}",
                    arguments = listOf(
                        navArgument("page") { type = NavType.IntType; defaultValue = -1 },
                        navArgument("q") { type = NavType.StringType; defaultValue = "" },
                    ),
                    enterTransition = { fadeIn(tween(220)) + scaleIn(Motion.move(), initialScale = 0.92f) },
                    popExitTransition = { fadeOut(tween(200)) + scaleOut(Motion.move(), targetScale = 0.92f) },
                ) { e ->
                    ProvideNavScope(this) {
                        ReaderScreen(
                            id = Uri.decode(e.arguments?.getString("id").orEmpty()),
                            startPage = e.arguments?.getInt("page")?.takeIf { it > 0 },
                            query = e.arguments?.getString("q").orEmpty().ifBlank { null },
                            nav = actions,
                        )
                    }
                }
            }
        }
    }
}

private const val DISCOVER = 0
private const val LIBRARY = 1
private const val SEARCH = 2

@Composable
private fun Home(nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(DISCOVER) }
    // Each tab keeps its own scroll position and filters while you switch between them.
    val tabStates = rememberSaveableStateHolder()
    // Tapping the tab you're already on scrolls it back to the top.
    val reselect = remember { List(3) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) } }
    // Back from Library or Search returns to Discover before leaving the app.
    BackHandler(enabled = tab != DISCOVER) { tab = DISCOVER }
    val haze = remember { HazeState() }
    val tabs = remember {
        listOf(
            TabItem("Discover", Icons.Outlined.AutoStories, Icons.Rounded.AutoStories),
            TabItem("Library", Icons.Outlined.CollectionsBookmark, Icons.Rounded.CollectionsBookmark),
        )
    }

    Box(Modifier.fillMaxSize().background(Gloss.colors.ground)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            CompositionLocalProvider(LocalBottomBarPadding provides TabBarClearance) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "tabs",
                ) { t ->
                    tabStates.SaveableStateProvider(t) {
                        when (t) {
                            DISCOVER -> DiscoverScreen(nav, reselect[DISCOVER])
                            LIBRARY -> LibraryScreen(nav, reselect[LIBRARY])
                            else -> SearchScreen(nav, reselect[SEARCH])
                        }
                    }
                }
            }
        }
        GlassTabBar(
            haze = haze,
            tabs = tabs,
            selected = if (tab == SEARCH) -1 else tab,
            onSelect = { i -> if (i == tab) reselect[i].tryEmit(Unit) else tab = i },
            search = TabItem("Search", Icons.Rounded.Search),
            searchSelected = tab == SEARCH,
            onSearch = { if (tab == SEARCH) reselect[SEARCH].tryEmit(Unit) else tab = SEARCH },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
