package app.gloss.ui.learn

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gloss.learn.Kanji
import app.gloss.learn.Kana
import app.gloss.learn.SavedWord
import app.gloss.learn.Slang
import app.gloss.learn.SlangTerm
import app.gloss.learn.Token
import app.gloss.learn.Word
import app.gloss.ui.components.Hairline
import app.gloss.ui.components.RoundIconButton
import app.gloss.ui.components.pressable
import app.gloss.ui.container
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GoldLeaf
import app.gloss.ui.theme.Motion

/** Where a passage came from, so a saved word can point back to its page. */
data class PassageSource(val issueId: String, val issueTitle: String, val page: Int)

/** The part of a word card that changes as you dig in: the word, or one of its kanji. */
private sealed interface Focus {
    data class OnWord(val token: Token) : Focus
    data class OnKanji(val token: Token, val ch: String) : Focus
}

/**
 * The Learn panel for a passage: the Japanese with readings over the kanji, split into words
 * you can tap, the chosen word's card, and every word in the passage below it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearnPanel(blocks: List<String>, source: PassageSource, modifier: Modifier = Modifier) {
    val c = container()
    val g = Gloss.colors
    val passage = remember(blocks) { blocks.joinToString("\n") }
    val tokens by produceState<List<List<Token>>?>(null, blocks) {
        value = if (c.japanese.ready()) blocks.map { c.japanese.tokenize(it) } else emptyList()
    }
    var focus by remember { mutableStateOf<Focus?>(null) }
    val slang = remember(passage) { Slang.inText(passage) }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Learn", style = Gloss.type.title2, color = g.text, modifier = Modifier.weight(1f))
            SpeakButton(passage, "Listen to the passage")
        }
        val all = tokens
        when {
            all == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = g.lapis, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Opening the dictionary…", style = Gloss.type.footnote, color = g.secondary)
                }
            }
            all.isEmpty() -> Text(
                "This copy of Gloss was built without its dictionary.",
                style = Gloss.type.subhead, color = g.secondary, modifier = Modifier.padding(20.dp),
            )
            else -> {
                // The passage itself, word by word, readings above the kanji.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    all.forEach { line ->
                        FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            line.forEach { t ->
                                val chosen = (focus as? Focus.OnWord)?.token === t || (focus as? Focus.OnKanji)?.token === t
                                RubyWord(t, chosen, onTap = if (t.word != null && !t.word.isGrammar) ({ focus = Focus.OnWord(t) }) else null)
                            }
                        }
                    }
                }
                Hairline(Modifier.padding(horizontal = 20.dp))
                AnimatedContent(
                    targetState = focus,
                    transitionSpec = { fadeIn(Motion.settle()) togetherWith fadeOut(tween(90)) },
                    label = "focus",
                ) { f ->
                    when (f) {
                        null -> Text(
                            "Tap a word to look it up. Readings sit above the kanji.",
                            style = Gloss.type.subhead, color = g.secondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                        is Focus.OnWord -> WordCard(
                            f.token.word!!, f.token, passage, source,
                            onKanji = { focus = Focus.OnKanji(f.token, it) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                        is Focus.OnKanji -> KanjiCard(
                            f.ch, onBack = { focus = Focus.OnWord(f.token) },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
                if (slang.isNotEmpty()) {
                    SectionLabel("Gamer slang")
                    slang.forEach { SlangRow(it) }
                }
                val words = remember(all) {
                    all.flatten().filter { it.word != null && !it.word.isGrammar }.distinctBy { it.word!!.id }
                }
                if (words.isNotEmpty()) {
                    SectionLabel("In this passage")
                    words.forEach { t ->
                        PassageWordRow(t, passage, source, onOpen = { focus = Focus.OnWord(t) })
                    }
                }
                Text(
                    "Dictionary: JMdict and KANJIDIC2 by the EDRDG; stroke order by KanjiVG. Both CC BY-SA.",
                    style = Gloss.type.caption, color = g.tertiary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = Gloss.type.footnote.copy(fontWeight = FontWeight(600)), color = Gloss.colors.secondary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** One word of the passage: its reading in small type over the kanji, gold when chosen. */
@Composable
private fun RubyWord(t: Token, chosen: Boolean, onTap: (() -> Unit)?) {
    val g = Gloss.colors
    val rubyStyle = Gloss.type.caption2.copy(fontSize = 10.sp, lineHeight = 12.sp)
    val baseStyle = Gloss.type.callout.copy(fontSize = 19.sp, lineHeight = 26.sp)
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (chosen) g.goldWash else Color.Transparent)
            .then(if (onTap != null) Modifier.pressable(onTap, scaleTo = 0.94f, onClickLabel = "Look up ${t.surface}") else Modifier)
            .padding(horizontal = 1.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        t.ruby.forEach { (text, ruby) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ruby ?: " ", style = rubyStyle, color = if (ruby == null) Color.Transparent else if (g.isDark) GoldLeaf.Hi else g.goldInk, maxLines = 1)
                Text(text, style = baseStyle, color = if (onTap == null) g.secondary else g.text)
            }
        }
    }
    // A thin gap between words keeps them tappable apart without reading as spaces.
    Spacer(Modifier.width(2.dp))
}

/** The full card for a word: headword, reading, voice, save, meanings, kanji to open. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordCard(
    word: Word,
    token: Token?,
    context: String,
    source: PassageSource?,
    onKanji: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = container()
    val g = Gloss.colors
    val saved by c.words.words.collectAsState()
    val isSaved = saved.any { it.wordId == word.id }
    val headword = token?.base?.takeIf { b -> word.kanji.contains(b) || word.kana.contains(b) } ?: word.headword
    val slang = token?.let { Slang.forWord(it.surface, it.base) } ?: Slang.forWord(headword, headword)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(headword, style = Gloss.type.title1, color = g.text)
                val reading = word.reading
                if (reading != headword) Text(reading, style = Gloss.type.subhead, color = g.secondary)
            }
            SpeakButton(headword.takeIf { it.any(Kana::isKanji) }?.let { word.reading } ?: headword, "Say $headword")
            RoundIconButton(
                if (isSaved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                if (isSaved) "Remove from your words" else "Save to your words",
                {
                    c.words.toggle(
                        SavedWord(
                            wordId = word.id, text = headword, reading = word.reading, meaning = word.meaning,
                            issueId = source?.issueId.orEmpty(), issueTitle = source?.issueTitle.orEmpty(),
                            page = source?.page ?: 0, context = context.take(400), savedAt = System.currentTimeMillis(),
                        ),
                    )
                },
                tint = if (isSaved) g.gold else g.text,
            )
        }
        if (token != null && token.surface != headword) {
            Text("Appears as ${token.surface}", style = Gloss.type.footnote, color = g.tertiary)
        }
        if (word.common) {
            Spacer(Modifier.height(6.dp))
            Tag("Common word", g.lapis)
        }
        Spacer(Modifier.height(10.dp))
        word.senses.take(5).forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("${i + 1}.", style = Gloss.type.subhead, color = g.tertiary, modifier = Modifier.width(22.dp))
                Column {
                    Text(s.glosses, style = Gloss.type.body, color = g.text)
                    val label = posLabel(s.pos, s.notes)
                    if (label.isNotEmpty()) Text(label, style = Gloss.type.caption, color = g.tertiary)
                }
            }
        }
        if (slang != null) {
            Spacer(Modifier.height(10.dp))
            SlangNote(slang)
        }
        val kanji = headword.filter { Kana.isKanji(it) && it != '々' }.map { it.toString() }.distinct()
        if (kanji.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Kanji", style = Gloss.type.footnote.copy(fontWeight = FontWeight(600)), color = g.secondary)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                kanji.forEach { k -> KanjiChip(k) { onKanji(k) } }
            }
        }
    }
}

@Composable
private fun KanjiChip(ch: String, onClick: () -> Unit) {
    val g = Gloss.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(g.fill)
            .pressable(onClick, scaleTo = 0.94f, onClickLabel = "Open the kanji $ch"),
        contentAlignment = Alignment.Center,
    ) {
        Text(ch, style = Gloss.type.title2, color = g.text)
    }
}

/** A kanji's meanings, readings and grade, with its strokes drawn in order. */
@Composable
fun KanjiCard(ch: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = container()
    val g = Gloss.colors
    val info by produceState<Kanji?>(null, ch) { value = c.japanese.kanji(ch) }
    Column(modifier.fillMaxWidth()) {
        if (onBack != null) {
            Row(
                Modifier.heightIn(min = 40.dp).clip(CircleShape).pressable(onBack, onClickLabel = "Back to the word").padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = g.lapis, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Word", style = Gloss.type.subhead, color = g.lapis)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            StrokeOrder(info?.paths.orEmpty(), Modifier.size(132.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val k = info
                if (k == null) {
                    Text(ch, style = Gloss.type.largeTitle, color = g.text)
                } else {
                    Text(k.meanings.split("; ").take(4).joinToString(", "), style = Gloss.type.headline, color = g.text)
                    Spacer(Modifier.height(8.dp))
                    if (k.onyomi.isNotBlank()) Reading("On", k.onyomi)
                    if (k.kunyomi.isNotBlank()) Reading("Kun", k.kunyomi.replace(".", ""))
                    Spacer(Modifier.height(8.dp))
                    val facts = listOfNotNull(
                        k.strokes?.let { "$it strokes" },
                        when (val gr = k.grade) {
                            null -> null
                            in 1..6 -> "Taught in grade $gr"
                            8 -> "Secondary school"
                            else -> "Used in names"
                        },
                    )
                    Text(facts.joinToString(" · "), style = Gloss.type.footnote, color = g.secondary)
                }
            }
        }
    }
}

@Composable
private fun Reading(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = Gloss.type.caption.copy(fontWeight = FontWeight(600)), color = Gloss.colors.tertiary, modifier = Modifier.width(34.dp).padding(top = 2.dp))
        Text(value, style = Gloss.type.subhead, color = Gloss.colors.text)
    }
}

/**
 * Draws a kanji's strokes one after another, the way it's written, then holds and starts
 * over. Tap to watch again. With animations off it shows the finished kanji.
 */
@Composable
fun StrokeOrder(paths: List<String>, modifier: Modifier = Modifier) {
    val g = Gloss.colors
    val parsed = remember(paths) { paths.mapNotNull { d -> runCatching { PathParser().parsePathString(d).toPath() }.getOrNull() } }
    val progress = remember(parsed) { Animatable(0f) }
    var replay by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val motionOff = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    LaunchedEffect(parsed, replay) {
        if (parsed.isEmpty()) return@LaunchedEffect
        if (motionOff) { progress.snapTo(parsed.size.toFloat()); return@LaunchedEffect }
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(parsed.size.toFloat(), tween(parsed.size * 520, easing = LinearEasing))
            kotlinx.coroutines.delay(1600)
        }
    }
    val ink = g.text
    val ghost = g.separator
    val lead = GoldLeaf.Mid
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(g.fill)
            .border(0.6.dp, g.separator, RoundedCornerShape(16.dp))
            .pressable({ replay++ }, scaleTo = 0.97f, onClickLabel = "Draw the strokes again")
            .semantics { contentDescription = "Stroke order, ${paths.size} strokes" },
        contentAlignment = Alignment.Center,
    ) {
        if (parsed.isEmpty()) {
            Text("No stroke data", style = Gloss.type.caption, color = g.tertiary)
        } else {
            Canvas(Modifier.padding(10.dp).fillMaxWidth().height(112.dp)) {
                val unit = minOf(size.width, size.height) / 109f
                val dx = (size.width - 109f * unit) / 2f
                val p = progress.value
                val measure = PathMeasure()
                val partial = Path()
                scale(unit, unit, pivot = Offset.Zero) {
                    translate(dx / unit, 0f) {
                        parsed.forEachIndexed { i, path ->
                            val style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            drawPath(path, ghost, style = style)
                            when {
                                i + 1 <= p -> drawPath(path, ink, style = style)
                                i < p -> {
                                    measure.setPath(path, false)
                                    partial.reset()
                                    measure.getSegment(0f, measure.length * (p - i), partial, true)
                                    drawPath(partial, lead, style = style)
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                androidx.compose.material3.Icon(Icons.Rounded.Replay, contentDescription = null, tint = g.tertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Plays [text] with the phone's Japanese voice, or explains how to get one. */
@Composable
fun SpeakButton(text: String, label: String) {
    val c = container()
    val g = Gloss.colors
    val ctx = LocalContext.current
    val hasVoice by c.speaker.hasVoice.collectAsState()
    var asked by remember { mutableStateOf(false) }
    RoundIconButton(Icons.AutoMirrored.Rounded.VolumeUp, label, { asked = true; c.speaker.speak(text) }, tint = g.lapis)
    if (asked && hasVoice == false) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asked = false },
            title = { Text("No Japanese voice") },
            text = { Text("Your phone's text-to-speech doesn't have a Japanese voice yet. You can add one in its settings, usually under Speech services or Text-to-speech, then come back.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    asked = false
                    try { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (e: ActivityNotFoundException) { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Open settings") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { asked = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text, style = Gloss.type.caption2.copy(fontWeight = FontWeight(600)), color = color,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun SlangNote(term: SlangTerm) {
    val g = Gloss.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(g.lapisTint).padding(12.dp),
    ) {
        Box(Modifier.padding(top = 3.dp).size(width = 3.dp, height = 16.dp).background(Brush.verticalGradient(GoldLeaf.stops), CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Gamer slang: ${term.meaning}", style = Gloss.type.subhead.copy(fontWeight = FontWeight(600)), color = g.text)
            if (term.note.isNotBlank()) Text(term.note, style = Gloss.type.footnote, color = g.secondary)
        }
    }
}

@Composable
fun SlangRow(term: SlangTerm, modifier: Modifier = Modifier) {
    val g = Gloss.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(term.term, style = Gloss.type.headline, color = g.text)
            if (term.reading.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(term.reading, style = Gloss.type.footnote, color = g.secondary)
            }
        }
        Text(term.meaning, style = Gloss.type.subhead, color = g.text)
        if (term.note.isNotBlank()) Text(term.note, style = Gloss.type.footnote, color = g.secondary)
    }
}

@Composable
private fun PassageWordRow(t: Token, passage: String, source: PassageSource, onOpen: () -> Unit) {
    val c = container()
    val g = Gloss.colors
    val word = t.word!!
    val saved by c.words.words.collectAsState()
    val isSaved = saved.any { it.wordId == word.id }
    val headword = t.base.takeIf { b -> word.kanji.contains(b) || word.kana.contains(b) } ?: word.headword
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onOpen, scaleTo = 0.99f, role = Role.Button, onClickLabel = "Open $headword")
            .padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(headword, style = Gloss.type.headline, color = g.text)
                if (word.reading != headword) {
                    Spacer(Modifier.width(8.dp))
                    Text(word.reading, style = Gloss.type.footnote, color = g.secondary)
                }
            }
            Text(word.meaning, style = Gloss.type.footnote, color = g.secondary, maxLines = 2)
        }
        RoundIconButton(
            if (isSaved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            if (isSaved) "Remove $headword from your words" else "Save $headword",
            {
                c.words.toggle(
                    SavedWord(word.id, headword, word.reading, word.meaning, source.issueId, source.issueTitle, source.page,
                        passage.take(400), System.currentTimeMillis()),
                )
            },
            tint = if (isSaved) g.gold else g.tertiary,
        )
    }
}

/** JMdict's part-of-speech codes, said plainly. */
private fun posLabel(pos: List<String>, notes: List<String>): String {
    val parts = pos.mapNotNull { p ->
        when {
            p == "n" -> "noun"
            p.startsWith("v5") || p == "v1" || p == "v1-s" || p == "vk" || p == "vz" -> "verb"
            p == "vs-i" || p == "vs-s" -> "verb"
            p == "vs" -> "noun, takes する"
            p == "adj-i" || p == "adj-ix" -> "adjective"
            p == "adj-na" -> "な-adjective"
            p == "adj-no" -> "takes の"
            p == "adj-pn" -> "pre-noun"
            p.startsWith("adv") -> "adverb"
            p == "exp" -> "expression"
            p == "int" -> "interjection"
            p == "pn" -> "pronoun"
            p == "ctr" -> "counter"
            p == "pref" || p == "n-pref" -> "prefix"
            p == "suf" || p == "n-suf" -> "suffix"
            p == "conj" -> "conjunction"
            p == "num" -> "number"
            else -> null
        }
    }.distinct() + notes.mapNotNull {
        when (it) {
            "uk" -> "usually in kana"
            "abbr" -> "abbreviation"
            "sl" -> "slang"
            "on-mim" -> "sound word"
            else -> null
        }
    }
    return parts.joinToString(" · ")
}
