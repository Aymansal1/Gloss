package app.gloss.download

import java.io.File
import java.io.IOException

/**
 * A download in progress: `<dest>.part` holds the bytes so far and `<dest>.part.src` names the
 * source they came from, so a retry of the same source can carry on instead of starting over.
 */
internal class PartialFile(private val dest: File, private val source: String) {
    val file = File(dest.parentFile, dest.name + ".part")
    private val src = File(dest.parentFile, dest.name + ".part.src")

    init { dest.parentFile?.mkdirs() }

    /** Bytes already saved from this same source; anything left from another source is dropped. */
    fun resumable(): Long {
        val same = file.exists() && src.exists() && runCatching { src.readText() }.getOrNull() == source
        if (!same) discard()
        return if (same) file.length() else 0L
    }

    /** Starts fresh: an empty part, tagged with this source. */
    fun begin() {
        file.writeBytes(ByteArray(0))
        src.writeText(source)
    }

    fun commit(to: File = dest) {
        if (to.exists()) to.delete()
        if (!file.renameTo(to)) throw IOException("Could not move the download into place")
        src.delete()
    }

    fun discard() {
        file.delete()
        src.delete()
    }
}
