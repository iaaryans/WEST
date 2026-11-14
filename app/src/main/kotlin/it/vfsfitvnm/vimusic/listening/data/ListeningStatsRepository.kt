package it.vfsfitvnm.vimusic.listening.data

import android.content.Context
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.listening.domain.ListeningOverview
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriod
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriodType
import it.vfsfitvnm.vimusic.listening.domain.ListeningTrendPoint
import it.vfsfitvnm.vimusic.listening.domain.WrappedSummary
import it.vfsfitvnm.vimusic.listening.domain.chartGranularity
import it.vfsfitvnm.vimusic.listening.domain.formatListeningDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Facade over [ListeningHistoryDao] exposing ready-to-render statistics as
 * cold Flows. Every computation runs on Dispatchers.Default and re-emits
 * whenever the history table changes, following the same Flow-based pattern
 * the rest of the app uses for database queries.
 */
object ListeningStatsRepository {

    private val dao: ListeningHistoryDao
        get() = Database.listeningHistory

    // ------------------------------------------------------------------
    // Overview
    // ------------------------------------------------------------------

    fun overview(
        period: ListeningPeriod,
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<ListeningOverview> = combine(
        dao.sessionsBetween(0, Long.MAX_VALUE),
        dao.distinctArtists(period.from, period.to),
        dao.unmappedArtistSessions(period.from, period.to)
    ) { allSessions, distinctArtists, unmapped ->
        val inPeriod = if (period.type == ListeningPeriodType.AllTime) {
            allSessions
        } else {
            allSessions.filter { it.timestamp >= period.from && it.timestamp < period.to }
        }
        ListeningStatsCalculator.overview(
            sessionsInPeriod = inPeriod,
            allSessions = allSessions,
            distinctArtists = distinctArtists,
            unmappedArtistPlays = unmapped.toLong(),
            period = period,
            zone = zone,
            today = LocalDate.now(zone)
        )
    }.flowOn(Dispatchers.Default)

    // ------------------------------------------------------------------
    // Listening-time analytics
    // ------------------------------------------------------------------

    fun trend(
        period: ListeningPeriod,
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<List<ListeningTrendPoint>> =
        dao.pointsBetween(period.from, period.to)
            .map { points -> ListeningStatsCalculator.trend(points, period, zone) }
            .flowOn(Dispatchers.Default)

    // ------------------------------------------------------------------
    // Rankings
    // ------------------------------------------------------------------

    fun topSongs(
        period: ListeningPeriod,
        limit: Int,
    ): Flow<List<TopSongEntry>> = dao.topSongs(period.from, period.to, limit)

    fun topArtists(
        period: ListeningPeriod,
        limit: Int,
    ): Flow<List<TopArtistEntry>> = dao.topArtists(period.from, period.to, limit)

    fun topAlbums(
        period: ListeningPeriod,
        limit: Int,
    ): Flow<List<TopAlbumEntry>> = dao.topAlbums(period.from, period.to, limit)

    // ------------------------------------------------------------------
    // Calendar
    // ------------------------------------------------------------------

    fun points(
        period: ListeningPeriod,
    ): Flow<List<ListeningPointRow>> = dao.pointsBetween(period.from, period.to)

    // ------------------------------------------------------------------
    // WEST Wrapped
    // ------------------------------------------------------------------

    fun availableYears(zone: ZoneId = ZoneId.systemDefault()): Flow<List<Int>> =
        dao.firstTimestamp().map { first ->
            val currentYear = LocalDate.now(zone).year
            val firstYear = first?.let { Instant.ofEpochMilli(it).atZone(zone).year }
            if (firstYear == null || firstYear > currentYear) {
                listOf(currentYear)
            } else {
                (firstYear..currentYear).toList()
            }
        }

    fun wrapped(
        year: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<WrappedSummary> {
        val period = ListeningPeriod.year(year, zone)
        return combine(
            dao.sessionsBetween(period.from, period.to),
            dao.distinctArtists(period.from, period.to),
            dao.topSongs(period.from, period.to, 5),
            dao.topArtists(period.from, period.to, 5),
            dao.topAlbums(period.from, period.to, 5)
        ) { sessions, distinctArtists, topSongs, topArtists, topAlbums ->
            ListeningStatsCalculator.wrapped(
                year = year,
                sessions = sessions,
                distinctArtists = distinctArtists,
                topSongs = topSongs,
                topArtists = topArtists,
                topAlbums = topAlbums
            )
        }.flowOn(Dispatchers.Default)
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    fun playCountThresholdDescription(context: Context): String {
        val thresholdMs = ListeningSettings.playCountThresholdMs(context)
        return "A song counts as played after ${formatListeningDuration(thresholdMs)} (or half of a short track)"
    }
}
