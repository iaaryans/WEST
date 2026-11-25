package it.vfsfitvnm.vimusic.intelligence

import org.junit.Assert.*
import org.junit.Test

class SmartQueueRankerTest {
    private fun track(id: String, artist: String = "") = SmartQueueRanker.Track(id, "Song $id", artist)

    @Test fun selectedSearchResultStartsFirstAndFollowingResultsStayInOrder() {
        val output = SmartQueueRanker.searchQueue(track("b"), listOf(track("a"), track("b"), track("c")))
        assertEquals(listOf("b", "c", "a"), output.map { it.id })
    }

    @Test fun handlesOneResultAndDeduplicatesAndBoundsSearchQueue() {
        assertEquals(listOf("one"), SmartQueueRanker.searchQueue(track("one"), listOf(track("one"), track("one"))).map { it.id })
        val output = SmartQueueRanker.searchQueue(track("7"), (0..30).map { track(it.toString()) }, 12)
        assertEquals(12, output.size)
        assertEquals("7", output.first().id)
        assertEquals(12, output.map { it.id }.toSet().size)
    }

    @Test fun currentlyPlayingAndAlreadyQueuedTracksNeverRepeatInRadio() {
        val output = SmartQueueRanker.radioQueue(
            listOf(track("a"), track("b"), track("b"), track("c")), track("a"),
            setOf("a", "c"), SmartQueueRanker.Signals()
        )
        assertEquals(listOf("b"), output.map { it.id })
    }

    @Test fun onboardingArtistImprovesDiscoveryRelevance() {
        val output = SmartQueueRanker.radioQueue(
            listOf(track("generic", "Unknown"), track("preferred", "Arijit Singh")), null,
            emptySet(), SmartQueueRanker.Signals(preferredArtists = setOf("Arijit Singh"))
        )
        assertEquals("preferred", output.first().id)
    }

    @Test fun mutedAndRecentlyPlayedArePenalized() {
        val output = SmartQueueRanker.radioQueue(
            listOf(track("muted"), track("recent"), track("new")), null, emptySet(),
            SmartQueueRanker.Signals(mutedSongs = setOf("muted"), recentlyPlayed = setOf("recent"))
        )
        assertEquals("new", output.first().id)
        assertTrue(output.none { it.id == "muted" })
    }

    @Test fun manualOrderIsNotReplacedByIntelligence() {
        val output = SmartQueueRanker.searchQueue(track("requested"), listOf(track("other"), track("requested")))
        assertEquals(listOf("requested", "other"), output.map { it.id })
    }

    @Test fun emptyAndInvalidInputsDoNotCrash() {
        assertTrue(SmartQueueRanker.searchQueue(track(""), listOf(track("a"))).isEmpty())
        assertTrue(SmartQueueRanker.radioQueue(emptyList(), null, emptySet(), SmartQueueRanker.Signals()).isEmpty())
    }
}
