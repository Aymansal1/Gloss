package app.gloss

import app.gloss.learn.Difficulty
import app.gloss.learn.History
import app.gloss.learn.Slang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoryTest {
    @Test fun daysWrapRoundNewYear() {
        assertEquals(2, History.dayGap(LocalDate.parse("1990-12-31"), LocalDate.parse("2026-01-02")))
        assertEquals(0, History.dayGap(LocalDate.parse("1990-11-21"), LocalDate.parse("2026-11-21")))
    }

    @Test fun picksThisWeeksFact() {
        val (fact, dated) = History.forDay(LocalDate.parse("2026-12-04"))
        assertTrue(dated)
        assertEquals(1994, fact.date!!.year) // PlayStation, 3 Dec 1994
    }

    @Test fun slangPrefersTheLongerTerm() {
        val hits = Slang.inText("攻略本を見た").map { it.term }
        assertEquals(listOf("攻略本"), hits)
    }

    @Test fun gradesDifficulty() {
        val easy = "日本".repeat(30)
        assertEquals(Difficulty.Level.Easy, Difficulty.of(easy, mapOf("日" to 1, "本" to 1))!!.level)
        val hard = "攻略".repeat(30)
        assertEquals(Difficulty.Level.Hard, Difficulty.of(hard, mapOf("攻" to 8, "略" to 5))!!.level)
    }
}
