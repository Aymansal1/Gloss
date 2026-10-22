package app.gloss.learn

import java.text.Normalizer

/**
 * Hepburn romaji both ways, for matching names in the Japanese against the English translation
 * ("Yukiho Hagiwara" says 萩原雪歩 is a name, and how it's read).
 */
object Romaji {
    private val syllables: Map<String, String> = buildMap {
        val rows = """
            あ a い i う u え e お o か ka き ki く ku け ke こ ko さ sa し shi す su せ se そ so
            た ta ち chi つ tsu て te と to な na に ni ぬ nu ね ne の no は ha ひ hi ふ fu へ he ほ ho
            ま ma み mi む mu め me も mo や ya ゆ yu よ yo ら ra り ri る ru れ re ろ ro わ wa ゐ i ゑ e を o ん n
            が ga ぎ gi ぐ gu げ ge ご go ざ za じ ji ず zu ぜ ze ぞ zo だ da ぢ ji づ zu で de ど do
            ば ba び bi ぶ bu べ be ぼ bo ぱ pa ぴ pi ぷ pu ぺ pe ぽ po ゔ vu
            きゃ kya きゅ kyu きょ kyo しゃ sha しゅ shu しょ sho ちゃ cha ちゅ chu ちょ cho にゃ nya にゅ nyu にょ nyo
            ひゃ hya ひゅ hyu ひょ hyo みゃ mya みゅ myu みょ myo りゃ rya りゅ ryu りょ ryo ぎゃ gya ぎゅ gyu ぎょ gyo
            じゃ ja じゅ ju じょ jo びゃ bya びゅ byu びょ byo ぴゃ pya ぴゅ pyu ぴょ pyo
            ふぁ fa ふぃ fi ふぇ fe ふぉ fo てぃ ti でぃ di とぅ tu どぅ du しぇ she じぇ je ちぇ che うぃ wi うぇ we うぉ wo
            ゔぁ va ゔぃ vi ゔぇ ve ゔぉ vo ぁ a ぃ i ぅ u ぇ e ぉ o ゃ ya ゅ yu ょ yo
        """.trim().split(Regex("\\s+"))
        for (i in rows.indices step 2) put(rows[i], rows[i + 1])
    }

    /** Kana to Hepburn: ゆきほ → yukiho, おちあい → ochiai, ちはや → chihaya. */
    fun of(kana: String): String {
        val s = Kana.toHiragana(kana)
        val out = StringBuilder()
        var double = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == 'っ') { double = true; i++; continue }
            if (c == 'ー') { out.lastOrNull()?.let { out.append(it) }; i++; continue }
            val two = if (i + 1 < s.length) syllables[s.substring(i, i + 2)] else null
            val r = two ?: syllables[c.toString()] ?: c.toString()
            if (double && r.isNotEmpty() && r[0] !in "aiueon") out.append(if (r.startsWith("ch")) 't' else r[0])
            double = false
            out.append(r)
            i += if (two != null) 2 else 1
        }
        return out.toString()
    }

    private val reverse: Map<String, String> by lazy {
        buildMap {
            // Later entries win, so the kana a romaji spelling most likely means goes last.
            for ((k, v) in syllables) if (k.length == 1 || k[1] !in "ぁぃぅぇぉ" || v !in this) put(v, k)
            put("i", "い"); put("e", "え"); put("o", "お"); put("ji", "じ"); put("zu", "ず"); put("n", "ん")
            put("si", "し"); put("ti", "ち"); put("tu", "つ"); put("hu", "ふ"); put("zi", "じ"); put("di", "ぢ")
        }
    }

    /** Romaji back to hiragana, or null if it isn't romaji: Hagiwara → はぎわら. */
    fun toKana(romaji: String): String? {
        val s = romaji.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c !in 'a'..'z') return null
            if (i + 1 < s.length && c == s[i + 1] && c !in "aiueon") { out.append('っ'); i++; continue }
            if (c == 't' && s.startsWith("tch", i)) { out.append('っ'); i++; continue }
            if (c == 'n' && (i + 1 == s.length || s[i + 1] !in "aiueoy")) { out.append('ん'); i++; continue }
            if (c == 'm' && i + 1 < s.length && s[i + 1] in "bpm") { out.append('ん'); i++; continue }
            var hit: String? = null
            for (len in 3 downTo 1) {
                if (i + len > s.length) continue
                reverse[s.substring(i, i + len)]?.let { hit = it; i += len }
                if (hit != null) break
            }
            out.append(hit ?: return null)
        }
        return out.toString()
    }

    /** The capitalised words of an English translation, lower-cased and stripped of macrons. */
    fun namesIn(english: String): Set<String> {
        val plain = Normalizer.normalize(english, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return Regex("[A-Za-z]+").findAll(plain).map { it.value }
            .filter { it.length >= 2 && it[0].isUpperCase() && !(it.length > 1 && it.all { c -> c.isUpperCase() }) }
            .map { it.lowercase() }.toSet()
    }

    /** Spelling-insensitive: long vowels folded (Yūji, Yuuji, Yuji), ん before b/p/m as n. */
    fun key(romaji: String): String = romaji.lowercase()
        .replace(Regex("([aiueo])h(?![aiueoy])"), "$1")
        .replace("ou", "o").replace("oo", "o").replace("uu", "u").replace("aa", "a").replace("ii", "i").replace("ee", "e")
        .replace(Regex("m(?=[bpm])"), "n")

    /** Also blind to voicing, for readings that shift in a name (萩原: はぎはら, said Hagiwara). */
    fun looseKey(romaji: String): String = key(romaji)
        .replace("j", "sh").replace('g', 'k').replace('z', 's').replace('d', 't')
        .replace('b', 'h').replace('p', 'h').replace('w', 'h').replace('f', 'h')
}
