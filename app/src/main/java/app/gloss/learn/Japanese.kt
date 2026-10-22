package app.gloss.learn

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The bundled Japanese dictionary (JMdict, JMnedict, KANJIDIC2 and KanjiVG, built by
 * tools/japanese_pack.py).
 * The database ships compressed in the APK and is unpacked to private storage the first time
 * something needs it.
 */
class Japanese(private val context: Context) {
    private val lock = Mutex()
    @Volatile private var db: SQLiteDatabase? = null
    private val formCache = object : LinkedHashMap<String, List<Word>>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Word>>?) = size > 4000
    }

    /** False when this build was made without the dictionary (a fresh clone, say). */
    val bundled: Boolean by lazy { runCatching { context.assets.list("")?.contains(ASSET) == true }.getOrDefault(false) }

    private suspend fun open(): SQLiteDatabase? = db ?: lock.withLock {
        db ?: withContext(Dispatchers.IO) {
            if (!bundled) return@withContext null
            val file = File(context.noBackupFilesDir, "japanese-$FORMAT.db")
            if (!file.exists()) {
                // Unpack beside the target and rename, so a half-written file is never opened.
                context.noBackupFilesDir.listFiles { f -> f.name.startsWith("japanese-") }?.forEach { it.delete() }
                val tmp = File(file.path + ".part")
                context.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
                tmp.renameTo(file)
            }
            runCatching { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) }
                .onFailure { Log.e(TAG, "Couldn't open the dictionary", it); file.delete() }
                .getOrNull()
                ?.also { db = it }
        }
    }

    suspend fun ready(): Boolean = open() != null

    /** Entries listed under [form] exactly, everyday words first. */
    private fun entries(d: SQLiteDatabase, form: String): List<Word> = synchronized(formCache) { formCache[form] } ?: run {
        val out = ArrayList<Word>(2)
        d.rawQuery(
            "SELECT e.id, e.kanji, e.kana, e.senses, e.rank FROM form f JOIN entry e ON e.id = f.id WHERE f.form = ? ORDER BY f.rank, e.id LIMIT 8",
            arrayOf(form),
        ).use { c -> while (c.moveToNext()) out += wordFrom(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4)) }
        synchronized(formCache) { formCache[form] = out }
        out
    }

    private val nameCache = object : LinkedHashMap<String, List<Name>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Name>>?) = size > 2000
    }

    /** The ways [form] reads as a person's name, from JMnedict. */
    private fun names(d: SQLiteDatabase, form: String): List<Name> = synchronized(nameCache) { nameCache[form] } ?: run {
        val out = d.rawQuery("SELECT readings FROM name WHERE form = ?", arrayOf(form)).use { c ->
            if (!c.moveToNext()) emptyList()
            else c.getString(0).split('\n').mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 2) null else Name(p[0], p[1].split(',').filter { it.isNotEmpty() }, p.getOrElse(2) { "" })
            }
        }
        synchronized(nameCache) { nameCache[form] = out }
        out
    }

    suspend fun word(id: Long): Word? {
        val d = open() ?: return null
        return withContext(Dispatchers.IO) {
            d.rawQuery("SELECT id, kanji, kana, senses, rank FROM entry WHERE id = ?", arrayOf(id.toString())).use { c ->
                if (c.moveToNext()) wordFrom(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4)) else null
            }
        }
    }

    suspend fun kanji(ch: String): Kanji? {
        val d = open() ?: return null
        return withContext(Dispatchers.IO) { kanjiIn(d, listOf(ch)).firstOrNull() }
    }

    /** Grade and JLPT level for each distinct kanji in [chars], for working out how hard a text is. */
    suspend fun kanjiLevels(chars: Collection<String>): Map<String, Int?> {
        val d = open() ?: return emptyMap()
        return withContext(Dispatchers.IO) {
            val out = HashMap<String, Int?>()
            chars.chunked(400).forEach { chunk ->
                d.rawQuery(
                    "SELECT ch, grade FROM kanji WHERE ch IN (${chunk.joinToString(",") { "?" }})",
                    chunk.toTypedArray(),
                ).use { c -> while (c.moveToNext()) out[c.getString(0)] = if (c.isNull(1)) null else c.getInt(1) }
            }
            out
        }
    }

    private fun kanjiIn(d: SQLiteDatabase, chars: List<String>): List<Kanji> {
        val out = ArrayList<Kanji>()
        d.rawQuery(
            "SELECT ch, strokes, grade, jlpt, onyomi, kunyomi, meanings, paths FROM kanji WHERE ch IN (${chars.joinToString(",") { "?" }})",
            chars.toTypedArray(),
        ).use { c ->
            while (c.moveToNext()) {
                out += Kanji(
                    ch = c.getString(0),
                    strokes = if (c.isNull(1)) null else c.getInt(1),
                    grade = if (c.isNull(2)) null else c.getInt(2),
                    jlpt = if (c.isNull(3)) null else c.getInt(3),
                    onyomi = c.getString(4).orEmpty(),
                    kunyomi = c.getString(5).orEmpty(),
                    meanings = c.getString(6).orEmpty(),
                    paths = c.getString(7)?.split('\n').orEmpty(),
                )
            }
        }
        return out.sortedBy { k -> chars.indexOf(k.ch) }
    }

    /**
     * Splits Japanese text into words, tracing inflected verbs and adjectives back to their
     * dictionary form and picking out people's names. [hint], the English translation, helps tell
     * which kanji are names and how they're read. Non-Japanese runs come back as plain tokens.
     */
    suspend fun tokenize(text: String, hint: String = ""): List<Token> {
        val d = open() ?: return listOf(Token(text, null, text, ""))
        return withContext(Dispatchers.Default) { Tokenizer.tokenize(text, { entries(d, it) }, { names(d, it) }, hint) }
    }

    companion object {
        private const val TAG = "Japanese"
        private const val ASSET = "japanese.db"
        private const val FORMAT = 2

        internal fun wordFrom(id: Long, kanji: String, kana: String, senses: String, rank: Int) = Word(
            id = id,
            kanji = kanji.split('|').filter { it.isNotEmpty() },
            kana = kana.split('|').filter { it.isNotEmpty() },
            senses = senses.split('\n').mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3) null
                else Sense(parts[0].split(',').filter { it.isNotEmpty() }, parts[1].split(',').filter { it.isNotEmpty() }, parts[2])
            },
            rank = rank,
        )
    }
}
