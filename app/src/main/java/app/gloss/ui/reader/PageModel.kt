package app.gloss.ui.reader

import app.gloss.data.PageText
import app.gloss.data.RosettaFormat
import app.gloss.data.TextRegion
import app.gloss.search.SearchText

/**
 * One page, ready for the reader. Original block i pairs with translation block i (same
 * convention as the desktop app), and [blockToRegion] links block i to a box on the scan.
 */
class PageModel(val number: Int, text: PageText, val regions: List<TextRegion>) {
    val original = RosettaFormat.splitBlocks(text.transcription)
    val translation = RosettaFormat.splitBlocks(text.translation)
    val summary = RosettaFormat.splitBlocks(text.summary)
    val blockToRegion: IntArray = RosettaFormat.matchBlocksToRegions(original, regions)
    val hasText get() = original.isNotEmpty() || translation.isNotEmpty() || summary.isNotEmpty()

    /** Block indices linked to region [regionIdx] (index into [regions]). */
    fun blocksForRegion(regionIdx: Int): List<Int> = blockToRegion.indices.filter { blockToRegion[it] == regionIdx }

    fun regionForBlock(block: Int): Int? = blockToRegion.getOrNull(block)?.takeIf { it >= 0 }

    /** Translation for a region: the paired translation blocks, falling back to the region's own text. */
    fun translationForRegion(regionIdx: Int): List<RosettaFormat.Block> =
        blocksForRegion(regionIdx).mapNotNull { translation.getOrNull(it) }

    /** First block (by index) whose original or translation contains any of [terms]. */
    fun firstBlockMatching(terms: List<String>): Int? {
        if (terms.isEmpty()) return null
        val count = maxOf(original.size, translation.size)
        for (i in 0 until count) {
            val hay = SearchText.normalize((original.getOrNull(i)?.text ?: "") + "\n" + (translation.getOrNull(i)?.text ?: ""))
            if (terms.any { hay.contains(it) }) return i
        }
        return null
    }

    /** Region whose detected text contains any of [terms] (for queries that miss the block pairing). */
    fun firstRegionMatching(terms: List<String>): Int? {
        if (terms.isEmpty()) return null
        return regions.indices.firstOrNull { r -> terms.any { SearchText.normalize(regions[r].text).contains(it) } }
    }

    /**
     * Region under a page-relative point (0..1), preferring the smallest box when they overlap,
     * so a caption inside a big article box can still be picked.
     */
    fun regionAt(x: Float, y: Float): Int? {
        var best: Int? = null
        var bestArea = Float.MAX_VALUE
        regions.forEachIndexed { i, r ->
            r.boxes.forEach { b ->
                if (x >= b.left && x <= b.right && y >= b.top && y <= b.bottom && b.area < bestArea) {
                    best = i; bestArea = b.area
                }
            }
        }
        return best
    }
}

/** What the reader currently has selected on a page. */
data class Selection(val page: Int, val block: Int?, val region: Int?)
