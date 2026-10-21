package app.gloss

import app.gloss.data.Bookmark
import app.gloss.data.ReadingBackup
import app.gloss.data.ReadingCodec
import app.gloss.data.RecentRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingCodecTest {
    private val sample = ReadingBackup(
        bookmarks = listOf(Bookmark("famitsu-1994-05", 42, "Chrono Trigger preview", 1_000L)),
        pages = mapOf("famitsu-1994-05" to 42, "beep-1987-01" to 7),
        recents = listOf(RecentRead("famitsu-1994-05", 42, 2_000L)),
        settings = mapOf("rtl" to "true", "textScale" to "1.2"),
    )

    @Test fun backupRoundTrips() {
        val back = ReadingCodec.decodeBackup(ReadingCodec.encodeBackup(sample, 5_000L, "1.0.3"))
        assertEquals(sample, back)
    }

    @Test fun rejectsOtherFiles() {
        val notOurs = runCatching { ReadingCodec.decodeBackup("""{"hello":"world"}""") }
        assertTrue(notOurs.exceptionOrNull() is IllegalArgumentException)
        val garbage = runCatching { ReadingCodec.decodeBackup("not json") }
        assertTrue(garbage.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun refusesBackupsFromTheFuture() {
        val raw = ReadingCodec.encodeBackup(sample, 0L, "9.9").replace(Regex("\"version\":\\s*1"), "\"version\": 99")
        val e = runCatching { ReadingCodec.decodeBackup(raw) }.exceptionOrNull()
        assertTrue(e?.message.orEmpty().contains("newer"))
    }

    @Test fun mergeKeepsTheNewerPlaceAndEveryBookmark() {
        val local = ReadingBackup(
            bookmarks = listOf(Bookmark("a", 3, "", 10L)),
            pages = mapOf("a" to 3, "b" to 20),
            recents = listOf(RecentRead("a", 3, 100L), RecentRead("b", 20, 500L)),
            settings = mapOf("rtl" to "true"),
        )
        val incoming = ReadingBackup(
            bookmarks = listOf(Bookmark("a", 3, "the boss map", 50L), Bookmark("c", 1, "", 60L)),
            pages = mapOf("a" to 9, "b" to 2, "c" to 1),
            recents = listOf(RecentRead("a", 9, 300L), RecentRead("b", 2, 200L)),
            settings = mapOf("rtl" to "false"),
        )
        val m = ReadingCodec.merge(local, incoming)
        assertEquals(9, m.pages["a"])  // read later on the other phone
        assertEquals(20, m.pages["b"]) // read later here
        assertEquals(1, m.pages["c"])  // only known there
        assertEquals(2, m.bookmarks.size)
        val a3 = m.bookmarks.first { it.issueId == "a" && it.page == 3 }
        assertEquals("the boss map", a3.note)
        assertEquals(10L, a3.createdAt)
        assertEquals(listOf("b", "a"), m.recents.map { it.issueId })
        assertEquals("true", m.settings["rtl"]) // this phone's settings stay
    }
}
