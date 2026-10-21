package app.gloss

import app.gloss.data.CatalogItem
import app.gloss.data.RosettaFormat
import app.gloss.data.RosettaFormat.BlockKind
import app.gloss.data.compareVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RosettaFormatTest {

    private val master = """
        [[PAGE_001]]
        #GA-TRANSCRIPTION
        スーパーマリオ３
        攻略特集

        #GA-TRANSLATION
        Super Mario 3
        Strategy special

        #GA-SUMMARY
        The cover.

        [[PAGE_002]]
        #GA-TRANSCRIPTION
        新作ゲーム
        [[PAGE_010]]
        # GA-TRANSCRIPTION
        最後
        # GA-TRANSLATION
        Last
    """.trimIndent()

    @Test fun splitsPagesAndSections() {
        val pages = RosettaFormat.splitPages(master)
        assertEquals(setOf(1, 2, 10), pages.keys)
        val p1 = RosettaFormat.splitSections(pages.getValue(1))
        assertEquals("スーパーマリオ３\n攻略特集", p1.transcription)
        assertEquals("Super Mario 3\nStrategy special", p1.translation)
        assertEquals("The cover.", p1.summary)
        val p2 = RosettaFormat.splitSections(pages.getValue(2))
        assertEquals("新作ゲーム", p2.transcription)
        assertEquals("", p2.translation)
        val p10 = RosettaFormat.splitSections(pages.getValue(10))
        assertEquals("最後", p10.transcription)
        assertEquals("Last", p10.translation)
    }

    @Test fun perPageFileNames() {
        assertEquals(12, RosettaFormat.perPageNumber("Famitsu_1997_p012.txt"))
        assertEquals(3, RosettaFormat.perPageNumber("dir/Mag_P3.TXT"))
        assertNull(RosettaFormat.perPageNumber("Mag_COMPLETE.txt"))
    }

    @Test fun parsesMetadata() {
        val m = RosettaFormat.parseMetadata("Magazine Name: Beep\nVersion: 1.2\nScanner URL: x.org\nJunk line\nPdf Filename: a.pdf")
        assertEquals("Beep", m["name"]); assertEquals("1.2", m["version"])
        assertEquals("x.org", m["scanner_url"]); assertEquals("a.pdf", m["pdf_filename"])
    }

    @Test fun parsesCoordinates() {
        val json = """[
          {"page": 1, "data": [
            {"text": "スーパーマリオ３", "box": [100, 200, 150, 800]},
            {"text": "zero", "box": [0, 0, 0, 0]},
            {"text": "split", "boxes": [[300, 100, 350, 400], [360, 100, 400, 400]]}
          ]},
          {"page": "2", "data": []},
          {"oops": true}
        ]"""
        val c = RosettaFormat.parseCoordinates(json)
        assertEquals(setOf(1, 2), c.keys)
        val regions = c.getValue(1)
        assertEquals(2, regions.size) // zero box dropped
        val b = regions[0].boxes[0]
        assertEquals(0.095f, b.top, 1e-4f); assertEquals(0.195f, b.left, 1e-4f)
        assertEquals(0.155f, b.bottom, 1e-4f); assertEquals(0.805f, b.right, 1e-4f)
        assertEquals(2, regions[1].boxes.size)
        assertTrue(RosettaFormat.parseCoordinates("not json").isEmpty())
    }

    @Test fun splitsMarkdownBlocks() {
        val md = """
            # Title
            First paragraph
            continues here.

            - item one
            - item two
              more of two
            1. numbered

            | A | B |
            |---|---|
            | 1 | 2 |
            After table.
        """.trimIndent()
        val kinds = RosettaFormat.splitBlocks(md).map { it.kind }
        assertEquals(listOf(BlockKind.HEADING, BlockKind.PARAGRAPH, BlockKind.LIST, BlockKind.TABLE, BlockKind.PARAGRAPH), kinds)
    }

    @Test fun matchesBlocksToRegions() {
        val blocks = RosettaFormat.splitBlocks("スーパーマリオ３\n\n攻略特集のページ\n\n関係ない")
        val regions = RosettaFormat.parseCoordinates(
            """[{"page":1,"data":[
                {"text":"攻略特集のページ","box":[10,10,20,20]},
                {"text":"スーパーマリオ３","box":[30,30,40,40]}]}]""",
        ).getValue(1)
        val m = RosettaFormat.matchBlocksToRegions(blocks, regions)
        assertEquals(1, m[0]); assertEquals(0, m[1]); assertEquals(-1, m[2])
    }

    @Test fun similarityMatchesDesktop() {
        assertEquals(1.0, RosettaFormat.similarity("abc", "abc"), 1e-9)
        assertEquals(0.4 + 0.6 * 2 / 4, RosettaFormat.similarity("ab", "abcd"), 1e-9)
        assertEquals(0.0, RosettaFormat.similarity("", "x"), 1e-9)
    }

    @Test fun parsesBothCatalogShapes() {
        val arr = """[{"id":"a","magazine_name":"Beep","pdf_filename":"Beep_01.pdf","pdf_sources":["https://x/a.pdf"],
            "tags":"action, retro","adult_content":"true","version":"1.1"}, {"no_id":1}]"""
        val items = CatalogItem.parseCatalog(arr)
        assertEquals(1, items.size)
        val i = items[0]
        assertEquals("Beep_01_Data.zip", i.zipFilename)
        assertEquals(listOf("action", "retro"), i.tags)
        assertTrue(i.adult)
        val obj = """{"update_url":"https://x","items":[{"id":7,"pdf_filename":"z.pdf","pdf_sources":["https://x/z.pdf"]}]}"""
        assertEquals("7", CatalogItem.parseCatalog(obj).single().id)
    }

    @Test fun nameFallbacksAndPlaceholders() {
        val raw = """[
          {"id":"nours-42","magazine_name":"","pdf_filename":"NOURS_Vol_42.pdf","pdf_sources":["https://x/a.pdf"]},
          {"id":"ng-1990","magazine_name":"  ","zip_sources":["https://x/b.zip"]},
          {"id":"titled","title":"LOGiN","zip_sources":["https://x/c.zip"]},
          {"id":"placeholder","magazine_name":""},
          {"id":"no-sources","magazine_name":"Beep","pdf_sources":[]}
        ]"""
        val items = CatalogItem.parseCatalog(raw)
        assertEquals(listOf("nours-42", "ng-1990", "titled"), items.map { it.id })
        assertEquals("NOURS Vol 42", items[0].magazineName)
        assertEquals("Ng 1990", items[1].magazineName)
        assertEquals("LOGiN", items[2].magazineName)
        assertTrue(items.all { it.magazineName.isNotBlank() })
    }

    @Test fun comparesVersions() {
        assertTrue(compareVersions("1.10", "1.9") > 0)
        assertTrue(compareVersions("2.0", "2") == 0)
        assertTrue(compareVersions("1.0", "1.0.1") < 0)
    }
}
