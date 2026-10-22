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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toIntSize
import app.gloss.ui.container
import app.gloss.ui.theme.InterDisplay
import app.gloss.ui.theme.InterText
import app.gloss.ui.theme.Pigment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The angle a book rests at: turned a little so its spine and depth show. */
private const val REST = 22f

/**
 * The issue's cover as a real magazine you can spin. It's drawn as a solid box in perspective:
 * the cover, the spine on the right (Japanese magazines bind on the right), the page edges on
 * the left and the back cover behind, each lit from the upper left, with a sheen that slides
 * across the glossy cover as it turns. Drag sideways to turn it; tap to flip it over.
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

    val angle = remember(info.id) { Animatable(REST) }
    val scope = rememberCoroutineScope()
    val a = pinnedAngle ?: angle.value
    val showingBack = cos(Math.toRadians(a.toDouble())) < 0

    // Settle on the nearer face, carrying the fling's speed so a flick spins it right round.
    fun settle(velocity: Float) {
        scope.launch {
            val target = ((angle.value - REST + velocity * 0.18f) / 180f).roundToInt() * 180f + REST
            angle.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 140f), initialVelocity = velocity)
            angle.snapTo(wrap(target))
        }
    }
    fun flip() {
        scope.launch {
            val target = ((angle.value - REST) / 180f).roundToInt() * 180f + REST + 180f
            angle.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 140f))
            angle.snapTo(wrap(target))
        }
    }

    // Once ever, a small turn shows that the book moves.
    if (pinnedAngle == null) {
        LaunchedEffect(Unit) {
            val prefs = context.getSharedPreferences("book", Context.MODE_PRIVATE)
            if (prefs.getBoolean("hinted", false)) return@LaunchedEffect
            prefs.edit().putBoolean("hinted", true).apply()
            delay(700)
            angle.animateTo(REST - 40f, spring(dampingRatio = 0.75f, stiffness = 120f))
            angle.animateTo(REST, spring(dampingRatio = 0.7f, stiffness = 110f))
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
        .pointerInput(info.id) { detectTapGestures(onTap = { flip() }) }

    val front = rememberGraphicsLayer()
    val rear = rememberGraphicsLayer()
    val spine = rememberGraphicsLayer()
    val pigment = Pigment.forSeries(info.magazineName)
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
        val count = if (pages > 0) pages else 220
        // About 6% of the width for a 220-page weekly, up to a fat 13% for a big monthly.
        val thickness = w * (count / 220f * 0.06f).coerceIn(0.035f, 0.13f)

        // The faces are laid out here but only drawn into layers, which the book below projects
        // onto its sides. They stay live, so a cover that finishes loading appears on the book.
        Box(Modifier.fillMaxSize().recordInto(front)) {
            IssueCover(info, Modifier.fillMaxSize(), shared = CoverSharing.None, shape = RectangleShape)
        }
        Box(Modifier.fillMaxSize().recordInto(rear)) {
            val bmp = back
            if (bmp != null) {
                Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                StockBack(info, publisher, Modifier.fillMaxSize())
            }
        }
        Box(Modifier.width(thickness).fillMaxHeight().recordInto(spine)) { Spine(info, Modifier.fillMaxSize()) }

        // Snapshot the faces into bitmaps to map onto the box (layers themselves don't take a
        // perspective transform everywhere). Again a little later, once a cover image arrives.
        var faces by remember(info.id) { mutableStateOf<Faces?>(null) }
        LaunchedEffect(info.id, back, thickness) {
            for (wait in longArrayOf(32, 300, 900, 2200, 5000)) {
                delay(wait)
                runCatching { Faces(front.toImageBitmap(), rear.toImageBitmap(), spine.toImageBitmap()) }
                    .getOrNull()?.let { faces = it }
            }
        }
        Spacer(Modifier.fillMaxSize().drawBehind { drawBook(a, thickness.toPx(), faces, pigment) })
    }
}

private class Faces(val front: ImageBitmap, val back: ImageBitmap, val spine: ImageBitmap)

private fun wrap(degrees: Float) = ((degrees - REST) % 360f + 360f) % 360f + REST

/** Lays the content out and records it into [layer] without drawing it on screen. */
private fun Modifier.recordInto(layer: GraphicsLayer) = drawWithContent {
    layer.record(this, layoutDirection, size.toIntSize()) { this@drawWithContent.drawContent() }
}

/** Light from the upper left, a little in front: x and z of a unit vector (y doesn't matter here). */
private val LIGHT = run { val x = -0.42f; val z = 1f; val n = sqrt(x * x + z * z); floatArrayOf(x / n, z / n) }

/**
 * Draws the book turned [deg] degrees about its vertical axis (positive brings the spine
 * toward you). Each visible side is a texture mapped onto its corners in perspective.
 */
private fun DrawScope.drawBook(deg: Float, t: Float, faces: Faces?, pigment: Color) {
    val w = size.width
    val h = size.height
    val hw = w / 2f
    val hh = h / 2f
    val ht = t / 2f
    val rad = Math.toRadians(deg.toDouble())
    val ca = cos(rad).toFloat()
    val sa = sin(rad).toFloat()
    val eye = h * 3.2f
    fun project(x: Float, y: Float, z: Float): Offset {
        val xr = x * ca - z * sa
        val zr = x * sa + z * ca
        val s = eye / (eye - zr)
        return Offset(hw + xr * s, hh + y * s)
    }

    // Corners of each side, top-left first and clockwise as seen from outside.
    val frontQuad = listOf(project(-hw, -hh, ht), project(hw, -hh, ht), project(hw, hh, ht), project(-hw, hh, ht))
    val backQuad = listOf(project(hw, -hh, -ht), project(-hw, -hh, -ht), project(-hw, hh, -ht), project(hw, hh, -ht))
    val spineQuad = listOf(project(hw, -hh, ht), project(hw, -hh, -ht), project(hw, hh, -ht), project(hw, hh, ht))
    val edgeQuad = listOf(project(-hw, -hh, -ht), project(-hw, -hh, ht), project(-hw, hh, ht), project(-hw, hh, -ht))

    // A soft shadow on the surface under the book.
    val all = frontQuad + backQuad
    val floor = all.maxOf { it.y } + 4.dp.toPx()
    val reach = (all.maxOf { it.x } - all.minOf { it.x }) * 0.62f
    scale(1f, 0.11f, pivot = Offset(hw, floor)) {
        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent), center = Offset(hw, floor), radius = reach),
            radius = reach, center = Offset(hw, floor),
        )
    }

    // Light on a side whose outward normal starts as (nx, nz): darker as it turns away.
    fun shade(nx: Float, nz: Float): Float {
        val rx = nx * ca - nz * sa
        val rz = nx * sa + nz * ca
        val lit = (rx * LIGHT[0] + rz * LIGHT[1]).coerceAtLeast(0f)
        return (1f - lit) * 0.5f
    }

    val n = ((deg % 360f) + 540f) % 360f - 180f
    if (frontQuad.facing()) mapOnto(frontQuad, w, h) {
        texture(faces?.front, w, h, pigment)
        hinge(w, h, spineOnRight = true)
        // The coated stock catches the light as the cover turns past it.
        val sheen = 0.2f * exp(-((n - 8f) / 34f).let { it * it })
        val u = w * (0.5f - n / 80f)
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = sheen), Color.Transparent),
                start = Offset(u - w * 0.45f, 0f), end = Offset(u + w * 0.45f, h),
            ),
        )
        drawRect(Color.Black.copy(alpha = shade(0f, 1f)))
    }
    if (backQuad.facing()) mapOnto(backQuad, w, h) {
        texture(faces?.back, w, h, pigment.shade(0.2f))
        hinge(w, h, spineOnRight = false)
        drawRect(Color.Black.copy(alpha = shade(0f, -1f)))
    }
    if (spineQuad.facing()) mapOnto(spineQuad, t, h) {
        texture(faces?.spine, t, h, pigment.shade(0.1f))
        // Rounded by the binding: lighter down the middle, darker where it meets the covers.
        drawRect(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.22f), 0.35f to Color.Transparent,
                0.65f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f),
            ),
        )
        drawRect(Color.Black.copy(alpha = shade(1f, 0f)))
    }
    if (edgeQuad.facing()) mapOnto(edgeQuad, t, h) {
        pageEdges(t, h, pigment)
        drawRect(Color.Black.copy(alpha = shade(-1f, 0f)))
    }
}

/** [image] stretched over a [tw] x [th] side, or plain [fallback] until it's ready. */
private fun DrawScope.texture(image: ImageBitmap?, tw: Float, th: Float, fallback: Color) {
    if (image == null || image.width == 0) {
        drawRect(fallback, size = Size(tw, th))
        return
    }
    drawImage(
        image,
        srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset.Zero, dstSize = IntSize(tw.roundToInt(), th.roundToInt()),
        filterQuality = FilterQuality.High,
    )
}

/** A side faces the viewer when its corners still run clockwise on screen. */
private fun List<Offset>.facing(): Boolean {
    var area = 0f
    for (i in indices) {
        val p = this[i]
        val q = this[(i + 1) % size]
        area += p.x * q.y - q.x * p.y
    }
    return area > 0f
}

/** Draws [content], laid out in a [tw] x [th] box, stretched in perspective onto [quad]. */
private fun DrawScope.mapOnto(quad: List<Offset>, tw: Float, th: Float, content: DrawScope.() -> Unit) {
    val m = android.graphics.Matrix()
    val ok = m.setPolyToPoly(
        floatArrayOf(0f, 0f, tw, 0f, tw, th, 0f, th), 0,
        floatArrayOf(quad[0].x, quad[0].y, quad[1].x, quad[1].y, quad[2].x, quad[2].y, quad[3].x, quad[3].y), 0,
        4,
    )
    if (!ok) return
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.nativeCanvas.concat(m)
        canvas.clipRect(0f, 0f, tw, th)
        content()
        canvas.restore()
    }
}

/** The crease a perfect binding presses into the cover, a few millimetres in from the spine. */
private fun DrawScope.hinge(w: Float, h: Float, spineOnRight: Boolean) {
    val at = if (spineOnRight) w * 0.955f else w * 0.045f
    val half = w * 0.018f
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Transparent,
            0.45f to Color.Black.copy(alpha = 0.16f),
            0.6f to Color.White.copy(alpha = 0.12f),
            1f to Color.Transparent,
            startX = at - half, endX = at + half,
        ),
        topLeft = Offset(at - half, 0f), size = Size(half * 2f, h),
    )
}

/** The fore edge: hundreds of page edges, with a sliver of each cover at either side. */
private fun DrawScope.pageEdges(t: Float, h: Float, pigment: Color) {
    drawRect(Color(0xFFEDE6D6), size = Size(t, h))
    val r = Random(7)
    val step = 1.3f
    var x = 0f
    while (x < t) {
        drawRect(Color.Black.copy(alpha = 0.03f + r.nextFloat() * 0.07f), topLeft = Offset(x, 0f), size = Size(0.6f, h))
        x += step + r.nextFloat() * 0.8f
    }
    val board = (t * 0.06f).coerceIn(1f, 3f)
    drawRect(pigment.shade(0.25f), size = Size(board, h))
    drawRect(pigment.shade(0.25f), topLeft = Offset(t - board, 0f), size = Size(board, h))
}

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
