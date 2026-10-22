package app.gloss.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import app.gloss.ui.theme.Gloss
import dev.chrisbanes.haze.HazeState

/**
 * What a piece of liquid glass looks through: the screen behind the floating controls, recorded
 * once by [glassSource] and drawn again, bent, inside every [liquidGlass] over it.
 */
@Stable
class GlassBackdrop {
    internal var layer: GraphicsLayer? by mutableStateOf(null)
    internal var origin: Offset by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop() }

/** Records what this draws so liquid glass laid over it can refract it. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithCache {
        val layer = obtainGraphicsLayer()
        backdrop.layer = layer
        onDrawWithContent {
            layer.record { this@onDrawWithContent.drawContent() }
            drawLayer(layer)
        }
    }

/**
 * Liquid glass, iOS 26 style: the picture behind is lightly blurred and made more vivid, and
 * near the rim it bends as if through a thick, rounded pane, with the colours splitting a little
 * where it bends most. A bright edge catches the light from the top left.
 *
 * Phones that can't run the shader, and the JVM screenshot tests, get the frosted [glass] over
 * [fallback] instead.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: GlassBackdrop,
    fallback: HazeState,
    shape: Shape,
    tint: Color = Gloss.colors.surface,
): Modifier {
    val shader = remember { if (LiquidLens.supported) runCatching { RuntimeShader(LiquidLens.SOURCE) }.getOrNull() else null }
        ?: return glass(fallback, shape, tint)
    val c = Gloss.colors
    var position by remember { mutableStateOf(Offset.Zero) }
    val fill = tint.copy(alpha = if (c.isDark) 0.42f else 0.5f)
    val rimHi = Color.White.copy(alpha = if (c.isDark) 0.38f else 0.9f)
    val rimLo = Color.White.copy(alpha = if (c.isDark) 0.08f else 0.3f)

    return this
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawWithCache {
            val radius = (shape as? CornerBasedShape)?.topStart?.toPx(size, this)?.coerceAtMost(size.minDimension / 2f)
                ?: (size.minDimension / 2f)
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            val pane = obtainGraphicsLayer()
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("depth", minOf(16.dp.toPx(), size.minDimension / 2f))
            shader.setFloatUniform("bend", 14.dp.toPx())
            shader.setFloatUniform("spread", 0.12f)
            pane.renderEffect = LiquidLens.effect(shader, 3.dp.toPx()).asComposeRenderEffect()
            val rim = Brush.linearGradient(
                0f to rimHi, 0.35f to rimLo, 0.65f to rimLo, 1f to rimHi.copy(alpha = rimHi.alpha * 0.45f),
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
            val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.isDark) 0.06f else 0.16f), Color.Transparent), endY = size.height * 0.6f)
            val edge = 1.dp.toPx()

            onDrawBehind {
                val source = backdrop.layer
                if (source != null && size.minDimension > 0f) {
                    val shift = backdrop.origin - position
                    pane.record { translate(shift.x, shift.y) { drawLayer(source) } }
                    clipPath(clip) { drawLayer(pane) }
                }
                drawPath(clip, fill)
                drawPath(clip, sheen)
                clipPath(clip) { drawPath(clip, rim, style = Stroke(edge * 2)) }
            }
        }
}

internal object LiquidLens {
    /** RuntimeShader needs a real GPU pipeline; Robolectric's software canvas has none. */
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Build.FINGERPRINT != "robolectric"

    /** Blur first, then bend, then lift the colour, the order glass does it in. */
    fun effect(shader: RuntimeShader, blur: Float): RenderEffect {
        val soft = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
        val bent = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"), soft)
        val vivid = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) })
        return RenderEffect.createChainEffect(RenderEffect.createColorFilterEffect(vivid), bent)
    }

    /**
     * A rounded pane with a circular bevel: flat in the middle, steeper towards the rim, where
     * the picture is pulled in from further inside. Red and blue bend by slightly different
     * amounts, which is the faint rainbow on real glass edges.
     */
    const val SOURCE = """
        uniform shader content;
        uniform float2 size;
        uniform float radius;
        uniform float depth;
        uniform float bend;
        uniform float spread;

        float box(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
        }

        half4 main(float2 coord) {
            float2 hs = size * 0.5;
            float2 p = coord - hs;
            float d = box(p, hs, radius);
            float inside = clamp(-d / depth, 0.0, 1.0);
            float2 n = float2(
                box(p + float2(1.0, 0.0), hs, radius) - box(p - float2(1.0, 0.0), hs, radius),
                box(p + float2(0.0, 1.0), hs, radius) - box(p - float2(0.0, 1.0), hs, radius));
            n = n / max(length(n), 0.0001);
            float t = 1.0 - inside;
            float slope = 1.0 - sqrt(max(1.0 - t * t, 0.0));
            float2 shift = -n * bend * slope;
            half4 mid = content.eval(coord + shift);
            half4 red = content.eval(coord + shift * (1.0 + spread));
            half4 blue = content.eval(coord + shift * (1.0 - spread));
            return half4(red.r, mid.g, blue.b, mid.a);
        }
    """
}
