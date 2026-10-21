package app.gloss

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gloss.download.NotEnoughSpace
import app.gloss.download.PermanentDownloadError
import app.gloss.download.SourceFetcher
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/** Which download failures are worth retrying, and the free-space check, against canned responses. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DownloadsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val hits = mutableListOf<String>()

    private fun fetcher(answer: (String) -> Response.Builder) = SourceFetcher(
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val url = chain.request().url.toString()
            hits += url
            answer(url).request(chain.request()).protocol(Protocol.HTTP_1_1).message("").build()
        }).build(),
    )

    private fun fetch(f: SourceFetcher, vararg urls: String): Exception? = runCatching {
        f.fetch(urls.toList(), tmp.root.resolve("scan.pdf"), { _, _ -> }, { false })
    }.exceptionOrNull() as Exception?

    @Test fun downloadsAFile() {
        val f = fetcher { Response.Builder().code(200).body("hello".toResponseBody()) }
        assertEquals(null, fetch(f, "https://a.example/scan.pdf"))
        assertEquals("hello", tmp.root.resolve("scan.pdf").readText())
    }

    @Test fun missingFilesAreNotRetried() {
        val f = fetcher { Response.Builder().code(404).body("".toResponseBody()) }
        val e = fetch(f, "https://a.example/x.pdf", "https://b.example/x.pdf")
        assertTrue("$e", e is PermanentDownloadError)
        assertEquals(2, hits.size)
    }

    @Test fun serverTroubleIsRetried() {
        val f = fetcher { url -> Response.Builder().code(if ("a." in url) 404 else 503).body("".toResponseBody()) }
        val e = fetch(f, "https://a.example/x.pdf", "https://b.example/x.pdf")
        assertTrue("$e", e is IOException)
        assertFalse("$e", e is PermanentDownloadError)
    }

    @Test fun refusesAFileBiggerThanTheFreeSpace() {
        val huge = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = tmp.root.usableSpace + (1L shl 30)
            override fun source(): BufferedSource = Buffer()
        }
        val f = fetcher { Response.Builder().code(200).body(huge) }
        val e = fetch(f, "https://a.example/x.pdf", "https://b.example/x.pdf")
        assertTrue("$e", e is NotEnoughSpace)
        assertTrue(e!!.message!!.startsWith("Not enough space: needs "))
        // Another mirror won't make room, so it stops at the first.
        assertEquals(1, hits.size)
        assertFalse(tmp.root.resolve("scan.pdf").exists())
    }
}
