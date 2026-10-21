package app.gloss.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gloss.MainActivity
import app.gloss.screens.Fixtures.Box
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The same walk as [AppTest], on an issue built to break layouts: a title too long for any row,
 * 240 pages, a paragraph-long gloss, and an unbroken run of full-width Japanese. Run again at
 * 150% system font, since readers with large text must not lose anything.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class WorstCaseTest {
    @get:Rule(order = 0) val fixture = WorstCaseFixture()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun walkThrough(prefix: String) {
        rule.onAllNodesWithText("Library")[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(TITLE_START, substring = true).isPresent() }
        rule.shot("${prefix}_1_library")
        rule.onAllNodesWithText(TITLE_START, substring = true)[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Read").isPresent() }
        rule.shot("${prefix}_2_issue")
        rule.onAllNodesWithText("Read")[0].performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("of 240", substring = true).isPresent() }
        rule.shot("${prefix}_3_reader")
        rule.onNodeWithContentDescription("Page 1").performTouchInput {
            click(Offset(width * 0.27f, height * 0.42f))
        }
        rule.mainClock.advanceTimeBy(500)
        rule.shot("${prefix}_4_long_gloss")
        rule.onRoot().performTouchInput { swipeUp(startY = height * 0.92f, endY = height * 0.25f) }
        rule.shot("${prefix}_5_sheet")
    }

    @Test fun worstCase() = walkThrough("worst")

    @Test @Config(fontScale = 1.5f)
    fun worstCaseLargeText() = walkThrough("worst_text150")

    companion object {
        const val TITLE_START = "Weekly Famicom"
    }
}

/** Installs the worst-case issue. */
class WorstCaseFixture : ExternalResource() {
    override fun before() {
        Fixtures.install(
            id = "worst-case-1994",
            magazine = "Weekly Famicom Tsūshin Special Extra Edition: The Complete Super Famicom Strategy Encyclopedia",
            issueName = "Vol. 274, Combined New Year and Spring Special Issue with Bonus Poster",
            pages = worstPages,
        )
    }

    private val worstPages: List<List<Box>> = listOf(
        listOf(
            Box(60, 80, 190, 920,
                "週刊ファミコン通信特別増刊号　スーパーファミコン完全攻略大百科　永久保存版",
                "**Weekly Famicom Tsūshin Special Extra Edition**: the complete Super Famicom strategy encyclopedia, a keepsake edition"),
            Box(250, 80, 560, 470,
                "ワールド１から８まで全ステージを完全攻略。隠しブロック、ワープゾーン、無限１ＵＰの場所まで、編集部が三か月かけて調べ上げた情報をすべて公開する。これを読めば君もマリオ博士だ！",
                "Every stage from World 1 to World 8, completely mapped. Hidden blocks, Warp Zones, even where to find infinite 1-Ups: everything our editors spent three months digging up, all published here. Read this and you'll be a Mario expert too! Note that some tricks only work on the first print run of the cartridge, so check the label on the back before you try them."),
            Box(250, 530, 560, 920,
                "ＳＵＰＥＲＭＡＲＩＯＫＡＲＴＴＩＭＥＡＴＴＡＣＫＲＥＣＯＲＤＳ１９９４",
                "SUPERMARIOKARTTIMEATTACKCHAMPIONSHIPOFFICIALRANKINGTABLE1994"),
            Box(640, 80, 930, 920, "笛", "Whistle"),
        ),
    ) + (2..240).map { n -> listOf(Box(400, 150, 600, 850, "ページ$n", "Page $n")) }
}
