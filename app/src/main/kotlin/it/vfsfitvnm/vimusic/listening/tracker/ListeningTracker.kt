package it.vfsfitvnm.vimusic.listening.tracker

import android.content.Context
import android.content.SharedPreferences
import android.database.SQLException
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import it.vfsfitvnm.vimusic.Converters
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.listening.data.ListeningHistoryEntity
import it.vfsfitvnm.vimusic.listening.data.ListeningSettings
import it.vfsfitvnm.vimusic.listening.data.listeningHistory
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.transaction
import it.vfsfitvnm.vimusic.utils.preferences

/**
 * Bridges the existing Media3 playback architecture to the listening
 * statistics database.
 *
 * Registered as a plain [Player.Listener] on the service player (one
 * `addListener` call in `PlayerService.onCreate`, one `shutdown()` in
 * `onDestroy`); it never touches player configuration, so existing
 * playback behaviour is unaffected.
 *
 * Session lifecycle:
 *  - starts when a media item becomes current (skip, auto-advance, new queue);
 *  - accumulates only while `player.isPlaying` is true;
 *  - finalizes on transition away, on STATE_ENDED/STATE_IDLE, when a
 *    REPEAT_ONE loop restarts the same item (a legitimate new play), and at
 *    service shutdown — exactly once per session;
 *  - seeks inside a song never finalize nor create sessions.
 *
 * App restarts: while a session is playing, a snapshot is persisted every
 * 15 seconds; if the process dies before the session can be flushed, the
 * snapshot is recovered and written on the next service start (loss is
 * bounded by the heartbeat interval). The snapshot is cleared BEFORE the
 * flush task is submitted, so a session can be lost in a microscopic window
 * but can never be recovered twice (no duplicate events).
 */
class ListeningTracker(
    context: Context,
    private val player: Player
) : Player.Listener {

    private val appContext: Context = context.applicationContext
    private val preferences: SharedPreferences = appContext.preferences

    private var session: PlaybackSession? = null

    private val handler = Handler(Looper.getMainLooper())
    private var heartbeatScheduled = false

    private val heartbeat = object : Runnable {
        override fun run() {
            if (session?.isAccumulating == true) {
                persistSnapshot()
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            } else {
                heartbeatScheduled = false
            }
        }
    }

    init {
        recoverOrphanedSession()
    }

    // ------------------------------------------------------------------
    // Player.Listener
    // ------------------------------------------------------------------

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        finalizeCurrent(endedAt = System.currentTimeMillis())
        if (mediaItem != null) beginSession(mediaItem)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val current = session
        if (isPlaying) {
            val active = current ?: player.currentMediaItem?.let {
                // Safety net: playback resumed without a transition event
                // (e.g. seeking back into a track that had reached ENDED).
                beginSession(it)
                session
            } ?: return
            active.onDurationKnown(player.duration)
            active.onPlaying(player.playbackParameters.speed)
            startHeartbeat()
        } else {
            current?.onNotPlaying()
            persistSnapshot()
            stopHeartbeat()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                session?.onDurationKnown(player.duration)
                if (session == null) {
                    // Playback restarted on the same item after ENDED
                    // (seek back): a legitimate replay, i.e. a new session.
                    player.currentMediaItem?.let { beginSession(it) }
                }
            }

            Player.STATE_ENDED,
            Player.STATE_IDLE -> {
                finalizeCurrent(endedAt = System.currentTimeMillis())
            }
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return

        val current = session ?: return

        // A backward automatic jump landing at the start of the same item is
        // a completed REPEAT_ONE loop: one full pass has been played, the
        // next pass is a new legitimate play.
        val looped = oldPosition.mediaItemIndex == newPosition.mediaItemIndex &&
            newPosition.positionMs < LOOP_RESTART_POSITION_MS &&
            oldPosition.positionMs > MINIMUM_LOOP_MS

        if (looped) {
            val mediaItem = current.mediaItem
            val durationMs = current.durationMs
            finalizeCurrent(endedAt = System.currentTimeMillis())
            beginSession(mediaItem, durationMs)
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Flushes the active session; called from PlayerService.onDestroy before player.release(). */
    fun shutdown() {
        finalizeCurrent(endedAt = System.currentTimeMillis())
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun beginSession(mediaItem: MediaItem, durationMs: Long = 0L) {
        val newSession = PlaybackSession(mediaItem, System.currentTimeMillis())
        if (durationMs > 0) newSession.onDurationKnown(durationMs)
        val duration = player.duration
        if (duration > 0) newSession.onDurationKnown(duration)
        if (player.isPlaying) {
            newSession.onPlaying(player.playbackParameters.speed)
            startHeartbeat()
        }
        session = newSession
        persistSnapshot()
    }

    private fun finalizeCurrent(endedAt: Long) {
        val current = session ?: return
        session = null
        stopHeartbeat()

        current.finalize()

        // Clear BEFORE submitting the flush task: a session may be lost in a
        // microscopic window but can never be recovered twice.
        clearSnapshot()

        val playedMs = current.playedMs
        if (playedMs <= MINIMUM_SESSION_MS) return

        val effectiveThreshold = ListeningSettings.effectiveThresholdMs(
            configuredMs = ListeningSettings.playCountThresholdMs(preferences),
            durationMs = current.durationMs
        )
        val counted = current.isCountedPlay(effectiveThreshold)

        val mediaItem = current.mediaItem
        val entity = ListeningHistoryEntity(
            songId = mediaItem.mediaId,
            timestamp = current.startedAtEpochMs,
            endedAt = endedAt,
            playedMs = playedMs,
            counted = counted
        )

        query {
            // Ensure the Song (and its artist/album mappings, when the item
            // carries them) exists so the history row's foreign key holds.
            // Idempotent thanks to OnConflictStrategy.IGNORE.
            try {
                Database.insert(mediaItem)
            } catch (_: Exception) {
            }

            try {
                Database.listeningHistory.insert(entity)
            } catch (_: SQLException) {
            }
        }
    }

    private fun startHeartbeat() {
        if (heartbeatScheduled) return
        heartbeatScheduled = true
        handler.postDelayed(heartbeat, HEARTBEAT_INTERVAL_MS)
    }

    private fun stopHeartbeat() {
        handler.removeCallbacks(heartbeat)
        heartbeatScheduled = false
    }

    // ------------------------------------------------------------------
    // Restart recovery
    // ------------------------------------------------------------------

    private fun recoverOrphanedSession() {
        if (!preferences.getBoolean(KEY_SNAPSHOT_ACTIVE, false)) return

        val songId = preferences.getString(KEY_SNAPSHOT_SONG_ID, null)
        val startedAt = preferences.getLong(KEY_SNAPSHOT_STARTED_AT, 0L)
        val playedMs = preferences.getLong(KEY_SNAPSHOT_PLAYED_MS, 0L)
        val lastSeenAt = preferences.getLong(KEY_SNAPSHOT_LAST_SEEN_AT, startedAt)

        clearSnapshot()

        if (songId == null || playedMs <= MINIMUM_SESSION_MS) return

        val mediaItem = decodeSnapshotMediaItem() ?: fallbackMediaItem(songId)

        // One transaction: the recovered session either lands completely
        // (history row + song play time) or not at all, so a kill between the
        // two writes can never leave the library totals inconsistent with the
        // history. The snapshot was cleared above, so recovery runs exactly
        // once per interrupted session — no double accumulation is possible.
        transaction {
            try {
                Database.insert(mediaItem)
            } catch (_: Exception) {
            }

            try {
                Database.listeningHistory.insert(
                    ListeningHistoryEntity(
                        songId = songId,
                        timestamp = startedAt,
                        endedAt = lastSeenAt,
                        playedMs = playedMs,
                        counted = playedMs >= ListeningSettings.playCountThresholdMs(preferences)
                    )
                )
            } catch (_: SQLException) {
            }

            // Keep Song.totalPlayTimeMs consistent with the recovered
            // session: normal (non-crash) playback accumulates it in
            // PlayerService.onPlaybackStatsReady, which never runs for a
            // process that died mid-session. Without this the song stays at
            // zero total play time and disappears from the Songs library.
            // Deleted/unavailable songs simply no-op (UPDATE on a missing id).
            if (playedMs > TOTAL_PLAY_TIME_MINIMUM_MS) {
                Database.incrementTotalPlayTimeMs(songId, playedMs)
            }
        }
    }

    private fun persistSnapshot() {
        val current = session ?: return
        preferences.edit()
            .putBoolean(KEY_SNAPSHOT_ACTIVE, true)
            .putString(KEY_SNAPSHOT_SONG_ID, current.mediaItem.mediaId)
            .putString(KEY_SNAPSHOT_TITLE, current.mediaItem.mediaMetadata.title?.toString())
            .putString(KEY_SNAPSHOT_ARTIST, current.mediaItem.mediaMetadata.artist?.toString())
            .putString(KEY_SNAPSHOT_ARTWORK, current.mediaItem.mediaMetadata.artworkUri?.toString())
            .putString(
                KEY_SNAPSHOT_DURATION_TEXT,
                current.mediaItem.mediaMetadata.extras?.getString("durationText")
            )
            .putLong(KEY_SNAPSHOT_STARTED_AT, current.startedAtEpochMs)
            .putLong(KEY_SNAPSHOT_PLAYED_MS, current.currentPlayedMs())
            .putLong(KEY_SNAPSHOT_LAST_SEEN_AT, System.currentTimeMillis())
            .putString(KEY_SNAPSHOT_MEDIA_ITEM, encodeSnapshotMediaItem(current.mediaItem))
            .apply()
    }

    private fun clearSnapshot() {
        preferences.edit()
            .putBoolean(KEY_SNAPSHOT_ACTIVE, false)
            .remove(KEY_SNAPSHOT_SONG_ID)
            .remove(KEY_SNAPSHOT_TITLE)
            .remove(KEY_SNAPSHOT_ARTIST)
            .remove(KEY_SNAPSHOT_ARTWORK)
            .remove(KEY_SNAPSHOT_DURATION_TEXT)
            .remove(KEY_SNAPSHOT_STARTED_AT)
            .remove(KEY_SNAPSHOT_PLAYED_MS)
            .remove(KEY_SNAPSHOT_LAST_SEEN_AT)
            .remove(KEY_SNAPSHOT_MEDIA_ITEM)
            .apply()
    }

    private fun encodeSnapshotMediaItem(mediaItem: MediaItem): String? = runCatching {
        Converters.mediaItemToByteArray(mediaItem)
            ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
    }.getOrNull()

    private fun decodeSnapshotMediaItem(): MediaItem? = runCatching {
        preferences.getString(KEY_SNAPSHOT_MEDIA_ITEM, null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?.let { Converters.mediaItemFromByteArray(it) }
    }.getOrNull()

    private fun fallbackMediaItem(songId: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(songId)
            .setUri(songId)
            .setCustomCacheKey(songId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(preferences.getString(KEY_SNAPSHOT_TITLE, null) ?: songId)
                    .setArtist(preferences.getString(KEY_SNAPSHOT_ARTIST, null))
                    .setArtworkUri(preferences.getString(KEY_SNAPSHOT_ARTWORK, null)?.toUri())
                    .setExtras(
                        bundleOf(
                            "durationText" to preferences.getString(KEY_SNAPSHOT_DURATION_TEXT, null)
                        )
                    )
                    .build()
            )
            .build()

    private companion object {
        /** Sessions shorter than this are not recorded at all (accidental skips). */
        const val MINIMUM_SESSION_MS = 5_000L

        /** Below this the play-time total is not accumulated (matches PlayerService). */
        const val TOTAL_PLAY_TIME_MINIMUM_MS = 5_000L

        const val HEARTBEAT_INTERVAL_MS = 15_000L

        /** A repeat loop restart lands (almost) exactly at position 0. */
        const val LOOP_RESTART_POSITION_MS = 500L

        /** Ignore backward jumps that did not play a meaningful amount first. */
        const val MINIMUM_LOOP_MS = 2_000L

        const val KEY_SNAPSHOT_ACTIVE = "listeningSnapshotActive"
        const val KEY_SNAPSHOT_SONG_ID = "listeningSnapshotSongId"
        const val KEY_SNAPSHOT_TITLE = "listeningSnapshotTitle"
        const val KEY_SNAPSHOT_ARTIST = "listeningSnapshotArtist"
        const val KEY_SNAPSHOT_ARTWORK = "listeningSnapshotArtwork"
        const val KEY_SNAPSHOT_DURATION_TEXT = "listeningSnapshotDurationText"
        const val KEY_SNAPSHOT_STARTED_AT = "listeningSnapshotStartedAt"
        const val KEY_SNAPSHOT_PLAYED_MS = "listeningSnapshotPlayedMs"
        const val KEY_SNAPSHOT_LAST_SEEN_AT = "listeningSnapshotLastSeenAt"
        const val KEY_SNAPSHOT_MEDIA_ITEM = "listeningSnapshotMediaItem"
    }
}
