package app.gloss.data

import org.json.JSONArray
import org.json.JSONObject

/** A portable copy of [ReadingState]: what a backup file holds. */
data class ReadingBackup(
    val bookmarks: List<Bookmark>,
    val pages: Map<String, Int>,
    val recents: List<RecentRead>,
    val settings: Map<String, String>,
)

/** JSON for reading state, shared by preferences storage and backup files. */
object ReadingCodec {
    const val FORMAT = "gloss-reading"
    const val VERSION = 1

    fun encodeBookmarks(list: List<Bookmark>): String = JSONArray(list.map {
        JSONObject().put("id", it.issueId).put("page", it.page).put("note", it.note).put("at", it.createdAt)
    }).toString()

    fun decodeBookmarks(raw: String?): List<Bookmark> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Bookmark(it.getString("id"), it.optInt("page", 1), it.optString("note"), it.optLong("at"))
        }
    }.getOrDefault(emptyList())

    fun encodeRecents(list: List<RecentRead>): String = JSONArray(list.map {
        JSONObject().put("id", it.issueId).put("page", it.page).put("at", it.at)
    }).toString()

    fun decodeRecents(raw: String?): List<RecentRead> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            RecentRead(it.getString("id"), it.optInt("page", 1), it.optLong("at"))
        }
    }.getOrDefault(emptyList())

    fun encodePages(pages: Map<String, Int>): String = JSONObject().apply {
        pages.forEach { (k, v) -> put(k, v) }
    }.toString()

    fun decodePages(raw: String?): Map<String, Int> = runCatching {
        val o = JSONObject(raw ?: return emptyMap())
        o.keys().asSequence().associateWith { o.optInt(it, 1) }
    }.getOrDefault(emptyMap())

    fun encodeBackup(b: ReadingBackup, exportedAt: Long, appVersion: String): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", exportedAt)
        .put("app", appVersion)
        .put("bookmarks", JSONArray(encodeBookmarks(b.bookmarks)))
        .put("recents", JSONArray(encodeRecents(b.recents)))
        .put("pages", JSONObject(encodePages(b.pages)))
        .put("settings", JSONObject().apply { b.settings.forEach { (k, v) -> put(k, v) } })
        .toString(2)

    /** Parses a backup file, or throws [IllegalArgumentException] with a reason a person can read. */
    fun decodeBackup(raw: String): ReadingBackup {
        val o = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw IllegalArgumentException("This file isn't a Gloss backup.")
        if (o.optString("format") != FORMAT) throw IllegalArgumentException("This file isn't a Gloss backup.")
        if (o.optInt("version", 0) > VERSION) {
            throw IllegalArgumentException("This backup is from a newer version of Gloss. Update the app first.")
        }
        val settings = o.optJSONObject("settings")?.let { s -> s.keys().asSequence().associateWith { s.optString(it) } }.orEmpty()
        return ReadingBackup(
            bookmarks = decodeBookmarks(o.optJSONArray("bookmarks")?.toString()),
            pages = decodePages(o.optJSONObject("pages")?.toString()),
            recents = decodeRecents(o.optJSONArray("recents")?.toString()),
            settings = settings,
        )
    }

    /**
     * Combines two states. Pages: the incoming value wins only when that issue is newer in the
     * incoming recents (or unknown locally). Bookmarks: union, keeping a note where one exists.
     */
    fun merge(local: ReadingBackup, incoming: ReadingBackup): ReadingBackup {
        val localAt = local.recents.associate { it.issueId to it.at }
        val incomingAt = incoming.recents.associate { it.issueId to it.at }
        val pages = HashMap(local.pages)
        incoming.pages.forEach { (id, page) ->
            val mine = localAt[id]
            val theirs = incomingAt[id]
            if (!pages.containsKey(id) || (theirs != null && (mine == null || theirs > mine))) pages[id] = page
        }
        val recents = (local.recents + incoming.recents)
            .groupBy { it.issueId }
            .map { (_, rs) -> rs.maxBy { it.at } }
            .sortedByDescending { it.at }
        val bookmarks = (local.bookmarks + incoming.bookmarks)
            .groupBy { it.issueId to it.page }
            .map { (_, bs) ->
                val first = bs.minBy { it.createdAt }
                first.copy(note = bs.map { it.note }.firstOrNull { it.isNotBlank() } ?: "")
            }
            .sortedByDescending { it.createdAt }
        return ReadingBackup(bookmarks, pages, recents, local.settings)
    }
}
