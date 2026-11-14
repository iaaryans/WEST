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
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.listening.data.ListeningStatsRepository
import it.vfsfitvnm.vimusic.listening.data.TopArtistEntry
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriodType
import it.vfsfitvnm.vimusic.listening.domain.formatListeningDuration
import it.vfsfitvnm.vimusic.listening.domain.formatPlural
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.ValueSelectorDialog
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import java.time.ZoneId

/**
 * Most played artists with ranking, total listening time, play count and
 * distinct track count; tapping an artist opens their page.
 */
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun TopArtistsScreen(
    onArtistClick: (String) -> Unit
) {
    val (colorPalette) = LocalAppearance.current

    var periodType by rememberSaveable { mutableStateOf(ListeningPeriodType.AllTime) }
    var showPeriodDialog by rememberSaveable { mutableStateOf(false) }

    var entries by persistList<TopArtistEntry>("listening/topArtists")

    val zone = ZoneId.systemDefault()

    LaunchedEffect(periodType) {
        ListeningStatsRepository.topArtists(periodFor(periodType, zone), limit = 50).collect { entries = it }
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
                title = "Top Artists",
                modifier = Modifier.padding(end = 16.dp)
            ) {
                HeaderIconButton(
                    icon = R.drawable.time,
                    color = colorPalette.text,
                    onClick = { showPeriodDialog = true }
                )
            }
        }

        if (entries.isEmpty()) {
            item(key = "empty", contentType = 1) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    EmptyState(
                        title = "No artists yet",
                        text = "Artists appear here once their songs are counted as played."
                    )
                }
            }
        } else {
            itemsIndexed(
                items = entries,
                key = { _, entry -> entry.artistId }
            ) { index, entry ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TopArtistRow(
                        rank = index + 1,
                        name = entry.name ?: entry.artistId,
                        detail = formatListeningDuration(entry.totalPlayedMs) +
                            " · " + formatPlural(entry.playCount, "play") +
                            " · " + formatPlural(entry.trackCount, "track"),
                        thumbnailUrl = entry.thumbnailUrl,
                        onClick = { onArtistClick(entry.artistId) }
                    )
                }
            }
        }
    }
}
