package it.vfsfitvnm.vimusic.listening.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import it.vfsfitvnm.vimusic.listening.data.ListeningStatsRepository
import it.vfsfitvnm.vimusic.listening.data.TopArtistEntry
import it.vfsfitvnm.vimusic.listening.data.TopSongEntry
import it.vfsfitvnm.vimusic.listening.domain.ListeningOverview
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriod
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriodType
import it.vfsfitvnm.vimusic.listening.domain.ListeningTrendPoint
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * State holder for the listening insights dashboard.
 *
 * WEST has no androidx ViewModel infrastructure by design (screens hold
 * state through Compose persistence), so this class follows the project's
 * architecture instead of forcing a new dependency: it is a plain state
 * holder created with [remember], it observes the database through the same
 * Flow pattern every other screen uses, and it is cancelled when the
 * screen leaves composition.
 *
 * It centralizes what would otherwise be a dozen LaunchedEffects: period
 * selection (including the custom range) and the collection of overview,
 * chart and ranking flows, with automatic switching when the period changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListeningStatsViewModel(
    private val scope: CoroutineScope,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    var periodType by mutableStateOf(ListeningPeriodType.Month)
        private set

    var customStart by mutableStateOf<LocalDate?>(null)
        private set

    var customEnd by mutableStateOf<LocalDate?>(null)
        private set

    var overview by mutableStateOf<ListeningOverview?>(null)
        private set

    var trend by mutableStateOf<List<ListeningTrendPoint>>(emptyList())
        private set

    var topSongs by mutableStateOf<List<TopSongEntry>>(emptyList())
        private set

    var topArtists by mutableStateOf<List<TopArtistEntry>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    private var jobs: List<Job> = emptyList()

    /** Currently selected period derived from the picker state. */
    val period: ListeningPeriod
        get() {
            val now = System.currentTimeMillis()
            return when (periodType) {
                ListeningPeriodType.Day -> ListeningPeriod.day(now, zone)
                ListeningPeriodType.Week -> ListeningPeriod.week(now, zone)
                ListeningPeriodType.Month -> ListeningPeriod.month(now, zone)
                ListeningPeriodType.Year -> ListeningPeriod.year(now, zone)
                ListeningPeriodType.AllTime -> ListeningPeriod.allTime()
                ListeningPeriodType.Custom -> {
                    val start = customStart
                    val end = customEnd
                    if (start != null && end != null) {
                        ListeningPeriod.custom(start, end, zone)
                    } else {
                        ListeningPeriod.month(now, zone)
                    }
                }
            }
        }

    fun selectPeriod(type: ListeningPeriodType) {
        if (type == ListeningPeriodType.Custom) {
            // Seed a sensible default range: last 30 days
            val today = LocalDate.now(zone)
            if (customStart == null || customEnd == null) {
                customStart = today.minusDays(29)
                customEnd = today
            }
        }
        periodType = type
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        customStart = if (start <= end) start else end
        customEnd = if (start <= end) end else start
        periodType = ListeningPeriodType.Custom
    }

    fun start() {
        val periodFlow = snapshotFlow { period }
        jobs = listOf(
            scope.launch {
                periodFlow
                    .flatMapLatest { ListeningStatsRepository.overview(it, zone) }
                    .collect {
                        overview = it
                        isLoading = false
                    }
            },
            scope.launch {
                periodFlow
                    .flatMapLatest { ListeningStatsRepository.trend(it, zone) }
                    .collect { trend = it }
            },
            scope.launch {
                periodFlow
                    .flatMapLatest { ListeningStatsRepository.topSongs(it, limit = 5) }
                    .collect { topSongs = it }
            },
            scope.launch {
                periodFlow
                    .flatMapLatest { ListeningStatsRepository.topArtists(it, limit = 5) }
                    .collect { topArtists = it }
            }
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }
}

/** Creates (and starts) the dashboard state holder, tied to composition. */
@Composable
fun rememberListeningStatsViewModel(): ListeningStatsViewModel {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ListeningStatsViewModel(scope) }

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    return viewModel
}
