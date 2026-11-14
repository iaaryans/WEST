package it.vfsfitvnm.vimusic.listening.tracker

import android.os.SystemClock
import androidx.media3.common.MediaItem

/**
 * One continuous playback of a single media item.
 *
 * Accumulates the ACTUAL playback duration: the clock only runs while the
 * player reports `isPlaying` (so pauses, buffering, focus loss and
 * suppression never count as listening time) and each accumulated segment
 * is scaled by the playback speed active during that segment.
 *
 * Seeking inside the song does not reset anything — only real playing time
 * is measured, so a seek can never inflate the duration nor create an
 * extra play.
 *
 * @param uptimeClock injectable monotonic clock (epoch-free, milliseconds),
 *        replaced in unit tests.
 */
class PlaybackSession(
    val mediaItem: MediaItem,
    val startedAtEpochMs: Long,
    private val uptimeClock: () -> Long = SystemClock::elapsedRealtime
) {
    /** Actual playback duration accumulated so far (completed segments). */
    var playedMs: Long = 0L
        private set

    /** Track duration in milliseconds, 0 when unknown. */
    var durationMs: Long = 0L
        private set

    /** True once this session has been flushed (or discarded); guards double writes. */
    var isFinalized: Boolean = false
        private set

    private var resumeUptimeMs: Long = -1L
    private var resumeSpeed: Float = 1f

    val isAccumulating: Boolean
        get() = resumeUptimeMs != -1L

    /**
     * Start accumulating. Called every time the player transitions to
     * `isPlaying == true` (after pause, buffering, suppression...).
     */
    fun onPlaying(speed: Float) {
        if (isFinalized || resumeUptimeMs != -1L) return
        resumeUptimeMs = uptimeClock()
        resumeSpeed = if (speed > 0f) speed else 1f
    }

    /**
     * Stop accumulating and fold the elapsed segment into [playedMs].
     * Returns the length of the folded segment (0 when not accumulating).
     */
    fun onNotPlaying(): Long {
        val resumeAt = resumeUptimeMs
        if (resumeAt == -1L) return 0L
        resumeUptimeMs = -1L
        val segment = ((uptimeClock() - resumeAt) * resumeSpeed.toDouble()).toLong()
        playedMs += segment
        return segment
    }

    /** Playback duration including the segment currently in progress. */
    fun currentPlayedMs(): Long {
        val resumeAt = resumeUptimeMs
        if (resumeAt == -1L) return playedMs
        val pending = ((uptimeClock() - resumeAt) * resumeSpeed.toDouble()).toLong()
        return playedMs + pending
    }

    fun onDurationKnown(duration: Long) {
        if (durationMs <= 0 && duration > 0) durationMs = duration
    }

    /**
     * Folds the pending segment and freezes the session; afterwards no
     * further accumulation is possible. Called exactly once per session by
     * the tracker.
     */
    fun finalize() {
        onNotPlaying()
        isFinalized = true
    }

    /** A session counts as one play when it reached the effective threshold. */
    fun isCountedPlay(effectiveThresholdMs: Long): Boolean = playedMs >= effectiveThresholdMs
}
