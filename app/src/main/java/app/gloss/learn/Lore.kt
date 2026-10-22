package app.gloss.learn

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A dated moment in Japanese game history, or a timeless one when [date] is null. */
data class Fact(val date: LocalDate?, val text: String)

/** Gaming history for Discover: what happened this week, years ago. */
object History {
    private fun d(s: String) = LocalDate.parse(s)

    val facts: List<Fact> = listOf(
        Fact(d("1983-07-15"), "Nintendo's Family Computer, the Famicom, went on sale in Japan. Sega's SG-1000 launched the very same day."),
        Fact(d("1985-09-13"), "Super Mario Bros. came out for the Famicom in Japan."),
        Fact(d("1986-02-21"), "The Famicom Disk System launched in Japan, with The Legend of Zelda as its first game."),
        Fact(d("1986-05-27"), "Enix released the first Dragon Quest for the Famicom."),
        Fact(d("1986-08-06"), "Metroid came out in Japan on the Famicom Disk System."),
        Fact(d("1987-01-26"), "Dragon Quest II came out for the Famicom."),
        Fact(d("1987-10-30"), "NEC's PC Engine launched in Japan."),
        Fact(d("1987-12-17"), "Capcom released Rockman, known abroad as Mega Man."),
        Fact(d("1987-12-18"), "Square released the first Final Fantasy."),
        Fact(d("1988-02-10"), "Dragon Quest III came out, and the queues for it made national news."),
        Fact(d("1988-10-29"), "Sega's Mega Drive launched in Japan."),
        Fact(d("1989-04-21"), "Nintendo's Game Boy went on sale in Japan."),
        Fact(d("1990-02-11"), "Dragon Quest IV came out for the Famicom."),
        Fact(d("1990-04-20"), "The first Fire Emblem came out for the Famicom."),
        Fact(d("1990-11-21"), "The Super Famicom launched in Japan, with Super Mario World alongside it."),
        Fact(d("1991-11-21"), "The Legend of Zelda: A Link to the Past came out for the Super Famicom."),
        Fact(d("1992-09-27"), "Dragon Quest V came out for the Super Famicom."),
        Fact(d("1994-04-02"), "Final Fantasy VI came out for the Super Famicom."),
        Fact(d("1994-11-22"), "Sega's Saturn launched in Japan."),
        Fact(d("1994-12-03"), "Sony's PlayStation launched in Japan."),
        Fact(d("1995-03-11"), "Chrono Trigger came out for the Super Famicom."),
        Fact(d("1996-02-27"), "Pokémon Red and Green came out for the Game Boy."),
        Fact(d("1996-06-23"), "The Nintendo 64 launched in Japan with Super Mario 64."),
        Fact(d("1997-01-31"), "Final Fantasy VII came out for the PlayStation."),
        Fact(d("1998-11-21"), "The Legend of Zelda: Ocarina of Time came out, later the first game to get a perfect 40 from Famitsu's Cross Review."),
        Fact(d("1998-11-27"), "Sega's Dreamcast launched in Japan."),
        Fact(null, "Famitsu's Cross Review has four critics each score a game out of 10. A perfect 40 is rare enough to make headlines."),
        Fact(null, "Cartridge games were sold by size: magazines advertised 8メガ or 16メガ, in megabits, as a selling point."),
        Fact(null, "Before battery saves, many games gave you a password to write down. Dragon Quest called it the spell of resurrection, 復活の呪文."),
        Fact(null, "Reader pages, 投稿, were a big part of these magazines: fan art, tips and jokes sent in by post."),
        Fact(null, "Magazines often bundled 付録, extras like posters, stickers or whole strategy booklets."),
        Fact(null, "裏技, secret tricks, were a staple: codes for extra lives, hidden stages and debug menus, printed for readers to try."),
        Fact(null, "Famicom Tsūshin, the magazine now called Famitsu, published its first issue in June 1986."),
    )

    /** The dated fact nearest to [today]'s day of the year within a week, else one for the day. */
    fun forDay(today: LocalDate): Pair<Fact, Boolean> {
        val near = facts.filter { it.date != null }
            .map { it to dayGap(it.date!!, today) }
            .filter { it.second <= 3 }
            .minByOrNull { it.second }
        if (near != null) return near.first to true
        return facts[Math.floorMod(today.toEpochDay(), facts.size.toLong()).toInt()] to false
    }

    /** Days between two dates' month and day, ignoring the year, wrapping round New Year. */
    fun dayGap(a: LocalDate, b: LocalDate): Int {
        val x = MonthDay.from(a).atYear(2001)
        val y = MonthDay.from(b).atYear(2001)
        val gap = abs(ChronoUnit.DAYS.between(x, y)).toInt()
        return minOf(gap, 365 - gap)
    }
}

/** A short, credited introduction to a magazine from English Wikipedia. */
data class WikiSummary(val title: String, val extract: String, val url: String)

/**
 * Looks magazines up on Wikipedia and remembers the answer (including "nothing found"), so each
 * magazine costs one search at most.
 */
class Wikipedia(private val http: OkHttpClient, private val cacheFile: File) {
    private val cache = HashMap<String, WikiSummary?>()
    private var loaded = false

    suspend fun summary(magazine: String): WikiSummary? = withContext(Dispatchers.IO) {
        synchronized(cache) {
            if (!loaded) { load(); loaded = true }
            if (cache.containsKey(magazine)) return@withContext cache[magazine]
        }
        val found = runCatching { fetch(magazine) }
            .onFailure { Log.w("Wikipedia", "Lookup failed for $magazine", it) }
            .getOrElse { return@withContext null } // Offline: don't remember the miss.
        synchronized(cache) { cache[magazine] = found; save() }
        found
    }

    private fun fetch(magazine: String): WikiSummary? {
        val search = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("list", "search")
            .addQueryParameter("srsearch", "$magazine Japanese video game magazine")
            .addQueryParameter("srlimit", "3").addQueryParameter("format", "json")
            .build()
        val hits = http.newCall(Request.Builder().url(search).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            JSONObject(r.body?.string().orEmpty()).getJSONObject("query").getJSONArray("search")
        }
        for (i in 0 until hits.length()) {
            val title = hits.getJSONObject(i).getString("title")
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/".toHttpUrl().newBuilder().addPathSegment(title.replace(' ', '_')).build()
            val o = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                JSONObject(r.body?.string().orEmpty())
            } ?: continue
            val extract = o.optString("extract")
            // Only a page that is about a magazine counts; a search can drift to a game or a word.
            if (o.optString("type") == "standard" && extract.contains("magazine", ignoreCase = true) && relates(magazine, title, extract)) {
                val page = o.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page")
                    ?: "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}"
                return WikiSummary(title, extract, page)
            }
        }
        return null
    }

    /** Whether the page shares a real word with the magazine's name. */
    private fun relates(magazine: String, title: String, extract: String): Boolean {
        val words = Regex("""[A-Za-z]{3,}""").findAll(magazine.lowercase()).map { it.value }.filter { it !in setOf("the", "magazine", "weekly", "monthly") }.toList()
        if (words.isEmpty()) return false
        val hay = (title + " " + extract.take(300)).lowercase()
        return words.any { hay.contains(it) }
    }

    private fun load() {
        runCatching {
            val o = JSONObject(cacheFile.readText())
            for (k in o.keys()) {
                val v = o.optJSONObject(k)
                cache[k] = v?.let { WikiSummary(it.getString("t"), it.getString("e"), it.getString("u")) }
            }
        }
    }

    private fun save() {
        val o = JSONObject()
        cache.forEach { (k, v) -> o.put(k, v?.let { JSONObject().put("t", it.title).put("e", it.extract).put("u", it.url) } ?: JSONObject.NULL) }
        runCatching { cacheFile.writeText(o.toString()) }
    }
}

/** How hard an issue's Japanese is, judged by its kanji against what Japanese schools teach. */
data class Difficulty(val level: Level, val beyondSchool: Float, val kanjiSeen: Int) {
    enum class Level(val label: String) { Easy("Easy"), Medium("Medium"), Hard("Hard") }

    companion object {
        /**
         * [grades] maps each kanji in the text to its KANJIDIC2 grade: 1 to 6 are primary
         * school, 8 the rest of the everyday list, 9 and 10 name kanji; null for rarer ones.
         */
        fun of(text: String, grades: Map<String, Int?>): Difficulty? {
            var total = 0
            var hard = 0
            for (c in text) {
                if (!Kana.isKanji(c) || c == '々') continue
                total++
                val g = grades[c.toString()]
                if (g == null || g > 6) hard++
            }
            if (total < 40) return null
            val share = hard.toFloat() / total
            val level = when {
                share < 0.12f -> Level.Easy
                share < 0.24f -> Level.Medium
                else -> Level.Hard
            }
            return Difficulty(level, share, grades.size)
        }
    }
}
