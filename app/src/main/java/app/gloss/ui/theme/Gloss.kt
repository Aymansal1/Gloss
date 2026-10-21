@file:OptIn(ExperimentalTextApi::class)

package app.gloss.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.gloss.R

/**
 * Gloss is set in illuminated-manuscript pigments on an iOS-style shell: lapis owns the
 * shell and every primary action, gold leaf marks whatever has been glossed (a selected
 * passage, a search hit), and vermilion rubric marks the reader's own marks (bookmarks).
 */
@Immutable
data class GlossColors(
    val isDark: Boolean,
    /** Screen background behind grouped content. */
    val ground: Color,
    /** Cells, cards and rows. */
    val surface: Color,
    /** Sheets, menus and anything floating. */
    val raised: Color,
    /** Search fields, segmented controls, inactive chips. */
    val fill: Color,
    val separator: Color,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val lapis: Color,
    val onLapis: Color,
    /** Lapis laid thinly over a surface, for tinted buttons and selected rows. */
    val lapisTint: Color,
    val gold: Color,
    /** Gold that can carry text on [ground]. */
    val goldInk: Color,
    /** Translucent gold for highlighting text and boxes on a page. */
    val goldWash: Color,
    val vermilion: Color,
    val danger: Color,
)

/** Gold leaf as a three-stop gradient, for the signature underline and the mark. */
object GoldLeaf {
    val Hi = Color(0xFFF6D886)
    val Mid = Color(0xFFE3B341)
    val Lo = Color(0xFFB98320)
    val stops = listOf(Hi, Mid, Lo)
}

val LightGloss = GlossColors(
    isDark = false,
    ground = Color(0xFFF2F3F8),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFFFFFFF),
    fill = Color(0xFFE8EAF2),
    separator = Color(0xFFD5D8E4),
    text = Color(0xFF0B0D1C),
    secondary = Color(0xFF5B5F7E),
    tertiary = Color(0xFF8D91AB),
    lapis = Color(0xFF2338C2),
    onLapis = Color.White,
    lapisTint = Color(0xFFE4E7F9),
    gold = Color(0xFFDDA623),
    goldInk = Color(0xFF8A6300),
    goldWash = Color(0x66F2C94C),
    vermilion = Color(0xFFD5412A),
    danger = Color(0xFFD12F3A),
)

val DarkGloss = GlossColors(
    isDark = true,
    ground = Color(0xFF070A1C),
    surface = Color(0xFF11142A),
    raised = Color(0xFF181C37),
    fill = Color(0xFF1F2446),
    separator = Color(0xFF292E52),
    text = Color(0xFFEEF0FA),
    secondary = Color(0xFF9DA2C6),
    tertiary = Color(0xFF6A6F96),
    lapis = Color(0xFF5068FF),
    onLapis = Color.White,
    lapisTint = Color(0xFF1B2358),
    gold = Color(0xFFF2C14E),
    goldInk = Color(0xFFF2C14E),
    goldWash = Color(0x55F2C14E),
    vermilion = Color(0xFFFF6B4F),
    danger = Color(0xFFFF5C66),
)

/** Manuscript pigments; each magazine series is given one. */
object Pigment {
    val Lapis = Color(0xFF2338C2)
    val Malachite = Color(0xFF1E8F5E)
    val Orpiment = Color(0xFFE1A91A)
    val Murex = Color(0xFF8A2D8F)
    val Azurite = Color(0xFF2F7FD1)
    val Minium = Color(0xFFE0662D)
    val Verdigris = Color(0xFF2A9D8F)
    val Madder = Color(0xFFC23152)
    val all = listOf(Lapis, Malachite, Orpiment, Murex, Azurite, Minium, Verdigris, Madder)

    fun forSeries(name: String): Color = all[(name.lowercase().hashCode() and 0x7fffffff) % all.size]

    /** Text colour that reads on top of [pigment]. */
    fun on(pigment: Color): Color = if (pigment.luminance() > 0.45f) Color(0xFF14110A) else Color.White
}

private fun inter(weight: Int, opsz: Float) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
)

/** Inter at text optical size, for everything 17sp and under. */
val InterText = FontFamily(inter(400, 14f), inter(500, 14f), inter(600, 14f), inter(700, 14f))

/** Inter at display optical size, for large titles. */
val InterDisplay = FontFamily(inter(600, 32f), inter(700, 32f), inter(800, 32f))

/** The scribe's hand: glosses, page numbers and the wordmark. */
val Scribe = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** The type scale, named after iOS's text styles so screens read like the platform they echo. */
@Immutable
data class GlossType(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val caption2: TextStyle,
    /** Italic serif for the wordmark-sized moments. */
    val glossDisplay: TextStyle,
    /** Italic serif for section-level glosses and page numerals. */
    val glossTitle: TextStyle,
    /** Italic serif at reading size, for short annotations. */
    val glossBody: TextStyle,
)

private fun text(size: Float, line: Float, weight: Int = 400, tracking: Float = 0f) = TextStyle(
    fontFamily = InterText, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = FontWeight(weight), letterSpacing = tracking.em,
)

private fun display(size: Float, line: Float, weight: Int, tracking: Float) = TextStyle(
    fontFamily = InterDisplay, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = FontWeight(weight), letterSpacing = tracking.em,
)

private fun scribe(size: Float, line: Float) = TextStyle(
    fontFamily = Scribe, fontStyle = FontStyle.Italic, fontSize = size.sp, lineHeight = line.sp,
)

val GlossTypeScale = GlossType(
    largeTitle = display(34f, 41f, 800, -0.022f),
    title1 = display(28f, 34f, 700, -0.02f),
    title2 = display(22f, 28f, 700, -0.016f),
    title3 = text(20f, 25f, 600, -0.012f),
    headline = text(17f, 22f, 600, -0.01f),
    body = text(17f, 24f, 400, -0.006f),
    callout = text(16f, 22f, 400, -0.004f),
    subhead = text(15f, 20f, 400, -0.002f),
    footnote = text(13f, 18f, 400),
    caption = text(12f, 16f, 500, 0.002f),
    caption2 = text(11f, 13f, 500, 0.006f),
    glossDisplay = scribe(44f, 46f),
    glossTitle = scribe(26f, 30f),
    glossBody = scribe(20f, 25f),
)

/** iOS-like motion: springs everywhere, nothing linear, nothing that bounces twice. */
object Motion {
    /** The finger going down: the element sinks at once, no bounce. */
    fun <T> pressIn(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1400f)

    /** Releases and toggles: quick, with a hint of overshoot. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.7f, stiffness = 600f)

    /** Content the reader is waiting on (the gloss card): in fast, no bounce. */
    fun <T> reveal(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 650f)

    /** Things that move to a new place (selection pills, sheets, cards). */
    fun <T> move(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 420f)

    /** Gentle settles (fades, colour). */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f)

    fun offset(): FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 0.9f, stiffness = 520f)
}

enum class Appearance(val label: String) { System("Automatic"), Light("Light"), Dark("Dark") }

val LocalGlossColors = staticCompositionLocalOf { LightGloss }
val LocalGlossType = staticCompositionLocalOf { GlossTypeScale }

object Gloss {
    val colors: GlossColors
        @Composable @ReadOnlyComposable get() = LocalGlossColors.current
    val type: GlossType
        @Composable @ReadOnlyComposable get() = LocalGlossType.current
}

@Composable
fun isGlossDark(appearance: Appearance): Boolean = when (appearance) {
    Appearance.System -> isSystemInDarkTheme()
    Appearance.Light -> false
    Appearance.Dark -> true
}

@Composable
fun GlossTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkGloss else LightGloss
    val t = GlossTypeScale
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.lapis, onPrimary = c.onLapis, primaryContainer = c.lapisTint, onPrimaryContainer = c.text,
            secondary = c.gold, onSecondary = Color(0xFF14110A), tertiary = c.vermilion,
            background = c.ground, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.fill, onSurfaceVariant = c.secondary,
            surfaceContainerLowest = c.ground, surfaceContainerLow = c.surface, surfaceContainer = c.raised,
            surfaceContainerHigh = c.raised, surfaceContainerHighest = c.fill,
            outline = c.separator, outlineVariant = c.separator, error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.lapis, onPrimary = c.onLapis, primaryContainer = c.lapisTint, onPrimaryContainer = c.text,
            secondary = c.gold, onSecondary = Color(0xFF14110A), tertiary = c.vermilion,
            background = c.ground, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.fill, onSurfaceVariant = c.secondary,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.raised,
            surfaceContainerHigh = c.raised, surfaceContainerHighest = c.fill,
            outline = c.separator, outlineVariant = c.separator, error = c.danger,
        )
    }
    // Material components that remain (dialogs, menus, sliders) speak the same type scale.
    val typography = Typography(
        displaySmall = t.largeTitle, headlineMedium = t.title1, headlineSmall = t.title2,
        titleLarge = t.title3, titleMedium = t.headline, titleSmall = t.subhead.copy(fontWeight = FontWeight(600)),
        bodyLarge = t.body, bodyMedium = t.subhead, bodySmall = t.footnote,
        labelLarge = t.subhead.copy(fontWeight = FontWeight(600)), labelMedium = t.caption, labelSmall = t.caption2,
    )
    CompositionLocalProvider(LocalGlossColors provides c, LocalGlossType provides t) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
