package app.gloss.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.gloss.GlossApp
import app.gloss.data.CatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class DownloadMode {
    /** Transcription/translation data only. */
    TEXT,
    /** Scan only (upgrade a text-only install). */
    SCAN,
    /** Data + scan. */
    FULL,
}

data class DownloadProgress(
    val running: Boolean,
    val stage: String = "",
    val done: Long = 0,
    val total: Long = 0,
    val error: String? = null,
) {
    val fraction: Float? get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

/** Fetches a URL into a file, trying each mirror in order. Mega links are decrypted natively. */
class SourceFetcher(private val http: OkHttpClient) {
    private val mega = MegaDownloader(http)

    fun fetch(
        sources: List<String>,
        dest: File,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        if (sources.isEmpty()) throw IOException("No download sources listed")
        var last: Exception? = null
        for (url in sources) {
            try {
                if (MegaDownloader.isMegaUrl(url)) mega.download(url, dest, onProgress, isCancelled)
                else plain(url, dest, onProgress, isCancelled)
                return
            } catch (e: Exception) {
                if (isCancelled()) throw e
                Log.w("Fetcher", "Source failed: ${redact(url)}", e)
                last = e
            }
        }
        throw IOException("All sources failed: ${last?.message}", last)
    }

    private fun plain(url: String, dest: File, onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean) {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Unsupported URL scheme" }
        val part = PartialFile(dest, url)
        val have = part.resumable()
        val req = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        http.newCall(req).execute().use { resp ->
            // The server forgot the range (file changed, or past the end): start this source over.
            if (resp.code == 416) { part.discard(); return plain(url, dest, onProgress, isCancelled) }
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty response")
            // Only trust a 206 that starts exactly where our bytes end; anything else restarts.
            val resumed = have > 0 && resp.code == 206 &&
                resp.header("Content-Range")?.startsWith("bytes $have-") == true
            if (!resumed) part.begin()
            val start = if (resumed) have else 0L
            val total = body.contentLength().let { if (it >= 0) it + start else -1L }
            body.byteStream().use { input ->
                FileOutputStream(part.file, resumed).buffered(1 shl 16).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var done = start
                    onProgress(done, total)
                    while (true) {
                        if (isCancelled()) throw IOException("Download cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                    if (total > 0 && done != total) throw IOException("Download cut off at $done of $total bytes; it will resume")
                }
            }
        }
        part.commit(dest)
    }

    private fun redact(url: String) = url.substringBefore('#')
}

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_ID = "id"
        const val KEY_MODE = "mode"
        const val KEY_STAGE = "stage"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val CHANNEL = "downloads"
    }

    private val app get() = applicationContext as GlossApp
    private var lastReport = 0L

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val mode = DownloadMode.valueOf(inputData.getString(KEY_MODE) ?: DownloadMode.FULL.name)
        val c = app.container
        val item = c.catalog.byId(id) ?: run {
            c.catalog.load(forceNetwork = false)
            c.catalog.byId(id)
        } ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Issue not in catalog"))

        runCatching { setForeground(foregroundInfo(item, "Starting…", null)) }

        val dir = c.library.dirFor(id).apply { mkdirs() }
        try {
            if (mode != DownloadMode.SCAN && item.hasData) {
                fetchStage(item, "Text & translation", item.zipSources, File(dir, "data.zip"))
            }
            if (mode != DownloadMode.TEXT && item.hasScan) {
                fetchStage(item, "Scan", item.pdfSources, File(dir, "scan.pdf"))
            }
            if (!File(dir, "data.zip").exists() && !File(dir, "scan.pdf").exists()) {
                throw IOException("Nothing was downloaded (no sources)")
            }
            c.library.register(item)
            c.search.indexIssue(id)
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Download failed for $id", e)
            // Leave any already-complete files; an empty dir from a failed first install is removed.
            if (dir.listFiles().isNullOrEmpty()) dir.delete()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed")))
        }
    }

    private suspend fun fetchStage(item: CatalogItem, stage: String, sources: List<String>, dest: File) {
        report(item, stage, 0, 0, force = true)
        c().fetcher.fetch(sources, dest, onProgress = { done, total ->
            report(item, stage, done, total)
        }, isCancelled = { isStopped })
    }

    private fun c() = app.container

    private fun report(item: CatalogItem, stage: String, done: Long, total: Long, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastReport < 350) return
        lastReport = now
        setProgressAsync(workDataOf(KEY_STAGE to stage, KEY_DONE to done, KEY_TOTAL to total))
        val pct = if (total > 0) (done * 100 / total).toInt() else null
        runCatching {
            applicationContext.getSystemService(NotificationManager::class.java)
                .notify(item.id.hashCode(), notification(item, stage, pct))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val id = inputData.getString(KEY_ID).orEmpty()
        val item = app.container.catalog.byId(id)
        return if (item != null) foregroundInfo(item, "Starting…", null)
        else ForegroundInfo(id.hashCode(), baseBuilder().setContentTitle("Downloading").build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun foregroundInfo(item: CatalogItem, stage: String, pct: Int?) =
        ForegroundInfo(item.id.hashCode(), notification(item, stage, pct), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    private fun notification(item: CatalogItem, stage: String, pct: Int?) = baseBuilder()
        .setContentTitle("${item.magazineName} · ${item.displayTitle}")
        .setContentText(if (pct != null) "$stage · $pct%" else stage)
        .setProgress(100, pct ?: 0, pct == null)
        .build()

    private fun baseBuilder(): NotificationCompat.Builder {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
    }
}

/** Thin facade the UI talks to. One unique work chain per issue. */
class DownloadCenter(private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)

    private fun name(id: String) = "dl-$id"

    fun start(id: String, mode: DownloadMode) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(Data.Builder().putString(DownloadWorker.KEY_ID, id).putString(DownloadWorker.KEY_MODE, mode.name).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("download")
            .addTag("id:$id")
            .build()
        wm.enqueueUniqueWork(name(id), ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(id: String) {
        wm.cancelUniqueWork(name(id))
    }

    fun progress(id: String): Flow<DownloadProgress?> =
        wm.getWorkInfosForUniqueWorkFlow(name(id)).map { infos ->
            val info = infos.lastOrNull() ?: return@map null
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                    DownloadProgress(running = true, stage = "Waiting for network…")
                WorkInfo.State.RUNNING -> DownloadProgress(
                    running = true,
                    stage = info.progress.getString(DownloadWorker.KEY_STAGE) ?: "Starting…",
                    done = info.progress.getLong(DownloadWorker.KEY_DONE, 0),
                    total = info.progress.getLong(DownloadWorker.KEY_TOTAL, 0),
                )
                WorkInfo.State.FAILED -> DownloadProgress(
                    running = false,
                    error = info.outputData.getString(DownloadWorker.KEY_ERROR) ?: "Download failed",
                )
                WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> null
            }
        }

    /** Ids with a download currently queued or running. */
    fun active(): Flow<Set<String>> = wm.getWorkInfosByTagFlow("download").map { infos ->
        infos.filter { !it.state.isFinished }.mapNotNull { info ->
            info.tags.firstOrNull { it.startsWith("id:") }?.removePrefix("id:")
        }.toSet()
    }
}
