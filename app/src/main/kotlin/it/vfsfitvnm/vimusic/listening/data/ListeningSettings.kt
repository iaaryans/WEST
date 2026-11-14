package it.vfsfitvnm.vimusic.listening.data

import android.content.Context
import android.content.SharedPreferences
import it.vfsfitvnm.vimusic.utils.preferences

/**
 * SharedPreferences keys for the listening statistics feature.
 *
 * Stored in the app-wide "preferences" store, the same one every other
 * setting uses, so backups and existing tooling keep working.
 */
object ListeningSettings {
    const val playCountThresholdKey = "listeningPlayCountThresholdMs"

    /**
     * Configurable play-counting threshold in milliseconds. A listening
     * session counts as one play when the actual playback duration reaches
     * this value — or half the track duration for tracks shorter than twice
     * the threshold (the "short track reaches 50%" rule).
     */
    const val DEFAULT_PLAY_COUNT_THRESHOLD_MS = 30_000L

    val PLAY_COUNT_THRESHOLD_CHOICES = listOf(15_000L, 30_000L, 60_000L)

    fun playCountThresholdMs(preferences: SharedPreferences): Long {
        val stored = preferences.getLong(playCountThresholdKey, DEFAULT_PLAY_COUNT_THRESHOLD_MS)
        return if (stored <= 0) DEFAULT_PLAY_COUNT_THRESHOLD_MS else stored
    }

    fun playCountThresholdMs(context: Context): Long =
        playCountThresholdMs(context.preferences)

    /**
     * Effective threshold for one session given the track duration:
     * min(configured threshold, duration / 2) — a completed short track
     * always satisfies it, while a seek to the end of a long track does not.
     * Unknown durations (0 or negative) fall back to the configured value.
     */
    fun effectiveThresholdMs(configuredMs: Long, durationMs: Long): Long {
        if (durationMs <= 0) return configuredMs
        return minOf(configuredMs, durationMs / 2)
    }
}
