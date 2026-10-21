package app.gloss.search

import java.text.Normalizer

/**
 * Pure text helpers for the search index (JVM-testable).
 *
 * Japanese has no spaces, so a word tokenizer sees a whole sentence as one "word" and
 * mid-sentence terms never match. We index Japanese runs as overlapping character
 * bigrams ("東京都" -> "東京 京都") and turn a Japanese query into the matching
 * bigram phrase, which finds the term anywhere in the text.
 */
object SearchText {

    fun isCjk(c: Char): Boolean = c in '぀'..'ヿ' || c in '㐀'..'䶿' ||
        c in '一'..'鿿' || c in '豈'..'﫿' || c == 'ー' || c in 'ｦ'..'ﾟ'

    fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()

    /** Text to store in the bigram column. */
    fun bigramIndexText(raw: String): String {
        val s = normalize(raw)
        val out = StringBuilder(s.length * 3)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                isCjk(c) -> {
                    var j = i
                    while (j < s.length && isCjk(s[j])) j++
                    appendBigrams(s.substring(i, j), out)
                    i = j
                }
                c.isLetterOrDigit() -> {
                    var j = i
                    while (j < s.length && s[j].isLetterOrDigit() && !isCjk(s[j])) j++
                    out.append(s, i, j).append(' ')
                    i = j
                }
                else -> i++
            }
        }
        return out.toString().trim()
    }

    private fun appendBigrams(run: String, out: StringBuilder) {
        if (run.length == 1) { out.append(run).append(' '); return }
        for (k in 0 until run.length - 1) out.append(run, k, k + 2).append(' ')
    }

    data class ParsedQuery(
        /** FTS4 MATCH expression, or null if there is nothing positive to search for. */
        val match: String?,
        /** Normalised plain terms, used for highlighting and snippets. */
        val highlightTerms: List<String>,
        /** Normalised terms that must NOT appear. */
        val excluded: List<String>,
    )

    /**
     * Supports: plain words (AND), "exact phrases", -exclude, prefix*.
     * Japanese terms are converted to bigram phrases on the jpb column.
     */
    fun parseQuery(input: String): ParsedQuery {
        val tokens = tokenize(input)
        val parts = ArrayList<String>()
        val highlight = ArrayList<String>()
        val excluded = ArrayList<String>()
        for ((rawTerm, quoted) in tokens) {
            var term = rawTerm
            val negative = !quoted && term.startsWith("-") && term.length > 1
            if (negative) term = term.substring(1)
            val prefix = !quoted && term.endsWith("*") && term.length > 1
            if (prefix) term = term.dropLast(1)
            val norm = normalize(term).trim()
            if (norm.isEmpty()) continue
            if (negative) { excluded += norm; continue }
            highlight += norm
            parts += ftsExpr(norm, prefix)
        }
        return ParsedQuery(parts.filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { null }, highlight, excluded)
    }

    private fun ftsExpr(norm: String, prefix: Boolean): String {
        val hasCjk = norm.any(::isCjk)
        if (!hasCjk) {
            val words = norm.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return ""
            return if (prefix && words.size == 1) "${words[0]}*" else "\"${words.joinToString(" ")}\""
        }
        // Note: FTS4 rejects column filters on quoted phrases (jpb:"a b"), so phrases are bare.
        // That's fine: bigram tokens of Japanese text only ever exist in the jpb column.
        val bigrams = bigramIndexText(norm).split(' ').filter { it.isNotEmpty() }
        if (bigrams.isEmpty()) return ""
        if (bigrams.size == 1) {
            // A single Japanese character: match any bigram that starts with it.
            return if (bigrams[0].length == 1) "${bigrams[0]}*" else bigrams[0]
        }
        return "\"${bigrams.joinToString(" ")}\""
    }

    private fun tokenize(input: String): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>()
        var i = 0
        val s = input.trim()
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '"' -> {
                    val end = s.indexOf('"', i + 1).let { if (it < 0) s.length else it }
                    s.substring(i + 1, end).trim().takeIf { it.isNotEmpty() }?.let { out += it to true }
                    i = end + 1
                }
                else -> {
                    var j = i
                    while (j < s.length && !s[j].isWhitespace() && s[j] != '"') j++
                    out += s.substring(i, j) to false
                    i = j
                }
            }
        }
        return out
    }

    /** A short excerpt of [text] around the first hit, with hit ranges for highlighting. */
    fun snippet(text: String, terms: List<String>, radius: Int = 70): Pair<String, List<IntRange>> {
        val flat = text.replace(Regex("[#*_`>|]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (flat.isEmpty()) return "" to emptyList()
        val lower = normalize(flat)
        val hitAt = terms.mapNotNull { t -> lower.indexOf(t).takeIf { it >= 0 } }.minOrNull()
        val start = if (hitAt == null) 0 else (hitAt - radius).coerceAtLeast(0)
        val end = ((hitAt ?: 0) + radius * 2).coerceAtMost(flat.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < flat.length) "…" else ""
        val body = flat.substring(start, end)
        val shown = prefix + body + suffix
        // Plain lowercase (not NFKC) so indices line up with the displayed text.
        val lowerShown = shown.lowercase()
        val ranges = ArrayList<IntRange>()
        for (t in terms) {
            if (t.isEmpty()) continue
            var from = 0
            while (true) {
                val k = lowerShown.indexOf(t, from)
                if (k < 0) break
                ranges += k until k + t.length
                from = k + t.length
            }
        }
        return shown to ranges
    }
}
