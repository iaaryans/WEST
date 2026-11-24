package it.vfsfitvnm.vimusic.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class HomeShelfPolicyTest {
    @Test fun recentRowsAlwaysContainAtMostFourTracks() {
        val pages = HomeShelfPolicy.pages((1..13).toList())
        assertEquals(listOf(4, 4, 4, 1), pages.map { it.size })
        assertEquals((1..13).toList(), pages.flatten())
    }

    @Test fun duplicateTracksAreRemovedWithoutChangingOrder() {
        val result = HomeShelfPolicy.unique(listOf("a", "b", "a", "", "c", "b")) { it }
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test fun topPicksAndRecentsAreFirstAndMoodspaceIsNotPromotedOnHome() {
        assertEquals(HomeShelfPolicy.TOP_PICKS, HomeShelfPolicy.homeSections.first())
        assertEquals(HomeShelfPolicy.RECENTS, HomeShelfPolicy.homeSections[1])
        assertFalse(HomeShelfPolicy.homeSections.any { it.contains("Moodspace", ignoreCase = true) })
    }

    @Test fun emptyListsDoNotCreateBlankPages() {
        assertTrue(HomeShelfPolicy.pages(emptyList<Int>()).isEmpty())
        assertTrue(HomeShelfPolicy.unique(emptyList<String>()) { it }.isEmpty())
    }
}
