package it.vfsfitvnm.vimusic.listening.presentation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.persist.persistList
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.listening.components.CalendarHeatmap
import it.vfsfitvnm.vimusic.listening.components.HeatmapLegend
import it.vfsfitvnm.vimusic.listening.components.heatIntensity
import it.vfsfitvnm.vimusic.listening.data.ListeningPointRow
import it.vfsfitvnm.vimusic.listening.data.ListeningStatsCalculator
import it.vfsfitvnm.vimusic.listening.data.ListeningStatsRepository
import it.vfsfitvnm.vimusic.listening.domain.DailyListening
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriod
import it.vfsfitvnm.vimusic.listening.domain.MostActiveDay
import it.vfsfitvnm.vimusic.listening.domain.WeekdayListening
import it.vfsfitvnm.vimusic.listening.domain.formatCount
import it.vfsfitvnm.vimusic.listening.domain.formatListeningDuration
import it.vfsfitvnm.vimusic.listening.domain.formatPlural
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val monthTitleFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val activeDayFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

/**
 * Calendar-based listening activity: month heatmap of daily minutes,
 * weekday distribution and the most active listening days (month + all-time).
 */
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun ListeningCalendarScreen() {
    val (colorPalette, typography) = LocalAppearance.current

    val zone = ZoneId.systemDefault()

    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now(zone).toString()) }
    val yearMonth = remember(monthKey) { YearMonth.parse(monthKey) }

    var points by persistList<ListeningPointRow>("listening/calendarPoints")
    var allTimePoints by persistList<ListeningPointRow>("listening/calendarAllTime")

    LaunchedEffect(monthKey) {
        ListeningStatsRepository.points(ListeningPeriod.of(yearMonth, zone)).collect { points = it }
    }

    LaunchedEffect(Unit) {
        ListeningStatsRepository.points(ListeningPeriod.allTime()).collect { allTimePoints = it }
    }

    val dayStats = remember(points, zone) {
        ListeningStatsCalculator.dailyStats(points, zone)
    }
    val mostActive = remember(points, zone) {
        ListeningStatsCalculator.mostActiveDays(points, zone, count = 5)
    }
    val allTimeMostActive = remember(allTimePoints, zone) {
        ListeningStatsCalculator.mostActiveDays(allTimePoints, zone, count = 5)
    }
    val weekdays = remember(points, zone) {
        ListeningStatsCalculator.weekdayDistribution(points, zone)
    }

    val monthTotal = dayStats.values.sumOf { it.playedMs }
    val monthActiveDays = dayStats.values.count { it.playedMs > 0 }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            .asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        item(key = "header", contentType = 0) {
            Header(
                title = "Listening Calendar",
                modifier = Modifier.padding(end = 16.dp)
            ) {
                HeaderIconButton(
                    icon = R.drawable.chevron_back,
                    color = colorPalette.text,
                    onClick = { monthKey = yearMonth.minusMonths(1).toString() }
                )

                HeaderIconButton(
                    icon = R.drawable.chevron_forward,
                    color = colorPalette.text,
                    onClick = { monthKey = yearMonth.plusMonths(1).toString() }
                )
            }
        }

        item(key = "month-title", contentType = 1) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                BasicText(
                    text = yearMonth.format(monthTitleFormat),
                    style = typography.s.semiBold
                )
                BasicText(
                    text = formatListeningDuration(monthTotal) + " listened · " +
                        formatPlural(monthActiveDays.toLong(), "active day"),
                    style = typography.xxs.secondary
                )
            }
        }

        item(key = "heatmap", contentType = 2) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                CalendarHeatmap(
                    yearMonth = yearMonth,
                    dayStats = dayStats
                )
                HeatmapLegend()
            }
        }

        item(key = "weekdays", contentType = 3) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                SectionTitle("By weekday")
                WeekdayBars(weekdays = weekdays)
            }
        }

        item(key = "most-active", contentType = 4) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                SectionTitle("Most active days")
                if (mostActive.isEmpty() || mostActive.first().playedMs == 0L) {
                    BasicText(
                        text = "Nothing recorded this month yet",
                        style = typography.xxs.secondary
                    )
                } else {
                    mostActive.filter { it.playedMs > 0 }.forEach { day ->
                        MostActiveDayRow(day)
                    }
                }
            }
        }

        item(key = "most-active-all-time", contentType = 5) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                SectionTitle("All-time most active days")
                if (allTimeMostActive.isEmpty() || allTimeMostActive.first().playedMs == 0L) {
                    BasicText(
                        text = "Nothing recorded yet",
                        style = typography.xxs.secondary
                    )
                } else {
                    allTimeMostActive.filter { it.playedMs > 0 }.forEach { day ->
                        MostActiveDayRow(day)
                    }
                }
            }
        }
    }
}

@Composable
private fun MostActiveDayRow(day: MostActiveDay) {
    val (colorPalette, typography) = LocalAppearance.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorPalette.background1)
                .padding(vertical = 6.dp)
        ) {
            BasicText(
                text = day.date.dayOfMonth.toString(),
                style = typography.s.semiBold.let { it.copy(color = colorPalette.accent) }
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            BasicText(
                text = day.date.format(activeDayFormat),
                style = typography.xs.semiBold
            )
            BasicText(
                text = formatListeningDuration(day.playedMs) + " · " + formatPlural(day.plays, "play"),
                style = typography.xxs.secondary
            )
        }
    }
}

@Composable
private fun WeekdayBars(weekdays: List<WeekdayListening>) {
    val (colorPalette, typography) = LocalAppearance.current
    val peak = weekdays.maxOfOrNull { it.playedMs } ?: 0L

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorPalette.background1)
            .padding(all = 14.dp)
    ) {
        weekdays.forEach { weekday ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    text = java.time.DayOfWeek.of(weekday.weekday + 1)
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    style = typography.xxs.secondary,
                    modifier = Modifier.width(34.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colorPalette.background2)
                ) {
                    val fraction = heatIntensity(weekday.playedMs, peak)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(colorPalette.accent)
                    )
                }

                BasicText(
                    text = formatListeningDuration(weekday.playedMs),
                    style = typography.xxs.secondary,
                    modifier = Modifier.width(56.dp)
                )
            }
        }
    }
}
