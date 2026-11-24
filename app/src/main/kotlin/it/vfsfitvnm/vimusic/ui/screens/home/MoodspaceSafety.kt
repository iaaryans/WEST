package it.vfsfitvnm.vimusic.ui.screens.home

import it.vfsfitvnm.innertube.requests.MoodExploreCategory
import it.vfsfitvnm.innertube.requests.MoodExplorePlaylist
import it.vfsfitvnm.innertube.requests.MoodExploreShelf

/**
 * Identity and navigation policy for untrusted, changing online Explore catalogs.
 * Titles are display labels, NOT unique identifiers: seasonal genres (e.g.
 * Hanukkah) can contain many unnamed "Playlists" shelves and duplicate titles.
 */
internal object MoodspaceSafety {
    fun categoryId(category: MoodExploreCategory): String = if (category.browseId.isBlank()) {
        "offline/${category.title.trim().lowercase()}"
    } else {
        "online/${category.browseId.trim()}/${category.params.orEmpty()}"
    }

    fun sections(items: List<MoodExploreShelf>): List<MoodExploreShelf> = items
        .take(MAX_SHELVES)
        .mapNotNull { shelf ->
            val valid = shelf.playlists
                .filter { it.title.isNotBlank() && browseTarget(it.browseId) != null }
                .distinctBy { it.browseId }
                .take(MAX_PLAYLISTS_PER_SHELF)
            if (valid.isEmpty()) null else shelf.copy(
                title = shelf.title.trim().ifBlank { "Playlists" }, playlists = valid
            )
        }

    // The category page may contain repeated shelf titles; the index is unique
    // within that page and each changed page receives a different category id.
    fun shelfKey(categoryId: String, position: Int): String = "$categoryId/shelf/$position"
    fun playlistKey(categoryId: String, shelfIndex: Int, position: Int): String =
        "$categoryId/$shelfIndex/playlist/$position"

    enum class BrowseTarget { PLAYLIST, ALBUM, ARTIST }

    fun browseTarget(rawId: String): BrowseTarget? {
        val id = rawId.trim()
        if (id.length < 3 || id.length > 256 || id.any { it.isWhitespace() || it.isISOControl() }) return null
        return when {
            id.startsWith("MPRE") -> BrowseTarget.ALBUM
            id.startsWith("UC") -> BrowseTarget.ARTIST
            id.startsWith("VLPL") || id.startsWith("VL") || id.startsWith("PL") -> BrowseTarget.PLAYLIST
            else -> null // special navigation endpoint; never feed it into PlaylistScreen
        }
    }

    const val MAX_SHELVES = 24
    const val MAX_PLAYLISTS_PER_SHELF = 24
}
