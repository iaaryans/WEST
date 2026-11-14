package it.vfsfitvnm.vimusic.listening

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import it.vfsfitvnm.vimusic.listening.data.ListeningSettings
import it.vfsfitvnm.vimusic.listening.tracker.PlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the play-counting rules of a single playback session using a
 * fake monotonic clock.
 */
class PlaybackSessionTest {

    private class FakeClock(var now: Long = 0L) {
        fun advance(ms: Long) {
            now += ms
        }
    }

    private fun mediaItem(id: String = "song1"): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setCustomCacheKey(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Song $id")
                    .setArtist("Artist")
                    .build()
            )
            .build()

    private fun session(clock: FakeClock, durationMs: Long = 0L) =
        PlaybackSession(mediaItem(), startedAtEpochMs = 1_000L, uptimeClock = { clock.now }).also {
            if (durationMs > 0) it.onDurationKnown(durationMs)
        }

    // ------------------------------------------------------------------
    // Accumulation
    // ------------------------------------------------------------------

    @Test
    fun `paused time is not accumulated`() {
        val clock = FakeClock()
        val session = session(clock)

        session.onPlaying(1f)
        clock.advance(20_000)
        session.onNotPlaying()

        clock.advance(3_600_000) // one hour paused
        session.onNotPlaying()   // no-op, already stopped

        session.onPlaying(1f)
        clock.advance(15_000)
        session.onNotPlaying()

        assertEquals(35_000L, session.playedMs)
    }

    @Test
    fun `multiple pause resume cycles keep accumulating`() {
        val clock = FakeClock()
        val session = session(clock)

        repeat(5) {
            session.onPlaying(1f)
            clock.advance(10_000)
            session.onNotPlaying()
            clock.advance(5_000)
        }

        assertEquals(50_000L, session.playedMs)
    }

    @Test
    fun `double onPlaying does not double count`() {
        val clock = FakeClock()
        val session = session(clock)

        session.onPlaying(1f)
        clock.advance(10_000)
        session.onPlaying(1f) // duplicate event, must be ignored
        clock.advance(10_000)
        session.onNotPlaying()

        assertEquals(20_000L, session.playedMs)
    }

    @Test
    fun `playback speed scales the accumulated segment`() {
        val clock = FakeClock()
        val session = session(clock)

        session.onPlaying(2f)
        clock.advance(10_000)
        session.onNotPlaying()

        assertEquals(20_000L, session.playedMs)
    }

    @Test
    fun `currentPlayedMs includes the pending segment`() {
        val clock = FakeClock()
        val session = session(clock)

        session.onPlaying(1f)
        clock.advance(10_000)
        session.onNotPlaying()
        session.onPlaying(1f)
        clock.advance(7_000)

        assertEquals(17_000L, session.currentPlayedMs())
        assertEquals(10_000L, session.playedMs)
    }

    // ------------------------------------------------------------------
    // Counting rules
    // ------------------------------------------------------------------

    @Test
    fun `less than 30 seconds does not count`() {
        val clock = FakeClock()
        val session = session(clock, durationMs = 200_000)

        session.onPlaying(1f)
        clock.advance(29_999)
        session.onNotPlaying()

        assertFalse(
            session.isCountedPlay(
                ListeningSettings.effectiveThresholdMs(30_000, session.durationMs)
            )
        )
    }

    @Test
    fun `at least 30 seconds counts`() {
        val clock = FakeClock()
        val session = session(clock, durationMs = 200_000)

        session.onPlaying(1f)
        clock.advance(30_000)
        session.onNotPlaying()

        assertTrue(
            session.isCountedPlay(
                ListeningSettings.effectiveThresholdMs(30_000, session.durationMs)
            )
        )
    }

    @Test
    fun `short track reaching half of its duration counts`() {
        val clock = FakeClock()
        val session = session(clock, durationMs = 40_000)

        session.onPlaying(1f)
        clock.advance(20_000) // exactly 50% of a 40s track
        session.onNotPlaying()

        val threshold = ListeningSettings.effectiveThresholdMs(30_000, session.durationMs)
        assertEquals(20_000L, threshold)
        assertTrue(session.isCountedPlay(threshold))
    }

    @Test
    fun `completed short track counts even below 30 seconds`() {
        val clock = FakeClock()
        val session = session(clock, durationMs = 20_000)

        session.onPlaying(1f)
        clock.advance(20_000) // full track played
        session.onNotPlaying()

        assertTrue(
            session.isCountedPlay(
                ListeningSettings.effectiveThresholdMs(30_000, session.durationMs)
            )
        )
    }

    @Test
    fun `seeking to the end of a long track does not count`() {
        val clock = FakeClock()
        val session = session(clock, durationMs = 200_000)

        session.onPlaying(1f)
        clock.advance(5_000)  // seeked near the end, 5s played, track "ends"
        session.onNotPlaying()

        assertFalse(
            session.isCountedPlay(
                ListeningSettings.effectiveThresholdMs(30_000, session.durationMs)
            )
        )
    }

    @Test
    fun `unknown duration falls back to the configured threshold`() {
        val session = session(FakeClock(), durationMs = 0)

        assertEquals(30_000L, ListeningSettings.effectiveThresholdMs(30_000, session.durationMs))
    }

    @Test
    fun `configured threshold is respected when larger than half duration`() {
        val session = session(FakeClock(), durationMs = 40_000)

        // A user configuring 60s still counts a fully played 40s track
        assertEquals(20_000L, ListeningSettings.effectiveThresholdMs(60_000, session.durationMs))
    }

    // ------------------------------------------------------------------
    // Finalization guard
    // ------------------------------------------------------------------

    @Test
    fun `session stops accumulating after finalization`() {
        val clock = FakeClock()
        val session = session(clock)

        session.onPlaying(1f)
        clock.advance(31_000)
        session.onNotPlaying()
        session.finalize()

        session.onPlaying(1f) // must be ignored
        clock.advance(10_000)
        session.onNotPlaying()

        assertEquals(31_000L, session.playedMs)
    }
}
