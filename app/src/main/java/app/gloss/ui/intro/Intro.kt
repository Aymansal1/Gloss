package app.gloss.ui.intro

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gloss.ui.components.PillButton
import app.gloss.ui.components.Wordmark
import app.gloss.ui.components.goldLeafUnderline
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GoldLeaf
import app.gloss.ui.theme.Motion
import app.gloss.ui.theme.Pigment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The welcome screen. A fresh install sees it once; people updating from an older version
 * never do, and anyone can bring it back from Settings.
 */
object Intro {
    private const val PREFS = "intro"
    private const val SEEN = "seen"

    private val _showing = MutableStateFlow(false)
    val showing: StateFlow<Boolean> = _showing.asStateFlow()

    /** Decides once per launch whether a first-time reader should be welcomed. */
    fun check(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SEEN, false)) return
        // An update keeps the first install time, so only a fresh install has the two equal.
        val fresh = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).let { it.firstInstallTime == it.lastUpdateTime }
        }.getOrDefault(false)
        if (fresh) _showing.value = true else markSeen(context)
    }

    fun show() { _showing.value = true }

    fun dismiss(context: Context) {
        markSeen(context)
        _showing.value = false
    }

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SEEN, true).apply()
    }
}

/** Whether the system asks for no animation (animator duration scale 0). */
private fun motionOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * iOS-style welcome: the name, a page being glossed over and over, three things Gloss does,
 * and one button. [animate] off shows the demo already glossed and everything in place.
 */
@Composable
fun IntroScreen(onDone: () -> Unit, animate: Boolean = !motionOff(LocalContext.current)) {
    val g = Gloss.colors
    BackHandler(onBack = onDone)
    Box(Modifier.fillMaxSize().background(g.ground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 36.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.rise(0, animate), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Welcome to", style = Gloss.type.title2, color = g.secondary)
                Wordmark(Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Japanese game magazines, with the English one tap away.",
                style = Gloss.type.body, color = g.secondary, textAlign = TextAlign.Center,
                modifier = Modifier.rise(1, animate).widthIn(max = 320.dp),
            )
            Spacer(Modifier.height(28.dp))
            GlossDemo(animate, Modifier.rise(2, animate).widthIn(max = 380.dp).fillMaxWidth())
            Spacer(Modifier.height(32.dp))
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Feature(Icons.Rounded.TouchApp, "Tap to translate",
                    "Tap any text on a scan and its English appears right beside it, on the page.",
                    Modifier.rise(3, animate))
                Feature(Icons.Rounded.School, "Learn as you read",
                    "Tap Learn on a translation for each word's reading, meaning and voice. Star the ones to keep.",
                    Modifier.rise(4, animate))
                Feature(Icons.Rounded.DownloadForOffline, "Read offline",
                    "Download an issue once and read it anywhere, no connection needed.",
                    Modifier.rise(5, animate))
                Feature(Icons.Rounded.Search, "Search every page",
                    "Find a word in English or Japanese across every issue you've downloaded.",
                    Modifier.rise(6, animate))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Scans and translations come from Gaming Alexandria's archive. Gloss isn't affiliated with them, and the translations are machine-made, so now and then they're wrong.",
                style = Gloss.type.footnote, color = g.tertiary, textAlign = TextAlign.Center,
                modifier = Modifier.rise(7, animate).widthIn(max = 380.dp),
            )
        }
        // The button stays put while the rest scrolls under a fade.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to g.ground.copy(alpha = 0f), 0.3f to g.ground))
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PillButton("Start reading", onDone, Modifier.widthIn(max = 420.dp).fillMaxWidth())
        }
    }
}

/** Each block floats up into place a beat after the one above it. */
@Composable
private fun Modifier.rise(order: Int, animate: Boolean): Modifier {
    val k = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
        delay(90L + order * 70L)
        k.animateTo(1f, Motion.move())
    }
    val lift = with(LocalDensity.current) { 18.dp.toPx() }
    return graphicsLayer {
        alpha = k.value.coerceIn(0f, 1f)
        translationY = (1f - k.value) * lift
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    val g = Gloss.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(g.lapisTint), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = g.lapis, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Gloss.type.headline, color = g.text)
            Spacer(Modifier.height(2.dp))
            Text(body, style = Gloss.type.subhead, color = g.secondary)
        }
    }
}

private val Paper = Color(0xFFF3EEE3)
private val Ink = Color(0xFF1C1A16)
private val InkSoft = Color(0xFF6E685C)

/**
 * A made-up magazine page that glosses itself on a loop: a finger taps the headline, gold
 * leaf sweeps under it and the English card grows out of the box, then it all clears.
 */
@Composable
private fun GlossDemo(animate: Boolean, modifier: Modifier = Modifier) {
    val start = if (animate) 0f else 1f
    val tap = remember { Animatable(0f) }
    val sweep = remember { Animatable(start) }
    val card = remember { Animatable(start) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        delay(900)
        while (true) {
            tap.snapTo(0f)
            tap.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            launch { tap.animateTo(0f, tween(260)) }
            launch { sweep.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) }
            delay(140)
            card.animateTo(1f, Motion.reveal())
            delay(2800)
            launch { sweep.animateTo(0f, Motion.settle()) }
            card.animateTo(0f, Motion.settle())
            delay(1100)
        }
    }

    // Purely a picture: the features below say the same thing in words.
    Box(modifier.height(272.dp).clearAndSetSemantics { }) {
        Column(
            Modifier
                .fillMaxSize()
                .shadow(14.dp, RoundedCornerShape(8.dp), clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.26f))
                .clip(RoundedCornerShape(8.dp))
                .background(Paper)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("特集", style = Gloss.type.caption.copy(fontWeight = FontWeight.Bold), color = Color(0xFFD5412A))
                Spacer(Modifier.weight(1f))
                Text("p. 12", style = Gloss.type.glossBody.copy(fontSize = 15.sp), color = InkSoft)
            }
            Spacer(Modifier.height(8.dp))
            Box {
                Text(
                    "今月の新作ソフト",
                    style = Gloss.type.title1.copy(fontWeight = FontWeight.Black),
                    color = Ink,
                    modifier = Modifier
                        .background(g_wash(sweep.value), RoundedCornerShape(3.dp))
                        .goldLeafUnderline(sweep.value, thickness = 4.dp)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                )
                // The fingertip, pressing down on the headline.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .graphicsLayer {
                            val t = tap.value
                            alpha = t * 0.9f
                            scaleX = 1.3f - 0.3f * t
                            scaleY = 1.3f - 0.3f * t
                        }
                        .background(Ink.copy(alpha = 0.16f), CircleShape)
                        .border(1.5.dp, Ink.copy(alpha = 0.35f), CircleShape),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Box(
                    Modifier
                        .size(width = 96.dp, height = 120.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.linearGradient(listOf(Pigment.Azurite, Pigment.Lapis))),
                )
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("冬の大作がついに登場！", style = Gloss.type.footnote.copy(fontWeight = FontWeight.Bold), color = Ink)
                    Text(
                        "発売日と価格をまとめてチェック。話題のアクションからRPGまで、今月はこの10本に注目だ。",
                        style = Gloss.type.caption.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
                        color = InkSoft,
                    )
                }
            }
        }
        // The gloss card, as the reader draws it.
        Column(
            Modifier
                .padding(start = 26.dp, end = 26.dp, top = 92.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val k = card.value.coerceIn(0f, 1f)
                    alpha = k
                    scaleX = 0.94f + 0.06f * k
                    scaleY = 0.94f + 0.06f * k
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .shadow(20.dp, RoundedCornerShape(18.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF2141832))
                .border(0.8.dp, GoldLeaf.Mid.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text("This month's new games", style = Gloss.type.callout.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(
                "The big winter releases are finally here! Every release date and price in one place.",
                style = Gloss.type.callout, color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(8.dp))
            Text("今月の新作ソフト", style = Gloss.type.footnote, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(18.dp).height(2.dp).background(Brush.horizontalGradient(GoldLeaf.stops), RoundedCornerShape(1.dp)))
                Spacer(Modifier.width(8.dp))
                Text("Whole page", style = Gloss.type.footnote.copy(fontWeight = FontWeight(600)), color = GoldLeaf.Hi)
            }
        }
    }
}

/** The gold wash that fills a tapped box, faded in with the sweep. */
private fun g_wash(k: Float) = Color(0x66F2C94C).copy(alpha = 0.4f * k.coerceIn(0f, 1f))
