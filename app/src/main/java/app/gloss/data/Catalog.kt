package app.gloss.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CatalogItem(
    val id: String,
    val magazineName: String,
    val issueName: String,
    val date: String,
    val publisher: String,
    val version: String,
    val pdfFilename: String,
    val pdfSources: List<String>,
    val zipFilename: String,
    val zipSources: List<String>,
    val tags: List<String>,
    val scanner: String,
    val scannerUrl: String,
    val editor: String,
    val editorUrl: String,
    val notes: String,
    val description: String,
    val adult: Boolean,
    val originalLanguage: String,
    val translatedLanguage: String,
    val coverUrl: String,
) {
    /** "Weekly Famitsu · 1997-10-31" style title for lists. */
    val displayTitle: String get() = issueName.ifBlank { date.ifBlank { magazineName } }
    val year: String get() = Regex("""(19|20)\d{2}""").find(date)?.value ?: ""
    val hasScan: Boolean get() = pdfSources.isNotEmpty()
    val hasData: Boolean get() = zipSources.isNotEmpty()

    companion object {
        /** "nours_vol_42-data" -> "Nours Vol 42 Data": a readable stand-in when a name is missing. */
        internal fun humanize(raw: String): String = raw
            .substringBeforeLast('.', raw)
            .replace(Regex("""(?i)_data$"""), "")
            .replace(Regex("""[_\-.]+"""), " ")
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { w -> if (w.any(Char::isLowerCase) || w.length > 4) w.replaceFirstChar(Char::uppercase) else w.uppercase() }

        fun fromJson(o: JSONObject): CatalogItem? {
            val id = o.opt("id")?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
            val pdfSources = o.strList("pdf_sources")
            val zipSources = o.strList("zip_sources")
            // An entry with nothing to download (a placeholder or half-written entry) can't be used.
            if (pdfSources.isEmpty() && zipSources.isEmpty()) return null
            val pdfFilename = o.str("pdf_filename")
            val stem = pdfFilename.substringBeforeLast('.', pdfFilename)
            val name = listOf(o.str("magazine_name"), o.str("name"), o.str("title"))
                .firstOrNull { it.isNotBlank() }
                ?: listOf(stem, o.str("zip_filename"), id).map(::humanize).firstOrNull { it.isNotBlank() }
                ?: id
            return CatalogItem(
                id = id,
                magazineName = name,
                issueName = o.str("issue_name"),
                date = o.str("date"),
                publisher = o.str("publisher"),
                version = o.str("version"),
                pdfFilename = pdfFilename.ifBlank { "$id.pdf" },
                pdfSources = pdfSources,
                zipFilename = o.str("zip_filename").ifBlank { "${stem.ifBlank { id }}_Data.zip" },
                zipSources = zipSources,
                tags = o.strList("tags").flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() },
                scanner = o.str("scanner"),
                scannerUrl = o.str("scanner_url"),
                editor = o.str("editor"),
                editorUrl = o.str("editor_url"),
                notes = o.str("notes"),
                description = o.str("description"),
                adult = listOf("adult_content", "nsfw", "mature", "adult").any { o.truthy(it) },
                originalLanguage = o.str("original_language").uppercase(),
                translatedLanguage = o.str("translated_language").uppercase(),
                coverUrl = o.str("cover_url"),
            )
        }

        /** Accepts both catalog shapes: a bare array, or {"update_url": ..., "items": [...]}. */
        fun parseCatalog(raw: String): List<CatalogItem> {
            val trimmed = raw.trim()
            val arr: JSONArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
                else -> JSONArray()
            }
            val out = ArrayList<CatalogItem>(arr.length())
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::fromJson)?.let(out::add)
            return out
        }
    }
}

private fun JSONObject.str(key: String): String {
    val v = opt(key) ?: return ""
    if (v == JSONObject.NULL) return ""
    return v.toString().trim()
}

private fun JSONObject.strList(key: String): List<String> = when (val v = opt(key)) {
    is JSONArray -> (0 until v.length()).mapNotNull { v.optString(it, "").trim().takeIf(String::isNotEmpty) }
    is String -> if (v.isBlank()) emptyList() else listOf(v.trim())
    else -> emptyList()
}

private fun JSONObject.truthy(key: String): Boolean = when (val v = opt(key)) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> v.trim().lowercase() in setOf("true", "yes", "1", "y")
    else -> false
}

/** Compares dotted versions numerically ("1.10" > "1.9"); falls back to string compare. */
fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.', '-').map { it.toIntOrNull() }
    val pb = b.split('.', '-').map { it.toIntOrNull() }
    if (pa.any { it == null } || pb.any { it == null }) return a.compareTo(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val c = (pa.getOrNull(i) ?: 0).compareTo(pb.getOrNull(i) ?: 0)
        if (c != 0) return c
    }
    return 0
}

class CatalogRepository(
    private val http: OkHttpClient,
    private val cacheFile: File,
) {
    companion object {
        val OFFICIAL_URLS = listOf(
            "https://www.gamingalexandria.com/ga-researcher/catalog.json",
            "https://archive.org/download/ga-researcher-files/catalog.json",
        )
        private const val TAG = "Catalog"
    }

    sealed interface Status {
        data object Idle : Status
        data object Loading : Status
        data class Offline(val reason: String) : Status
    }

    private val _items = MutableStateFlow<List<CatalogItem>>(emptyList())
    val items: StateFlow<List<CatalogItem>> = _items.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun byId(id: String): CatalogItem? = _items.value.firstOrNull { it.id == id }

    /** Shows the cached catalog immediately, then refreshes from the network. */
    suspend fun load(forceNetwork: Boolean = true) {
        if (_items.value.isEmpty()) loadCache()
        if (forceNetwork) refresh()
    }

    private suspend fun loadCache() = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext
        runCatching { CatalogItem.parseCatalog(cacheFile.readText()) }
            .onSuccess { _items.value = dedupe(it) }
            .onFailure { Log.w(TAG, "Cached catalog unreadable", it) }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _status.value = Status.Loading
        var lastError = "no catalog source reachable"
        for (url in OFFICIAL_URLS) {
            try {
                val req = Request.Builder().url(url).header("Accept", "application/json").build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val raw = resp.body?.string().orEmpty()
                    val parsed = CatalogItem.parseCatalog(raw)
                    if (parsed.isEmpty()) error("empty catalog")
                    val tmp = File(cacheFile.path + ".tmp")
                    tmp.writeText(raw)
                    tmp.renameTo(cacheFile)
                    _items.value = dedupe(parsed)
                    _status.value = Status.Idle
                    return@withContext
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                Log.w(TAG, "Catalog fetch failed: $url", e)
            }
        }
        _status.value = Status.Offline(lastError)
    }

    private fun dedupe(items: List<CatalogItem>) = items.distinctBy { it.id }
}
