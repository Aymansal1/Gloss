package app.gloss.ui.reader

/**
 * How pages are grouped onto the screen. Single pages are one per screen; spreads put facing
 * pages together the way the magazine was printed: the cover alone, then 2–3, 4–5, and so on.
 */
object Spreads {
    /** Screens for [total] pages, each a list of 1-based page numbers in reading order. */
    fun layout(total: Int, spreads: Boolean): List<List<Int>> {
        if (total <= 0) return emptyList()
        if (!spreads) return (1..total).map { listOf(it) }
        val out = ArrayList<List<Int>>()
        out += listOf(1)
        var p = 2
        while (p <= total) {
            out += if (p + 1 <= total) listOf(p, p + 1) else listOf(p)
            p += 2
        }
        return out
    }

    /** Index of the screen showing [page]. */
    fun indexOf(screens: List<List<Int>>, page: Int): Int =
        screens.indexOfFirst { page in it }.coerceAtLeast(0)

    /** Human label for a screen: "12" or "12–13". */
    fun label(screen: List<Int>): String = if (screen.size == 1) "${screen[0]}" else "${screen.first()}–${screen.last()}"
}
