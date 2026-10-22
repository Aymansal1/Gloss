package app.gloss.learn

/**
 * Traces inflected verbs and adjectives back to the dictionary form: 食べなかった → 食べない →
 * 食べる. Each rule swaps an ending and says what kind of word the result must be; a chain
 * stops when the result is a word the dictionary knows with a matching part of speech.
 */
object Deinflect {
    const val V1 = 1
    const val V5 = 2
    const val VK = 4
    const val VS = 8
    const val ADJ = 16
    /** Polite forms in ます, still to be taken back to the plain verb. */
    const val MASU = 32
    /** Plain past in た/だ, which たら and たり lead back to. */
    const val PAST = 64
    /** A te-form with いる after it (していた), still to be taken back to the te-form. */
    const val TE = 128
    const val ANY = 0x7fffffff
    private const val VERB = V1 or V5 or VK or VS

    /** A form [s] could be an inflection of, [steps] rules back from it. */
    data class Candidate(val term: String, val types: Int, val steps: Int = 0)

    private class Rule(val from: String, val to: String, val typesIn: Int, val typesOut: Int)

    private val rules: Map<String, List<Rule>> by lazy { build().groupBy { it.from } }
    private val longest: Int by lazy { rules.keys.maxOf { it.length } }

    /** [s] itself, then every form it could be an inflection of, nearest first. */
    fun candidates(s: String): List<Candidate> {
        val out = ArrayList<Candidate>()
        val seen = HashSet<Pair<String, Int>>()
        val queue = ArrayDeque<Candidate>()
        queue += Candidate(s, ANY)
        while (queue.isNotEmpty() && out.size < 64) {
            val c = queue.removeFirst()
            if (!seen.add(c.term to c.types)) continue
            out += c
            for (len in minOf(longest, c.term.length) downTo 1) {
                val tail = c.term.takeLast(len)
                for (r in rules[tail].orEmpty()) {
                    if (c.types and r.typesIn == 0) continue
                    val stem = c.term.dropLast(len)
                    if (stem.isEmpty() && r.to.isEmpty()) continue
                    queue += Candidate(stem + r.to, r.typesOut, c.steps + 1)
                }
            }
        }
        return out
    }

    /** Whether [word] can be the kind of word a deinflected [types] candidate needs. */
    fun fits(word: Word, types: Int): Boolean {
        if (types == ANY) return true
        val pos = word.senses.flatMap { it.pos }
        var have = 0
        for (p in pos) {
            have = have or when {
                p == "v1" || p == "v1-s" || p == "vz" -> V1
                p.startsWith("v5") -> V5
                p == "vk" -> VK
                p == "vs-i" || p == "vs-s" -> VS
                p == "adj-i" || p == "adj-ix" -> ADJ
                else -> 0
            }
        }
        return have and types != 0
    }

    private fun build(): List<Rule> {
        val r = ArrayList<Rule>()
        fun add(from: String, to: String, inT: Int, outT: Int) { r += Rule(from, to, inT, outT) }

        // Godan rows: dictionary ending, then the i, a, e and o stems, then te and ta.
        val godan = listOf(
            listOf("う", "い", "わ", "え", "お", "って", "った"),
            listOf("く", "き", "か", "け", "こ", "いて", "いた"),
            listOf("ぐ", "ぎ", "が", "げ", "ご", "いで", "いだ"),
            listOf("す", "し", "さ", "せ", "そ", "して", "した"),
            listOf("つ", "ち", "た", "て", "と", "って", "った"),
            listOf("ぬ", "に", "な", "ね", "の", "んで", "んだ"),
            listOf("ぶ", "び", "ば", "べ", "ぼ", "んで", "んだ"),
            listOf("む", "み", "ま", "め", "も", "んで", "んだ"),
            listOf("る", "り", "ら", "れ", "ろ", "って", "った"),
        )
        for ((dict, i, a, e, o, te, ta) in godan.map { Row(it) }) {
            add(a + "ない", dict, ADJ, V5)
            add(a + "ず", dict, ANY, V5)
            add(a + "れる", dict, V1, V5)        // passive
            add(a + "せる", dict, V1, V5)        // causative
            add(i + "ます", dict, MASU, V5)
            add(i + "ながら", dict, ANY, V5)
            add(i + "たい", dict, ADJ, V5)
            add(e + "る", dict, V1, V5)          // potential
            add(e + "ば", dict, ANY, V5)
            add(o + "う", dict, ANY, V5)         // volitional
            add(te, dict, ANY, V5)
            add(ta, dict, PAST, V5)
        }
        // 行く bends the rule: 行って, not 行いて.
        for (k in listOf("行", "い")) { add(k + "って", k + "く", ANY, V5); add(k + "った", k + "く", PAST, V5) }

        // Ichidan: the stem takes the ending directly.
        for ((suffix, inT) in listOf(
            "ない" to ADJ, "ず" to ANY, "られる" to V1, "れる" to V1, "させる" to V1, "ます" to MASU,
            "たい" to ADJ, "れば" to ANY, "よう" to ANY, "て" to ANY, "た" to PAST, "ろ" to ANY, "ながら" to ANY,
        )) add(suffix, "る", inT, V1)

        // する and 来る, in kana and kanji.
        for ((stem, dict, t) in listOf(Triple("し", "する", VS), Triple("さ", "する", VS))) {
            if (stem == "し") {
                for ((suffix, inT) in listOf("ない" to ADJ, "ます" to MASU, "たい" to ADJ, "よう" to ANY, "て" to ANY, "た" to PAST, "ろ" to ANY, "ながら" to ANY)) add(stem + suffix, dict, inT, t)
            } else {
                for ((suffix, inT) in listOf("れる" to V1, "せる" to V1)) add(stem + suffix, dict, inT, t)
            }
        }
        add("すれば", "する", ANY, VS)
        for (k in listOf("来", "")) {
            val ko = if (k.isEmpty()) "こ" else k
            val ki = if (k.isEmpty()) "き" else k
            val ku = if (k.isEmpty()) "く" else k
            add(ko + "ない", ku + "る", ADJ, VK)
            add(ko + "られる", ku + "る", V1, VK)
            add(ko + "させる", ku + "る", V1, VK)
            add(ko + "よう", ku + "る", ANY, VK)
            add(ko + "い", ku + "る", ANY, VK)
            add(ki + "ます", ku + "る", MASU, VK)
            add(ki + "たい", ku + "る", ADJ, VK)
            add(ki + "て", ku + "る", ANY, VK)
            add(ki + "た", ku + "る", PAST, VK)
            add(ku + "れば", ku + "る", ANY, VK)
        }

        // している, してる and 読んでいた are the te-form plus いる: one word to look up, not three.
        for (t in listOf("て", "で")) { add(t + "いる", t, V1, TE); add(t + "る", t, V1, TE) }

        // Polite endings lead back to ます.
        for (s in listOf("ました", "ません", "ましょう", "まして")) add(s, "ます", ANY, MASU)
        add("ませんでした", "ません", ANY, MASU)
        // たら and たり are built on the past.
        for ((from, to) in listOf("たら" to "た", "だら" to "だ", "たり" to "た", "だり" to "だ")) add(from, to, ANY, PAST)

        // い-adjectives (ない behaves like one, which is how 食べなかった unwinds).
        add("かった", "い", PAST, ADJ)
        add("くない", "い", ADJ, ADJ)
        add("くて", "い", ANY, ADJ)
        add("く", "い", ANY, ADJ)
        add("ければ", "い", ANY, ADJ)
        add("さ", "い", ANY, ADJ)
        add("すぎる", "い", V1, ADJ)
        add("そう", "い", ANY, ADJ)
        add("かったら", "い", ANY, ADJ)
        add("くなかった", "い", ANY, ADJ)
        add("なかった", "ない", ANY, ADJ)
        add("なくて", "ない", ANY, ADJ)
        add("なければ", "ない", ANY, ADJ)
        return r
    }

    private data class Row(val dict: String, val i: String, val a: String, val e: String, val o: String, val te: String, val ta: String) {
        constructor(l: List<String>) : this(l[0], l[1], l[2], l[3], l[4], l[5], l[6])
    }
}
