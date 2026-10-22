package app.gloss.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gloss.learn.SavedWord
import app.gloss.learn.Tokenizer
import app.gloss.ui.intro.IntroScreen
import app.gloss.ui.learn.KanjiCard
import app.gloss.ui.learn.LearnScreen
import app.gloss.ui.theme.GlossTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Learn features against the real bundled dictionary, and how their screens look. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LearnTest {
    @get:Rule(order = 0) val fixture = InstallFixture()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val japanese get() = Fixtures.app().container.japanese

    @Test fun findsDictionaryFormsOfInflectedWords() = runBlocking {
        assertTrue("dictionary should be bundled", japanese.ready())
        fun bases(text: String) = runBlocking { japanese.tokenize(text) }.filter { it.word != null }.map { it.base }
        assertEquals(listOf("食べる"), bases("食べなかった"))
        assertTrue(bases("笛を使えばワープゾーンへ行ける").containsAll(listOf("笛", "使う")))
        assertTrue("ついに" in bases("冬の大作がついに登場"))
        val reading = japanese.tokenize("来ました").first { it.word != null }.reading
        assertEquals("きました", reading)
    }

    @Test fun readsKanjiWithStrokes() = runBlocking {
        val k = japanese.kanji("食")
        assertNotNull(k)
        assertEquals(9, k!!.strokes)
        assertEquals(9, k.paths.size)
    }

    @Test fun pairsReadingsWithKanji() {
        val t = runBlocking { japanese.tokenize("攻略特集") }
        assertTrue(t.all { it.word != null })
        assertEquals(listOf("攻略" to "こうりゃく"), t.first().ruby)
        // Pure function, no dictionary: unknown katakana stays whole.
        assertEquals(listOf("ファミコン"), Tokenizer.tokenize("ファミコン") { emptyList() }.map { it.surface })
    }

    @Test fun learnTab() {
        val c = Fixtures.app().container
        runBlocking { japanese.ready() }
        val word = runBlocking { japanese.tokenize("攻略") }.first().word!!
        if (!c.words.has(word.id)) {
            c.words.toggle(SavedWord(word.id, "攻略", word.reading, word.meaning, Fixtures.ID, "Famicom Tsūshin · 4 Mar 1994", 1, "スーパーマリオ３ 攻略特集", 0L))
        }
        rule.setContent { GlossTheme(dark = false) { LearnScreen(Fixtures.nav) } }
        rule.shot("learn_1_words")
        rule.onAllNodesWithText("攻略  こうりゃく")[0].performClick()
        rule.waitForIdle()
        rule.shot("learn_2_word")
    }

    @Test fun slangList() {
        rule.setContent { GlossTheme(dark = true) { LearnScreen(Fixtures.nav) } }
        rule.onAllNodesWithText("Gamer slang")[0].performClick()
        rule.shot("learn_3_slang")
    }

    @Test fun kanjiCard() {
        rule.setContent { GlossTheme(dark = true) { KanjiCard("攻", onBack = null) } }
        rule.waitUntil(30_000) { rule.onAllNodesWithText("On").isPresent() }
        rule.shot("learn_4_kanji")
    }

    @Test fun welcomeLight() {
        rule.setContent { GlossTheme(dark = false) { IntroScreen(onDone = {}, animate = false) } }
        rule.shot("intro_light")
    }

    @Test @Config(qualifiers = "+night")
    fun welcomeDark() {
        rule.setContent { GlossTheme(dark = true) { IntroScreen(onDone = {}, animate = false) } }
        rule.shot("intro_dark")
    }
}
