package it.vfsfitvnm.vimusic.ui.screens.home

import it.vfsfitvnm.innertube.requests.MoodExploreCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodArtworkPrefetchPolicyTest {
    private val categories = (0..39).map { MoodExploreCategory("Mood $it", "browse/$it") }

    @Test fun aHeaderOnlyViewportWarmsTheFirstRowAndOneLookaheadRow() {
        val actual = MoodArtworkPrefetchPolicy.visible(categories, columns = 2, visibleItemIndices = listOf(0))
        assertEquals(listOf("browse/0", "browse/1", "browse/2", "browse/3"), actual.map { it.browseId })
    }

    @Test fun scrollingFarDownDoesNotFetchAboveTheViewport() {
        val result = MoodArtworkPrefetchPolicy.visible(categories, columns = 2, visibleItemIndices = listOf(11,12))
        assertEquals(listOf(20,21,22,23,24,25), result.map { it.browseId.substringAfter('/').toInt() })
    }

    @Test fun requestBudgetIsHardBounded() {
        val result = MoodArtworkPrefetchPolicy.visible(categories, columns = 3,
            visibleItemIndices = (1..15).toList(), maxRequests = 4)
        assertEquals(4, result.size)
    }

    @Test fun imagesWithKnownArtworkAndMissingIdsAreSkipped() {
        val input = listOf(
            MoodExploreCategory("Known", "browse/a", imageUrl = "https://image"),
            MoodExploreCategory("No ID", ""),
            MoodExploreCategory("Fetch", "browse/c"),
            MoodExploreCategory("Duplicate", "browse/c")
        )
        val result = MoodArtworkPrefetchPolicy.visible(input, columns = 2, visibleItemIndices = listOf(1,2))
        assertEquals(listOf("browse/c"), result.map { it.browseId })
    }

    @Test fun emptyListAndInvalidLimitsAreSafe() {
        assertTrue(MoodArtworkPrefetchPolicy.visible(emptyList(), 2, listOf(0)).isEmpty())
        assertTrue(MoodArtworkPrefetchPolicy.visible(categories, 0, listOf(0)).isEmpty())
        assertTrue(MoodArtworkPrefetchPolicy.visible(categories, 2, emptyList()).isEmpty())
        assertTrue(MoodArtworkPrefetchPolicy.visible(categories, 2, listOf(0), maxRequests = 0).isEmpty())
    }
}
