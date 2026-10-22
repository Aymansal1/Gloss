package app.gloss.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gloss.data.CatalogItem
import app.gloss.learn.History
import app.gloss.ui.components.CoverInfo
import app.gloss.ui.components.CoverSharing
import app.gloss.ui.components.IssueCover
import app.gloss.ui.components.coverShadow
import app.gloss.ui.components.pressable
import app.gloss.ui.prettyDate
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GoldLeaf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FactDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

/** An issue from the catalog that came out this week (or month) in some past year. */
internal fun issueForToday(items: List<CatalogItem>, today: LocalDate): Pair<CatalogItem, Boolean>? {
    val dated = items.mapNotNull { item ->
        runCatching { LocalDate.parse(item.date.trim()) }.getOrNull()?.takeIf { it.year < today.year }?.let { item to it }
    }
    if (dated.isEmpty()) return null
    // The same pick all day, a different one tomorrow.
    fun pick(list: List<Pair<CatalogItem, LocalDate>>) = list.sortedBy { it.first.id }[Math.floorMod(today.toEpochDay(), list.size.toLong()).toInt()].first
    val week = dated.filter { History.dayGap(it.second, today) <= 3 }
    if (week.isNotEmpty()) return pick(week) to true
    val month = dated.filter { it.second.monthValue == today.monthValue }
    return if (month.isNotEmpty()) pick(month) to false else null
}

/** Discover's look back: an issue from this week in history, and a moment from gaming history. */
@Composable
fun FromTheArchive(items: List<CatalogItem>, onIssue: (String) -> Unit) {
    val g = Gloss.colors
    val today = remember { LocalDate.now() }
    val issue = remember(items, today) { issueForToday(items, today) }
    val (fact, dated) = remember(today) { History.forDay(today) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("From the archive", style = Gloss.type.title2, color = g.text, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        issue?.let { (item, week) ->
            val year = item.year
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable({ onIssue(item.id) }, scaleTo = 0.98f, onClickLabel = "Open ${item.magazineName}")
                    .clip(RoundedCornerShape(16.dp))
                    .background(g.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IssueCover(CoverInfo.of(item), Modifier.width(62.dp).coverShadow(), shared = CoverSharing.None)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (week) "This week in $year" else "This month in $year",
                        style = Gloss.type.caption.copy(fontWeight = FontWeight(600)), color = g.lapis,
                    )
                    Text(item.magazineName, style = Gloss.type.headline, color = g.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(prettyDate(item.date), style = Gloss.type.footnote, color = g.secondary)
                    val ago = LocalDate.now().year - (year.toIntOrNull() ?: LocalDate.now().year)
                    if (ago > 0) Text("On newsstands $ago years ago", style = Gloss.type.footnote, color = g.tertiary)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(g.surface).padding(14.dp),
        ) {
            Box(Modifier.padding(top = 3.dp).width(3.dp).height(30.dp).background(Brush.verticalGradient(GoldLeaf.stops), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column {
                // A dated fact always carries its date: "came out" means nothing without a when.
                Text(
                    when {
                        fact.date == null -> "Did you know"
                        dated -> "This week in ${fact.date.year}"
                        else -> "On ${fact.date.format(FactDate)}"
                    },
                    style = Gloss.type.caption.copy(fontWeight = FontWeight(600)), color = g.secondary,
                )
                Spacer(Modifier.height(2.dp))
                Text(fact.text, style = Gloss.type.subhead, color = g.text)
            }
        }
    }
}
