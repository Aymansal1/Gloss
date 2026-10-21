package app.gloss

import app.gloss.download.MegaDownloader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * The vector (mega_body.bin + mega_vector.json) was generated with Rosetta's own
 * mega_download.py helpers, so passing here means the Kotlin port decrypts and
 * verifies exactly like the reference implementation.
 */
class MegaTest {
    private fun res(name: String) = javaClass.classLoader!!.getResourceAsStream(name)!!

    @Test fun parsesShareLinks() {
        assertEquals("AbC" to "k3y", MegaDownloader.parseShare("https://mega.nz/file/AbC#k3y"))
        assertEquals("H" to "K", MegaDownloader.parseShare("https://mega.co.nz/#!H!K"))
        assertFalse(MegaDownloader.isMegaUrl("https://archive.org/download/x.pdf"))
        assertFalse(MegaDownloader.isMegaUrl("http://mega.nz/file/AbC#k3y"))
    }

    @Test fun chunkScheduleMatchesMega() {
        val c = MegaDownloader.chunks(300_007)
        assertEquals(listOf(0L to 0x20000L, 0x20000L to (300_007L - 0x20000L)), c)
        assertEquals(300_007L, c.sumOf { it.second })
    }

    @Test fun decryptsAndVerifiesReferenceVector() {
        val v = JSONObject(res("mega_vector.json").bufferedReader().readText())
        val nk = MegaDownloader.decodeNodeKey(v.getString("key"))
        assertEquals(v.getString("name"), MegaDownloader.decryptAttributes(v.getString("at"), nk.key).getString("n"))
        val out = ByteArrayOutputStream()
        MegaDownloader.decryptStream(res("mega_body.bin"), out, v.getLong("size"), nk)
        val sha = MessageDigest.getInstance("SHA-256").digest(out.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(v.getString("sha256"), sha)
    }

    @Test fun rejectsTamperedBody() {
        val v = JSONObject(res("mega_vector.json").bufferedReader().readText())
        val nk = MegaDownloader.decodeNodeKey(v.getString("key"))
        val body = res("mega_body.bin").readBytes()
        body[1234] = (body[1234].toInt() xor 1).toByte()
        val failed = runCatching {
            MegaDownloader.decryptStream(body.inputStream(), ByteArrayOutputStream(), v.getLong("size"), nk)
        }.isFailure
        assertTrue("tampered data must fail the MAC check", failed)
    }

    @Test fun resumesFromTheLastWholeChunk() {
        val v = JSONObject(res("mega_vector.json").bufferedReader().readText())
        val nk = MegaDownloader.decodeNodeKey(v.getString("key"))
        val size = v.getLong("size")
        val body = res("mega_body.bin").readBytes()
        val full = ByteArrayOutputStream().also { MegaDownloader.decryptStream(body.inputStream(), it, size, nk) }.toByteArray()

        // A download that died partway through the second chunk.
        val part = File.createTempFile("mega", ".part").apply { deleteOnExit(); writeBytes(full.copyOf(200_000)) }
        val (start, mac) = MegaDownloader.resumePoint(part, size, nk)
        assertEquals(0x20000L, start)
        assertEquals(0x20000L, part.length())

        FileOutputStream(part, true).use { out ->
            val rest = body.copyOfRange(start.toInt(), body.size).inputStream()
            MegaDownloader.decryptStream(rest, out, size, nk, start = start, mac = mac)
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(part.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(v.getString("sha256"), sha)
    }
}
