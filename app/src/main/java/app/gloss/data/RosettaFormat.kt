package app.gloss.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Parsers for the Rosetta / Gaming Alexandria data format.
 *
 *  _COMPLETE.txt    [[PAGE_001]] #GA-TRANSCRIPTION ... #GA-TRANSLATION ... #GA-SUMMARY ...
 *  _COORDINATES.json [{"page": 1, "data": [{"text": "<jp>", "box": [ymin,xmin,ymax,xmax]}, ...]}]
 *                    boxes are normalised 0..1000; "boxes" (list of boxes) is used for split regions.
 *  metadata.txt     "Key: value" lines.
 */
object RosettaFormat {

    private val PAGE_MARKER = Regex("""\[\[PAGE_(\d+)]]""")
    private val TRANSCRIPTION_HEADER = Regex("""^#\s?GA-TRANSCRIPTION\s*""", RegexOption.IGNORE_CASE)
    private val TRANSLATION_SPLIT = Regex("""#\s?GA-TRANSLATION""", RegexOption.IGNORE_CASE)
    private val SUMMARY_SPLIT = Regex("""#\s?GA-SUMMARY""", RegexOption.IGNORE_CASE)
    private val PER_PAGE_FILE = Regex("""_p0*(\d+)\.txt$""", RegexOption.IGNORE_CASE)

    /** Splits a master _COMPLETE.txt into {pageNumber (1-based) -> raw page text}. */
    fun splitPages(text: String): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        val matches = PAGE_MARKER.findAll(text).toList()
        for ((i, m) in matches.withIndex()) {
            val page = m.groupValues[1].toIntOrNull() ?: continue
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            out[page] = text.substring(m.range.last + 1, end).trim()
        }
        return out
    }

    /** Page number from a per-page file name like "Mag_p012.txt", or null. */
    fun perPageNumber(fileName: String): Int? =
        PER_PAGE_FILE.find(fileName)?.groupValues?.get(1)?.toIntOrNull()

    /** Splits one page into (transcription, translation, summary). */
    fun splitSections(raw: String): PageText {
        val clean = TRANSCRIPTION_HEADER.replace(raw, "")
        val parts = TRANSLATION_SPLIT.split(clean, limit = 2)
        val jp: String
        var en = ""
        val sum: String
        if (parts.size > 1) {
            jp = parts[0]
            val sub = SUMMARY_SPLIT.split(parts[1], limit = 2)
            en = sub[0]
            sum = sub.getOrElse(1) { "" }
        } else {
            val sub = SUMMARY_SPLIT.split(parts[0], limit = 2)
            jp = sub[0]
            sum = sub.getOrElse(1) { "" }
        }
        return PageText(jp.trim(), en.trim(), sum.trim())
    }

    private val META_KEYS = mapOf(
        "magazine name" to "name", "publisher" to "publisher", "date" to "date",
        "issue name" to "issue_name", "scanner" to "scanner", "scanner url" to "scanner_url",
        "editor" to "editor", "editor url" to "editor_url", "region" to "region",
        "translation" to "translation", "tags" to "tags", "version" to "version",
        "notes" to "notes", "pdf filename" to "pdf_filename",
    )

    fun parseMetadata(text: String): Map<String, String> {
        val meta = LinkedHashMap<String, String>()
        for (line in text.lineSequence()) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = META_KEYS[line.substring(0, idx).trim().lowercase()] ?: continue
            meta[key] = line.substring(idx + 1).trim()
        }
        return meta
    }

    /** Parses the coordinates JSON into {page (1-based) -> regions}. Tolerates junk entries. */
    fun parseCoordinates(json: String): Map<Int, List<TextRegion>> {
        val out = HashMap<Int, List<TextRegion>>()
        val arr = try { JSONArray(json) } catch (e: Exception) { return out }
        for (i in 0 until arr.length()) {
            val entry = arr.optJSONObject(i) ?: continue
            val page = entry.opt("page")?.toString()?.trim()?.toIntOrNull() ?: continue
            val data = entry.optJSONArray("data") ?: continue
            val regions = ArrayList<TextRegion>()
            for (j in 0 until data.length()) {
                val item = data.optJSONObject(j) ?: continue
                val boxes = readBoxes(item)
                if (boxes.isEmpty()) continue
                regions += TextRegion(index = j, text = item.optString("text", "").trim(), boxes = boxes)
            }
            out[page] = regions
        }
        return out
    }

    private fun readBoxes(item: JSONObject): List<NBox> {
        val raw = ArrayList<JSONArray>()
        item.optJSONArray("boxes")?.let { list ->
            for (k in 0 until list.length()) list.optJSONArray(k)?.let(raw::add)
        }
        if (raw.isEmpty()) item.optJSONArray("box")?.let(raw::add)
        return raw.mapNotNull { a ->
            if (a.length() < 4) return@mapNotNull null
            val v = DoubleArray(4) { a.optDouble(it, Double.NaN) }
            if (v.any { it.isNaN() } || v.all { it == 0.0 }) return@mapNotNull null
            // Same 5-unit padding the desktop viewer applies, clamped to the page.
            val pad = 5.0
            NBox(
                top = ((v[0] - pad).coerceAtLeast(0.0) / 1000.0).toFloat(),
                left = ((v[1] - pad).coerceAtLeast(0.0) / 1000.0).toFloat(),
                bottom = ((v[2] + pad).coerceAtMost(1000.0) / 1000.0).toFloat(),
                right = ((v[3] + pad).coerceAtMost(1000.0) / 1000.0).toFloat(),
            ).takeIf { it.bottom > it.top && it.right > it.left }
        }
    }

    // ---------------------------------------------------------------------------------
    // Markdown blocks: the desktop app pairs the Nth rendered block of the transcription
    // with the Nth block of the translation, then fuzzy-matches transcription blocks to
    // coordinate regions. We mirror its block notion (paragraph, heading, list, table).
    // ---------------------------------------------------------------------------------

    enum class BlockKind { PARAGRAPH, HEADING, LIST, TABLE }

    data class Block(val kind: BlockKind, val text: String)

    private val LIST_ITEM = Regex("""^\s*([-*+]|\d+[.)])\s+""")
    private val HEADING = Regex("""^\s{0,3}#{1,6}\s""")

    fun splitBlocks(markdown: String): List<Block> {
        val blocks = ArrayList<Block>()
        val buf = ArrayList<String>()
        var kind: BlockKind? = null

        fun flush() {
            if (buf.isNotEmpty() && kind != null) {
                val text = buf.joinToString("\n").trim()
                if (text.isNotEmpty()) blocks += Block(kind!!, text)
            }
            buf.clear(); kind = null
        }

        for (line in markdown.replace("\r\n", "\n").split('\n')) {
            val t = line.trim()
            val lineKind = when {
                t.isEmpty() -> null
                HEADING.containsMatchIn(line) -> BlockKind.HEADING
                t.startsWith("|") -> BlockKind.TABLE
                LIST_ITEM.containsMatchIn(line) -> BlockKind.LIST
                else -> BlockKind.PARAGRAPH
            }
            when {
                lineKind == null -> flush()
                lineKind == BlockKind.HEADING -> { flush(); blocks += Block(BlockKind.HEADING, t) }
                // Indented continuation lines stay with the list they belong to.
                kind == BlockKind.LIST && lineKind == BlockKind.PARAGRAPH && line.startsWith(" ") -> buf += line
                kind == lineKind -> buf += line
                kind == BlockKind.PARAGRAPH && lineKind == BlockKind.LIST -> { flush(); kind = lineKind; buf += line }
                else -> { flush(); kind = lineKind; buf += line }
            }
        }
        flush()
        return blocks
    }

    // --- Fuzzy matching (port of the desktop's attachTextHoverMagic) ---

    private val NON_MATCH_CHARS =
        Regex("[^a-z0-9\\u3040-\\u30ff\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff]")

    fun cleanForMatch(s: String): String =
        NON_MATCH_CHARS.replace(Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(), "")

    fun similarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        if (s1 == s2) return 1.0
        if (s1.contains(s2) || s2.contains(s1)) {
            val min = minOf(s1.length, s2.length).toDouble()
            val max = maxOf(s1.length, s2.length).toDouble()
            return (min / max) * 0.6 + 0.4
        }
        if (s1.length < 2 || s2.length < 2) return 0.0
        val counts = HashMap<String, Int>()
        for (i in 0 until s2.length - 1) counts.merge(s2.substring(i, i + 2), 1, Int::plus)
        var inter = 0
        for (i in 0 until s1.length - 1) {
            val bg = s1.substring(i, i + 2)
            val c = counts[bg] ?: 0
            if (c > 0) { inter++; counts[bg] = c - 1 }
        }
        return 2.0 * inter / ((s1.length - 1) + (s2.length - 1))
    }

    /**
     * For each transcription block, the index into [regions] it best matches, or -1.
     * Ties are broken by length difference, then by relative position, as on desktop.
     */
    fun matchBlocksToRegions(blocks: List<Block>, regions: List<TextRegion>): IntArray {
        val result = IntArray(blocks.size) { -1 }
        if (regions.isEmpty()) return result
        val regionTexts = regions.map { cleanForMatch(it.text) }
        val blockTexts = blocks.map { cleanForMatch(it.text) }
        for ((bi, bt) in blockTexts.withIndex()) {
            if (bt.isEmpty()) continue
            var best = -1; var bestSim = 0.0
            var bestLen = Int.MAX_VALUE; var bestPos = Double.MAX_VALUE
            for ((ri, rt) in regionTexts.withIndex()) {
                if (rt.isEmpty()) continue
                val sim = similarity(bt, rt)
                val lenDiff = kotlin.math.abs(bt.length - rt.length)
                val posDiff = kotlin.math.abs(bi.toDouble() / blockTexts.size - ri.toDouble() / regions.size)
                val better = sim > bestSim ||
                    (sim == bestSim && (lenDiff < bestLen || (lenDiff == bestLen && posDiff < bestPos)))
                if (better) { best = ri; bestSim = sim; bestLen = lenDiff; bestPos = posDiff }
            }
            if (best >= 0 && bestSim > 0.15) result[bi] = best
        }
        return result
    }
}

data class PageText(val transcription: String, val translation: String, val summary: String) {
    val isEmpty get() = transcription.isBlank() && translation.isBlank() && summary.isBlank()
}

/** A box in page-relative coordinates (0..1). */
data class NBox(val top: Float, val left: Float, val bottom: Float, val right: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width * height
}

/** One detected text region on a page; [boxes] has >1 entry when the text was split across areas. */
data class TextRegion(val index: Int, val text: String, val boxes: List<NBox>)
