package app.gloss.pdf

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders PDF pages with Android's built-in PdfRenderer (fine for image-scan PDFs).
 * One document is open at a time and access is serialised; rendered bitmaps are cached.
 *
 * A page that crashes inside the system's PDF engine takes the whole app down and can't be
 * caught, and the reader would hit it again on every open. So each page is noted in a file
 * before it's drawn and the note removed after. If the app then died of a native crash with the
 * note still there, that page is skipped from then on instead.
 */
class PdfPages(context: Context) {
    private val marker = File(context.filesDir, "pdf-rendering")
    private val brokenFile = File(context.filesDir, "pdf-broken-pages")
    private val broken: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        runCatching { if (brokenFile.exists()) broken += brokenFile.readLines().filter { it.isNotBlank() } }
        val last = marker.takeIf { it.exists() }?.let { runCatching { it.readText().trim() }.getOrNull() }
        if (!last.isNullOrEmpty() && diedOfNativeCrash(context)) {
            broken += last
            runCatching { brokenFile.appendText(last + "\n") }
            Log.w("PdfPages", "Skipping $last from now on: it crashed the PDF engine")
        }
        marker.delete()
    }

    private fun diedOfNativeCrash(context: Context): Boolean = runCatching {
        context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(null, 0, 1)
            .firstOrNull()?.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
    }.getOrDefault(false)

    /** True for a page that crashed the PDF engine before; it's shown as a placeholder. */
    fun isBroken(file: File, pageIndex: Int): Boolean = pageKey(file, pageIndex) in broken

    private fun pageKey(file: File, pageIndex: Int) = "${file.path}#$pageIndex"

    private inline fun <T> guarded(file: File, pageIndex: Int, block: () -> T): T {
        runCatching { marker.writeText(pageKey(file, pageIndex)) }
        try {
            return block()
        } finally {
            marker.delete()
        }
    }

    private val mutex = Mutex()
    private var openFile: File? = null
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val aspects = ConcurrentHashMap<String, FloatArray>()

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private fun ensureOpen(file: File): PdfRenderer {
        if (openFile == file && renderer != null) return renderer!!
        closeLocked()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(pfd)
        fd = pfd; renderer = r; openFile = file
        return r
    }

    private fun closeLocked() {
        runCatching { renderer?.close() }
        runCatching { fd?.close() }
        renderer = null; fd = null; openFile = null
    }

    suspend fun close() = mutex.withLock { closeLocked() }

    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { ensureOpen(file).pageCount }.getOrDefault(0) }
    }

    /** width / height for each page (0-based), computed once per document. */
    suspend fun aspects(file: File): FloatArray = withContext(Dispatchers.IO) {
        aspects[file.path]?.let { return@withContext it }
        mutex.withLock {
            val result = runCatching {
                val r = ensureOpen(file)
                FloatArray(r.pageCount) { i ->
                    if (isBroken(file, i)) 0f
                    else guarded(file, i) { r.openPage(i).use { p -> p.width.toFloat() / p.height.coerceAtLeast(1) } }
                }
            }.getOrElse { FloatArray(0) }
            aspects[file.path] = result
            result
        }
    }

    fun cached(file: File, pageIndex: Int, widthPx: Int): Bitmap? = cache.get(key(file, pageIndex, bucket(widthPx)))

    /** Renders page [pageIndex] (0-based) at [widthPx] wide. Width is bucketed to reuse cache entries. */
    suspend fun render(file: File, pageIndex: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val w = bucket(widthPx)
        val k = key(file, pageIndex, w)
        cache.get(k)?.let { return@withContext it }
        mutex.withLock {
            cache.get(k)?.let { return@withLock it }
            try {
                val r = ensureOpen(file)
                if (pageIndex !in 0 until r.pageCount || isBroken(file, pageIndex)) return@withLock null
                guarded(file, pageIndex) { r.openPage(pageIndex).use { page ->
                    val h = (w.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(k, bmp)
                    bmp
                } }
            } catch (e: Exception) {
                Log.e("PdfPages", "Render failed ${file.name} p$pageIndex", e)
                null
            } catch (e: OutOfMemoryError) {
                // Big zoomed pages add up; drop the cache and let the page fall back to what's on screen.
                Log.e("PdfPages", "Out of memory rendering ${file.name} p$pageIndex at $w", e)
                cache.evictAll()
                null
            }
        }
    }

    /** First page as a small JPEG next to the issue, used as a cover when the catalog has none. */
    suspend fun ensureCover(pdf: File, out: File): File? = withContext(Dispatchers.IO) {
        if (out.exists() && out.lastModified() >= pdf.lastModified()) return@withContext out
        if (!pdf.exists()) return@withContext null
        val bmp = render(pdf, 0, 480) ?: return@withContext null
        runCatching {
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            out
        }.getOrNull()
    }

    private fun bucket(w: Int): Int = when {
        w <= 160 -> 160 // page thumbnails
        w <= 480 -> 480
        w <= 1080 -> 1080
        w <= 1440 -> 1440
        w <= 2160 -> 2160
        else -> 2880
    }

    private fun key(file: File, page: Int, w: Int) = "${file.path}#$page@$w"
}
