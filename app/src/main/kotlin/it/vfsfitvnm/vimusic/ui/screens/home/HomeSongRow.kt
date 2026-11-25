package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.medium
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.media3.common.MediaItem
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.performance.SmoothArtwork

/**
 * Home-only direct row: no extra thumbnail Box or generic item indirection.
 * Preserves thumbnail size, typography, click/long-click and feedback content.
 */
@Composable
private fun HomeSongRowContent(
    artworkUrl: String?, title: String?, authors: String?, duration: String?,
    thumbnailSize: Dp, modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val density = LocalDensity.current
    val pixels = with(density) { thumbnailSize.roundToPx() }.coerceAtLeast(96)
    val (_, typography, shape) = LocalAppearance.current
    val titleStyle = typography.xs.semiBold
    val authorStyle = typography.xs.semiBold.secondary
    val durationStyle = typography.xxs.secondary.medium
    Row(modifier.padding(vertical = Dimensions.itemsVerticalPadding, horizontal = 16.dp)
        .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SmoothArtwork(url = artworkUrl, targetPixels = pixels,
            modifier = Modifier.size(thumbnailSize).clip(shape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (trailingContent == null) {
                BasicText(title.orEmpty(), style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(title.orEmpty(), style = titleStyle, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    trailingContent()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(authors.orEmpty(), style = authorStyle, maxLines = 1,
                    overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
                if (duration != null) BasicText(duration, style = durationStyle, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
internal fun HomeSongRow(
    song: Song, thumbnailSize: Dp, modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) = HomeSongRowContent(song.thumbnailUrl, song.title, song.artistsText, song.durationText,
    thumbnailSize, modifier, trailingContent)

@Composable
internal fun HomeSongRow(
    song: Innertube.SongItem, thumbnailSize: Dp, modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) = HomeSongRowContent(song.thumbnail?.url, song.info?.name,
    remember<String?>(song.authors) { song.authors?.joinToString(", ") { it.name.orEmpty() } }, song.durationText,
    thumbnailSize, modifier, trailingContent)

@Composable
internal fun HomeSongRow(
    song: MediaItem, thumbnailSize: Dp, modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) = HomeSongRowContent(song.mediaMetadata.artworkUri?.toString(),
    song.mediaMetadata.title?.toString(), song.mediaMetadata.artist?.toString(),
    song.mediaMetadata.extras?.getString("durationText"), thumbnailSize, modifier, trailingContent)
