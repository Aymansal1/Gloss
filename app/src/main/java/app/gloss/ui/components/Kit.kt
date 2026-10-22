package app.gloss.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GoldLeaf
import app.gloss.ui.theme.Motion

/**
 * iOS-style press feedback: no ripple, the element sinks a little under the finger and
 * springs back. [haptic] adds a light tick on tap (long presses get the system's own).
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    scaleTo: Float = 0.96f,
    haptic: Boolean = false,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val down = pressed && enabled
    val scale by animateFloatAsState(if (down) scaleTo else 1f, if (down) Motion.pressIn() else Motion.press(), label = "press")
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClick = onLongClick,
            onClick = {
                if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
}

/** A table-row press: the row greys out while held, like a UITableView cell. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rowPress(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    role: Role? = Role.Button,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill by animateColorAsState(
        if (pressed) Gloss.colors.fill else Color.Transparent, Motion.settle(), label = "row",
    )
    this
        .background(fill)
        .combinedClickable(
            interactionSource = source, indication = null, role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

enum class PillStyle { Filled, Tinted, Plain }

/** A capsule button: the primary action shape everywhere in Gloss. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Gloss.colors.lapis,
    height: Dp = 50.dp,
) {
    val c = Gloss.colors
    val (bg, fg) = when (style) {
        PillStyle.Filled -> color to (if (color == c.lapis) c.onLapis else Color.White)
        PillStyle.Tinted -> (if (color == c.lapis) c.lapisTint else color.copy(alpha = 0.14f)) to color
        PillStyle.Plain -> Color.Transparent to color
    }
    Row(
        modifier
            .defaultMinSize(minWidth = 88.dp)
            .height(height)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick, enabled = enabled, haptic = style == PillStyle.Filled)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Gloss.type.headline, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A round icon button, 44dp to the eye and 48dp to the finger. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Gloss.colors.lapis,
    background: Color = Color.Transparent,
    size: Dp = 44.dp,
) {
    Box(
        modifier
            .size(maxOf(size, 48.dp))
            .pressable(onClick, scaleTo = 0.88f, onClickLabel = contentDescription)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

/** A segmented control with a thumb that springs between segments. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Gloss.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.fill)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(segment * selected, Motion.move(), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .shadow(3.dp, RoundedCornerShape(8.dp), clip = false, ambientColor = Color.Black.copy(alpha = 0.12f))
                .clip(RoundedCornerShape(8.dp))
                // Opaque, so the shadow underneath doesn't show through the thumb.
                .background(if (c.isDark) c.tertiary.copy(alpha = 0.55f).compositeOver(c.fill) else c.surface),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                val haptics = LocalHapticFeedback.current
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickableNoIndication(role = Role.Tab) {
                            if (i != selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(i)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = Gloss.type.footnote.copy(fontWeight = if (i == selected) androidx.compose.ui.text.font.FontWeight(600) else androidx.compose.ui.text.font.FontWeight(500)),
                        color = c.text,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoIndication(role: Role, onClick: () -> Unit): Modifier = composed {
    combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = role, onClick = onClick)
}

/** An iOS-style switch: lapis track, white thumb that springs across. */
@Composable
fun GlossSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Gloss.colors
    val track by animateColorAsState(if (checked) c.lapis else c.fill, Motion.settle(), label = "track")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, Motion.move(), label = "thumb")
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(track)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** An inset grouped section, the iOS Settings way: optional header, rounded card, optional footer. */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Gloss.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                header, style = Gloss.type.footnote, color = c.secondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
            )
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface), content = content)
        if (footer != null) {
            Text(
                footer, style = Gloss.type.footnote, color = c.secondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

/**
 * One row of a [Section]. The separator starts where the text starts, and rows that do
 * something get a chevron (or [trailing]) and a press highlight.
 */
@Composable
fun Cell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** A last line that always shows, however long [subtitle] runs. */
    detail: String? = null,
    titleColor: Color = Gloss.colors.text,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null && trailing == null,
    divider: Boolean = true,
    titleMaxLines: Int = 1,
) {
    val c = Gloss.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.rowPress(onClick, onLongClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.padding(start = 16.dp), contentAlignment = Alignment.Center) { leading() }
        }
        Box(Modifier.weight(1f).padding(start = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(end = 14.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = Gloss.type.body, color = titleColor, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(subtitle, style = Gloss.type.footnote, color = c.secondary, maxLines = if (detail != null) 2 else 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (detail != null) {
                        Text(detail, style = Gloss.type.footnote, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(10.dp))
                    trailing()
                }
                if (chevron) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                        tint = c.tertiary, modifier = Modifier.padding(start = 6.dp).size(22.dp),
                    )
                }
            }
            if (divider) Hairline(Modifier.align(Alignment.BottomStart))
        }
    }
}

/** A 0.5dp separator. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Gloss.colors.separator) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(color))
}

/** The rounded, filled search field from the iOS search bar. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val c = Gloss.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Gloss.type.body.copy(color = c.text),
        cursorBrush = SolidColor(c.lapis),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(c.fill).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = c.secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = Gloss.type.body, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier.size(32.dp).pressable({ onValueChange("") }, scaleTo = 0.85f, onClickLabel = "Clear"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Cancel, contentDescription = "Clear", tint = c.tertiary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

/** An illustration or mark with a short title and explanation, for empty and error states. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    art: Painter? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Gloss.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            art != null -> Image(art, contentDescription = null, modifier = Modifier.size(132.dp))
            icon != null -> Box(
                Modifier.size(72.dp).clip(CircleShape).background(c.lapisTint),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = c.lapis, modifier = Modifier.size(34.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = Gloss.type.title3, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = Gloss.type.subhead, color = c.secondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        content()
    }
}

/**
 * The signature mark: a gold-leaf bar drawn under content, swept in from the leading edge
 * as [progress] goes from 0 to 1. Used under glossed passages and the wordmark.
 */
fun Modifier.goldLeafUnderline(progress: Float, thickness: Dp = 4.dp, inset: Dp = 0.dp): Modifier = drawBehind {
    if (progress <= 0f) return@drawBehind
    val h = thickness.toPx()
    val y = size.height - h / 2
    val start = inset.toPx()
    val end = start + (size.width - 2 * start) * progress.coerceIn(0f, 1f)
    drawLine(
        brush = Brush.horizontalGradient(GoldLeaf.stops, startX = start, endX = size.width - start),
        start = Offset(start, y), end = Offset(end, y),
        strokeWidth = h, cap = StrokeCap.Round,
    )
}

/** The wordmark, set live in the scribe's italic with its gold gloss under "oss". */
@Composable
fun Wordmark(modifier: Modifier = Modifier, style: TextStyle = Gloss.type.glossDisplay, color: Color = Gloss.colors.text) {
    Row(modifier.semantics { contentDescription = "Gloss" }, verticalAlignment = Alignment.Bottom) {
        Text("gl", style = style, color = color)
        Text("oss", style = style, color = color, modifier = Modifier.goldLeafUnderline(1f, thickness = 4.dp, inset = 1.dp))
    }
}
