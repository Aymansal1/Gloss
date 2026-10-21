package app.gloss

import app.gloss.search.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test fun indexesJapaneseAsBigrams() {
        assertEquals("ファ ァミ ミコ コン ン通 通信 2", SearchText.bigramIndexText("ファミコン通信２"))
        assertEquals("nes 版", SearchText.bigramIndexText("ＮＥＳ　版"))
    }

    @Test fun buildsQueries() {
        assertEquals("新作", SearchText.parseQuery("新作").match)
        assertEquals("\"マリ リオ\"", SearchText.parseQuery("マリオ").match)
        assertEquals("新*", SearchText.parseQuery("新").match)
        assertEquals("\"strategy guide\"", SearchText.parseQuery("\"Strategy Guide\"").match)
        assertEquals("strat*", SearchText.parseQuery("strat*").match)
        val q = SearchText.parseQuery("mario -kart")
        assertEquals("\"mario\"", q.match)
        assertEquals(listOf("kart"), q.excluded)
        assertNull(SearchText.parseQuery("  ").match)
    }

    @Test fun snippetsHighlightTheHit() {
        val (s, ranges) = SearchText.snippet("x".repeat(200) + " the Mario special " + "y".repeat(200), listOf("mario"))
        assertTrue(s.startsWith("…") && s.endsWith("…"))
        assertEquals("Mario", s.substring(ranges[0].first, ranges[0].last + 1))
    }
}
