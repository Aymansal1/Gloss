package app.gloss

import app.gloss.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun parsesTheLatestRelease() {
        val json = """
            {"tag_name":"v1.0.42","body":"Faster search","html_url":"https://github.com/Aymansal1/Gloss/releases/tag/v1.0.42",
             "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
                       {"name":"Gloss-1.0.42.apk","browser_download_url":"https://x/Gloss-1.0.42.apk"}]}
        """.trimIndent()
        val r = UpdateChecker.parseLatest(json)!!
        assertEquals("1.0.42", r.version)
        assertEquals("Faster search", r.notes)
        assertEquals("https://x/Gloss-1.0.42.apk", r.apkUrl)
    }

    @Test fun releaseWithoutApkStillParses() {
        val r = UpdateChecker.parseLatest("""{"tag_name":"v2.0.0","html_url":"https://x"}""")!!
        assertNull(r.apkUrl)
        assertNull(UpdateChecker.parseLatest("{}"))
    }

    @Test fun comparesVersionsNumerically() {
        assertTrue(UpdateChecker.isNewer("1.0.10", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("v1.1", "1.0.99"))
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.0.10"))
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.0.9"))
        assertFalse(UpdateChecker.isNewer("1.0.9", ""))
    }
}
