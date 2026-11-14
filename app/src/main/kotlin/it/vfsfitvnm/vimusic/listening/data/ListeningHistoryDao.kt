package it.vfsfitvnm.vimusic.listening.data

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.DatabaseInitializer
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the ListeningHistory table added in database version 24.
 *
 * Kept as a separate interface (instead of growing the existing `Database`
 * DAO) so the statistics feature stays an isolated module; it is reachable
 * through the same singleton used by the rest of the app:
 * `Database.listeningHistory`.
 */
@Dao
interface ListeningHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Throws(SQLException::class)
    fun insert(entry: ListeningHistoryEntity): Long

    @Query("SELECT MIN(timestamp) FROM ListeningHistory")
    fun firstTimestamp(): Flow<Long?>

    /**
     * All sessions whose START falls inside [from, to[ — the raw input for the
     * statistics calculator. Returns a Flow so stats refresh whenever new
     * history is recorded.
     */
    @Query("SELECT id, songId, timestamp, endedAt, playedMs, counted FROM ListeningHistory WHERE timestamp >= :from AND timestamp < :to")
    fun sessionsBetween(from: Long, to: Long): Flow<List<ListeningSessionRow>>

    @Query("SELECT timestamp, playedMs, counted FROM ListeningHistory WHERE timestamp >= :from AND timestamp < :to")
    fun pointsBetween(from: Long, to: Long): Flow<List<ListeningPointRow>>

    /**
     * Number of distinct artists listened to (counted plays only). Songs
     * without an artist mapping are not represented here; the overview also
     * reports them separately so nothing is silently faked.
     */
    @Query(
        "SELECT COUNT(DISTINCT m.artistId) FROM ListeningHistory h " +
            "JOIN SongArtistMap m ON m.songId = h.songId " +
            "WHERE h.counted = 1 AND h.timestamp >= :from AND h.timestamp < :to"
    )
    fun distinctArtists(from: Long, to: Long): Flow<Int>

    /** Sessions of songs without any artist mapping (counted plays only). */
    @Query(
        "SELECT COUNT(*) FROM ListeningHistory h " +
            "WHERE h.counted = 1 AND h.timestamp >= :from AND h.timestamp < :to " +
            "AND NOT EXISTS (SELECT 1 FROM SongArtistMap m WHERE m.songId = h.songId)"
    )
    fun unmappedArtistSessions(from: Long, to: Long): Flow<Int>

    @Query(
        "SELECT h.songId AS songId, s.title AS title, s.artistsText AS artistsText, s.thumbnailUrl AS thumbnailUrl, " +
            "SUM(h.counted) AS playCount, SUM(h.playedMs) AS totalPlayedMs, COUNT(h.id) AS sessionCount " +
            "FROM ListeningHistory h JOIN Song s ON s.id = h.songId " +
            "WHERE h.timestamp >= :from AND h.timestamp < :to " +
            "GROUP BY h.songId HAVING playCount > 0 " +
            "ORDER BY playCount DESC, totalPlayedMs DESC LIMIT :limit"
    )
    fun topSongs(from: Long, to: Long, limit: Int): Flow<List<TopSongEntry>>

    @Query(
        "SELECT m.artistId AS artistId, a.name AS name, a.thumbnailUrl AS thumbnailUrl, " +
            "SUM(h.counted) AS playCount, SUM(h.playedMs) AS totalPlayedMs, COUNT(DISTINCT h.songId) AS trackCount " +
            "FROM ListeningHistory h " +
            "JOIN SongArtistMap m ON m.songId = h.songId " +
            "LEFT JOIN Artist a ON a.id = m.artistId " +
            "WHERE h.timestamp >= :from AND h.timestamp < :to " +
            "GROUP BY m.artistId HAVING playCount > 0 " +
            "ORDER BY totalPlayedMs DESC, playCount DESC LIMIT :limit"
    )
    fun topArtists(from: Long, to: Long, limit: Int): Flow<List<TopArtistEntry>>

    @Query(
        "SELECT m.albumId AS albumId, al.title AS title, al.authorsText AS artistsText, al.thumbnailUrl AS thumbnailUrl, " +
            "SUM(h.counted) AS playCount, SUM(h.playedMs) AS totalPlayedMs, COUNT(DISTINCT h.songId) AS trackCount " +
            "FROM ListeningHistory h " +
            "JOIN SongAlbumMap m ON m.songId = h.songId " +
            "LEFT JOIN Album al ON al.id = m.albumId " +
            "WHERE h.timestamp >= :from AND h.timestamp < :to " +
            "GROUP BY m.albumId HAVING playCount > 0 " +
            "ORDER BY playCount DESC, totalPlayedMs DESC LIMIT :limit"
    )
    fun topAlbums(from: Long, to: Long, limit: Int): Flow<List<TopAlbumEntry>>
}

/**
 * Accessor mirroring the existing `Database.internal` extension: the DAO is
 * implemented by the Room singleton, like every other DAO in the app.
 */
val Database.listeningHistory: ListeningHistoryDao
    get() = DatabaseInitializer.Instance.listeningHistoryDao
