package app.gloss.ui.learn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.gloss.Nav
import app.gloss.learn.SavedWord
import app.gloss.learn.Slang
import app.gloss.learn.Word
import app.gloss.ui.ScrollToTopOn
import app.gloss.ui.components.Cell
import app.gloss.ui.components.EmptyState
import app.gloss.ui.components.LargeTitleScreen
import app.gloss.ui.components.PillButton
import app.gloss.ui.components.PillStyle
import app.gloss.ui.components.RoundIconButton
import app.gloss.ui.components.Section
import app.gloss.ui.components.SegmentedControl
import app.gloss.ui.container
import app.gloss.ui.prettyDate
import app.gloss.ui.theme.Gloss
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Learn tab: words you saved while reading, and the magazines' gamer slang. */
@Composable
fun LearnScreen(nav: Nav, reselect: Flow<Unit> = emptyFlow()) {
    val c = container()
    val g = Gloss.colors
    val words by c.words.words.collectAsState()
    var view by rememberSaveable { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<SavedWord?>(null) }
    var removing by remember { mutableStateOf<SavedWord?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ScrollToTopOn(reselect, list)

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/tab-separated-values")) { dest ->
        if (dest == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(dest)?.use { it.write(c.words.ankiExport().toByteArray()) }
                        ?: error("Couldn't open the file")
                }
                "Saved. In Anki, choose Import File and pick it: each word becomes a card, tagged gloss."
            }.getOrElse { "Couldn't save the file: ${it.message}" }
        }
    }

    LargeTitleScreen(
        title = "Learn",
        state = list,
        header = {
            Text(
                when (words.size) {
                    0 -> "Japanese from the pages you read"
                    1 -> "1 saved word"
                    else -> "${words.size} saved words"
                },
                style = Gloss.type.subhead, color = g.secondary,
            )
            Spacer(Modifier.height(14.dp))
            SegmentedControl(listOf("Your words", "Gamer slang"), view, { view = it })
        },
        actions = {
            if (words.isNotEmpty()) RoundIconButton(Icons.Rounded.IosShare, "Export your words for Anki", { exporter.launch("gloss-words.txt") })
        },
    ) {
        if (view == 0) {
            if (words.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No words yet",
                        body = "While reading, tap a translated box, then Learn. Every word gets its reading, meaning and voice; star the ones to keep.",
                        icon = Icons.Rounded.School,
                    )
                }
            } else {
                item(key = "words") {
                    Section(Modifier.padding(top = 8.dp)) {
                        words.forEachIndexed { i, w ->
                            Cell(
                                title = if (w.reading.isNotBlank() && w.reading != w.text) "${w.text}  ${w.reading}" else w.text,
                                subtitle = w.meaning,
                                detail = w.issueTitle.takeIf { it.isNotBlank() }?.let { "$it · p. ${w.page}" },
                                onClick = { open = w },
                                onLongClick = { removing = w },
                                divider = i != words.lastIndex,
                            )
                        }
                    }
                }
                item(key = "hint") {
                    Text(
                        "Hold a word to remove it. Export sends them all to a file Anki can import.",
                        style = Gloss.type.footnote, color = g.secondary,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp),
                    )
                }
            }
        } else {
            item(key = "slang-intro") {
                Text(
                    "The jargon of 80s and 90s game magazines. Learn shows these whenever a passage uses one.",
                    style = Gloss.type.subhead, color = g.secondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(Slang.terms, key = { "s-" + it.term }) { term ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(g.surface).padding(vertical = 4.dp)) {
                        SlangRow(term)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    open?.let { w ->
        WordSheet(
            saved = w,
            onDismiss = { open = null },
            onOpenPage = if (w.issueId.isNotBlank() && w.page > 0) ({ open = null; nav.read(w.issueId, w.page, null) }) else null,
        )
    }
    removing?.let { w ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${w.text}?") },
            text = { Text("It comes off your list. You can star it again from any page.") },
            confirmButton = { TextButton(onClick = { c.words.remove(w.wordId); removing = null }) { Text("Remove", color = Gloss.colors.danger) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

/** A saved word's full card in a sheet, with the way back to the page it came from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(saved: SavedWord, onDismiss: () -> Unit, onOpenPage: (() -> Unit)?) {
    val c = container()
    val g = Gloss.colors
    val word by produceState<Word?>(null, saved.wordId) { value = c.japanese.word(saved.wordId) }
    var kanji by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = g.raised,
        contentColor = g.text,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            val w = word
            val k = kanji
            when {
                k != null -> KanjiCard(k, onBack = { kanji = null }, modifier = Modifier.padding(horizontal = 20.dp))
                w != null -> WordCard(w, null, saved.context, null, onKanji = { kanji = it }, modifier = Modifier.padding(horizontal = 20.dp))
                else -> Text(saved.text, style = Gloss.type.title1, color = g.text, modifier = Modifier.padding(horizontal = 20.dp))
            }
            if (saved.context.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text("Where you found it", style = Gloss.type.footnote, color = g.secondary, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(4.dp))
                Text(saved.context, style = Gloss.type.subhead, color = g.text, modifier = Modifier.padding(horizontal = 20.dp), maxLines = 6)
                if (saved.issueTitle.isNotBlank()) {
                    Text("${saved.issueTitle} · p. ${saved.page}", style = Gloss.type.footnote, color = g.tertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            if (onOpenPage != null) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                    PillButton("Open the page", onOpenPage, Modifier.fillMaxWidth(), style = PillStyle.Tinted)
                }
            }
        }
    }
}

/** "Famitsu · 4 Mar 1994", the label a saved word carries back to its issue. */
fun issueLabel(magazine: String, date: String): String = listOf(magazine, prettyDate(date)).filter { it.isNotBlank() }.joinToString(" · ")
