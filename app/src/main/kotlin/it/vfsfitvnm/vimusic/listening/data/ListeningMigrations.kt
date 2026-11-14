package it.vfsfitvnm.vimusic.listening.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Additive migration 23 → 24: creates the ListeningHistory table and
 * backfills it from the existing Event table so long-time users keep their
 * listening history in the richer format.
 *
 * - No existing table is deleted, renamed or reset.
 * - Playlists, favourites, settings and the Event table itself are untouched
 *   (quick picks keep working exactly as before).
 * - Every Event row already references an existing Song (same foreign key
 *   with CASCADE), so the backfill cannot violate the new foreign key.
 *
 * The SQL statements live in [STATEMENTS] so the migration can be executed
 * and verified on the JVM against a real SQLite engine in unit tests.
 */
class From23To24Migration : Migration(23, 24) {
    override fun migrate(it: SupportSQLiteDatabase) {
        executeAll(it::execSQL)
    }

    companion object {
        val STATEMENTS = listOf(
            "CREATE TABLE IF NOT EXISTS `ListeningHistory` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`songId` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`endedAt` INTEGER NOT NULL, " +
                "`playedMs` INTEGER NOT NULL, " +
                "`counted` INTEGER NOT NULL, " +
                "FOREIGN KEY(`songId`) REFERENCES `Song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",

            "CREATE INDEX IF NOT EXISTS `index_ListeningHistory_songId` ON `ListeningHistory` (`songId`)",

            "CREATE INDEX IF NOT EXISTS `index_ListeningHistory_timestamp` ON `ListeningHistory` (`timestamp`)",

            // Preserve the existing listening history: every Event is already
            // a counted play (the service only inserts events above 30 s).
            "INSERT INTO ListeningHistory (songId, timestamp, endedAt, playedMs, counted) " +
                "SELECT songId, timestamp, timestamp, playTime, 1 FROM Event WHERE playTime > 0"
        )

        fun executeAll(exec: (String) -> Unit) {
            STATEMENTS.forEach(exec)
        }
    }
}
