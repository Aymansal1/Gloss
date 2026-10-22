package app.gloss.ui.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.gloss.Nav
import app.gloss.ui.components.LargeTitleScreen
import app.gloss.ui.components.pressable
import app.gloss.ui.container
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.Motion
import app.gloss.ui.theme.Pigment

@Composable
fun SeriesScreen(name: String, nav: Nav) {
    val c = container()
    val items by c.catalog.items.collectAsState()
    val installed by c.library.issues.collectAsState()
    val showAdult by c.reading.showAdult.collectAsState()
    var year by rememberSaveable { mutableStateOf<String?>(null) }

    val issues = remember(items, showAdult, name) {
        items.filter { it.magazineName == name && (showAdult || !it.adult) }.sortedBy { it.date }
    }
    val years = remember(issues) { issues.map { it.year }.filter { it.isNotBlank() }.distinct() }
    val shown = remember(issues, year) { issues.filter { year == null || it.year == year } }
    val pigment = Pigment.forSeries(name)
    val columns = (LocalConfiguration.current.screenWidthDp / 128).coerceIn(2, 6)
    val rows = remember(shown, columns) { shown.chunked(columns) }

    LargeTitleScreen(
        title = name,
        onBack = nav.back,
        backLabel = "Discover",
        header = {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(pigment))
                Spacer(Modifier.width(8.dp))
                val pub = issues.firstOrNull()?.publisher.orEmpty()
                Text(
                    listOf(if (issues.size == 1) "1 issue" else "${issues.size} issues", pub).filter { it.isNotBlank() }.joinToString(" · "),
                    style = Gloss.type.subhead, color = Gloss.colors.secondary,
                )
            }
        },
    ) {
        if (years.size > 1) {
            item(key = "years") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    YearChip("All", year == null, pigment) { year = null }
                    years.forEach { y -> YearChip(y, year == y, pigment) { year = if (year == y) null else y } }
                }
            }
        }
        items(rows.size, key = { "row-$it-${year ?: "all"}" }) { r ->
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                rows[r].forEach { item ->
                    ShelfIssue(
                        item, installed[item.id],
                        onClick = { nav.issue(item.id) },
                        onRead = { nav.read(item.id, c.reading.lastPage(item.id), null) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rows[r].size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun YearChip(label: String, selected: Boolean, pigment: Color, onClick: () -> Unit) {
    val c = Gloss.colors
    val bg by animateColorAsState(if (selected) pigment else c.surface, Motion.settle(), label = "chip")
    val fg by animateColorAsState(if (selected) Pigment.on(pigment) else c.text, Motion.settle(), label = "chipText")
    Box(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick, scaleTo = 0.92f, role = Role.Tab)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Gloss.type.subhead, color = fg)
    }
}
