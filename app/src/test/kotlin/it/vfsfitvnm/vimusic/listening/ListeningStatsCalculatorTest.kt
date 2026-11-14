package it.vfsfitvnm.vimusic.listening

import it.vfsfitvnm.vimusic.listening.data.ListeningPointRow
import it.vfsfitvnm.vimusic.listening.data.ListeningSessionRow
import it.vfsfitvnm.vimusic.listening.data.ListeningStatsCalculator
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriod
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Verifies the statistics engine: day attribution across date boundaries
 * and time zones (including DST), streak calculation, empty input and
 * period math.
 */
class ListeningStatsCalculatorTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    private fun row(
        songId: String,
        at: LocalDateTime,
        playedMs: Long = 60_000,
        counted: Boolean = true,
        zone: ZoneId = utc
    ) = ListeningSessionRow(
        id = 0L,
        songId = songId,
        timestamp = at.atZone(zone).toInstant().toEpochMilli(),
        endedAt = at.plusMinutes(1).atZone(zone).toInstant().toEpochMilli(),
        playedMs = playedMs,
        counted = counted
    )

    private fun point(at: LocalDateTime, playedMs: Long = 60_000, counted: Boolean = true) =
        ListeningPointRow(
            timestamp = at.atZone(utc).toInstant().toEpochMilli(),
            playedMs = playedMs,
            counted = counted
        )

    // ------------------------------------------------------------------
    // Streaks
    // ------------------------------------------------------------------

    @Test
    fun `empty history has zero streaks`() {
        val streaks = ListeningStatsCalculator.streaks(emptyList(), utc, LocalDate.of(2025, 6, 15))
        assertEquals(0, streaks.current)
        assertEquals(0, streaks.longest)
    }

    @Test
    fun `current streak counts back from today`() {
        val today = LocalDate.of(2025, 6, 15)
        val sessions = (0..4L).map { day ->
            row("s$day", LocalDateTime.of(2025, 6, 15, 10, 0).minusDays(day))
        }

        val streaks = ListeningStatsCalculator.streaks(sessions, utc, today)
        assertEquals(5, streaks.current)
    }

    @Test
    fun `streak stays alive until midnight when yesterday was active`() {
        val today = LocalDate.of(2025, 6, 15)
        val sessions = listOf(
            row("a", LocalDateTime.of(2025, 6, 14, 22, 0)),
            row("b", LocalDateTime.of(2025, 6, 13, 9, 0))
        )

        val streaks = ListeningStatsCalculator.streaks(sessions, utc, today)
        assertEquals(2, streaks.current)
    }

    @Test
    fun `streak broken by an inactive yesterday`() {
        val today = LocalDate.of(2025, 6, 15)
        val sessions = listOf(
            row("a", LocalDateTime.of(2025, 6, 12, 22, 0)),
            row("b", LocalDateTime.of(2025, 6, 11, 9, 0))
        )

        val streaks = ListeningStatsCalculator.streaks(sessions, utc, today)
        assertEquals(0, streaks.current)
        assertEquals(2, streaks.longest)
    }

    @Test
    fun `longest streak is the maximal consecutive run`() {
        val sessions = listOf(
            row("a", LocalDateTime.of(2025, 1, 1, 8, 0)),
            row("b", LocalDateTime.of(2025, 1, 2, 8, 0)),
            row("c", LocalDateTime.of(2025, 1, 3, 8, 0)),
            // gap on the 4th
            row("d", LocalDateTime.of(2025, 1, 5, 8, 0)),
            row("e", LocalDateTime.of(2025, 1, 6, 8, 0)),
            row("f", LocalDateTime.of(2025, 1, 7, 8, 0)),
            row("g", LocalDateTime.of(2025, 1, 8, 8, 0))
        )

        val streaks = ListeningStatsCalculator.streaks(sessions, utc, LocalDate.of(2025, 6, 15))
        assertEquals(0, streaks.current)
        assertEquals(4, streaks.longest)
    }

    // ------------------------------------------------------------------
    // Day attribution and time zones
    // ------------------------------------------------------------------

    @Test
    fun `session before midnight belongs to its start day`() {
        val zone = ZoneId.of("Europe/Rome")
        // 23:58 on 5 June in Rome
        val timestamp = LocalDateTime.of(2025, 6, 5, 23, 58).atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            LocalDate.of(2025, 6, 5),
            ListeningStatsCalculator.localDate(timestamp, zone)
        )
    }

    @Test
    fun `UTC session lands on the next day in a positive offset zone`() {
        val zone = ZoneId.of("Asia/Tokyo")
        // 20:00 UTC on 5 June = 6 June 05:00 in Tokyo
        val timestamp = LocalDateTime.of(2025, 6, 5, 20, 0).atZone(utc).toInstant().toEpochMilli()

        assertEquals(
            LocalDate.of(2025, 6, 6),
            ListeningStatsCalculator.localDate(timestamp, zone)
        )
    }

    @Test
    fun `DST spring forward keeps day attribution correct`() {
        val zone = ZoneId.of("Europe/Rome")
        // 30 March 2025, 02:30 does not exist (clocks jump 02:00 -> 03:00);
        // a timestamp at 01:59 UTC+1 = 01:00 UTC belongs to the 30th
        val beforeTransition = LocalDateTime.of(2025, 3, 30, 1, 59)
            .atZone(zone).toInstant().toEpochMilli()
        val afterTransition = LocalDateTime.of(2025, 3, 30, 3, 1)
            .atZone(zone).toInstant().toEpochMilli()

        assertEquals(LocalDate.of(2025, 3, 30), ListeningStatsCalculator.localDate(beforeTransition, zone))
        assertEquals(LocalDate.of(2025, 3, 30), ListeningStatsCalculator.localDate(afterTransition, zone))
    }

    @Test
    fun `day boundary in local time is respected for bucketing`() {
        val zone = ZoneId.of("America/New_York")
        // Two sessions: 23:30 and 00:30 local on consecutive days
        val lateNight = ListeningPointRow(
            timestamp = LocalDateTime.of(2025, 6, 5, 23, 30).atZone(zone).toInstant().toEpochMilli(),
            playedMs = 60_000,
            counted = true
        )
        val earlyMorning = ListeningPointRow(
            timestamp = LocalDateTime.of(2025, 6, 6, 0, 30).atZone(zone).toInstant().toEpochMilli(),
            playedMs = 60_000,
            counted = true
        )

        val stats = ListeningStatsCalculator.dailyStats(listOf(lateNight, earlyMorning), zone)

        assertEquals(2, stats.size)
        assertEquals(60_000L, stats.getValue(LocalDate.of(2025, 6, 5)).playedMs)
        assertEquals(60_000L, stats.getValue(LocalDate.of(2025, 6, 6)).playedMs)
    }

    // ------------------------------------------------------------------
    // Overview
    // ------------------------------------------------------------------

    @Test
    fun `overview aggregates totals from counted and uncounted sessions`() {
        val zone = utc
        val period = ListeningPeriod.month(LocalDateTime.of(2025, 6, 15, 12, 0).atZone(zone).toInstant().toEpochMilli(), zone)
        val today = LocalDate.of(2025, 6, 30)

        val sessions = listOf(
            row("a", LocalDateTime.of(2025, 6, 1, 10, 0), playedMs = 60_000, counted = true),
            row("a", LocalDateTime.of(2025, 6, 2, 10, 0), playedMs = 45_000, counted = true),  // replay
            row("b", LocalDateTime.of(2025, 6, 3, 10, 0), playedMs = 10_000, counted = false), // short listen
            row("c", LocalDateTime.of(2025, 6, 3, 11, 0), playedMs = 60_000, counted = true)
        )

        val overview = ListeningStatsCalculator.overview(
            sessionsInPeriod = sessions,
            allSessions = sessions,
            distinctArtists = 2,
            unmappedArtistPlays = 1,
            period = period,
            zone = zone,
            today = today
        )

        assertEquals(175_000L, overview.totalListeningMs)   // all listening time, even uncounted
        assertEquals(3L, overview.totalPlays)               // only counted plays
        assertEquals(2L, overview.uniqueSongs)              // 'a' (replay is the same song) and 'c'; 'b' was never counted
        assertEquals(2L, overview.uniqueArtists)
        assertEquals(3L, overview.activeDays)
        assertEquals(30L, overview.periodDays)
        assertEquals(175_000L / 30L, overview.averageDailyMs)
    }

    @Test
    fun `overview with empty history is empty but consistent`() {
        val period = ListeningPeriod.month(LocalDateTime.of(2025, 6, 15, 12, 0).atZone(utc).toInstant().toEpochMilli(), utc)

        val overview = ListeningStatsCalculator.overview(
            sessionsInPeriod = emptyList(),
            allSessions = emptyList(),
            distinctArtists = 0,
            unmappedArtistPlays = 0,
            period = period,
            zone = utc,
            today = LocalDate.of(2025, 6, 30)
        )

        assertTrue(overview.isEmpty)
        assertEquals(0L, overview.totalListeningMs)
        assertEquals(30L, overview.periodDays) // June has 30 days; never zero, avoids division by zero
    }

    @Test
    fun `all-time average uses days since first session`() {
        val today = LocalDate.of(2025, 6, 30)
        val sessions = listOf(
            row("a", LocalDateTime.of(2025, 6, 28, 10, 0), playedMs = 90_000)
        )

        val overview = ListeningStatsCalculator.overview(
            sessionsInPeriod = sessions,
            allSessions = sessions,
            distinctArtists = 0,
            unmappedArtistPlays = 0,
            period = ListeningPeriod.allTime(),
            zone = utc,
            today = today
        )

        assertEquals(3L, overview.periodDays) // 28, 29, 30
        assertEquals(30_000L, overview.averageDailyMs)
    }

    // ------------------------------------------------------------------
    // Trend bucketing
    // ------------------------------------------------------------------

    @Test
    fun `hourly trend has 24 buckets`() {
        val zone = utc
        val period = ListeningPeriod.day(
            LocalDateTime.of(2025, 6, 5, 12, 0).atZone(zone).toInstant().toEpochMilli(),
            zone
        )
        val points = listOf(
            point(LocalDateTime.of(2025, 6, 5, 7, 15)),
            point(LocalDateTime.of(2025, 6, 5, 7, 45)),
            point(LocalDateTime.of(2025, 6, 5, 19, 0))
        )

        val trend = ListeningStatsCalculator.trend(points, period, zone)

        assertEquals(24, trend.size)
        assertEquals(120_000L, trend[7].playedMs)
        assertEquals(60_000L, trend[19].playedMs)
        assertEquals(0L, trend[12].playedMs)
    }

    @Test
    fun `monthly trend covers twelve months with labels`() {
        val zone = utc
        val period = ListeningPeriod.year(2025, zone)
        val points = listOf(
            point(LocalDateTime.of(2025, 1, 15, 10, 0)),
            point(LocalDateTime.of(2025, 3, 15, 10, 0), playedMs = 120_000)
        )

        val trend = ListeningStatsCalculator.trend(points, period, zone)

        assertEquals(12, trend.size)
        assertEquals(60_000L, trend[0].playedMs)
        assertEquals(120_000L, trend[2].playedMs)
        assertEquals("Mar", trend[2].label)
    }

    @Test
    fun `daily trend skips sessions outside the period`() {
        val zone = utc
        val period = ListeningPeriod.week(
            LocalDateTime.of(2025, 6, 5, 12, 0).atZone(zone).toInstant().toEpochMilli(),
            zone
        )
        val points = listOf(
            point(LocalDateTime.of(2025, 6, 2, 10, 0)),  // Monday of the week
            point(LocalDateTime.of(2025, 6, 10, 10, 0))  // next week, excluded
        )

        val trend = ListeningStatsCalculator.trend(points, period, zone)

        assertEquals(7, trend.size)
        assertEquals(60_000L, trend.sumOf { it.playedMs })
    }

    // ------------------------------------------------------------------
    // Yearly trend / All-time period (BUG-001 regression: OutOfMemoryError)
    // ------------------------------------------------------------------

    @Test
    fun `all-time yearly trend with zero sessions is empty instead of unbounded`() {
        // The All-time period spans 1970 .. year ~292 million; the trend must
        // not allocate one point per year of that raw range.
        val trend = ListeningStatsCalculator.trend(emptyList(), ListeningPeriod.allTime(), utc)

        assertTrue(trend.isEmpty())
    }

    @Test
    fun `all-time yearly trend with one session covers only the listening year`() {
        val points = listOf(point(LocalDateTime.of(2024, 3, 10, 12, 0), playedMs = 45_000))

        val trend = ListeningStatsCalculator.trend(points, ListeningPeriod.allTime(), utc)

        assertEquals(1, trend.size)
        assertEquals("2024", trend.single().label)
        assertEquals(45_000L, trend.single().playedMs)
        assertEquals(1L, trend.single().plays)
    }

    @Test
    fun `all-time yearly trend fills missing years between first and last session`() {
        val points = listOf(
            point(LocalDateTime.of(2022, 1, 15, 10, 0), playedMs = 10_000),
            point(LocalDateTime.of(2024, 7, 2, 10, 0), playedMs = 20_000)
        )

        val trend = ListeningStatsCalculator.trend(points, ListeningPeriod.allTime(), utc)

        assertEquals(3, trend.size) // 2022, 2023 (zero-filled), 2024
        assertEquals("2022", trend[0].label)
        assertEquals(10_000L, trend[0].playedMs)
        assertEquals(0L, trend[1].playedMs)
        assertEquals("2024", trend[2].label)
        assertEquals(20_000L, trend[2].playedMs)
    }

    @Test
    fun `all-time yearly trend stays bounded for a large history`() {
        // Thousands of sessions across many years: the trend must remain a
        // small bounded list and must complete quickly (no OOM).
        val points = (0 until 20_000).map { index ->
            point(
                LocalDateTime.of(1970 + index % 150, (index % 12) + 1, (index % 28) + 1, 10, 0),
                playedMs = 1_000L
            )
        }

        val trend = ListeningStatsCalculator.trend(points, ListeningPeriod.allTime(), utc)

        assertEquals(150, trend.size) // 1970 .. 2119, one point per listening year
        assertEquals(1970, trend.first().label.toInt())
        assertEquals(1970 + 149, trend.last().label.toInt())
        // Every played millisecond is preserved: 20_000 sessions x 1s
        assertEquals(20_000 * 1_000L, trend.sumOf { it.playedMs })
    }

    @Test
    fun `yearly trend for a bounded custom period is clamped to the listening data`() {
        val zone = utc
        // 900-year custom range but only one session: the rendered range must
        // follow the data, not the raw period bounds.
        val period = ListeningPeriod.custom(LocalDate.of(1120, 1, 1), LocalDate.of(2025, 12, 31), zone)
        val points = listOf(point(LocalDateTime.of(2025, 5, 20, 10, 0), playedMs = 5_000))

        val trend = ListeningStatsCalculator.trend(points, period, utc)

        assertEquals(1, trend.size)
        assertEquals("2025", trend.single().label)
        assertEquals(5_000L, trend.single().playedMs)
    }

    @Test
    fun `all-time total listening duration is preserved in the yearly trend`() {
        val points = listOf(
            point(LocalDateTime.of(2023, 2, 1, 9, 0), playedMs = 120_000, counted = true),
            point(LocalDateTime.of(2023, 2, 1, 9, 30), playedMs = 30_000, counted = false),
            point(LocalDateTime.of(2025, 6, 5, 18, 0), playedMs = 240_000, counted = true)
        )

        val trend = ListeningStatsCalculator.trend(points, ListeningPeriod.allTime(), utc)

        assertEquals(390_000L, trend.sumOf { it.playedMs })   // all listening time
        assertEquals(2L, trend.sumOf { it.plays })            // only counted plays
    }

    // ------------------------------------------------------------------
    // Most active days
    // ------------------------------------------------------------------

    @Test
    fun `most active days are sorted by listening time`() {
        val points = listOf(
            point(LocalDateTime.of(2025, 6, 1, 10, 0), playedMs = 30_000),
            point(LocalDateTime.of(2025, 6, 2, 10, 0), playedMs = 200_000),
            point(LocalDateTime.of(2025, 6, 3, 10, 0), playedMs = 90_000),
            point(LocalDateTime.of(2025, 6, 4, 10, 0), playedMs = 90_000)
        )

        val mostActive = ListeningStatsCalculator.mostActiveDays(points, utc, count = 3)

        assertEquals(3, mostActive.size)
        assertEquals(LocalDate.of(2025, 6, 2), mostActive[0].date)
        assertEquals(200_000L, mostActive[0].playedMs)
        // 3rd and 4th have equal time; both valid, order stable within input
        assertTrue(mostActive[2].date == LocalDate.of(2025, 6, 3) || mostActive[2].date == LocalDate.of(2025, 6, 4))
    }

    // ------------------------------------------------------------------
    // Periods
    // ------------------------------------------------------------------

    @Test
    fun `month period boundaries are local midnight`() {
        val zone = ZoneId.of("Europe/Rome")
        val period = ListeningPeriod.month(
            LocalDateTime.of(2025, 6, 15, 12, 0).atZone(zone).toInstant().toEpochMilli(),
            zone
        )

        val from = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(period.from), zone)
        val to = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(period.to), zone)

        assertEquals(LocalDateTime.of(2025, 6, 1, 0, 0), from)
        assertEquals(LocalDateTime.of(2025, 7, 1, 0, 0), to)
    }

    @Test
    fun `custom period is normalized when start is after end`() {
        val zone = utc
        val period = ListeningPeriod.custom(
            LocalDate.of(2025, 6, 10),
            LocalDate.of(2025, 6, 1),
            zone
        )

        assertEquals(LocalDate.of(2025, 6, 1), ListeningStatsCalculator.localDate(period.from, zone))
        // exclusive end: the last millisecond inside the range is the final
        // day of the inclusive end (10 June)
        assertEquals(LocalDate.of(2025, 6, 10), ListeningStatsCalculator.localDate(period.to - 1, zone))
    }

    @Test
    fun `repeated songs on the same day aggregate into one day`() {
        val points = listOf(
            point(LocalDateTime.of(2025, 6, 5, 10, 0)),
            point(LocalDateTime.of(2025, 6, 5, 11, 0)),
            point(LocalDateTime.of(2025, 6, 5, 12, 0), counted = false)
        )

        val stats = ListeningStatsCalculator.dailyStats(points, utc)

        assertEquals(1, stats.size)
        val day = stats.getValue(LocalDate.of(2025, 6, 5))
        assertEquals(180_000L, day.playedMs)
        assertEquals(2L, day.plays) // uncounted session is not a play
    }
}
