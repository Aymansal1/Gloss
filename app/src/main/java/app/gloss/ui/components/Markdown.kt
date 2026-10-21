package app.gloss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.gloss.data.RosettaFormat
import app.gloss.ui.theme.Gloss

/** Neutral tint for `code` spans; reads on both light and dark grounds. */
private val CodeTint = Color(0x26808599)

/** Inline markdown: **bold**, __bold__, *italic*, _italic_, `code`. */
fun inlineMarkdown(text: String, highlight: List<String> = emptyList(), marker: SpanStyle? = null): AnnotatedString {
    val base = buildAnnotatedString {
        var i = 0
        val s = text
        while (i < s.length) {
            when {
                s.startsWith("**", i) || s.startsWith("__", i) -> {
                    val tok = s.substring(i, i + 2)
                    val end = s.indexOf(tok, i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                        i = end + 2
                    } else { append(tok); i += 2 }
                }
                (s[i] == '*' || s[i] == '_') && i + 1 < s.length && !s[i + 1].isWhitespace() -> {
                    val end = s.indexOf(s[i], i + 1)
                    if (end > i + 1 && (s[i] == '*' || end + 1 >= s.length || !s[end + 1].isLetterOrDigit())) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                        i = end + 1
                    } else { append(s[i]); i++ }
                }
                s[i] == '`' -> {
                    val end = s.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(background = CodeTint)) { append(s.substring(i + 1, end)) }
                        i = end + 1
                    } else { append('`'); i++ }
                }
                s[i] == '\\' && i + 1 < s.length -> { append(s[i + 1]); i += 2 }
                else -> { append(s[i]); i++ }
            }
        }
    }
    if (highlight.isEmpty() || marker == null) return base
    val text = base.text
    return buildAnnotatedString {
        append(base)
        for (term in highlight) {
            if (term.isEmpty()) continue
            var from = 0
            while (true) {
                val k = text.indexOf(term, from, ignoreCase = true)
                if (k < 0) break
                addStyle(marker, k, k + term.length)
                from = k + term.length
            }
        }
    }
}

/** Renders one markdown block (paragraph, heading, list or table). */
@Composable
fun MarkdownBlock(
    block: RosettaFormat.Block,
    style: TextStyle,
    modifier: Modifier = Modifier,
    highlight: List<String> = emptyList(),
) {
    val marker = SpanStyle(background = Gloss.colors.goldWash)
    when (block.kind) {
        RosettaFormat.BlockKind.HEADING -> Text(
            inlineMarkdown(block.text.trimStart('#', ' '), highlight, marker),
            style = style.copy(fontWeight = FontWeight.Bold, fontSize = style.fontSize * 1.08f),
            modifier = modifier,
        )
        RosettaFormat.BlockKind.PARAGRAPH -> Text(
            inlineMarkdown(block.text.replace("\n", " "), highlight, marker), style = style, modifier = modifier,
        )
        RosettaFormat.BlockKind.LIST -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val items = splitListItems(block.text)
            items.forEachIndexed { idx, (ordered, body) ->
                Row {
                    Text(
                        if (ordered) "${idx + 1}." else "•",
                        style = style.copy(color = Gloss.colors.secondary),
                        modifier = Modifier.width(22.dp),
                    )
                    Text(inlineMarkdown(body, highlight, marker), style = style)
                }
            }
        }
        RosettaFormat.BlockKind.TABLE -> MarkdownTable(block.text, style, modifier, highlight)
    }
}

private val LIST_MARK = Regex("""^\s*([-*+]|\d+[.)])\s+""")

private fun splitListItems(text: String): List<Pair<Boolean, String>> {
    val out = ArrayList<Pair<Boolean, String>>()
    for (line in text.lines()) {
        val m = LIST_MARK.find(line)
        if (m != null) {
            out += (m.groupValues[1].first().isDigit()) to line.substring(m.range.last + 1).trim()
        } else if (out.isNotEmpty() && line.isNotBlank()) {
            val (o, b) = out.removeAt(out.size - 1)
            out += o to (b + " " + line.trim())
        }
    }
    return out
}

@Composable
private fun MarkdownTable(text: String, style: TextStyle, modifier: Modifier, highlight: List<String>) {
    val rows = text.lines()
        .map { it.trim() }
        .filter { it.startsWith("|") }
        .filterNot { Regex("""^\|?\s*:?-{2,}""").containsMatchIn(it) && it.all { ch -> ch in "|:- " } }
        .map { line -> line.trim('|').split('|').map { it.trim() } }
    if (rows.isEmpty()) return
    val cols = rows.maxOf { it.size }
    val g = Gloss.colors
    val marker = SpanStyle(background = g.goldWash)
    val cellStyle = style.copy(fontSize = style.fontSize * 0.92f, lineHeight = 1.4.em)
    Column(
        modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, g.separator),
    ) {
        rows.forEachIndexed { r, cells ->
            Row(Modifier.background(if (r == 0) g.fill else g.fill.copy(alpha = if (r % 2 == 0) 0.4f else 0f))) {
                for (c in 0 until cols) {
                    Text(
                        inlineMarkdown(cells.getOrElse(c) { "" }, highlight, marker),
                        style = if (r == 0) cellStyle.copy(fontWeight = FontWeight.Bold) else cellStyle,
                        modifier = Modifier
                            .widthIn(min = 72.dp, max = 220.dp)
                            .border(0.5.dp, g.separator)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** Plain-text version of a block (for manga-mode overlays). */
fun blockPlainText(block: RosettaFormat.Block): String =
    block.text.lines().joinToString(" ") { LIST_MARK.replace(it, "").trim().trim('|').replace("|", " · ") }
        .replace(Regex("""\*\*|__|`|^#+\s*"""), "")
        .replace(Regex("""\s+"""), " ").trim()

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Gloss.type.footnote, color = Gloss.colors.secondary, modifier = modifier)
}
