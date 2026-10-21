package app.gloss.data

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** An issue that has (at least) its text data on the device. */
data class InstalledIssue(
    val id: String,
    val magazineName: String,
    val issueName: String,
    val date: String,
    val publisher: String,
    val version: String,
    val coverUrl: String,
    val adult: Boolean,
    val installedAt: Long,
    val dir: File,
    /** Bumped when files change on disk so StateFlow observers see a new value. */
    val stamp: Long = 0L,
) {
    val pdfFile get() = File(dir, "scan.pdf")
    val zipFile get() = File(dir, "data.zip")
    val hasScan get() = pdfFile.exists()
    val hasData get() = zipFile.exists()
    val title get() = issueName.ifBlank { date.ifBlank { magazineName } }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("magazine_name", magazineName).put("issue_name", issueName)
        .put("date", date).put("publisher", publisher).put("version", version)
        .put("cover_url", coverUrl).put("adult", adult).put("installed_at", installedAt)

    companion object {
        fun fromJson(o: JSONObject, dir: File) = InstalledIssue(
            id = o.getString("id"),
            magazineName = o.optString("magazine_name"),
            issueName = o.optString("issue_name"),
            date = o.optString("date"),
            publisher = o.optString("publisher"),
            version = o.optString("version"),
            coverUrl = o.optString("cover_url"),
            adult = o.optBoolean("adult"),
            installedAt = o.optLong("installed_at"),
            dir = dir,
        )
    }
}

/**
 * On-disk library: filesDir/library/<id>/{scan.pdf, data.zip, info.json}.
 * Simpler than the desktop's folder-name heuristics because we control the layout.
 */
class LibraryStore(private val root: File) {

    private val _issues = MutableStateFlow<Map<String, InstalledIssue>>(emptyMap())
    val issues: StateFlow<Map<String, InstalledIssue>> = _issues.asStateFlow()

    private val contentCache = LruCache<String, IssueContent>(4)

    init {
        root.mkdirs()
    }

    fun dirFor(id: String): File = File(root, safeDirName(id))

    suspend fun scan() = withContext(Dispatchers.IO) {
        val found = HashMap<String, InstalledIssue>()
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val info = File(dir, "info.json")
            if (!info.exists()) return@forEach
            runCatching { InstalledIssue.fromJson(JSONObject(info.readText()), dir) }
                .onSuccess { if (it.hasData || it.hasScan) found[it.id] = it }
                .onFailure { Log.w("Library", "Bad info.json in ${dir.name}", it) }
        }
        _issues.value = found
    }

    /** Records an install after its files are in place, taking the version from metadata.txt when present. */
    suspend fun register(item: CatalogItem) = withContext(Dispatchers.IO) {
        val dir = dirFor(item.id)
        val zipVersion = runCatching { readZipMetadata(File(dir, "data.zip"))["version"] }.getOrNull()
        val issue = InstalledIssue(
            id = item.id,
            magazineName = item.magazineName,
            issueName = item.issueName,
            date = item.date,
            publisher = item.publisher,
            version = zipVersion?.takeIf { it.isNotBlank() } ?: item.version,
            coverUrl = item.coverUrl,
            adult = item.adult,
            installedAt = System.currentTimeMillis(),
            dir = dir,
        )
        File(dir, "info.json").writeText(issue.toJson().toString(2))
        contentCache.remove(item.id)
        _issues.update { it + (item.id to issue) }
        issue
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dirFor(id).deleteRecursively()
        contentCache.remove(id)
        _issues.update { it - id }
    }

    /** Frees space but keeps the text so the issue stays readable and searchable. */
    suspend fun deleteScan(id: String) = withContext(Dispatchers.IO) {
        val issue = _issues.value[id] ?: return@withContext
        issue.pdfFile.delete()
        if (!issue.hasData) delete(id) else _issues.update { it + (id to issue.copy(stamp = System.nanoTime())) }
    }

    suspend fun content(id: String): IssueContent? = withContext(Dispatchers.IO) {
        contentCache.get(id)?.let { return@withContext it }
        val issue = _issues.value[id] ?: return@withContext null
        val loaded = IssueContent.load(issue.zipFile)
        contentCache.put(id, loaded)
        loaded
    }

    private fun readZipMetadata(zip: File): Map<String, String> {
        if (!zip.exists()) return emptyMap()
        ZipFile(zip).use { z ->
            val entry = z.entries().asSequence().firstOrNull { baseName(it.name).equals("metadata.txt", true) }
                ?: return emptyMap()
            return RosettaFormat.parseMetadata(z.getInputStream(entry).use { String(it.readNBytes(1 shl 20), Charsets.UTF_8) })
        }
    }

    companion object {
        fun safeDirName(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
            // "", "." and ".." would point at the library folder itself or its parent.
            .let { if (it.all { c -> c == '.' }) "_$it" else it }
        fun baseName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')
    }
}

/** Everything parsed out of an issue's data ZIP. */
class IssueContent(
    val pages: Map<Int, PageText>,
    val regions: Map<Int, List<TextRegion>>,
    val metadata: Map<String, String>,
) {
    val lastTextPage: Int get() = maxOf(pages.keys.maxOrNull() ?: 0, regions.keys.maxOrNull() ?: 0)

    fun page(n: Int): PageText = pages[n] ?: PageText("", "", "")
    fun regionsFor(n: Int): List<TextRegion> = regions[n].orEmpty()

    companion object {
        val EMPTY = IssueContent(emptyMap(), emptyMap(), emptyMap())
        private const val MAX_TEXT_BYTES = 64 * 1024 * 1024

        fun load(zip: File): IssueContent {
            if (!zip.exists()) return EMPTY
            return try {
                ZipFile(zip).use { z ->
                    val entries = z.entries().asSequence().filter { !it.isDirectory }.toList()
                    // Real text files are a few MB; anything past the cap is cut off rather than
                    // letting a broken or hostile ZIP exhaust memory every time the issue opens.
                    fun read(e: java.util.zip.ZipEntry): String =
                        z.getInputStream(e).use { input ->
                            val bytes = input.readNBytes(MAX_TEXT_BYTES)
                            String(bytes, Charsets.UTF_8)
                        }

                    val pagesRaw = HashMap<Int, String>()
                    // Per-page files first, then the master file wins where both exist (desktop priority).
                    entries.forEach { e ->
                        val name = LibraryStore.baseName(e.name)
                        if (!name.endsWith("_COMPLETE.txt", true)) {
                            RosettaFormat.perPageNumber(name)?.let { pagesRaw[it] = read(e) }
                        }
                    }
                    entries.firstOrNull { LibraryStore.baseName(it.name).endsWith("_COMPLETE.txt", true) }
                        ?.let { pagesRaw.putAll(RosettaFormat.splitPages(read(it))) }

                    val regions = entries
                        .firstOrNull { LibraryStore.baseName(it.name).endsWith("_COORDINATES.json", true) }
                        ?.let { RosettaFormat.parseCoordinates(read(it)) }
                        .orEmpty()

                    val meta = entries
                        .firstOrNull { LibraryStore.baseName(it.name).equals("metadata.txt", true) }
                        ?.let { RosettaFormat.parseMetadata(read(it)) }
                        .orEmpty()

                    IssueContent(pagesRaw.mapValues { RosettaFormat.splitSections(it.value) }, regions, meta)
                }
            } catch (e: Exception) {
                Log.e("IssueContent", "Could not read ${zip.path}", e)
                EMPTY
            }
        }
    }
}
