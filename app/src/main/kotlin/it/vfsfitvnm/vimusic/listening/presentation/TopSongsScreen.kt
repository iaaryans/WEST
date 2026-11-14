package it.vfsfitvnm.vimusic.listening.presentation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.persist.persistList
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.listening.components.TopTrackItem
import it.vfsfitvnm.vimusic.listening.data.ListeningStatsRepository
import it.vfsfitvnm.vimusic.listening.data.TopSongEntry
import it.vfsfitvnm.vimusic.listening.data.asMediaItem
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriod
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriodType
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.ValueSelectorDialog
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.forcePlayAtIndex
import java.time.ZoneId

/**
 * Most played songs: top 10 / 20 / 50 over the selected period, with
 * artwork, play counts and accumulated listening time. Tapping a row plays
 * the whole ranking from that song.
 */
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun TopSongsScreen() {
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current

    var limit by rememberSaveable { mutableStateOf(10) }
    var periodType by rememberSaveable { mutableStateOf(ListeningPeriodType.AllTime) }
    var showLimitDialog by rememberSaveable { mutableStateOf(false) }
    var showPeriodDialog by rememberSaveable { mutableStateOf(false) }

    var entries by persistList<TopSongEntry>("listening/topSongs")

    val zone = ZoneId.systemDefault()

    LaunchedEffect(limit, periodType) {
        val period = periodFor(periodType, zone)
        ListeningStatsRepository.topSongs(period, limit).collect { entries = it }
    }

    if (showLimitDialog) {
        ValueSelectorDialog(
            onDismiss = { showLimitDialog = false },
            title = "Show top",
            selectedValue = limit.toLong(),
            values = listOf(10L, 20L, 50L),
            onValueSelected = { limit = it.toInt() },
            valueText = { "Top $it songs" }
        )
    }

    if (showPeriodDialog) {
        ValueSelectorDialog(
            onDismiss = { showPeriodDialog = false },
            title = "Period",
            selectedValue = periodType,
            values = listOf(
                ListeningPeriodType.Month,
                ListeningPeriodType.Year,
                ListeningPeriodType.AllTime
            ),
            onValueSelected = { periodType = it },
            valueText = { it.label }
        )
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            .asPaddingValues(),
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        item(key = "header", contentType = 0) {
            Header(
                title = "Top Songs",
                modifier = Modifier.padding(end = 16.dp)
            ) {
                HeaderIconButton(
                    icon = R.drawable.time,
                    color = colorPalette.text,
                    onClick = { showPeriodDialog = true }
                )

                HeaderIconButton(
                    icon = R.drawable.sort,
                    color = colorPalette.text,
                    onClick = { showLimitDialog = true }
                )
            }
        }

        if (entries.isEmpty()) {
            item(key = "empty", contentType = 1) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    EmptyState(
                        title = "No plays yet",
                        text = "Songs you listen to for at least 30 seconds appear here."
                    )
                }
            }
        } else {
            itemsIndexed(
                items = entries,
                key = { _, entry -> entry.songId }
            ) { index, entry ->
                TopTrackItem(
                    rank = index + 1,
                    entry = entry,
                    thumbnailSizePx = Dimensions.thumbnails.song.px,
                    thumbnailSizeDp = Dimensions.thumbnails.song,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    onClick = {
                        binder?.stopRadio()
                        binder?.player?.forcePlayAtIndex(
                            entries.map(TopSongEntry::asMediaItem),
                            index
                        )
                    }
                )
            }
        }
    }
}

/** Resolves a persisted period type into a concrete period. */
internal fun periodFor(type: ListeningPeriodType, zone: ZoneId): ListeningPeriod {
    val now = System.currentTimeMillis()
    return when (type) {
        ListeningPeriodType.Day -> ListeningPeriod.day(now, zone)
        ListeningPeriodType.Week -> ListeningPeriod.week(now, zone)
        ListeningPeriodType.Month -> ListeningPeriod.month(now, zone)
        ListeningPeriodType.Year -> ListeningPeriod.year(now, zone)
        ListeningPeriodType.AllTime -> ListeningPeriod.allTime()
        ListeningPeriodType.Custom -> ListeningPeriod.month(now, zone)
    }
}
