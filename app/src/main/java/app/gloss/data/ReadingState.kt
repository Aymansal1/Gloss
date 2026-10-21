package app.gloss.data

import android.content.Context
import app.gloss.ui.theme.Appearance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Bookmark(val issueId: String, val page: Int, val note: String, val createdAt: Long)

data class RecentRead(val issueId: String, val page: Int, val at: Long)

/**
 * Small per-user state: the page you're on in every issue, recents, bookmarks and reader
 * settings. Kept in shared preferences (which Android's backup also covers) as JSON.
 */
class ReadingState(context: Context) {
    private val prefs = context.getSharedPreferences("reading", Context.MODE_PRIVATE)

    private val _bookmarks = MutableStateFlow(ReadingCodec.decodeBookmarks(prefs.getString("bookmarks", null)))
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _recents = MutableStateFlow(ReadingCodec.decodeRecents(prefs.getString("recents", null)))
    val recents: StateFlow<List<RecentRead>> = _recents.asStateFlow()

    /**
     * Last page per issue. Unlike [recents] this is never trimmed, so an issue you put down
     * months and dozens of issues ago still opens where you left it.
     */
    private val pages = HashMap(ReadingCodec.decodePages(prefs.getString("pages", null))).apply {
        // Older builds only kept the 30 most recent issues; carry those over.
        _recents.value.forEach { putIfAbsent(it.issueId, it.page) }
    }

    private val _showAdult = MutableStateFlow(prefs.getBoolean("show_adult", false))
    val showAdult: StateFlow<Boolean> = _showAdult.asStateFlow()

    private val _textScale = MutableStateFlow(prefs.getFloat("text_scale", 1f))
    val textScale: StateFlow<Float> = _textScale.asStateFlow()

    /** Japanese magazines bind on the right, so swiping defaults to right-to-left. */
    private val _rightToLeft = MutableStateFlow(prefs.getBoolean("rtl", true))
    val rightToLeft: StateFlow<Boolean> = _rightToLeft.asStateFlow()

    private val _showZones = MutableStateFlow(prefs.getBoolean("zones", false))
    val showZones: StateFlow<Boolean> = _showZones.asStateFlow()

    /** Two pages side by side when the phone is held sideways. */
    private val _spreads = MutableStateFlow(prefs.getBoolean("spreads", true))
    val spreads: StateFlow<Boolean> = _spreads.asStateFlow()

    private val _appearance = MutableStateFlow(
        runCatching { Appearance.valueOf(prefs.getString("appearance", null) ?: "") }.getOrDefault(Appearance.System),
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun setRightToLeft(v: Boolean) { _rightToLeft.value = v; prefs.edit().putBoolean("rtl", v).apply() }
    fun setShowZones(v: Boolean) { _showZones.value = v; prefs.edit().putBoolean("zones", v).apply() }
    fun setSpreads(v: Boolean) { _spreads.value = v; prefs.edit().putBoolean("spreads", v).apply() }
    fun setAppearance(v: Appearance) { _appearance.value = v; prefs.edit().putString("appearance", v.name).apply() }
    fun setShowAdult(v: Boolean) { _showAdult.value = v; prefs.edit().putBoolean("show_adult", v).apply() }

    fun setTextScale(v: Float) {
        val c = v.coerceIn(0.8f, 1.6f)
        _textScale.value = c; prefs.edit().putFloat("text_scale", c).apply()
    }

    @Synchronized
    fun lastPage(issueId: String): Int = pages[issueId] ?: 1

    @Synchronized
    fun recordPage(issueId: String, page: Int) {
        pages[issueId] = page
        _recents.value = (listOf(RecentRead(issueId, page, System.currentTimeMillis())) +
            _recents.value.filter { it.issueId != issueId }).take(MAX_RECENTS)
        save()
    }

    @Synchronized
    fun forget(issueId: String) {
        pages.remove(issueId)
        _recents.value = _recents.value.filter { it.issueId != issueId }
        _bookmarks.value = _bookmarks.value.filter { it.issueId != issueId }
        save()
    }

    fun isBookmarked(issueId: String, page: Int) = _bookmarks.value.any { it.issueId == issueId && it.page == page }

    @Synchronized
    fun toggleBookmark(issueId: String, page: Int) {
        _bookmarks.value = if (isBookmarked(issueId, page)) {
            _bookmarks.value.filterNot { it.issueId == issueId && it.page == page }
        } else {
            listOf(Bookmark(issueId, page, "", System.currentTimeMillis())) + _bookmarks.value
        }
        save()
    }

    @Synchronized
    fun setNote(issueId: String, page: Int, note: String) {
        _bookmarks.value = _bookmarks.value.map {
            if (it.issueId == issueId && it.page == page) it.copy(note = note.trim()) else it
        }
        save()
    }

    @Synchronized
    fun removeBookmark(issueId: String, page: Int) {
        _bookmarks.value = _bookmarks.value.filterNot { it.issueId == issueId && it.page == page }
        save()
    }

    /** Everything worth keeping if the phone is lost, as a portable file. */
    @Synchronized
    fun export(): ReadingBackup = ReadingBackup(
        bookmarks = _bookmarks.value,
        pages = HashMap(pages),
        recents = _recents.value,
        settings = mapOf(
            "rtl" to _rightToLeft.value.toString(),
            "zones" to _showZones.value.toString(),
            "spreads" to _spreads.value.toString(),
            "show_adult" to _showAdult.value.toString(),
            "text_scale" to _textScale.value.toString(),
            "appearance" to _appearance.value.name,
        ),
    )

    /** Merges a backup into the current state; on conflicts the newer entry wins. */
    @Synchronized
    fun import(backup: ReadingBackup) {
        val merged = ReadingCodec.merge(export(), backup)
        pages.clear(); pages.putAll(merged.pages)
        _recents.value = merged.recents.take(MAX_RECENTS)
        _bookmarks.value = merged.bookmarks
        backup.settings["rtl"]?.toBooleanStrictOrNull()?.let { setRightToLeft(it) }
        backup.settings["zones"]?.toBooleanStrictOrNull()?.let { setShowZones(it) }
        backup.settings["spreads"]?.toBooleanStrictOrNull()?.let { setSpreads(it) }
        backup.settings["show_adult"]?.toBooleanStrictOrNull()?.let { setShowAdult(it) }
        backup.settings["text_scale"]?.toFloatOrNull()?.let { setTextScale(it) }
        backup.settings["appearance"]?.let { a -> Appearance.entries.firstOrNull { it.name == a } }?.let { setAppearance(it) }
        save()
    }

    private fun save() {
        prefs.edit()
            .putString("pages", ReadingCodec.encodePages(pages))
            .putString("recents", ReadingCodec.encodeRecents(_recents.value))
            .putString("bookmarks", ReadingCodec.encodeBookmarks(_bookmarks.value))
            .apply()
    }

    companion object {
        const val MAX_RECENTS = 50
    }
}
