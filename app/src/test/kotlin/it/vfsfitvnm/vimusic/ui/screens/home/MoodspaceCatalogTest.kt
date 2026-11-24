package it.vfsfitvnm.vimusic.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class MoodspaceCatalogTest {
    @Test fun offlineExploreShowsFullCategoryGrid() {
        assertTrue(MoodspaceCatalog.fallback.size >= 35)
        assertEquals(MoodspaceCatalog.fallback.size,
            MoodspaceCatalog.prioritize(MoodspaceCatalog.fallback, emptyList()).size)
    }

    @Test fun preferencesMovePreferredMoodsWithoutDroppingOtherGenres() {
        val actual = MoodspaceCatalog.prioritize(MoodspaceCatalog.fallback, listOf("Rock", "Sleep"))
        assertEquals(setOf("Rock", "Sleep"), actual.take(2).map { it.title }.toSet())
        assertTrue(actual.any { it.title == "Classical" })
    }

    @Test fun anonymousDiscoveryDoesNotRequireOnboarding() {
        assertEquals(listOf("happy feel good songs"),
            MoodspaceCatalog.discoveryQueries("Happy", "Artist", "Hindi", false))
        assertEquals("Jazz music songs", MoodspaceCatalog.discoveryQueries("Jazz", null, null, true).first())
    }

    @Test fun intelligenceCanBiasMoodQueriesWithoutRemovingNeutralFallback() {
        val queries = MoodspaceCatalog.discoveryQueries("Chill", "Singer A", "Hindi", true)
        assertEquals("Chill Singer A songs", queries.first())
        assertEquals("chill relaxing songs", queries.last())
        assertEquals(3, queries.distinct().size)
    }
}
