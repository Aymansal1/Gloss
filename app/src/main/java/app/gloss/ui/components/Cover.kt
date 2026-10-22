package app.gloss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.gloss.data.CatalogItem
import app.gloss.data.InstalledIssue
import app.gloss.ui.container
import app.gloss.ui.theme.GoldLeaf
import app.gloss.ui.theme.InterDisplay
import app.gloss.ui.theme.InterText
import app.gloss.ui.theme.Pigment
import app.gloss.ui.theme.Scribe
import coil3.compose.SubcomposeAsyncImage
import java.io.File

/** What a cover needs, from either a catalog entry or an installed issue. */
data class CoverInfo(
    val id: String,
    val magazineName: String,
    val title: String,
    val year: String,
    val coverUrl: String,
) {
    companion object {
        fun of(item: CatalogItem) = CoverInfo(item.id, item.magazineName, item.displayTitle, item.year, item.coverUrl)
        fun of(issue: InstalledIssue) = CoverInfo(
            issue.id, issue.magazineName, issue.title,
            Regex("""(19|20)\d{2}""").find(issue.date)?.value ?: "", issue.coverUrl,
        )
    }
}

val CoverShape = RoundedCornerShape(6.dp)
const val COVER_ASPECT = 0.72f

/** A soft, offset shadow under a cover, as if it were lying on the page. */
fun Modifier.coverShadow(): Modifier = this.shadow(
    elevation = 10.dp, shape = CoverShape, clip = false,
    ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.32f),
)

/**
 * Real art when we have it (catalog cover_url, else the first page of a downloaded scan);
 * otherwise a generated cover in the series' pigment. A thin highlight along the top edge
 * gives every cover the same glossy-stock finish.
 */
@Composable
fun IssueCover(info: CoverInfo, modifier: Modifier = Modifier, shared: CoverSharing = CoverSharing.Source) {
    val c = container()
    val installed = c.library.issues.value[info.id]
    var localCover by remember(info.id, installed?.stamp, installed?.hasScan) { mutableStateOf<File?>(null) }
    LaunchedEffect(info.id, installed?.hasScan) {
        if (info.coverUrl.isBlank() && installed != null && installed.hasScan) {
            localCover = c.pdf.ensureCover(installed.pdfFile, File(installed.dir, "cover.jpg"))
        }
    }
    val model: Any? = when {
        info.coverUrl.isNotBlank() -> info.coverUrl
        localCover != null -> localCover
        else -> null
    }
    Box(
        modifier
            .aspectRatio(COVER_ASPECT)
            .then(
                when {
                    shared == CoverSharing.Destination -> Modifier.sharedCover("cover-${info.id}")
                    shared == CoverSharing.Source && SharedCover.target == info.id -> Modifier.sharedCover("cover-${info.id}")
                    else -> Modifier
                },
            )
            .clip(CoverShape)
            .gloss(),
    ) {
        if (model == null) {
            PigmentCover(info, Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = "${info.magazineName} ${info.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { PigmentCover(info, Modifier.fillMaxSize()) },
                error = { PigmentCover(info, Modifier.fillMaxSize()) },
            )
        }
    }
}

/** A sheen across the top of a cover: coated stock catching the light. */
private fun Modifier.gloss(): Modifier = drawWithContent {
    drawContent()
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.16f),
            0.18f to Color.White.copy(alpha = 0.04f),
            0.5f to Color.Transparent,
        ),
    )
}

/** Generated cover: a field of the series' pigment, its masthead, and the year in the scribe's hand. */
@Composable
fun PigmentCover(info: CoverInfo, modifier: Modifier = Modifier) {
    val pigment = Pigment.forSeries(info.magazineName)
    val on = Pigment.on(pigment)
    BoxWithConstraints(
        modifier.background(
            Brush.linearGradient(listOf(pigment, pigment.copy(alpha = 1f).darken(0.18f))),
        ),
    ) {
        val w = maxWidth
        Column(Modifier.fillMaxSize().padding(w * 0.09f)) {
            Text(
                info.magazineName.uppercase(),
                fontFamily = InterDisplay,
                fontWeight = FontWeight(800),
                color = on,
                fontSize = (w.value * 0.13f).coerceIn(9f, 26f).sp,
                lineHeight = (w.value * 0.14f).coerceIn(10f, 28f).sp,
                letterSpacing = (-0.02).em,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (info.year.isNotBlank()) {
                Text(
                    info.year,
                    fontFamily = Scribe,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = on,
                    fontSize = (w.value * 0.26f).coerceIn(16f, 54f).sp,
                    lineHeight = (w.value * 0.26f).coerceIn(16f, 54f).sp,
                )
            }
            Box(
                Modifier.padding(vertical = w * 0.03f).width(w * 0.34f).height((w.value * 0.025f).coerceIn(1.5f, 4f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(GoldLeaf.stops)),
            )
            Text(
                info.title,
                fontFamily = InterText,
                fontWeight = FontWeight(500),
                color = on.copy(alpha = 0.82f),
                fontSize = (w.value * 0.075f).coerceIn(8f, 14f).sp,
                lineHeight = (w.value * 0.095f).coerceIn(10f, 18f).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

/** Whether a cover takes part in the cover-to-detail transition. */
enum class CoverSharing { None, Source, Destination }

/**
 * The cover that was just tapped. Only that one flies into the issue screen; without this,
 * every cover that appears on two screens at once (a shelf and a series grid) would try to.
 */
object SharedCover {
    var target by mutableStateOf<String?>(null)
}

/** One line of a cover's long-press menu. */
class TileAction(val label: String, val danger: Boolean = false, val run: () -> Unit)

/** Cover with its title underneath, for shelves and grids. A long press opens [actions]. */
@Composable
fun CoverTile(
    info: CoverInfo,
    caption: String,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    actions: List<TileAction> = emptyList(),
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val hold: (() -> Unit)? = if (actions.isEmpty()) null else ({
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        menu = true
    })
    Box(modifier) {
        Column(Modifier.pressable({ SharedCover.target = info.id; onClick() }, scaleTo = 0.95f, onLongClick = hold)) {
            Box {
                IssueCover(info, Modifier.fillMaxWidth().coverShadow())
                if (badge != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { badge() }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                caption,
                style = app.gloss.ui.theme.Gloss.type.footnote.copy(fontWeight = FontWeight(500)),
                color = app.gloss.ui.theme.Gloss.colors.text,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            actions.forEach { a ->
                DropdownMenuItem(
                    text = { if (a.danger) Text(a.label, color = app.gloss.ui.theme.Gloss.colors.danger) else Text(a.label) },
                    onClick = { menu = false; a.run() },
                )
            }
        }
    }
}
