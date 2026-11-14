package it.vfsfitvnm.vimusic.listening.presentation

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.vfsfitvnm.compose.persist.persistList
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.listening.data.ListeningStatsRepository
import it.vfsfitvnm.vimusic.listening.data.TopAlbumEntry
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriodType
import it.vfsfitvnm.vimusic.listening.domain.formatListeningDuration
import it.vfsfitvnm.vimusic.listening.domain.formatPlural
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.ValueSelectorDialog
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.medium
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.thumbnail
import java.time.ZoneId

/**
 * Most played albums: artwork, artist, play count and listening time;
 * tapping an album opens its page.
 */
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun TopAlbumsScreen(
    onAlbumClick: (String) -> Unit
) {
    val (colorPalette, typography, thumbnailShape) = LocalAppearance.current

    var periodType by rememberSaveable { mutableStateOf(ListeningPeriodType.AllTime) }
    var showPeriodDialog by rememberSaveable { mutableStateOf(false) }

    var entries by persistList<TopAlbumEntry>("listening/topAlbums")

    val zone = ZoneId.systemDefault()

    LaunchedEffect(periodType) {
        ListeningStatsRepository.topAlbums(periodFor(periodType, zone), limit = 50).collect { entries = it }
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
                title = "Top Albums",
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
                        title = "No albums yet",
                        text = "Albums appear here once their songs are counted as played."
                    )
                }
            }
        } else {
            itemsIndexed(
                items = entries,
                key = { _, entry -> entry.albumId }
            ) { index, entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxSize()
                        .clickable { onAlbumClick(entry.albumId) }
                ) {
                    BasicText(
                        text = (index + 1).toString(),
                        style = typography.m.semiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )

                    Box(
                        modifier = Modifier
                            .size(Dimensions.thumbnails.song)
                            .clip(thumbnailShape)
                            .background(colorPalette.background1)
                    ) {
                        AsyncImage(
                            model = entry.thumbnailUrl?.thumbnail(Dimensions.thumbnails.song.px),
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        BasicText(
                            text = entry.title ?: entry.albumId,
                            style = typography.xs.semiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        BasicText(
                            text = entry.artistsText.orEmpty(),
                            style = typography.xxs.semiBold.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BasicText(
                            text = formatPlural(entry.playCount, "play"),
                            style = typography.xxs.semiBold,
                            maxLines = 1
                        )
                        BasicText(
                            text = formatListeningDuration(entry.totalPlayedMs),
                            style = typography.xxs.secondary.medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
