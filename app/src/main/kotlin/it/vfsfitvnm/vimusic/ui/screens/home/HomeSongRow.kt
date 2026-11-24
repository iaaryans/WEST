package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.media3.common.MediaItem
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.performance.SmoothArtwork

/**
 * Identical SongItem presentation, but with a request sized to the Home thumbnail
 * instead of the legacy 800 px minimum. Shared SongItem and Player remain intact.
 */
@Composable
private fun HomeSongRowContent(
    artworkUrl: String?, title: String?, authors: String?, duration: String?,
    thumbnailSize: Dp, modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val density = LocalDensity.current
    val pixels = with(density) { thumbnailSize.roundToPx() }.coerceAtLeast(96)
    SongItem(
        title = title,
        authors = authors,
        duration = duration,
        thumbnailSizeDp = thumbnailSize,
        thumbnailContent = {
            SmoothArtwork(url = artworkUrl, targetPixels = pixels,
                modifier = Modifier.matchParentSize()
                    .clip(LocalAppearance.current.thumbnailShape))
        },
        modifier = modifier,
        trailingContent = trailingContent
    )
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
    song.authors?.joinToString(", ") { it.name.orEmpty() }, song.durationText,
    thumbnailSize, modifier, trailingContent)

@Composable
internal fun HomeSongRow(
    song: MediaItem, thumbnailSize: Dp, modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) = HomeSongRowContent(song.mediaMetadata.artworkUri?.toString(),
    song.mediaMetadata.title?.toString(), song.mediaMetadata.artist?.toString(),
    song.mediaMetadata.extras?.getString("durationText"), thumbnailSize, modifier, trailingContent)
