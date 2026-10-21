package app.gloss

import app.gloss.ui.reader.Spreads
import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadsTest {
    @Test fun singlePagesAreOnePerScreen() {
        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), Spreads.layout(3, spreads = false))
    }

    @Test fun coverStandsAloneThenFacingPages() {
        assertEquals(listOf(listOf(1), listOf(2, 3), listOf(4, 5)), Spreads.layout(5, spreads = true))
    }

    @Test fun oddLastPageStandsAlone() {
        assertEquals(listOf(listOf(1), listOf(2, 3), listOf(4)), Spreads.layout(4, spreads = true))
    }

    @Test fun findsTheScreenForAPage() {
        val screens = Spreads.layout(9, spreads = true)
        assertEquals(0, Spreads.indexOf(screens, 1))
        assertEquals(2, Spreads.indexOf(screens, 5))
        assertEquals(4, Spreads.indexOf(screens, 9))
        assertEquals(0, Spreads.indexOf(screens, 99))
    }

    @Test fun labels() {
        assertEquals("7", Spreads.label(listOf(7)))
        assertEquals("8–9", Spreads.label(listOf(8, 9)))
    }

    @Test fun emptyIssue() {
        assertEquals(emptyList<List<Int>>(), Spreads.layout(0, spreads = true))
    }
}
