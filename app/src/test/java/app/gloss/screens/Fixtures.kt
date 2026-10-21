package app.gloss.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.gloss.GlossApp
import app.gloss.Nav
import app.gloss.data.InstalledIssue
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A small fake issue on the "phone": four pages of text, boxes on the scan for each, and
 * drawn page images standing in for the PDF (Robolectric has no PDF engine).
 */
object Fixtures {
    const val ID = "famitsu-1994-03"

    private class Box(val top: Int, val left: Int, val bottom: Int, val right: Int, val jp: String, val en: String)

    private val pages = listOf(
        listOf(
            Box(60, 80, 190, 920, "スーパーマリオ３ 攻略特集", "**Super Mario Bros. 3** strategy special"),
            Box(250, 80, 560, 470, "ワールド１から８まで全ステージを完全攻略", "Every stage from World 1 to World 8, fully mapped"),
            Box(250, 530, 560, 920, "隠しアイテムの場所と裏技を一挙公開", "Where every hidden item is, plus all the secret tricks"),
            Box(640, 80, 930, 920, "笛を使えばワープゾーンへ行ける。最短ルートはこれだ", "Use the whistle to reach the Warp Zone. Here is the fastest route"),
        ),
        listOf(
            Box(70, 80, 170, 920, "新作ソフト情報", "New releases"),
            Box(230, 80, 900, 470, "ゼルダの伝説 神々のトライフォース", "The Legend of Zelda: A Link to the Past"),
            Box(230, 530, 900, 920, "ファイナルファンタジーⅤ 発売日決定", "Final Fantasy V gets a release date"),
        ),
        listOf(
            Box(80, 80, 200, 920, "読者のページ", "Readers' page"),
            Box(260, 80, 900, 920, "ハイスコアに挑戦！今月の一位は東京都の田中くん", "High score challenge! This month's champion is Tanaka from Tokyo"),
        ),
        listOf(
            Box(400, 150, 600, 850, "次号予告", "Next issue"),
        ),
    )

    val nav = Nav(issue = {}, series = {}, read = { _, _, _ -> }, settings = {}, back = {})

    fun app(): GlossApp = ApplicationProvider.getApplicationContext()

    /** Writes the issue to the library, primes the page images, and makes WorkManager usable. */
    fun install() {
        val app = app()
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(app) }
        val c = app.container
        val dir = c.library.dirFor(ID).apply { mkdirs() }
        val issue = InstalledIssue(
            id = ID, magazineName = "Famicom Tsūshin", issueName = "1994-03-04", date = "1994-03-04",
            publisher = "ASCII", version = "1.0", coverUrl = "", adult = false,
            installedAt = 0L, dir = dir,
        )
        File(dir, "info.json").writeText(issue.toJson().toString(2))
        writeZip(File(dir, "data.zip"))
        val pdf = File(dir, "scan.pdf").apply { writeText("%PDF-1.4 stand-in for tests") }
        c.pdf.primeForTests(pdf, pages.mapIndexed { i, boxes -> drawPage(i + 1, boxes) })
        runBlocking { c.library.scan() }
    }

    private fun writeZip(zip: File) {
        val complete = buildString {
            pages.forEachIndexed { i, boxes ->
                append("[[PAGE_${"%03d".format(i + 1)}]]\n#GA-TRANSCRIPTION\n")
                append(boxes.joinToString("\n\n") { it.jp })
                append("\n\n#GA-TRANSLATION\n")
                append(boxes.joinToString("\n\n") { it.en })
                append("\n\n#GA-SUMMARY\nPage ${i + 1} of the March 1994 issue.\n\n")
            }
        }
        val coords = pages.mapIndexed { i, boxes ->
            val data = boxes.joinToString(",") { b ->
                """{"text":"${b.jp}","box":[${b.top},${b.left},${b.bottom},${b.right}]}"""
            }
            """{"page":${i + 1},"data":[$data]}"""
        }.joinToString(",", "[", "]")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("Famitsu_1994-03_COMPLETE.txt")); z.write(complete.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("Famitsu_1994-03_COORDINATES.json")); z.write(coords.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("metadata.txt")); z.write("Magazine Name: Famicom Tsūshin\nVersion: 1.0\n".toByteArray()); z.closeEntry()
        }
    }

    /** A page that reads as a magazine at a glance: masthead, photos, and columns of text. */
    private fun drawPage(number: Int, boxes: List<Box>): Bitmap {
        val w = 1000; val h = 1414
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(246, 241, 230))
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 38, 46) }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (number == 1) Color.rgb(210, 50, 40) else Color.rgb(40, 90, 170) }
        val photo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 192, 176) }
        boxes.forEachIndexed { i, b ->
            val r = RectF(b.left / 1000f * w, b.top / 1000f * h, b.right / 1000f * w, b.bottom / 1000f * h)
            if (i == 0) {
                canvas.drawRoundRect(r, 12f, 12f, accent)
            } else {
                // A photo above, then lines of "text" filling the rest of the box.
                val photoBottom = r.top + r.height() * 0.38f
                canvas.drawRect(r.left, r.top, r.right, photoBottom, photo)
                var y = photoBottom + 24f
                while (y + 14f < r.bottom) {
                    val end = if ((y.toInt() / 26) % 5 == 4) r.left + r.width() * 0.6f else r.right
                    canvas.drawRect(r.left, y, end, y + 12f, ink)
                    y += 26f
                }
            }
        }
        return bmp
    }
}

/** Installs the fake issue before the activity under test starts. */
class InstallFixture : ExternalResource() {
    override fun before() = Fixtures.install()
}

private val shotsDir = File("build/outputs/roborazzi").apply { mkdirs() }

/** The whole screen, popups and dialogs included. */
fun ComposeTestRule.shot(name: String) {
    waitForIdle()
    captureScreenRoboImage(File(shotsDir, "$name.png").path)
}

fun SemanticsNodeInteractionCollection.isPresent() = fetchSemanticsNodes().isNotEmpty()
