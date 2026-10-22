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
    /** Someone's name from JMnedict rather than a word. */
    val isName: Boolean get() = senses.firstOrNull()?.pos.orEmpty().any { it.startsWith("nm-") }
    /** Made up on the spot (a name, a bit of grammar), so it can't be looked up again or saved. */
    val isBuiltIn: Boolean get() = id < 0
}

/** One way a spelling can be read as a name, from JMnedict. */
data class Name(
    val reading: String,
    /** s surname, g given, f female given, m male given, p a real person, pl also a place. */
    val types: List<String>,
    /** Only real people have one ("Shigeru Miyamoto (1952.11.16-), game designer"). */
    val gloss: String,
) {
    fun asWord(form: String, romaji: String = Romaji.of(reading)): Word = Word(
        id = -1,
        kanji = listOf(form),
        kana = listOf(reading),
        senses = listOf(Sense(types.filter { it != "pl" }.map { "nm-$it" }, emptyList(), gloss.ifEmpty { romaji.replaceFirstChar { it.uppercase() } })),
        rank = 200,
    )
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

/**
 * Splits Japanese text into dictionary words. Every way of cutting a stretch of Japanese into
 * known words is weighed at once and the cheapest wins, where everyday words, fewer pieces and
 * plain forms cost less. Cutting greedily from the left instead lets one word steal the start of
 * the next: 楽しみたくさん would read as "want to enjoy" + "Mr." rather than 楽しみ + たくさん.
 * Pure, so tests can feed it any word list.
 */
object Tokenizer {
    private const val MAX_WORD = 12
    private const val MAX_NAME = 5
    private const val NO_HINT_NAME = 24.0
    private const val HINT_NAME = 9.0

    /** Grammar JMdict lacks, or files under a misleading first sense (its な is "don't!"). */
    private val grammar: Map<String, Word> = listOf(
        Triple("な", "cop", "links a description to what it describes, as in 元気な子, a lively kid"),
        Triple("でした", "cop", "was; were (polite past of です)"),
        Triple("でしょう", "cop", "probably; I suppose; right? (polite)"),
        Triple("でしょ", "cop", "right?; surely (casual)"),
    ).mapIndexed { n, (form, pos, gloss) ->
        form to Word(-100L - n, emptyList(), listOf(form), listOf(Sense(listOf(pos), emptyList(), gloss)), 1)
    }.toMap()

    /** 3月 is March: JMdict has 月 as moon and month, but not the がつ of dates. */
    private val month = Word(-200, listOf("月"), listOf("がつ"), listOf(Sense(listOf("ctr"), emptyList(), "month of the year (3月 is March)")), 1)

    /** How counters read after a number, where the dictionary's first reading is another word's (本 is ほん, not もと). */
    private val counters = mapOf(
        "本" to "ほん", "枚" to "まい", "回" to "かい", "人" to "にん", "個" to "こ", "匹" to "ひき", "冊" to "さつ",
        "台" to "だい", "面" to "めん", "話" to "わ", "巻" to "かん", "号" to "ごう", "位" to "い", "点" to "てん",
        "円" to "えん", "年" to "ねん", "時" to "じ", "分" to "ふん", "秒" to "びょう", "歳" to "さい", "才" to "さい",
        "階" to "かい", "度" to "ど", "部" to "ぶ", "名" to "めい", "種" to "しゅ", "体" to "たい", "発" to "はつ",
        "番" to "ばん", "倍" to "ばい", "社" to "しゃ", "作" to "さく", "曲" to "きょく", "型" to "がた", "日" to "にち",
        "代" to "だい", "人目" to "にんめ", "機" to "き", "色" to "しょく", "件" to "けん", "週" to "しゅう",
    )

    private val honorifics = listOf("さん", "くん", "ちゃん", "さま", "様", "氏", "君", "先生", "選手", "監督")

    /** Whole-word suffixes that only follow a noun or a name: after kana they're misreads. */
    private fun isSuffix(w: Word) = w.senses.firstOrNull()?.pos.orEmpty().let { p -> "suf" in p || "n-suf" in p } &&
        w.senses.none { s -> s.pos.any { it == "n" || it.startsWith("v") || it.startsWith("adj") } }

    /** A step from one place in the text to a later one, with the tokens it reads there. */
    private class Edge(val end: Int, val cost: Double, val tokens: List<Token>)

    /**
     * [lookup] gives the words spelled exactly so, [names] the ways a spelling reads as a name,
     * and [hint] is the English translation, whose capitalised words tell real names apart.
     */
    fun tokenize(
        text: String,
        lookup: (String) -> List<Word>,
        names: (String) -> List<Name> = { emptyList() },
        hint: String = "",
    ): List<Token> {
        val hintNames = Romaji.namesIn(hint)
        val out = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            if (!Kana.isJapanese(text[i])) {
                var j = i
                while (j < text.length && !Kana.isJapanese(text[j])) j++
                out += Token(text.substring(i, j), null, text.substring(i, j), "")
                i = j
                continue
            }
            var j = i
            while (j < text.length && Kana.isJapanese(text[j])) j++
            out += segment(text, i, j, lookup, names, hintNames)
            i = j
        }
        return out
    }

    /** The cheapest reading of text[from, to), a stretch with no punctuation or Latin in it. */
    private fun segment(
        text: String, from: Int, to: Int,
        lookup: (String) -> List<Word>, names: (String) -> List<Name>, hintNames: Set<String>,
    ): List<Token> {
        val n = to - from
        val best = DoubleArray(n + 1) { Double.MAX_VALUE }
        val back = arrayOfNulls<Pair<Int, Edge>>(n + 1)
        best[0] = 0.0
        val nameCache = HashMap<String, List<Name>>()
        val cachedNames = { s: String -> nameCache.getOrPut(s) { names(s) } }
        for (i in 0 until n) {
            if (best[i] == Double.MAX_VALUE) continue
            for (e in edgesAt(text, from + i, to, lookup, cachedNames, hintNames)) {
                val k = e.end - from
                val c = best[i] + e.cost
                if (c < best[k] - 1e-9) { best[k] = c; back[k] = i to e }
            }
        }
        val out = ArrayList<Token>()
        var k = n
        while (k > 0) {
            val (i, e) = back[k]!!
            out.addAll(0, e.tokens)
            k = i
        }
        return out
    }

    private fun edgesAt(
        text: String, i: Int, to: Int,
        lookup: (String) -> List<Word>, names: (String) -> List<Name>, hintNames: Set<String>,
    ): List<Edge> {
        val edges = ArrayList<Edge>()
        val c = text[i]
        val prev = if (i > 0) text[i - 1] else ' '
        fun runEnd(test: (Char) -> Boolean): Int { var j = i; while (j < to && test(text[j])) j++; return j }
        val kanaEnd = runEnd { Kana.isHiragana(it) }
        val kataEnd = runEnd { Kana.isKatakana(it) }
        val kanjiEnd = runEnd { Kana.isKanji(it) }

        // Starting a word here would cut a known compound in two (元|気になる for 元気|に|なる).
        val splits = Kana.isKanji(prev) && Kana.isKanji(c) && lookup(text.substring(i - 1, i + 1)).any { !it.isName }
        val splitCost = if (splits) 5.0 else 0.0

        // Dictionary words starting here, inflected or not.
        for (len in 1..minOf(MAX_WORD, to - i)) {
            val s = text.substring(i, i + len)
            val atRunEnd = i + len == kanaEnd || i + len == kataEnd
            word(s, lookup, wholeRun = atRunEnd, prev = prev)?.let { edges += Edge(i + len, it.cost + splitCost, listOf(it.token)) }
        }

        // Names: spelled in kanji, or in kana when the translation names them.
        if (Kana.isKanji(c)) edges += nameEdges(text, i, kanjiEnd, names, hintNames)
        if (hintNames.isNotEmpty() && !Kana.isKanji(c)) {
            val end = if (Kana.isHiragana(c)) kanaEnd else kataEnd
            val keys = hintNames.map(Romaji::key)
            for (j in i + 2..end) {
                val s = text.substring(i, j)
                if (Romaji.key(Romaji.of(s)) in keys) {
                    val w = Name(Kana.toHiragana(s), listOf("g"), "").asWord(s)
                    edges += Edge(j, HINT_NAME, listOf(Token(s, w.copy(kanji = emptyList()), s, "")))
                }
            }
        }

        // Nothing known: an unknown katakana run stays whole (a name or a loanword the dictionary
        // lacks); anything else goes a character at a time, expensively.
        if (Kana.isKatakana(c) && !Kana.isKatakana(prev)) {
            val s = text.substring(i, kataEnd)
            edges += Edge(kataEnd, 28.0, listOf(Token(s, null, s, "")))
        }
        edges += Edge(i + 1, 30.0, listOf(Token(c.toString(), null, c.toString(), "")))
        return edges
    }

    private class Scored(val token: Token, val cost: Double)

    /** The best dictionary reading of exactly [s], with what it costs, or null if there's none. */
    private fun word(s: String, lookup: (String) -> List<Word>, wholeRun: Boolean, prev: Char): Scored? {
        val hiraganaOnly = s.all { Kana.isHiragana(it) || it == 'ー' }
        val katakanaOnly = s.all { Kana.isKatakana(it) }
        // No word starts with a small kana, a long-vowel mark or ん.
        if (s[0] in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮーんン" && s != "ん") return null
        if (katakanaOnly && !wholeRun && s.length < 3) return null
        val afterKana = Kana.isHiragana(prev)
        val afterNumber = prev.isDigit() || prev in "一二三四五六七八九十百千万何数"
        var best: Scored? = null
        fun consider(w: Word, base: String, cost: Double) {
            if (best == null || cost < best!!.cost - 1e-9) best = Scored(Token(s, w, base, readingOf(s, w, base)), cost)
        }
        grammar[s]?.let { g ->
            // な after a verb is "don't"; anywhere else it's the な of 元気な.
            if (s != "な" || prev !in "うくぐすつぬぶむる") consider(g, s, 10.2)
        }
        if (s == "月" && afterNumber) consider(month, s, 9.0)
        // Stretched kana (がんばりまーす, いいですよー) is the plain word said with feeling.
        val plain = if (hiraganaOnly && 'ー' in s && s[0] != 'ー') s.replace("ー", "") else s
        for (cand in Deinflect.candidates(plain)) {
            var words = lookup(cand.term).filter { !it.isName && Deinflect.fits(it, cand.types) }
            for (w in words) {
                var cost = when {
                    // Particles and the like are as everyday as words get, whatever JMdict's rank.
                    w.isGrammar -> 10.3
                    // JMdict's frequency bands are coarse (行く sits below 行う), so the number of
                    // senses, a fair sign of a word in wide use, counts for more.
                    w.common -> 10.6 - 0.15 * minOf(w.senses.size, 6) + w.rank / 100.0
                    else -> 16.0
                }
                if (cand.steps > 0) cost += 1.0 + 0.5 * cand.steps
                // A rare verb is a poor excuse for an inflection (同じよう isn't 同じる, "to agree").
                if (cand.steps > 0 && !w.common) cost += 4.0
                val exp = w.senses.firstOrNull()?.pos.orEmpty().contains("exp")
                // A run of kana can spell almost anything: a rare kana word that stops short of
                // the run's end is likelier a misreading than a word.
                if ((hiraganaOnly || katakanaOnly) && s.length > 1 && !wholeRun && !w.common) cost += 7.0
                // Rare words spelled in hiragana are mostly coincidences (といい isn't "in terms of
                // both", it's と + いい).
                if (hiraganaOnly && !w.common) cost += if (exp) 7.0 else 5.0
                // Words written in hiragana are mostly ones usually written that way (particles,
                // よく, まとめる). A short run that spells a kanji word, like の for 野 or してい for
                // 指定, is usually something else; a longer one (おもしろい, ぼく in a magazine for
                // kids) is a word spelled out. Honorifics (くん for 君) are fine.
                if (hiraganaOnly && !w.writtenInKana && !isSuffix(w)) cost += when (s.length) { 1, 2 -> 6.0; 3 -> 4.0; else -> 2.0 }
                // Kanji written where the word is usually kana is likelier the kanji word (彼 is かれ).
                if (!hiraganaOnly && s.any(Kana::isKanji) && w.senses.all { "uk" in it.notes }) cost += 2.0
                // In hiragana, an inflected everyday verb is the likely reading (して is する, not 仕手),
                // unless the whole thing is a set phrase (として).
                if (hiraganaOnly && cand.steps > 0 && w.common) cost -= 2.0
                if (hiraganaOnly && cand.steps == 0 && w.common && exp) cost -= 1.5
                // Kana pronouns (ぼく, きみ) are far commoner than the nouns they could spell.
                if (hiraganaOnly && w.senses.firstOrNull()?.pos.orEmpty().contains("pn")) cost -= 1.5
                // A lone hiragana is nearly always a particle (で is "at", not 出, "coming out").
                if (hiraganaOnly && s.length == 1 && !w.isGrammar) cost += 8.0
                // さん, たち and the like follow nouns and names, not verbs (楽しみたく + さん).
                if (afterKana && isSuffix(w)) cost += 8.0
                // After a number comes a counter: 10本 is ten of something, not 本 "origin".
                var shown = w
                if (afterNumber || prev in 'A'..'Z') {
                    val counting = w.senses.filter { se -> "ctr" in se.pos || "suf" in se.pos || "n-suf" in se.pos }
                    val said = counters[s]
                    if (counting.isNotEmpty()) {
                        cost -= 4.0
                        // Show the counting sense first: 日 after 25 is "day", not "Sunday".
                        shown = w.copy(senses = counting + (w.senses - counting.toSet()))
                        if (said != null) {
                            if (said in w.kana) { cost -= 3.0; shown = shown.copy(kana = listOf(said) + (w.kana - said)) } else cost += 3.0
                        }
                    } else if (Kana.isKanji(s[0])) cost += 8.0
                }
                // A prefix fastens to the kanji after it (超人気 is 超 + 人気, not 超人 + 気).
                if (w.senses.firstOrNull()?.pos.orEmpty().let { "pref" in it || "n-pref" in it } && s.length == 1) cost -= 2.0
                consider(shown, cand.term, cost)
            }
        }
        return best
    }

    /**
     * Ways to read kanji starting at [i] as names. The translation is the best witness: when it
     * says "Yukiho Hagiwara", 雪歩 and 萩原 are names and that's how they're read. Without it, a
     * surname then a given name filling the whole kanji run (落合祐里香), a known person (宮本茂)
     * or an honorific after (田中くん) make a name likelier than whatever words the kanji spell.
     */
    private fun nameEdges(text: String, i: Int, kanjiEnd: Int, names: (String) -> List<Name>, hintNames: Set<String>): List<Edge> {
        val edges = ArrayList<Edge>()
        val hintKeys = hintNames.map(Romaji::key).toSet()
        val hintLoose = hintNames.associateBy(Romaji::looseKey)
        fun after(j: Int) = text.substring(j)
        fun honorificAfter(j: Int) = honorifics.any { after(j).startsWith(it) }

        /** The name reading of [s] the translation vouches for, if any. */
        fun hinted(s: String): Token? {
            if (hintNames.isEmpty()) return null
            for (nm in names(s)) {
                if (Romaji.key(Romaji.of(nm.reading)) in hintKeys) return Token(s, nm.asWord(s), s, nm.reading)
            }
            for (nm in names(s)) {
                // Same name, voiced differently in the translation: read it the translation's way.
                val said = hintLoose[Romaji.looseKey(Romaji.of(nm.reading))] ?: continue
                val kana = Romaji.toKana(said) ?: continue
                return Token(s, nm.copy(reading = kana).asWord(s, said), s, kana)
            }
            return null
        }

        /** The likeliest reading of [s] as a name of one of [types], judged by how widely it's used. */
        fun likeliest(s: String, types: Set<String>): Token? = names(s)
            .filter { nm -> nm.types.any { it in types } }
            .maxByOrNull { it.types.size }
            ?.let { Token(s, it.asWord(s), s, it.reading) }

        for (len in 1..minOf(MAX_NAME, kanjiEnd - i)) {
            val s = text.substring(i, i + len)
            hinted(s)?.let { edges += Edge(i + len, HINT_NAME, listOf(it)) }
            val person = setOf("s", "g", "f", "m", "p")
            likeliest(s, person)?.let { t ->
                val cost = when {
                    "nm-p" in t.word!!.senses[0].pos && len >= 3 && i + len == kanjiEnd -> 12.0
                    honorificAfter(i + len) -> 14.0
                    else -> NO_HINT_NAME
                }
                edges += Edge(i + len, cost, listOf(t))
            }
            // Surname + given name, together filling the kanji run.
            if (i + len < kanjiEnd) {
                val surname = likeliest(s, setOf("s")) ?: continue
                for (glen in 1..minOf(MAX_NAME, kanjiEnd - i - len)) {
                    if (i + len + glen != kanjiEnd || len + glen < 3) continue
                    val g = text.substring(i + len, kanjiEnd)
                    val given = likeliest(g, setOf("g", "f", "m")) ?: continue
                    edges += Edge(kanjiEnd, NO_HINT_NAME, listOf(surname, given))
                }
            }
        }
        return edges
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
