package app.gloss.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.gloss.data.RosettaFormat
import app.gloss.ui.components.MarkdownBlock
import app.gloss.ui.components.blockPlainText
import app.gloss.ui.components.pressable
import app.gloss.ui.container
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GoldLeaf
import app.gloss.ui.theme.InterText
import app.gloss.ui.theme.Motion
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** The translation for the box that's selected on a page: what the gloss callout shows. */
class GlossNote(val translation: List<RosettaFormat.Block>, val original: List<RosettaFormat.Block>)

/**
 * One scanned page: fits its space, pinch/double-tap zoom, pan when zoomed, and overlays for
 * text regions. The selected region gets the signature gloss: gold leaf swept under it and its
 * English unfurled in a callout beside it, which follows the page as you zoom and pan.
 * Single-finger drags at 1x are left unconsumed so the pager can swipe pages.
 */
@Composable
fun ScanPage(
    pdf: File,
    pageIndex: Int,
    aspect: Float,
    model: PageModel?,
    selectedRegion: Int?,
    hitRegion: Int?,
    showZones: Boolean,
    mangaMode: Boolean,
    gloss: GlossNote?,
    textScale: Float,
    calloutBottom: Dp,
    onTapRegion: (Int) -> Unit,
    onTapEmpty: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onOpenGloss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = container()
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val a = if (aspect > 0f) aspect else 0.72f
        // Fit inside the space we're given.
        val contentW = if (cw / ch > a) ch * a else cw
        val contentH = contentW / a

        var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
        var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }

        fun clamp(o: Offset, s: Float): Offset {
            val maxX = max(0f, (contentW * s - cw) / 2f)
            val maxY = max(0f, (contentH * s - ch) / 2f)
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        fun setZoom(newScale: Float, newOffset: Offset) {
            val s = newScale.coerceIn(1f, MAX_ZOOM)
            val wasZoomed = scale > 1.01f
            scale = s
            offset = if (s <= 1.01f) Offset.Zero else clamp(newOffset, s)
            val zoomed = scale > 1.01f
            if (zoomed != wasZoomed) onZoomChanged(zoomed)
        }

        // Render sharper when zoomed in.
        val targetWidth = (contentW * (if (scale > 1.6f) 2f else 1f)).roundToInt().coerceAtLeast(240)
        var bitmap by remember(pdf, pageIndex) { mutableStateOf<Bitmap?>(c.pdf.cached(pdf, pageIndex, contentW.roundToInt())) }
        var failed by remember(pdf, pageIndex) { mutableStateOf(false) }
        LaunchedEffect(pdf, pageIndex, targetWidth) {
            val b = c.pdf.render(pdf, pageIndex, targetWidth)
            if (b != null) bitmap = b else if (bitmap == null) failed = true
        }

        val center = Offset(cw / 2f, ch / 2f)

        // The gold sweep restarts whenever a different box is selected.
        // The card with the English reveals fast; the gold leaf takes a little longer to lay down.
        val sweep = remember { Animatable(0f) }
        val reveal = remember { Animatable(0f) }
        LaunchedEffect(selectedRegion) {
            sweep.snapTo(0f)
            reveal.snapTo(0f)
            if (selectedRegion != null) {
                launch { reveal.animateTo(1f, Motion.reveal()) }
                sweep.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 160f))
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(pageIndex, contentW, contentH) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // A one-finger touch while zoomed only starts panning past the touch slop, so the
                        // tiny drift of a tap isn't consumed and taps can still select text.
                        var drift = Offset.Zero
                        var panning = false
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed == 1 && scale > 1.01f && !panning) {
                                drift += event.calculatePan()
                                panning = drift.getDistance() > viewConfiguration.touchSlop
                            }
                            if (pressed >= 2 || (scale > 1.01f && panning)) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                val z = newScale / scale
                                val anchor = if (centroid == Offset.Unspecified) center else centroid
                                val o = anchor - center - (anchor - center - offset) * z + pan
                                setZoom(newScale, o)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(pageIndex, contentW, contentH, model) {
                    detectTapGestures(
                        onDoubleTap = { p ->
                            if (scale > 1.01f) setZoom(1f, Offset.Zero)
                            else setZoom(DOUBLE_TAP_ZOOM, -(p - center) * (DOUBLE_TAP_ZOOM - 1f))
                        },
                        onTap = { p ->
                            // Viewport point -> page-relative (0..1) point.
                            val lx = (p.x - center.x - offset.x) / scale + contentW / 2f
                            val ly = (p.y - center.y - offset.y) / scale + contentH / 2f
                            val hit = model?.regionAt(lx / contentW, ly / contentH)
                            if (hit != null) onTapRegion(hit) else onTapEmpty()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val wDp = with(density) { contentW.toDp() }
            val hDp = with(density) { contentH.toDp() }
            Box(
                Modifier
                    .size(wDp, hDp)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    }
                    .background(Color.White),
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), contentDescription = "Page ${pageIndex + 1}",
                        contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF11142A)), contentAlignment = Alignment.Center) {
                        if (failed) {
                            Text(
                                "This page couldn't be drawn.\nIts text is still in the sheet below.",
                                style = Gloss.type.subhead, color = Color(0xFF9DA2C6), textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp),
                            )
                        } else {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = Color(0xFF5068FF))
                        }
                    }
                }
                if (model != null) {
                    RegionOverlay(model, selectedRegion, hitRegion, showZones && !mangaMode, scale, sweep.value)
                    if (mangaMode) MangaOverlay(model, contentW, contentH, selectedRegion)
                }
            }
        }

        // The callout lives outside the zoomed layer so it stays crisp and readable at any zoom.
        val region = selectedRegion?.let { model?.regions?.getOrNull(it) }
        if (!mangaMode && region != null && gloss != null && region.boxes.isNotEmpty()) {
            val l = region.boxes.minOf { it.left }
            val t = region.boxes.minOf { it.top }
            val r = region.boxes.maxOf { it.right }
            val b = region.boxes.maxOf { it.bottom }
            fun sx(x: Float) = center.x + offset.x + (x * contentW - contentW / 2f) * scale
            fun sy(y: Float) = center.y + offset.y + (y * contentH - contentH / 2f) * scale
            val anchor = Rect(sx(l), sy(t), sx(r), sy(b))
            GlossCallout(
                note = gloss,
                anchor = anchor,
                bottomReserve = with(density) { calloutBottom.toPx() },
                textScale = textScale,
                appear = reveal.value,
                onOpen = onOpenGloss,
            )
        }
    }
}

@Composable
private fun RegionOverlay(model: PageModel, selected: Int?, hit: Int?, showZones: Boolean, scale: Float, sweep: Float) {
    val lapis = Color(0xFF5068FF)
    val wash = Color(0x55F2C14E)
    Canvas(Modifier.fillMaxSize()) {
        val thin = 1.dp.toPx() / scale
        val leaf = 3.dp.toPx() / scale
        val radius = CornerRadius(3.dp.toPx() / scale)
        model.regions.forEachIndexed { i, r ->
            r.boxes.forEach { b ->
                val tl = Offset(b.left * size.width, b.top * size.height)
                val sz = Size(b.width * size.width, b.height * size.height)
                when {
                    i == selected -> {
                        drawRoundRect(wash.copy(alpha = wash.alpha * sweep), tl, sz, radius)
                        // Gold leaf laid along the foot of the box, left to right.
                        val y = tl.y + sz.height + leaf
                        drawLine(
                            Brush.horizontalGradient(GoldLeaf.stops, startX = tl.x, endX = tl.x + sz.width),
                            Offset(tl.x, y), Offset(tl.x + sz.width * sweep, y),
                            strokeWidth = leaf, cap = StrokeCap.Round,
                        )
                    }
                    i == hit -> drawRoundRect(wash, tl, sz, radius)
                    showZones -> drawRoundRect(lapis.copy(alpha = 0.7f), tl, sz, radius, style = Stroke(thin))
                }
            }
        }
    }
}

/**
 * The gloss: a dark card with the passage's English, placed under the box when there's room
 * (above it otherwise), growing out of the box as the gold sweeps in. Tap it for the full page.
 */
@Composable
private fun GlossCallout(
    note: GlossNote,
    anchor: Rect,
    bottomReserve: Float,
    textScale: Float,
    appear: Float,
    onOpen: () -> Unit,
) {
    val density = LocalDensity.current
    val g = Gloss.colors
    val margin = with(density) { 14.dp.toPx() }
    val gap = with(density) { 12.dp.toPx() }
    val topReserve = with(density) { 104.dp.toPx() }
    var above by remember { mutableStateOf(false) }
    Layout(
        content = {
            val base = Gloss.type.callout
            val style = base.copy(color = Color.White, fontSize = base.fontSize * textScale, lineHeight = base.lineHeight * textScale)
            Column(
                Modifier
                    .graphicsLayer {
                        val k = (0.35f + 0.65f * appear).coerceIn(0f, 1f)
                        alpha = k
                        scaleX = 0.94f + 0.06f * k
                        scaleY = 0.94f + 0.06f * k
                        transformOrigin = TransformOrigin(0.5f, if (above) 1f else 0f)
                    }
                    .shadow(20.dp, RoundedCornerShape(18.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF2141832))
                    .border(0.8.dp, GoldLeaf.Mid.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .pressable(onOpen, scaleTo = 0.98f, onClickLabel = "Read the whole page")
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                val scroll = rememberScrollState()
                Column(Modifier.weight(1f, fill = false).fadeWhenMore(scroll).verticalScroll(scroll)) {
                    if (note.translation.isEmpty()) {
                        Text("No translation is linked to this box.", style = Gloss.type.subhead, color = g.secondary)
                    }
                    note.translation.forEach { MarkdownBlock(it, style) }
                    if (note.original.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        val o = Gloss.type.footnote
                        note.original.take(3).forEach {
                            MarkdownBlock(it, o.copy(color = Color.White.copy(alpha = 0.6f), fontSize = o.fontSize * textScale))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(18.dp).height(2.dp).background(Brush.horizontalGradient(GoldLeaf.stops), RoundedCornerShape(1.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text("Whole page", style = Gloss.type.footnote.copy(fontWeight = FontWeight(600)), color = GoldLeaf.Hi)
                }
            }
        },
    ) { measurables, constraints ->
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val width = min(vw - 2 * margin, max(anchor.width + 2 * margin, with(density) { 300.dp.toPx() })).coerceAtLeast(1f)
        val spaceBelow = vh - bottomReserve - anchor.bottom - gap - margin
        val spaceAbove = anchor.top - gap - topReserve
        val goBelow = spaceBelow >= spaceAbove || spaceBelow > with(density) { 180.dp.toPx() }
        above = !goBelow
        val maxH = max(with(density) { 96.dp.toPx() }, if (goBelow) spaceBelow else spaceAbove)
        val p = measurables.first().measure(
            Constraints(minWidth = width.roundToInt(), maxWidth = width.roundToInt(), maxHeight = maxH.roundToInt().coerceAtLeast(1)),
        )
        val x = (anchor.center.x - p.width / 2f).coerceIn(margin, max(margin, vw - margin - p.width))
        val y = if (goBelow) anchor.bottom + gap else anchor.top - gap - p.height
        // Keep it on screen even if the box itself has been panned off.
        val cy = y.coerceIn(topReserve, max(topReserve, vh - bottomReserve - p.height))
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(x.roundToInt(), cy.roundToInt()) }
    }
}

/** Paints over each linked region and sets its translation inside, auto-sized to fit. */
@Composable
private fun MangaOverlay(model: PageModel, w: Float, h: Float, selected: Int?) {
    val density = LocalDensity.current
    model.regions.forEachIndexed { r, region ->
        val text = model.translationForRegion(r).joinToString("\n") { blockPlainText(it) }
        if (text.isBlank()) return@forEachIndexed
        // Use the region's largest box; split regions keep their first-listed text area.
        val b = region.boxes.maxByOrNull { it.area } ?: return@forEachIndexed
        val bw = with(density) { (b.width * w).toDp() }
        val bh = with(density) { (b.height * h).toDp() }
        Box(
            Modifier
                .offset { IntOffset((b.left * w).roundToInt(), (b.top * h).roundToInt()) }
                .size(bw, bh)
                .clip(RoundedCornerShape(2.dp))
                .background(if (r == selected) Color(0xFFFFF4D6) else Color(0xFFFCFBF8))
                .padding(horizontal = 2.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = text,
                style = TextStyle(
                    fontFamily = InterText,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF14161F),
                    textAlign = TextAlign.Center,
                    lineHeight = 1.15.em,
                ),
                autoSize = TextAutoSize.StepBased(minFontSize = 3.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
            )
        }
    }
}

/** A tiny page for the scrubber. */
@Composable
fun PageThumb(pdf: File, pageIndex: Int, modifier: Modifier = Modifier) {
    val c = container()
    var bmp by remember(pdf, pageIndex) { mutableStateOf(c.pdf.cached(pdf, pageIndex, 160)) }
    LaunchedEffect(pdf, pageIndex) { if (bmp == null) bmp = c.pdf.render(pdf, pageIndex, 160) }
    Box(modifier.background(Color(0xFF20254A))) {
        bmp?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Fades the bottom edge while there's more to scroll, so cut-off text reads as "keeps going". */
private fun Modifier.fadeWhenMore(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (scroll.canScrollForward) {
                val fade = 28.dp.toPx().coerceAtMost(size.height / 2)
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = size.height - fade, endY = size.height),
                    blendMode = BlendMode.DstOut,
                )
            }
        }
