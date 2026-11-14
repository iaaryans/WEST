package it.vfsfitvnm.vimusic.listening.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.listening.domain.DailyListening
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.sqrt

/**
 * Month grid heatmap of listening activity. Colour intensity follows the
 * square root of the day's listening time relative to the month peak, so
 * quiet days stay readable while busy days stand out.
 */
@Composable
fun CalendarHeatmap(
    yearMonth: YearMonth,
    dayStats: Map<LocalDate, DailyListening>,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current

    val weeks: List<List<LocalDate?>> = buildWeekGrid(yearMonth)

    val peak = dayStats.values.maxOfOrNull { it.playedMs } ?: 0L

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // Weekday header (Monday first)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.values().forEach { day ->
                BasicText(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = typography.xxs.secondary.semiBold.center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cellColor(date?.let { dayStats[it]?.playedMs } ?: 0L, peak, colorPalette.background1, colorPalette.accent))
                    ) {
                        if (date != null) {
                            val active = (dayStats[date]?.playedMs ?: 0L) > 0
                            BasicText(
                                text = date.dayOfMonth.toString(),
                                style = typography.xxs.semiBold.let {
                                    if (active) it.copy(color = colorPalette.onAccent) else it.secondary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 0..1 intensity of one day relative to the month peak (sqrt scale). */
fun heatIntensity(playedMs: Long, peak: Long): Float =
    if (peak <= 0 || playedMs <= 0) 0f
    else sqrt(playedMs.toFloat() / peak).coerceIn(0.25f, 1f)

@Composable
private fun cellColor(playedMs: Long, peak: Long, background: Color, accent: Color): Color {
    if (playedMs <= 0) return background
    return lerpColor(background, accent, heatIntensity(playedMs, peak))
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color = Color(
    red = start.red + (end.red - start.red) * fraction,
    green = start.green + (end.green - start.green) * fraction,
    blue = start.blue + (end.blue - start.blue) * fraction,
    alpha = start.alpha + (end.alpha - start.alpha) * fraction
)

/** Legend strip: less ▪▪▪▪ more */
@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    val (colorPalette, typography) = LocalAppearance.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        BasicText("Less", style = typography.xxs.secondary)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(lerpColor(colorPalette.background1, colorPalette.accent, fraction))
            )
        }
        BasicText("More", style = typography.xxs.secondary)
    }
}

/**
 * Builds the calendar grid for [yearMonth]: a list of weeks, each a list of
 * seven cells (null for padding before the first / after the last day).
 * Weeks start on Monday.
 */
fun buildWeekGrid(yearMonth: YearMonth): List<List<LocalDate?>> {
    val firstDay = yearMonth.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1 // Monday = 0 blanks
    val daysInMonth = yearMonth.lengthOfMonth()

    val cells = buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..daysInMonth) add(yearMonth.atDay(day))
        while (size % 7 != 0) add(null)
    }

    return cells.chunked(7)
}
