package it.vfsfitvnm.vimusic.listening

import it.vfsfitvnm.vimusic.listening.data.From23To24Migration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Executes the 23 → 24 migration against a real SQLite engine on the JVM:
 *
 * 1. builds a database shaped like the original version 23 (Song + Event),
 * 2. runs [From23To24Migration.STATEMENTS],
 * 3. verifies the new table, its indices, the foreign key and the backfill,
 *    and that existing data survived untouched.
 */
class From23To24MigrationTest {

    private fun connection(): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    private fun createVersion23Schema(connection: Connection) {
        connection.createStatement().use { statement ->
            // Minimal but faithful shape of the version 23 tables the
            // migration touches (columns referenced by the statements).
            statement.execute(
                "CREATE TABLE `Song` (" +
                    "`id` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`artistsText` TEXT, `durationText` TEXT, `thumbnailUrl` TEXT, " +
                    "`likedAt` INTEGER, `totalPlayTimeMs` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            statement.execute(
                "CREATE TABLE `Event` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`songId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `playTime` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`songId`) REFERENCES `Song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            statement.execute("CREATE INDEX `index_Event_songId` ON `Event` (`songId`)")

            // Existing user data: songs, an Event history, a playlist-ish table
            // that must not be touched by the migration.
            statement.execute("INSERT INTO Song (id, title, totalPlayTimeMs) VALUES ('songA', 'Song A', 100)")
            statement.execute("INSERT INTO Song (id, title, totalPlayTimeMs) VALUES ('songB', 'Song B', 200)")
            statement.execute("INSERT INTO Event (songId, timestamp, playTime) VALUES ('songA', 1000, 31000)")
            statement.execute("INSERT INTO Event (songId, timestamp, playTime) VALUES ('songA', 2000, 45000)")
            statement.execute("INSERT INTO Event (songId, timestamp, playTime) VALUES ('songB', 3000, 0)")
            statement.execute("CREATE TABLE Untouched (id INTEGER PRIMARY KEY, name TEXT)")
            statement.execute("INSERT INTO Untouched (name) VALUES ('keep-me')")
        }
    }

    @Test
    fun `migration creates the table, indices and backfills without touching existing data`() {
        connection().use { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            createVersion23Schema(connection)

            From23To24Migration.executeAll { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            connection.createStatement().use { statement ->
                // The new table exists with the expected shape
                statement.executeQuery(
                    "SELECT songId, timestamp, endedAt, playedMs, counted FROM ListeningHistory ORDER BY id"
                ).use { resultSet ->
                    val rows = mutableListOf<List<Any>>()
                    while (resultSet.next()) {
                        rows += listOf(
                            resultSet.getString(1),
                            resultSet.getLong(2),
                            resultSet.getLong(3),
                            resultSet.getLong(4),
                            resultSet.getInt(5)
                        )
                    }

                    // The zero-play Event row is filtered out by the backfill
                    assertEquals(2, rows.size)
                    assertEquals("songA", rows[0][0])
                    assertEquals(1000L, rows[0][1])
                    assertEquals(1000L, rows[0][2])  // endedAt backfilled from timestamp
                    assertEquals(31000L, rows[0][3])
                    assertEquals(1, rows[0][4])      // counted = 1
                    assertEquals("songA", rows[1][0])
                    assertEquals(45000L, rows[1][3])
                }

                // Indices exist
                statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'ListeningHistory'"
                ).use { resultSet ->
                    val indices = mutableListOf<String>()
                    while (resultSet.next()) indices += resultSet.getString(1)
                    assertTrue(indices.contains("index_ListeningHistory_songId"))
                    assertTrue(indices.contains("index_ListeningHistory_timestamp"))
                }

                // Existing data is untouched
                statement.executeQuery("SELECT COUNT(*) FROM Event").use { resultSet ->
                    resultSet.next()
                    assertEquals(3, resultSet.getInt(1))
                }
                statement.executeQuery("SELECT COUNT(*) FROM Song").use { resultSet ->
                    resultSet.next()
                    assertEquals(2, resultSet.getInt(1))
                }
                statement.executeQuery("SELECT name FROM Untouched").use { resultSet ->
                    resultSet.next()
                    assertEquals("keep-me", resultSet.getString(1))
                }

                // The foreign key is enforced: inserting a row for an
                // unknown song must fail.
                var constraintFailed = false
                try {
                    statement.execute(
                        "INSERT INTO ListeningHistory (songId, timestamp, endedAt, playedMs, counted) " +
                            "VALUES ('missing', 1, 2, 1000, 1)"
                    )
                } catch (_: Exception) {
                    constraintFailed = true
                }
                assertTrue(constraintFailed)
            }
        }
    }
}
