package app.gloss.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gloss.ui.theme.Gloss
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import dev.chrisbanes.haze.HazeState

/** What liquid glass looks through: the screen behind the floating controls. */
typealias GlassBackdrop = LayerBackdrop

@Composable
fun rememberGlassBackdrop(): GlassBackdrop = rememberLayerBackdrop()

/**
 * Liquid glass needs the GPU's shader effects; Robolectric's software canvas has none, so the
 * JVM screenshot tests get the frosted [glass] instead.
 */
private val liquidSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Build.FINGERPRINT != "robolectric"

/** Records what this draws so liquid glass laid over it can refract it. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier =
    if (liquidSupported) layerBackdrop(backdrop) else this

/**
 * Liquid glass, iOS 26 style, drawn by Kyant's Backdrop library: the picture behind is blurred,
 * made more vivid and bent by a lens towards the rim, under a tint that keeps labels readable
 * and a highlight along the edge. [bend] is how wide the bent band is; small buttons use less.
 * [lifted] adds a soft shadow under controls that float over the content.
 *
 * Where the effect can't run, the frosted [glass] over [fallback] stands in.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: GlassBackdrop,
    fallback: HazeState,
    shape: Shape,
    tint: Color = Gloss.colors.surface,
    bend: Dp = 24.dp,
    frost: Dp = 8.dp,
    lifted: Boolean = false,
): Modifier {
    if (!liquidSupported) {
        val base = if (lifted) shadow(18.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f)) else this
        return base.glass(fallback, shape, tint)
    }
    val surface = tint.copy(alpha = if (Gloss.colors.isDark) 0.5f else 0.55f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(frost.toPx())
            lens(bend.toPx(), bend.toPx())
        },
        // Backdrop's own shadow stays outside the shape, so it can't darken the glass.
        shadow = if (lifted) ({ Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.2f)) }) else null,
        onDrawSurface = { drawRect(surface) },
    )
}
