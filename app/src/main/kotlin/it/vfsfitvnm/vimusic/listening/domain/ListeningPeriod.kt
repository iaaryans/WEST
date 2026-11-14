package it.vfsfitvnm.vimusic.listening.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Time ranges the listening analytics can be computed over.
 * [from] is inclusive and [to] exclusive, both in epoch milliseconds.
 */
enum class ListeningPeriodType(val label: String) {
    Day("Daily"),
    Week("Weekly"),
    Month("Monthly"),
    Year("Yearly"),
    AllTime("All-time"),
    Custom("Custom")
}

/** Granularity of the listening-time chart, derived from the period. */
enum class ChartGranularity { Hour, Day, Month, Year }

data class ListeningPeriod(
    val type: ListeningPeriodType,
    val from: Long,
    val to: Long
) {
    companion object {
        private const val UNBOUNDED = Long.MAX_VALUE

        fun day(reference: Long, zone: ZoneId): ListeningPeriod {
            val date = Instant.ofEpochMilli(reference).atZone(zone).toLocalDate()
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            return ListeningPeriod(ListeningPeriodType.Day, start, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        }

        /** ISO week, starting on Monday. */
        fun week(reference: Long, zone: ZoneId): ListeningPeriod {
            val date = Instant.ofEpochMilli(reference).atZone(zone).toLocalDate()
            val monday = date.with(DayOfWeek.MONDAY)
            return ListeningPeriod(
                ListeningPeriodType.Week,
                monday.atStartOfDay(zone).toInstant().toEpochMilli(),
                monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            )
        }

        fun month(reference: Long, zone: ZoneId): ListeningPeriod {
            val yearMonth = YearMonth.from(Instant.ofEpochMilli(reference).atZone(zone))
            return ListeningPeriod(
                ListeningPeriodType.Month,
                yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            )
        }

        fun year(reference: Long, zone: ZoneId): ListeningPeriod {
            val year = Instant.ofEpochMilli(reference).atZone(zone).year
            return ListeningPeriod(
                ListeningPeriodType.Year,
                LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
                LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            )
        }

        fun year(year: Int, zone: ZoneId): ListeningPeriod = year(
            LocalDate.of(year, 6, 15).atStartOfDay(zone).toInstant().toEpochMilli(),
            zone
        )

        fun allTime(): ListeningPeriod = ListeningPeriod(ListeningPeriodType.AllTime, 0L, UNBOUNDED)

        fun custom(startInclusive: LocalDate, endInclusive: LocalDate, zone: ZoneId): ListeningPeriod {
            val normalizedStart = if (startInclusive <= endInclusive) startInclusive else endInclusive
            val normalizedEnd = if (startInclusive <= endInclusive) endInclusive else startInclusive
            return ListeningPeriod(
                ListeningPeriodType.Custom,
                normalizedStart.atStartOfDay(zone).toInstant().toEpochMilli(),
                normalizedEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            )
        }

        /** Whole calendar month of [yearMonth]. */
        fun of(yearMonth: YearMonth, zone: ZoneId): ListeningPeriod = ListeningPeriod(
            ListeningPeriodType.Custom,
            yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }
}

/**
 * Picks a sensible chart granularity: hourly for a single day, daily up to
 * two months, monthly up to two years, yearly beyond.
 */
fun ListeningPeriod.chartGranularity(): ChartGranularity = when (type) {
    ListeningPeriodType.Day -> ChartGranularity.Hour
    ListeningPeriodType.Week -> ChartGranularity.Day
    ListeningPeriodType.Month -> ChartGranularity.Day
    ListeningPeriodType.Year -> ChartGranularity.Month
    ListeningPeriodType.AllTime -> ChartGranularity.Year
    ListeningPeriodType.Custom -> {
        val days = ((to - from) / 86_400_000L).coerceAtLeast(1)
        when {
            days <= 1 -> ChartGranularity.Hour
            days <= 62 -> ChartGranularity.Day
            days <= 730 -> ChartGranularity.Month
            else -> ChartGranularity.Year
        }
    }
}
