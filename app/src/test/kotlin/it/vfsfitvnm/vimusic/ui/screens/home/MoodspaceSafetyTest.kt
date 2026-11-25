package it.vfsfitvnm.vimusic.ui.screens.home

import it.vfsfitvnm.innertube.requests.MoodExploreCategory
import it.vfsfitvnm.innertube.requests.MoodExplorePlaylist
import it.vfsfitvnm.innertube.requests.MoodExploreShelf
import org.junit.Assert.*
import org.junit.Test

/** Regression for seasonal categories containing repeated unnamed shelves. */
class MoodspaceSafetyTest {
    @Test fun hanukkahAndOtherSeasonalCategoriesHaveStableDistinctIds() {
        val hanukkah = MoodExploreCategory("Hanukkah", "MPSP_HOLIDAY", "2026")
        val seasonal = MoodExploreCategory("Hanukkah", "MPSP_OTHER", "2026")
        assertNotEquals(MoodspaceSafety.categoryId(hanukkah), MoodspaceSafety.categoryId(seasonal))
        assertEquals(MoodspaceSafety.categoryId(hanukkah), MoodspaceSafety.categoryId(hanukkah.copy()))
    }

    @Test fun repeatedPlaylistsShelvesAlwaysReceiveUniqueComposeKeys() {
        val id = MoodspaceSafety.categoryId(MoodExploreCategory("Hanukkah", "MPSP_HOLIDAY"))
        val titles = List(6) { "Playlists" }
        val keys = titles.indices.map { MoodspaceSafety.shelfKey(id, it) }
        assertEquals(titles.size, keys.toSet().size)
        assertEquals(6, titles.indices.map { MoodspaceSafety.playlistKey(id, it, 0) }.toSet().size)
    }

    @Test fun malformedBrowseTargetsAreExcludedButPlayableTracksRemain() {
        val input = listOf(
            MoodExploreShelf("", listOf(
                MoodExplorePlaylist("Good", "VLPL_VALID", null),
                MoodExplorePlaylist("Duplicate", "VLPL_VALID", null),
                MoodExplorePlaylist("Hanukkah special", "MPSP_UNSUPPORTED", null),
                MoodExplorePlaylist("Missing", "", null),
                MoodExplorePlaylist("   ", "VLPL_NO_TITLE", null)
            )),
            MoodExploreShelf("Playlists", listOf(
                MoodExplorePlaylist("Album", "MPREb_123", null),
                MoodExplorePlaylist("Artist", "UCabcdef", null)
            )),
            MoodExploreShelf("Playlists", emptyList())
        )
        val safe = MoodspaceSafety.sections(input)
        assertEquals(2, safe.size)
        assertEquals("Playlists", safe.first().title)
        assertEquals(listOf("VLPL_VALID"), safe.first().playlists.map { it.browseId })
        assertEquals(listOf("MPREb_123", "UCabcdef"), safe[1].playlists.map { it.browseId })
    }

    @Test fun unsupportedSeasonalEndpointsCannotBeRoutedAsPlaylists() {
        assertNull(MoodspaceSafety.browseTarget("MPSP_HANUKKAH"))
        assertNull(MoodspaceSafety.browseTarget("FEmusic_moods_and_genres"))
        assertNull(MoodspaceSafety.browseTarget("VLPL has whitespace"))
        assertEquals(MoodspaceSafety.BrowseTarget.PLAYLIST,
            MoodspaceSafety.browseTarget("VLPL_VALID"))
        assertEquals(MoodspaceSafety.BrowseTarget.ALBUM,
            MoodspaceSafety.browseTarget("MPREb_123"))
        assertEquals(MoodspaceSafety.BrowseTarget.ARTIST,
            MoodspaceSafety.browseTarget("UCabcdef"))
    }
}
