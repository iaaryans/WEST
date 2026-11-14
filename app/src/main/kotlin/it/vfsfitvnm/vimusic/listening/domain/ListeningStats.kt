package it.vfsfitvnm.vimusic.listening.domain

import it.vfsfitvnm.vimusic.listening.data.TopAlbumEntry
import it.vfsfitvnm.vimusic.listening.data.TopArtistEntry
import it.vfsfitvnm.vimusic.listening.data.TopSongEntry
import java.time.LocalDate

/** Headline numbers of the listening overview section. */
data class ListeningOverview(
    val totalListeningMs: Long,
    val totalPlays: Long,
    val uniqueSongs: Long,
    val uniqueArtists: Long,
    /** Counted plays of songs that have no artist mapping in the library. */
    val unmappedArtistPlays: Long,
    val averageDailyMs: Long,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val activeDays: Long,
    val periodDays: Long
) {
    val isEmpty: Boolean get() = totalListeningMs == 0L && totalPlays == 0L
}

/** One point of the listening-time chart. */
data class ListeningTrendPoint(
    val label: String,
    val playedMs: Long,
    val plays: Long
)

/** Listening totals for a single calendar day. */
data class DailyListening(
    val date: LocalDate,
    val playedMs: Long,
    val plays: Long
)

data class Streaks(
    val current: Int,
    val longest: Int
)

data class MostActiveDay(
    val date: LocalDate,
    val playedMs: Long,
    val plays: Long
)

/** Totals for one weekday (0 = Monday ... 6 = Sunday). */
data class WeekdayListening(
    val weekday: Int,
    val playedMs: Long
)

/** Annual summary powering WEST Wrapped. */
data class WrappedSummary(
    val year: Int,
    val totalListeningMs: Long,
    val totalPlays: Long,
    val uniqueSongs: Long,
    val uniqueArtists: Long,
    /** Month number 1..12 with the most listening, null when silent year. */
    val mostActiveMonth: Int?,
    val mostActiveMonthMs: Long,
    /** Exactly 12 entries, index 0 = January. */
    val monthlyPlayedMs: List<Long>,
    val topSongs: List<TopSongEntry>,
    val topArtists: List<TopArtistEntry>,
    val topAlbums: List<TopAlbumEntry>
)

// ----------------------------------------------------------------------
// Formatting helpers (shared by every statistics screen)
// ----------------------------------------------------------------------

/** "1d", "2d 3h", "1h", "3h 24m", "12m", "45s" — compact human duration. */
fun formatListeningDuration(ms: Long): String {
    if (ms <= 0) return "0s"
    val totalSeconds = ms / 1000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> if (hours > 0) "${days}d ${hours}h" else "${days}d"
        hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

/** "2,450 hours" — hours rounded down, grouped. */
fun formatHours(ms: Long): String {
    val hours = ms / 3_600_000
    return "%,d".format(hours)
}

fun formatCount(count: Long): String = "%,d".format(count)

fun formatPlural(count: Long, singular: String, plural: String = singular + "s"): String =
    if (count == 1L) "1 $singular" else "${formatCount(count)} $plural"
