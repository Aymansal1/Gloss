package app.gloss.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.gloss.ui.components.CoverInfo
import app.gloss.ui.components.SpinnableBook
import app.gloss.ui.theme.Gloss
import app.gloss.ui.theme.GlossTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The spinning cover at fixed angles: a downloaded issue (its last page as the back) on top,
 * and one that isn't on the phone (the made-up back) below. At +28° (where it rests) the spine shows on the right; at -35° the page edges show on the left.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BookTest {
    @get:Rule(order = 0) val fixture = InstallFixture()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val angles = listOf(0f, 28f, -35f, 90f, 208f)

    @Test fun turnsAtEveryAngle() {
        val installed = CoverInfo(Fixtures.ID, "Famicom Tsūshin", "March 4, 1994", "1994", "")
        val stock = CoverInfo("beep-1987-06", "Beep", "June 1987", "1987", "")
        rule.setContent {
            GlossTheme(dark = true) {
                Column(
                    Modifier.fillMaxSize().background(Gloss.colors.ground).padding(vertical = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(48.dp),
                ) {
                    listOf(installed, stock).forEach { info ->
                        Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            angles.forEach { a ->
                                SpinnableBook(info, Modifier.width(68.dp), publisher = "ASCII", pinnedAngle = a)
                            }
                        }
                    }
                }
            }
        }
        // The back page is rendered and decoded off the main thread, then handed back to it.
        repeat(30) { Thread.sleep(100); rule.waitForIdle() }
        rule.shot("book_angles")
    }
}
