package it.vfsfitvnm.vimusic.listening.data

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.vfsfitvnm.vimusic.models.Song

/**
 * One recorded listening session: a single continuous playback of one song
 * (from the moment it becomes the current media item until playback moves
 * away from it, ends, or a repeat loop restarts it).
 *
 * [timestamp] is the session START (used for day attribution in statistics),
 * [endedAt] is the session end (used for "recently played" ordering),
 * [playedMs] is the actual playback duration excluding pauses/buffering,
 * [counted] marks sessions that satisfy the play-counting threshold
 * (at least 30 seconds or half of a short track, whichever is smaller).
 */
@Immutable
@Entity(
    tableName = "ListeningHistory",
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("songId"),
        Index("timestamp")
    ]
)
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val timestamp: Long,
    val endedAt: Long,
    val playedMs: Long,
    val counted: Boolean
)

/** Minimal projection consumed by the statistics calculator. */
@Immutable
data class ListeningSessionRow(
    val id: Long,
    val songId: String,
    val timestamp: Long,
    val endedAt: Long,
    val playedMs: Long,
    val counted: Boolean
)

/** Timestamp/duration pair used for day bucketing (heatmap, charts). */
@Immutable
data class ListeningPointRow(
    val timestamp: Long,
    val playedMs: Long,
    val counted: Boolean
)

@Immutable
data class TopSongEntry(
    val songId: String,
    val title: String?,
    val artistsText: String?,
    val thumbnailUrl: String?,
    val playCount: Long,
    val totalPlayedMs: Long,
    val sessionCount: Long
)

@Immutable
data class TopArtistEntry(
    val artistId: String,
    val name: String?,
    val thumbnailUrl: String?,
    val playCount: Long,
    val totalPlayedMs: Long,
    val trackCount: Long
)

@Immutable
data class TopAlbumEntry(
    val albumId: String,
    val title: String?,
    val artistsText: String?,
    val thumbnailUrl: String?,
    val playCount: Long,
    val totalPlayedMs: Long,
    val trackCount: Long
)
