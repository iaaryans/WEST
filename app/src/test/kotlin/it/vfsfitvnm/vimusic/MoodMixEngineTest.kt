package it.vfsfitvnm.vimusic

import it.vfsfitvnm.vimusic.moodspace.MoodCandidate
import it.vfsfitvnm.vimusic.moodspace.Mood
import it.vfsfitvnm.vimusic.moodspace.MoodPersonalizationEngine
import it.vfsfitvnm.vimusic.moodspace.MoodMixEngine
import it.vfsfitvnm.vimusic.moodspace.MoodSuggestionEngine
import org.junit.Assert.*
import org.junit.Test

class MoodMixEngineTest {
    @Test fun explicitTagsTakePriorityWithoutDuplicatingTracks() {
        val library = listOf(
            MoodCandidate("a", "Artist A", 180, false, 0, true),
            MoodCandidate("b", "Artist B", 180, false, 0, true),
            MoodCandidate("c", "Artist A", 180, true, 8000, false),
            MoodCandidate("a", "Artist A", 180, false, 0, true),
            MoodCandidate("d", "Artist D", 0, true, 0, true)
        )
        val rank = MoodMixEngine.rank(library, 123)
        assertEquals(3, rank.size)
        assertEquals(setOf("a", "b"), rank.take(2).toSet())
        assertEquals(rank, MoodMixEngine.rank(library, 123))
    }
    @Test fun addingSongOffersSimilarArtistWithoutRepeatingTaggedSongs() {
        val library = listOf(
            MoodCandidate("seed", "Artist A", 180, false, 0, true),
            MoodCandidate("related", "artist a", 200, false, 0, false),
            MoodCandidate("favorite", "Artist B", 210, true, 0, false),
            MoodCandidate("invalid", "Artist A", 0, true, 0, false),
            MoodCandidate("related", "artist a", 200, false, 0, false)
        )
        val suggested = MoodSuggestionEngine.suggest(library, setOf("seed"))
        assertEquals("related", suggested.first())
        assertEquals(2, suggested.size)
        assertFalse("seed" in suggested)
        assertFalse("invalid" in suggested)
        assertEquals(suggested, MoodSuggestionEngine.suggest(library, setOf("seed")))
    }

    @Test fun suggestionsRespectMoodTagsAndLimit() {
        val library = (1..12).map { MoodCandidate("s$it", "Artist $it", 200, false, 0, false) }
        assertTrue(MoodSuggestionEngine.suggest(library, emptySet()).isEmpty())
        val suggested = MoodSuggestionEngine.suggest(library, setOf("s1"), limit = 4)
        assertEquals(4, suggested.size)
        assertEquals(suggested.size, suggested.toSet().size)
        assertFalse("s1" in suggested)
        assertTrue(MoodSuggestionEngine.suggest(library, setOf("s1"), limit = 0).isEmpty())
    }

    @Test fun moodDiscoveryWorksWithoutManuallyAddedSongs() {
        assertTrue(MoodPersonalizationEngine.queries(Mood.Happy, null, true).contains("happy upbeat feel good songs"))
        assertTrue(MoodPersonalizationEngine.queries(Mood.Sad, null, true).contains("sad emotional songs"))
        assertEquals(1, MoodPersonalizationEngine.queries(Mood.Happy, "", true).size)
    }

    @Test fun listenedToArtistPersonalizesMoodQuery() {
        val history = listOf(
            MoodCandidate("a", "Singer A", 200, false, 12 * 60_000L, false),
            MoodCandidate("b", "Singer B", 220, true, 3 * 60_000L, false),
            MoodCandidate("c", "Singer A", 180, true, 15 * 60_000L, false)
        )
        val artist = MoodPersonalizationEngine.favoriteArtist(history)
        assertEquals("Singer A", artist)
        assertEquals("happy upbeat feel good songs Singer A",
            MoodPersonalizationEngine.queries(Mood.Happy, artist, true).first())
        assertEquals(listOf("happy upbeat feel good songs"),
            MoodPersonalizationEngine.queries(Mood.Happy, artist, false))
    }

    @Test fun recommendationsWithoutHistoryDoNotInventAListenerProfile() {
        val neverPlayed = listOf(MoodCandidate("fresh", "Artist Z", 200, false, 0L, false))
        assertNull(MoodPersonalizationEngine.favoriteArtist(neverPlayed, setOf("fresh")))
        assertTrue(MoodSuggestionEngine.suggest(neverPlayed, emptySet(), preferredIds = setOf("fresh")).isEmpty())
    }

    @Test fun mutedTracksNeverAppearInMoodSuggestions() {
        val songs = listOf(
            MoodCandidate("favorite", "Artist A", 200, true, 180000L, false),
            MoodCandidate("muted", "Artist B", 200, true, 180000L, false)
        )
        assertEquals(listOf("favorite"),
            MoodSuggestionEngine.suggest(songs, emptySet(), mutedIds = setOf("muted")))
    }
}
