package app.gloss.download

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Downloads anonymous public Mega file shares without the Mega SDK:
 * fetch metadata from the public API, stream the encrypted body, decrypt with
 * AES-128-CTR and verify Mega's chunked CBC-MAC before accepting the file.
 * Port of Rosetta's mega_download.py.
 */
class MegaDownloader(private val http: OkHttpClient) {

    companion object {
        private val HOSTS = setOf("mega.nz", "mega.co.nz", "mega.io")
        private const val API = "https://g.api.mega.co.nz/cs"

        fun isMegaUrl(url: String): Boolean = runCatching { parseShare(url) }.isSuccess

        /** Returns (handle, base64url key) for /file/HANDLE#KEY and legacy #!HANDLE!KEY links. */
        fun parseShare(url: String): Pair<String, String> {
            val u = URI(url)
            require(u.scheme == "https" && u.host?.lowercase() in HOSTS) { "Not a Mega URL" }
            val path = u.rawPath.orEmpty()
            val frag = u.rawFragment.orEmpty()
            if (path.startsWith("/file/") && frag.isNotEmpty()) {
                val handle = path.removePrefix("/file/").substringBefore('/')
                require(handle.isNotEmpty()) { "Missing Mega handle" }
                return handle to frag
            }
            if ((path.isEmpty() || path == "/") && frag.startsWith("!")) {
                val parts = frag.substring(1).split('!', limit = 2)
                require(parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) { "Bad legacy Mega link" }
                return parts[0] to parts[1]
            }
            throw IllegalArgumentException("Unsupported Mega share URL")
        }

        internal fun b64url(s: String): ByteArray =
            Base64.getUrlDecoder().decode(s.trim().trimEnd('=').let { it + "=".repeat((4 - it.length % 4) % 4) })

        internal fun toInts(b: ByteArray): IntArray {
            val buf = ByteBuffer.wrap(b)
            return IntArray(b.size / 4) { buf.int }
        }

        internal fun toBytes(vararg ints: Int): ByteArray {
            val buf = ByteBuffer.allocate(ints.size * 4)
            ints.forEach { buf.putInt(it) }
            return buf.array()
        }

        /** Mega chunk boundaries: 128 KiB growing by 128 KiB up to 1 MiB, then 1 MiB. */
        internal fun chunks(size: Long): List<Pair<Long, Long>> {
            val out = ArrayList<Pair<Long, Long>>()
            var p = 0L
            var s = 0x20000L
            while (p + s < size) {
                out += p to s
                p += s
                if (s < 0x100000) s += 0x20000
            }
            out += p to (size - p)
            return out
        }

        internal data class NodeKey(val key: ByteArray, val iv: IntArray, val metaMac: IntArray)

        internal fun decodeNodeKey(keyB64: String): NodeKey {
            val raw = b64url(keyB64)
            require(raw.size == 32) { "Invalid Mega key length" }
            val w = toInts(raw)
            return NodeKey(
                key = toBytes(w[0] xor w[4], w[1] xor w[5], w[2] xor w[6], w[3] xor w[7]),
                iv = intArrayOf(w[4], w[5]),
                metaMac = intArrayOf(w[6], w[7]),
            )
        }

        internal fun decryptAttributes(atB64: String, key: ByteArray): JSONObject {
            val c = Cipher.getInstance("AES/CBC/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
            val data = c.doFinal(b64url(atB64))
            val text = String(data, Charsets.UTF_8)
            require(text.startsWith("MEGA")) { "Invalid Mega decryption key" }
            return JSONObject(text.substring(4, text.lastIndexOf('}') + 1))
        }

        /**
         * Decrypts a Mega file body of [size] bytes from [input] into [output] and verifies
         * the file MAC. Throws [MegaException] on truncation, cancellation or MAC mismatch.
         */
        internal fun decryptStream(
            input: InputStream,
            output: OutputStream,
            size: Long,
            nk: NodeKey,
            onProgress: (Long, Long) -> Unit = { _, _ -> },
            isCancelled: () -> Boolean = { false },
        ) {
            val keySpec = SecretKeySpec(nk.key, "AES")
            val ctr = Cipher.getInstance("AES/CTR/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(toBytes(nk.iv[0], nk.iv[1], 0, 0)))
            }
            val macAcc = Cipher.getInstance("AES/CBC/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(ByteArray(16)))
            }
            val chunkIv = IvParameterSpec(toBytes(nk.iv[0], nk.iv[1], nk.iv[0], nk.iv[1]))
            val chunkMacCipher = Cipher.getInstance("AES/CBC/NoPadding")
            var mac = ByteArray(16)
            var done = 0L
            val readBuf = ByteArray(1 shl 16)
            for ((_, chunkSize) in chunks(size)) {
                val raw = ByteArray(chunkSize.toInt())
                var filled = 0
                while (filled < raw.size) {
                    if (isCancelled()) throw MegaException("Download cancelled")
                    val n = input.read(readBuf, 0, minOf(readBuf.size, raw.size - filled))
                    if (n < 0) throw MegaException("Mega download truncated")
                    System.arraycopy(readBuf, 0, raw, filled, n)
                    filled += n
                    done += n
                    onProgress(done, size)
                }
                val plain = ctr.update(raw) ?: ByteArray(0)
                if (plain.isNotEmpty()) {
                    val padded = if (plain.size % 16 == 0) plain else plain.copyOf(plain.size + (16 - plain.size % 16))
                    chunkMacCipher.init(Cipher.ENCRYPT_MODE, keySpec, chunkIv)
                    val enc = chunkMacCipher.doFinal(padded)
                    mac = macAcc.update(enc.copyOfRange(enc.size - 16, enc.size))
                }
                output.write(plain)
            }
            val fm = toInts(mac)
            if ((fm[0] xor fm[1]) != nk.metaMac[0] || (fm[2] xor fm[3]) != nk.metaMac[1]) {
                throw MegaException("Mega download failed integrity check")
            }
        }
    }

    class MegaException(message: String) : IOException(message)

    /**
     * Downloads [url] to [dest] (written via a temp file, replaced only after the MAC verifies).
     * [onProgress] receives (bytesDone, totalBytes). [isCancelled] is polled between reads.
     * Returns the file name stored on Mega.
     */
    fun download(
        url: String,
        dest: File,
        onProgress: (Long, Long) -> Unit,
        isCancelled: () -> Boolean,
    ): String {
        val (handle, keyB64) = parseShare(url)
        val nk = decodeNodeKey(keyB64)
        val info = fileInfo(handle)
        val attrs = decryptAttributes(info.getString("at"), nk.key)
        val size = info.getLong("s")

        val tmp = File(dest.parentFile, dest.name + ".part")
        dest.parentFile?.mkdirs()
        try {
            val req = Request.Builder().url(info.getString("g")).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw MegaException("Mega storage HTTP ${resp.code}")
                val input = resp.body?.byteStream() ?: throw MegaException("Empty Mega response")
                tmp.outputStream().buffered(1 shl 16).use { out ->
                    decryptStream(input, out, size, nk, onProgress, isCancelled)
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw MegaException("Could not move downloaded file into place")
            return attrs.optString("n", dest.name)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun fileInfo(handle: String): JSONObject {
        // "ssl": 2 asks Mega for an HTTPS storage URL.
        val body = JSONArray().put(JSONObject().put("a", "g").put("g", 1).put("ssl", 2).put("p", handle))
        val req = Request.Builder()
            .url("$API?id=${Random.nextInt(0, Int.MAX_VALUE)}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw MegaException("Mega API HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty().trim()
            if (text.isEmpty()) throw MegaException("Empty Mega API response")
            if (!text.startsWith("[")) throw MegaException("Mega API error $text")
            val arr = JSONArray(text)
            val first = arr.opt(0)
            if (first is Number) throw MegaException("Mega API error $first (file removed or link invalid)")
            val info = first as? JSONObject ?: throw MegaException("Unexpected Mega API response")
            if (!info.has("g")) throw MegaException("Mega file not accessible")
            return info
        }
    }
}
