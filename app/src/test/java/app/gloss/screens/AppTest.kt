package app.gloss.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gloss.MainActivity
import app.gloss.ui.settings.SettingsScreen
import app.gloss.ui.theme.GlossTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The whole app from launch: the home tabs, then Library → issue → reader, in light and dark. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AppTest {
    @get:Rule(order = 0) val fixture = InstallFixture()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun walkThrough(prefix: String) {
        rule.shot("${prefix}_1_discover")
        rule.onAllNodesWithText("Library")[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Famicom Tsūshin").isPresent() }
        rule.shot("${prefix}_2_library")
        rule.onAllNodesWithText("Famicom Tsūshin")[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Read").isPresent() }
        rule.shot("${prefix}_3_issue")
        rule.onAllNodesWithText("Read")[0].performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("of 4").isPresent() }
        rule.shot("${prefix}_4_reader")
    }

    @Test fun light() = walkThrough("app_light")

    @Test @Config(qualifiers = "+night")
    fun dark() = walkThrough("app_dark")

    @Test fun search() {
        rule.onNodeWithContentDescription("Search").performClick()
        rule.shot("app_light_5_search")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsTest {
    @get:Rule(order = 0) val fixture = InstallFixture()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun settings() {
        rule.setContent { GlossTheme(dark = false) { SettingsScreen(Fixtures.nav) } }
        rule.shot("settings")
    }
}
