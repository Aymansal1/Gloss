package app.gloss.search

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import app.gloss.data.LibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SearchHit(
    val issueId: String,
    val page: Int,
    /** Which section matched best, for the result label. */
    val section: Section,
    val snippet: String,
    val highlights: List<IntRange>,
) {
    enum class Section { TRANSLATION, ORIGINAL, SUMMARY }
}

/**
 * Full-text index of every installed page (FTS4, available in Android's bundled SQLite).
 * Columns en/sm use the unicode61 tokenizer; jpb holds Japanese bigrams (see [SearchText]).
 */
class SearchIndex(context: Context, private val library: LibraryStore) {

    private val helper = object : SQLiteOpenHelper(context, "search.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            fun create(tokenizer: String) = db.execSQL(
                """CREATE VIRTUAL TABLE pages USING fts4(
                    issue_id, page, en, sm, jpb, jp_raw,
                    notindexed=issue_id, notindexed=page, notindexed=jp_raw,
                    tokenize=$tokenizer)""",
            )
            // unicode61 folds accents; "simple" is the always-available fallback. Both treat
            // CJK characters as token characters, so the bigram scheme works with either.
            try {
                create("unicode61")
            } catch (e: Exception) {
                Log.w("SearchIndex", "unicode61 unavailable, using simple tokenizer", e)
                create("simple")
            }
            db.execSQL("CREATE TABLE indexed (issue_id TEXT PRIMARY KEY, stamp INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS pages")
            db.execSQL("DROP TABLE IF EXISTS indexed")
            onCreate(db)
        }
    }

    private val lock = Mutex()

    suspend fun indexIssue(id: String) = withContext(Dispatchers.IO) {
        val issue = library.issues.value[id] ?: return@withContext
        val content = library.content(id) ?: return@withContext
        lock.withLock {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                db.delete("pages", "issue_id = ?", arrayOf(id))
                for ((page, text) in content.pages) {
                    if (text.isEmpty) continue
                    db.insert("pages", null, ContentValues().apply {
                        put("issue_id", id)
                        put("page", page)
                        put("en", text.translation)
                        put("sm", text.summary)
                        put("jpb", SearchText.bigramIndexText(text.transcription))
                        put("jp_raw", text.transcription)
                    })
                }
                db.insertWithOnConflict("indexed", null, ContentValues().apply {
                    put("issue_id", id); put("stamp", issue.zipFile.lastModified())
                }, SQLiteDatabase.CONFLICT_REPLACE)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    suspend fun removeIssue(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val db = helper.writableDatabase
            db.delete("pages", "issue_id = ?", arrayOf(id))
            db.delete("indexed", "issue_id = ?", arrayOf(id))
        }
    }

    /** Indexes any installed issue that is missing or changed, and drops uninstalled ones. */
    suspend fun sync() = withContext(Dispatchers.IO) {
        val installed = library.issues.value
        val known = HashMap<String, Long>()
        lock.withLock {
            helper.readableDatabase.rawQuery("SELECT issue_id, stamp FROM indexed", null).use { c ->
                while (c.moveToNext()) known[c.getString(0)] = c.getLong(1)
            }
        }
        (known.keys - installed.keys).forEach { removeIssue(it) }
        for ((id, issue) in installed) {
            if (!issue.hasData) continue
            if (known[id] != issue.zipFile.lastModified()) {
                runCatching { indexIssue(id) }.onFailure { Log.w("SearchIndex", "Index failed: $id", it) }
            }
        }
    }

    suspend fun search(query: String, limit: Int = 400): List<SearchHit> = withContext(Dispatchers.IO) {
        val parsed = SearchText.parseQuery(query)
        val match = parsed.match ?: return@withContext emptyList()
        val hits = ArrayList<SearchHit>()
        lock.withLock {
            try {
                helper.readableDatabase.rawQuery(
                    "SELECT issue_id, page, en, sm, jp_raw FROM pages WHERE pages MATCH ? LIMIT ?",
                    arrayOf(match, limit.toString()),
                ).use { c ->
                    while (c.moveToNext()) {
                        val en = c.getString(2).orEmpty()
                        val sm = c.getString(3).orEmpty()
                        val jp = c.getString(4).orEmpty()
                        val all = SearchText.normalize("$en\n$sm\n$jp")
                        if (parsed.excluded.any { all.contains(it) }) continue
                        hits += toHit(c.getString(0), c.getInt(1), en, sm, jp, parsed.highlightTerms)
                    }
                }
            } catch (e: Exception) {
                Log.w("SearchIndex", "Bad query: $match", e)
            }
        }
        hits
    }

    private fun toHit(id: String, page: Int, en: String, sm: String, jp: String, terms: List<String>): SearchHit {
        val sections = listOf(
            SearchHit.Section.TRANSLATION to en,
            SearchHit.Section.ORIGINAL to jp,
            SearchHit.Section.SUMMARY to sm,
        )
        val best = sections.firstOrNull { (_, text) ->
            val n = SearchText.normalize(text)
            terms.any { n.contains(it) }
        } ?: sections.first()
        val (snip, ranges) = SearchText.snippet(best.second, terms)
        return SearchHit(id, page, best.first, snip, ranges)
    }
}
