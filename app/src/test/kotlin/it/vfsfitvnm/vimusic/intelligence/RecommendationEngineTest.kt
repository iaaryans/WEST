package it.vfsfitvnm.vimusic.intelligence

import org.junit.Assert.*
import org.junit.Test

class RecommendationEngineTest {
    private val now = 1_800_000_000_000L

    @Test fun favoritesArePreferredToUnrelatedTracksInComfortMode() {
        val result = RecommendationEngine.recommend(listOf(
            RecommendationEngine.Candidate("favorite", "A", 400_000L, true),
            RecommendationEngine.Candidate("other", "B", 0, false)
        ), emptyList(), now, discoveryPercent = 0)
        assertEquals("favorite", result.madeForYou.first().id)
    }

    @Test fun suppressedTracksAreExcludedAndFeedbackBoostsResults() {
        val songs = listOf(
            RecommendationEngine.Candidate("A", "X", 0, false),
            RecommendationEngine.Candidate("B", "Y", 0, false),
            RecommendationEngine.Candidate("C", "Z", 0, false)
        )
        val result = RecommendationEngine.recommend(songs, emptyList(), now,
            boosted = setOf("B"), muted = setOf("A"))
        assertEquals("B", result.madeForYou.first().id)
        assertFalse(result.madeForYou.any { it.id == "A" })
    }

    @Test fun recentTracksArePenalizedAndOldTracksCanBeRediscovered() {
        val songs = listOf(
            RecommendationEngine.Candidate("old", "X", 500_000, true),
            RecommendationEngine.Candidate("today", "Y", 500_000, true),
            RecommendationEngine.Candidate("old2", "Z", 200_000, true)
        )
        val sessions = listOf(
            RecommendationEngine.Session("old", now - 60 * 86_400_000L, 120_000, true),
            RecommendationEngine.Session("today", now - 1_000, 120_000, true),
            RecommendationEngine.Session("old2", now - 50 * 86_400_000L, 60_000, true)
        )
        val result = RecommendationEngine.recommend(songs, sessions, now, maxPerSection = 1)
        assertNotEquals("today", result.madeForYou.first().id)
        assertEquals("old2", result.rediscover.firstOrNull()?.id)
    }

    @Test fun onboardingArtistsAffectColdStartRanking() {
        val songs = listOf(
            RecommendationEngine.Candidate("unrelated", "Different Singer", 0, false),
            RecommendationEngine.Candidate("preferred", "Arijit Singh", 0, false)
        )
        val ranked = RecommendationEngine.recommend(songs, emptyList(), now,
            preferredArtists = setOf("Arijit Singh"), discoveryPercent = 35)
        assertEquals("preferred", ranked.madeForYou.first().id)
        assertEquals("A favorite artist from your setup", ranked.madeForYou.first().explanation)
    }

    @Test fun explicitlySelectedSongRanksWithoutPastListening() {
        val songs = listOf(
            RecommendationEngine.Candidate("one", "Artist A", 0, false),
            RecommendationEngine.Candidate("two", "Artist B", 0, false)
        )
        val ranked = RecommendationEngine.recommend(songs, emptyList(), now,
            preferredSongs = setOf("two"))
        assertEquals("two", ranked.madeForYou.first().id)
    }

    @Test fun emptyAndDuplicateInputsDoNotCrash() {
        assertTrue(RecommendationEngine.recommend(emptyList(), emptyList(), now).madeForYou.isEmpty())
        val item = RecommendationEngine.Candidate("one", "A", 0, false)
        assertEquals(1, RecommendationEngine.recommend(listOf(item,item), emptyList(), now).madeForYou.size)
    }
}
