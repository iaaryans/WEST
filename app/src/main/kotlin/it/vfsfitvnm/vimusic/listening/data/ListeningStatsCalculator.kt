package it.vfsfitvnm.vimusic.listening.data

import it.vfsfitvnm.vimusic.listening.domain.ChartGranularity
import it.vfsfitvnm.vimusic.listening.domain.DailyListening
import it.vfsfitvnm.vimusic.listening.domain.ListeningOverview
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriod
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriodType
import it.vfsfitvnm.vimusic.listening.domain.ListeningTrendPoint
import it.vfsfitvnm.vimusic.listening.domain.MostActiveDay
import it.vfsfitvnm.vimusic.listening.domain.Streaks
import it.vfsfitvnm.vimusic.listening.domain.WeekdayListening
import it.vfsfitvnm.vimusic.listening.domain.WrappedSummary
import it.vfsfitvnm.vimusic.listening.domain.chartGranularity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure-Kotlin statistics engine: every metric is derived from raw session
 * rows, so it is fully unit-testable on the JVM and works offline.
 *
 * Day attribution uses the session START timestamp converted to the local
 * calendar date of the given time zone (java.time handles DST transitions
 * exactly).
 */
object ListeningStatsCalculator {

    // ------------------------------------------------------------------
    // Day keys
    // ------------------------------------------------------------------

    fun localDate(timestamp: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    // ------------------------------------------------------------------
    // Overview
    // ------------------------------------------------------------------

    fun overview(
        sessionsInPeriod: List<ListeningSessionRow>,
        allSessions: List<ListeningSessionRow>,
        distinctArtists: Int,
        unmappedArtistPlays: Long,
        period: ListeningPeriod,
        zone: ZoneId,
        today: LocalDate
    ): ListeningOverview {
        val totalListeningMs = sessionsInPeriod.sumOf { it.playedMs }
        val counted = sessionsInPeriod.filter { it.counted }
        val uniqueSongs = counted.map { it.songId }.distinct().size.toLong()
        val activeDays = sessionsInPeriod.map { localDate(it.timestamp, zone).toEpochDay() }.distinct().size.toLong()

        val periodDays = when (period.type) {
            ListeningPeriodType.AllTime -> {
                val first = allSessions.minOfOrNull { it.timestamp }
                if (first == null) 1L
                else (today.toEpochDay() - localDate(first, zone).toEpochDay() + 1).coerceAtLeast(1)
            }

            else -> {
                val start = localDate(period.from, zone)
                val end = localDate(period.to - 1, zone)
                (end.toEpochDay() - start.toEpochDay() + 1).coerceAtLeast(1)
            }
        }

        val averageDailyMs = totalListeningMs / periodDays

        return ListeningOverview(
            totalListeningMs = totalListeningMs,
            totalPlays = counted.size.toLong(),
            uniqueSongs = uniqueSongs,
            uniqueArtists = distinctArtists.toLong(),
            unmappedArtistPlays = unmappedArtistPlays,
            averageDailyMs = averageDailyMs,
            currentStreakDays = streaks(allSessions, zone, today).current,
            longestStreakDays = streaks(allSessions, zone, today).longest,
            activeDays = activeDays,
            periodDays = periodDays
        )
    }

    // ------------------------------------------------------------------
    // Streaks
    // ------------------------------------------------------------------

    /**
     * A day is "active" when at least one session starts on it.
     * The current streak is alive when today is active, or when yesterday is
     * active and today is not over yet.
     */
    fun streaks(sessions: List<ListeningSessionRow>, zone: ZoneId, today: LocalDate): Streaks {
        val days = sessions.map { localDate(it.timestamp, zone).toEpochDay() }.toSortedSet()
        if (days.isEmpty()) return Streaks(current = 0, longest = 0)

        var current = 0
        var probe = today.toEpochDay()
        if (!days.contains(probe) && days.contains(probe - 1)) probe -= 1
        while (days.contains(probe)) {
            current++
            probe--
        }

        var longest = 0
        var run = 0
        var previous = Long.MIN_VALUE
        for (day in days) {
            run = if (previous != Long.MIN_VALUE && day == previous + 1) run + 1 else 1
            if (run > longest) longest = run
            previous = day
        }

        return Streaks(current = current, longest = longest)
    }

    // ------------------------------------------------------------------
    // Trend chart
    // ------------------------------------------------------------------

    /** Hard cap on rendered year points (defence against pathological ranges). */
    private const val MAX_TREND_YEARS = 500

    fun trend(
        points: List<ListeningPointRow>,
        period: ListeningPeriod,
        zone: ZoneId
    ): List<ListeningTrendPoint> = when (period.chartGranularity()) {
        ChartGranularity.Hour -> hourlyTrend(points, zone)
        ChartGranularity.Day -> dailyTrend(points, period, zone)
        ChartGranularity.Month -> monthlyTrend(points, period, zone)
        ChartGranularity.Year -> yearlyTrend(points, period, zone)
    }

    private fun hourlyTrend(points: List<ListeningPointRow>, zone: ZoneId): List<ListeningTrendPoint> {
        val buckets = LongArray(24)
        val plays = LongArray(24)
        points.forEach { point ->
            val hour = Instant.ofEpochMilli(point.timestamp).atZone(zone).hour
            buckets[hour] += point.playedMs
            if (point.counted) plays[hour]++
        }
        return buckets.indices.map { hour ->
            ListeningTrendPoint(label = "%02d:00".format(hour), playedMs = buckets[hour], plays = plays[hour])
        }
    }

    private fun dailyTrend(points: List<ListeningPointRow>, period: ListeningPeriod, zone: ZoneId): List<ListeningTrendPoint> {
        val aggregated = points.groupBy { localDate(it.timestamp, zone) }
            .mapValues { (_, list) ->
                list.sumOf { it.playedMs } to list.count { it.counted }.toLong()
            }

        val start = localDate(period.from, zone)
        val endExclusive = localDate(period.to, zone)
        val result = mutableListOf<ListeningTrendPoint>()
        var cursor = start
        while (cursor < endExclusive && result.size < 370) {
            val (played, plays) = aggregated[cursor] ?: (0L to 0L)
            result += ListeningTrendPoint(
                label = "${cursor.dayOfMonth}",
                playedMs = played,
                plays = plays
            )
            cursor = cursor.plusDays(1)
        }
        return result
    }

    private fun monthlyTrend(points: List<ListeningPointRow>, period: ListeningPeriod, zone: ZoneId): List<ListeningTrendPoint> {
        val aggregated = points.groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }
            .mapValues { (_, list) ->
                list.sumOf { it.playedMs } to list.count { it.counted }.toLong()
            }

        var cursor = YearMonth.from(Instant.ofEpochMilli(period.from).atZone(zone))
        val end = YearMonth.from(Instant.ofEpochMilli(period.to - 1).atZone(zone))
        val result = mutableListOf<ListeningTrendPoint>()
        do {
            val (played, plays) = aggregated[cursor] ?: (0L to 0L)
            result += ListeningTrendPoint(
                label = cursor.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                playedMs = played,
                plays = plays
            )
            cursor = cursor.plusMonths(1)
        } while (cursor <= end && result.size < 240)
        return result
    }

    private fun yearlyTrend(points: List<ListeningPointRow>, period: ListeningPeriod, zone: ZoneId): List<ListeningTrendPoint> {
        val aggregated = points.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).year }
            .mapValues { (_, list) ->
                list.sumOf { it.playedMs } to list.count { it.counted }.toLong()
            }

        // Missing years inside the rendered range stay as zero points, but
        // the range itself is derived from the actual listening data: the
        // All-time period uses Long.MAX_VALUE as its upper bound, so
        // iterating the raw period bounds would allocate one point per year
        // between 1970 and year ~292 million (an OutOfMemoryError even with
        // an empty history). Years without any session carry no information
        // outside the first/last listening year, so they are not rendered.
        if (aggregated.isEmpty()) return emptyList()

        val dataFirstYear = aggregated.keys.min()
        val dataLastYear = aggregated.keys.max()

        val requestedFirstYear = Instant.ofEpochMilli(period.from).atZone(zone).year
        val requestedLastYear = Instant.ofEpochMilli(period.to - 1).atZone(zone).year

        val firstYear = maxOf(requestedFirstYear, dataFirstYear)
        val lastYear = minOf(requestedLastYear, dataLastYear)

        // Defensive cap for pathological custom ranges: with the data-derived
        // bounds above the range is normally small, but a custom period may
        // still span far more years than any real history (long sessions of
        // the same trend would never need more than this many year points).
        val cappedFirstYear = maxOf(firstYear, lastYear - (MAX_TREND_YEARS - 1))

        return (cappedFirstYear..lastYear).map { year ->
            val (played, plays) = aggregated[year] ?: (0L to 0L)
            ListeningTrendPoint(label = year.toString(), playedMs = played, plays = plays)
        }
    }

    // ------------------------------------------------------------------
    // Calendar
    // ------------------------------------------------------------------

    fun dailyStats(points: List<ListeningPointRow>, zone: ZoneId): Map<LocalDate, DailyListening> =
        points.groupBy { localDate(it.timestamp, zone) }
            .mapValues { (_, list) ->
                DailyListening(
                    date = localDate(list.first().timestamp, zone),
                    playedMs = list.sumOf { it.playedMs },
                    plays = list.count { it.counted }.toLong()
                )
            }

    fun mostActiveDays(points: List<ListeningPointRow>, zone: ZoneId, count: Int = 5): List<MostActiveDay> =
        dailyStats(points, zone).values
            .sortedByDescending { it.playedMs }
            .take(count)
            .map { MostActiveDay(date = it.date, playedMs = it.playedMs, plays = it.plays) }

    fun weekdayDistribution(points: List<ListeningPointRow>, zone: ZoneId): List<WeekdayListening> {
        val buckets = LongArray(7)
        points.forEach { point ->
            val dayOfWeek = Instant.ofEpochMilli(point.timestamp).atZone(zone).dayOfWeek
            buckets[dayOfWeek.value - 1] += point.playedMs
        }
        return buckets.indices.map { WeekdayListening(weekday = it, playedMs = buckets[it]) }
    }

    // ------------------------------------------------------------------
    // Wrapped
    // ------------------------------------------------------------------

    fun wrapped(
        year: Int,
        sessions: List<ListeningSessionRow>,
        distinctArtists: Int,
        topSongs: List<TopSongEntry>,
        topArtists: List<TopArtistEntry>,
        topAlbums: List<TopAlbumEntry>
    ): WrappedSummary {
        val monthly = LongArray(12)
        sessions.forEach { session ->
            val month = Instant.ofEpochMilli(session.timestamp).atZone(ZoneId.systemDefault()).monthValue
            monthly[month - 1] += session.playedMs
        }
        val bestMonth = monthly.indices.filter { monthly[it] > 0 }
            .maxByOrNull { monthly[it] }

        val counted = sessions.filter { it.counted }

        return WrappedSummary(
            year = year,
            totalListeningMs = sessions.sumOf { it.playedMs },
            totalPlays = counted.size.toLong(),
            uniqueSongs = counted.map { it.songId }.distinct().size.toLong(),
            uniqueArtists = distinctArtists.toLong(),
            mostActiveMonth = bestMonth?.plus(1),
            mostActiveMonthMs = bestMonth?.let { monthly[it] } ?: 0L,
            monthlyPlayedMs = monthly.toList(),
            topSongs = topSongs,
            topArtists = topArtists,
            topAlbums = topAlbums
        )
    }
}
