package app.gloss.learn

/** One sense of a dictionary word: its parts of speech, usage notes and English glosses. */
data class Sense(val pos: List<String>, val notes: List<String>, val glosses: String)

/** A JMdict entry. [rank] is lower for everyday words (99 when JMdict marks no frequency). */
data class Word(
    val id: Long,
    val kanji: List<String>,
    val kana: List<String>,
    val senses: List<Sense>,
    val rank: Int,
) {
    val common: Boolean get() = rank < 99
    val headword: String get() = kanji.firstOrNull() ?: kana.first()
    val reading: String get() = kana.first()
    val meaning: String get() = senses.firstOrNull()?.glosses.orEmpty()
    /** Particles, copulas and helper verbs: real words, but not ones worth a card. */
    val isGrammar: Boolean get() = senses.firstOrNull()?.pos.orEmpty().any { it == "prt" || it == "cop" || it.startsWith("aux") }
    /** Has no kanji spelling, or JMdict says it's usually written in kana anyway. */
    val writtenInKana: Boolean get() = kanji.isEmpty() || isGrammar || senses.any { "uk" in it.notes }
}

/** A kanji from KANJIDIC2, with its KanjiVG stroke paths (109×109 units) in drawing order. */
data class Kanji(
    val ch: String,
    val strokes: Int?,
    val grade: Int?,
    val jlpt: Int?,
    val onyomi: String,
    val kunyomi: String,
    val meanings: String,
    val paths: List<String>,
)

/** A stretch of text: a dictionary word (possibly inflected) or a character nothing matched. */
data class Token(
    val surface: String,
    val word: Word?,
    /** The dictionary form the surface was traced back to, e.g. 食べる for 食べなかった. */
    val base: String,
    /** How the surface is read, in hiragana; empty when it has no kanji to read. */
    val reading: String,
) {
    /** Kanji runs paired with their readings, for furigana: (text, ruby or null). */
    val ruby: List<Pair<String, String?>> get() = Furigana.align(surface, reading)
}

/** Splits Japanese text into dictionary words; pure, so tests can feed it any word list. */
object Tokenizer {
    private const val MAX_WORD = 12

    /** The matching itself, separate from the database so tests can feed it a word list. */
    fun tokenize(text: String, lookup: (String) -> List<Word>): List<Token> {
        val out = ArrayList<Token>()
        val plain = StringBuilder()
        fun flushPlain() { if (plain.isNotEmpty()) { out += Token(plain.toString(), null, plain.toString(), ""); plain.clear() } }
        var i = 0
        while (i < text.length) {
            if (!Kana.isJapanese(text[i])) { plain.append(text[i]); i++; continue }
            flushPlain()
            var end = i
            while (end < text.length && Kana.isJapanese(text[end]) && end - i < MAX_WORD) end++
            // Where this stretch of hiragana ends: a kana word that fills it is trustworthy.
            var kanaEnd = i
            while (kanaEnd < text.length && Kana.isHiragana(text[kanaEnd])) kanaEnd++
            var kataEnd = i
            while (kataEnd < text.length && Kana.isKatakana(text[kataEnd])) kataEnd++
            var hit: Token? = null
            for (len in (end - i) downTo 1) {
                val s = text.substring(i, i + len)
                hit = match(s, lookup, wholeRun = i + len == kanaEnd || i + len == kataEnd)
                if (hit != null) break
            }
            if (hit != null) { out += hit; i += hit.surface.length }
            else if (Kana.isKatakana(text[i])) {
                // An unknown katakana run is a name or a loanword the dictionary lacks: keep it whole.
                var j = i + 1
                while (j < text.length && Kana.isKatakana(text[j])) j++
                val s = text.substring(i, j)
                out += Token(s, null, s, "")
                i = j
            } else { out += Token(text[i].toString(), null, text[i].toString(), ""); i++ }
        }
        flushPlain()
        return out
    }

    private fun match(s: String, lookup: (String) -> List<Word>, wholeRun: Boolean): Token? {
        val hiraganaOnly = s.all { Kana.isHiragana(it) }
        val katakanaOnly = s.all { Kana.isKatakana(it) }
        var exact: Token? = null
        for (cand in Deinflect.candidates(s)) {
            var words = lookup(cand.term).filter { Deinflect.fits(it, cand.types) }
            // Words written in hiragana are mostly ones usually written that way (particles, よく,
            // まとめる). A short hiragana run that only spells a kanji word, like の for 野 or
            // してい for 指定, is misread; a long one (おもしろい) is a word spelled out for kids.
            if (hiraganaOnly) words = words.filter { it.writtenInKana || s.length >= 4 }.sortedByDescending { it.writtenInKana }
            // A run of kana can spell almost anything; only trust rare kana words that cover the
            // whole run, so particles don't get swallowed into nonsense. Katakana scraps of an
            // unknown name (the ファ of ファミコン) aren't words either.
            if ((hiraganaOnly || katakanaOnly) && s.length > 1 && !wholeRun) words = words.filter { it.common }
            if (katakanaOnly && !wholeRun && s.length < 3) words = emptyList()
            // A lone hiragana is nearly always a particle.
            if (hiraganaOnly && s.length == 1) words = words.sortedByDescending { it.isGrammar }
            val word = words.firstOrNull() ?: continue
            val token = Token(s, word, cand.term, readingOf(s, word, cand.term))
            if (cand.term != s) {
                // An inflected verb beats a rarer word that happens to be spelled the same (して
                // is する's te-form far more often than 仕手, "doer").
                return if (exact != null && !(hiraganaOnly && word.common) && exact.word!!.rank < word.rank) exact else token
            }
            if (exact == null) exact = token
        }
        return exact
    }

    /** Reads [surface] by taking the word's reading of [base] and swapping the kana ending. */
    private fun readingOf(surface: String, word: Word?, base: String): String {
        if (surface.none { Kana.isKanji(it) }) return ""
        word ?: return ""
        val baseReading = Kana.toHiragana(word.kana.first())
        var p = 0
        while (p < surface.length && p < base.length && surface[p] == base[p]) p++
        val baseTail = Kana.toHiragana(base.substring(p))
        val surfaceTail = Kana.toHiragana(surface.substring(p))
        if (!baseReading.endsWith(baseTail)) return baseReading
        // 来る is read き or こ before most endings, not く.
        if (base.endsWith("来る") && p == base.length - 1 && surfaceTail.isNotEmpty()) {
            val stem = when (surfaceTail.first()) { 'ま', 'た', 'て' -> "き"; 'な', 'よ', 'ら', 'さ', 'い' -> "こ"; else -> "く" }
            return baseReading.dropLast(2) + stem + surfaceTail
        }
        return baseReading.dropLast(baseTail.length) + surfaceTail
    }
}

object Kana {
    fun isKanji(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿' || c == '々' || c == '〆' || c == 'ヶ'
    fun isHiragana(c: Char): Boolean = c in 'ぁ'..'ゟ'
    fun isKatakana(c: Char): Boolean = c in 'ァ'..'ヺ' || c == 'ー'
    fun isJapanese(c: Char): Boolean = isKanji(c) || isHiragana(c) || isKatakana(c) || c == 'ー'

    fun toHiragana(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in 'ァ'..'ヶ') (c - 0x60) else c)
    }
}

/** Pairs each run of kanji with the part of the reading that belongs to it. */
object Furigana {
    fun align(surface: String, reading: String): List<Pair<String, String?>> {
        if (reading.isEmpty() || surface.none { Kana.isKanji(it) }) return listOf(surface to null)
        // Runs of kanji and of kana, alternating.
        val runs = ArrayList<Pair<String, Boolean>>()
        for (c in surface) {
            val k = Kana.isKanji(c)
            if (runs.isNotEmpty() && runs.last().second == k) runs[runs.size - 1] = (runs.last().first + c) to k
            else runs += c.toString() to k
        }
        val pattern = runs.joinToString("", "^", "$") { (t, k) -> if (k) "(.+?)" else Regex.escape(Kana.toHiragana(t)) }
        val m = Regex(pattern).find(reading) ?: return listOf(surface to reading)
        var g = 1
        return runs.map { (t, k) -> if (k) t to m.groupValues[g++] else t to null }
    }
}
