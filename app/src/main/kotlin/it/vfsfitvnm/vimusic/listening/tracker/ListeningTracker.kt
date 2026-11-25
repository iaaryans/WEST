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
        active = this
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        finalizeCurrent(endedAt = System.currentTimeMillis())
        if (mediaItem != null) beginSession(mediaItem)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val current = session
        if (isPlaying) {
            val active = current ?: player.currentMediaItem?.let {
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

    fun shutdown() {
        finalizeCurrent(endedAt = System.currentTimeMillis())
        if (active === this) active = null
    }

    fun checkpointNow() {
        persistSnapshot()
    }

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

    private fun recoverOrphanedSession() {
        if (!preferences.getBoolean(KEY_SNAPSHOT_ACTIVE, false)) return

        val songId = preferences.getString(KEY_SNAPSHOT_SONG_ID, null)
        val startedAt = preferences.getLong(KEY_SNAPSHOT_STARTED_AT, 0L)
        val playedMs = preferences.getLong(KEY_SNAPSHOT_PLAYED_MS, 0L)
        val lastSeenAt = preferences.getLong(KEY_SNAPSHOT_LAST_SEEN_AT, startedAt)

        clearSnapshot()

        if (songId == null || playedMs <= MINIMUM_SESSION_MS) return

        val mediaItem = decodeSnapshotMediaItem() ?: fallbackMediaItem(songId)

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

    internal companion object {
        const val MINIMUM_SESSION_MS = 5_000L
        const val TOTAL_PLAY_TIME_MINIMUM_MS = 5_000L
        const val HEARTBEAT_INTERVAL_MS = 15_000L
        const val LOOP_RESTART_POSITION_MS = 500L
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

        @Volatile
        var active: ListeningTracker? = null
    }
}
