package it.vfsfitvnm.vimusic.ui.screens.home

/** Pure Home-feed layout decisions, so paging and deduplication can be regression-tested. */
internal object HomeShelfPolicy {
    const val TOP_PICKS = "Top Picks for You"
    const val RECENTS = "Recents"
    const val QUICK_PICKS = "Quick picks"
    const val ITEMS_PER_PAGE = 4
    val homeSections = listOf(TOP_PICKS, RECENTS, "Picked for you", "Made for you",
        QUICK_PICKS, "Related albums", "Similar artists", "Playlists you might like")

    fun <T> unique(items: List<T>, id: (T) -> String): List<T> {
        val seen = HashSet<String>()
        return items.filter { item -> id(item).isNotBlank() && seen.add(id(item)) }
    }

    fun useStaticPage(pageCount: Int, fits: Boolean): Boolean = pageCount == 1 && fits

    fun <T> pages(items: List<T>): List<List<T>> = items.chunked(ITEMS_PER_PAGE)
}
