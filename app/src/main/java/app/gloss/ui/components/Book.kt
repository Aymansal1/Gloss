package app.gloss.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.gloss.ui.container
import app.gloss.ui.theme.InterDisplay
import app.gloss.ui.theme.InterText
import app.gloss.ui.theme.Pigment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Which way `graphicsLayer.rotationY` turns. The geometry below has a positive angle bring the
 * right edge toward the viewer; Android turns it away, so the sign flips.
 */
private const val TURN = -1f

/**
 * The issue's cover as a real magazine you can spin: drag sideways to turn it, with the spine
 * on the right (Japanese magazines bind on the right) and the back cover behind. It settles on
 * whichever face is nearer, and a tap on the back turns it to the front again.
 *
 * The back is the scan's last page once the issue is downloaded; the catalog only has front
 * covers, so before that it's a generated back in the series' colour. Thickness follows the
 * page count. [pinnedAngle] fixes the angle (for screenshots) and turns off the gestures.
 */
@Composable
fun SpinnableBook(
    info: CoverInfo,
    modifier: Modifier = Modifier,
    publisher: String = "",
    shared: CoverSharing = CoverSharing.None,
    pinnedAngle: Float? = null,
) {
    val c = container()
    val context = LocalContext.current
    val installed = c.library.issues.value[info.id]
    var back by remember(info.id) { mutableStateOf<ImageBitmap?>(null) }
    var pages by remember(info.id) { mutableIntStateOf(0) }
    LaunchedEffect(info.id, installed?.hasScan) {
        if (installed == null || !installed.hasScan) return@LaunchedEffect
        val file = c.pdf.ensureBackCover(installed.pdfFile, File(installed.dir, "back.jpg"))
        back = withContext(Dispatchers.IO) { file?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
        pages = c.pdf.pageCount(installed.pdfFile).takeIf { it > 0 }
            ?: runCatching { c.pdf.aspects(installed.pdfFile).size }.getOrDefault(0)
    }

    val angle = remember(info.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val a = pinnedAngle ?: angle.value
    val rad = Math.toRadians(a.toDouble())
    val sinA = sin(rad).toFloat()
    val cosA = cos(rad).toFloat()
    val showingBack = cosA < 0f

    // Settle on the nearer face, carrying the fling's speed so a flick spins it right round.
    fun settle(velocity: Float) {
        scope.launch {
            val target = ((angle.value + velocity * 0.18f) / 180f).roundToInt() * 180f
            angle.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 140f), initialVelocity = velocity)
            angle.snapTo(((target % 360f) + 360f) % 360f)
        }
    }
    fun flip() {
        scope.launch {
            angle.animateTo(angle.value + 180f, spring(dampingRatio = 0.82f, stiffness = 140f))
            angle.snapTo(((angle.value % 360f) + 360f) % 360f)
        }
    }

    // Once ever, a small turn shows that the cover moves.
    if (pinnedAngle == null) {
        LaunchedEffect(Unit) {
            val prefs = context.getSharedPreferences("book", Context.MODE_PRIVATE)
            if (prefs.getBoolean("hinted", false)) return@LaunchedEffect
            prefs.edit().putBoolean("hinted", true).apply()
            delay(700)
            angle.animateTo(26f, spring(dampingRatio = 0.75f, stiffness = 120f))
            angle.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 110f))
        }
    }

    val gestures = if (pinnedAngle != null) Modifier else Modifier
        .pointerInput(info.id) {
            val tracker = VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = { tracker.resetTracking(); scope.launch { angle.stop() } },
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    // A drag across the whole cover turns it half way round.
                    scope.launch { angle.snapTo(angle.value - dx / size.width * 180f) }
                },
                onDragEnd = { settle(-tracker.calculateVelocity().x / size.width * 180f) },
                onDragCancel = { settle(0f) },
            )
        }
        .pointerInput(info.id) { detectTapGestures(onTap = { if (cosOf(angle.value) < 0f) flip() }) }

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .aspectRatio(COVER_ASPECT)
            .then(if (shared == CoverSharing.Destination) Modifier.sharedCover("cover-${info.id}") else Modifier)
            .then(gestures)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (showingBack) "Show the front cover" else "Show the back cover") { flip(); true },
                )
            },
    ) {
        val w = maxWidth
        val count = if (pages > 0) pages else 200
        // About 4.5% of the width for 200 pages, like a real weekly.
        val thickness = w * (count / 200f * 0.045f).coerceIn(0.02f, 0.09f)
        val tPx = with(density) { thickness.toPx() }
        val halfW = with(density) { w.toPx() } / 2f
        val camera = 18f * density.density
        val shadow = with(density) { 10.dp.toPx() }

        if (sinA > 0.01f) {
            Spine(
                info,
                Modifier
                    .align(Alignment.Center)
                    .width(thickness)
                    .fillMaxHeight()
                    .graphicsLayer {
                        rotationY = TURN * (a - 90f)
                        translationX = halfW * cosA
                        cameraDistance = camera
                    },
            )
        }
        // Edge-on, a face is just a line with a shadow; leave it out.
        if (cosA > 0.02f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = TURN * a
                        translationX = -tPx / 2f * sinA
                        cameraDistance = camera
                        shadowElevation = shadow
                        shape = CoverShape
                        ambientShadowColor = Color.Black.copy(alpha = 0.22f)
                        spotShadowColor = Color.Black.copy(alpha = 0.32f)
                    },
            ) { IssueCover(info, Modifier.fillMaxSize(), shared = CoverSharing.None) }
        } else if (cosA < -0.02f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = TURN * (a + 180f)
                        translationX = tPx / 2f * sinA
                        cameraDistance = camera
                        shadowElevation = shadow
                        shape = CoverShape
                        clip = true
                        ambientShadowColor = Color.Black.copy(alpha = 0.22f)
                        spotShadowColor = Color.Black.copy(alpha = 0.32f)
                    },
            ) {
                val bmp = back
                if (bmp != null) {
                    Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    StockBack(info, publisher, Modifier.fillMaxSize())
                }
            }
        }
    }
}

private fun cosOf(degrees: Float) = cos(Math.toRadians(degrees.toDouble())).toFloat()

private fun Color.shade(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

/** The spine: the series' colour, rounded by light, with the masthead and issue running down it. */
@Composable
private fun Spine(info: CoverInfo, modifier: Modifier) {
    val pigment = Pigment.forSeries(info.magazineName)
    val on = Pigment.on(pigment)
    BoxWithConstraints(
        modifier.background(Brush.horizontalGradient(listOf(pigment.shade(0.3f), pigment.shade(0.05f), pigment.shade(0.25f)))),
        contentAlignment = Alignment.Center,
    ) {
        val length = maxHeight
        val size = (maxWidth.value * 0.55f).coerceIn(4f, 11f).sp
        Text(
            "${info.magazineName.uppercase()}   ${info.title}",
            color = on,
            fontFamily = InterDisplay,
            fontWeight = FontWeight(700),
            fontSize = size,
            lineHeight = size,
            letterSpacing = 0.02.em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            // Laid out along the spine's length, then turned to read top to bottom.
            modifier = Modifier.requiredWidth(length * 0.92f).graphicsLayer { rotationZ = 90f },
        )
    }
}

/**
 * A back cover for issues whose scan isn't on the phone: the series' colour, the masthead
 * small at the top, and a barcode panel like the one on every newsstand magazine.
 */
@Composable
private fun StockBack(info: CoverInfo, publisher: String, modifier: Modifier) {
    val pigment = Pigment.forSeries(info.magazineName)
    val on = Pigment.on(pigment)
    BoxWithConstraints(modifier.background(Brush.linearGradient(listOf(pigment.shade(0.12f), pigment.shade(0.38f))))) {
        val w = maxWidth
        Column(Modifier.fillMaxSize().padding(w * 0.09f)) {
            Text(
                info.magazineName.uppercase(),
                color = on.copy(alpha = 0.75f),
                fontFamily = InterDisplay,
                fontWeight = FontWeight(800),
                fontSize = (w.value * 0.06f).coerceIn(7f, 13f).sp,
                letterSpacing = 0.04.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (publisher.isNotBlank()) {
                Text(
                    publisher,
                    color = on.copy(alpha = 0.7f),
                    fontFamily = InterText,
                    fontSize = (w.value * 0.05f).coerceIn(6f, 11f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(w * 0.03f))
            }
            Column(
                Modifier
                    .background(Color.White, RoundedCornerShape(2.dp))
                    .padding(horizontal = w * 0.03f, vertical = w * 0.025f),
            ) {
                Barcode(info.id, Modifier.width(w * 0.4f).height(w * 0.14f))
                Text(
                    barcodeDigits(info.id),
                    color = Color.Black,
                    fontFamily = InterText,
                    fontSize = (w.value * 0.035f).coerceIn(5f, 9f).sp,
                    letterSpacing = 0.08.em,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun barcodeDigits(id: String): String {
    val r = Random(id.hashCode())
    return "4910" + (1..9).joinToString("") { r.nextInt(10).toString() }
}

/** Bars from the issue id, so each issue's back always looks the same. */
@Composable
private fun Barcode(id: String, modifier: Modifier) {
    Canvas(modifier) {
        val r = Random(id.hashCode() * 31)
        val bars = List(46) { 1 + r.nextInt(3) }
        val unit = size.width / (bars.sum() * 1.6f)
        var x = 0f
        bars.forEachIndexed { i, b ->
            val bw = b * unit
            if (i % 2 == 0) drawRect(Color.Black, Offset(x, 0f), Size(bw, size.height))
            x += bw * 1.6f
        }
    }
}

/** Whether a turned book is past edge-on, for callers that care which face shows. */
internal fun showsBack(angle: Float) = abs(((angle % 360f) + 360f) % 360f - 180f) < 90f
