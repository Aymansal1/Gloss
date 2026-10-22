package app.gloss.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gloss.ui.reader.ReaderScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the reader the way a person does (tap a box, swipe pages, open the options) and
 * records what it looks like. Any crash along the way fails the build instead of a phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ReaderTest {
    @get:Rule(order = 0) val fixture = InstallFixture()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private fun open(page: Int? = null) {
        rule.setContent { ReaderScreen(Fixtures.ID, page, null, Fixtures.nav) }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("of 4").isPresent() }
    }

    @Test fun glossesATappedBox() {
        open()
        rule.shot("reader_1_page")
        // The box "Every stage from World 1 to World 8" sits at 8–47% across, 25–56% down.
        rule.onNodeWithContentDescription("Page 1").performTouchInput {
            click(Offset(width * 0.27f, height * 0.42f))
        }
        // A single tap only counts once the double-tap window has passed.
        rule.mainClock.advanceTimeBy(500)
        rule.shot("reader_2_gloss")
    }

    @Test fun learnsFromAGloss() {
        open()
        rule.onNodeWithContentDescription("Page 1").performTouchInput {
            click(Offset(width * 0.27f, height * 0.42f))
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithText("Learn")[0].performClick()
        // The first open unpacks the dictionary, which takes a moment.
        rule.waitUntil(60_000) { rule.onAllNodesWithText("In this passage").isPresent() }
        rule.shot("reader_9_learn")
        rule.onAllNodesWithText("ステージ")[0].performClick()
        rule.waitForIdle()
        rule.shot("reader_10_word")
    }

    @Test fun setsTheEnglishOnThePage() {
        open()
        rule.onNodeWithContentDescription("Set the English on the page").performClick()
        rule.shot("reader_3_manga")
    }

    @Test fun showsReadingOptions() {
        open()
        rule.onNodeWithContentDescription("Reading options").performClick()
        rule.shot("reader_4_options")
    }

    @Test fun swipesThroughPagesBothWays() {
        open()
        // Pages turn right to left by default, so the next page is a swipe to the right.
        repeat(3) { rule.onRoot().performTouchInput { swipeRight() }; rule.waitForIdle() }
        rule.shot("reader_5_last_page")
        repeat(2) { rule.onRoot().performTouchInput { swipeLeft() }; rule.waitForIdle() }
        rule.shot("reader_6_back_two")
    }

    @Test fun pullsUpTheSheet() {
        open(page = 2)
        // Drag the sheet up from its peek near the bottom of the screen.
        rule.onRoot().performTouchInput { swipeUp(startY = height * 0.92f, endY = height * 0.25f) }
        rule.shot("reader_7_sheet")
    }

    @Test @Config(qualifiers = "+land")
    fun showsFacingPagesSideways() {
        open(page = 2)
        rule.shot("reader_8_spread")
    }
}
