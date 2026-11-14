package it.vfsfitvnm.vimusic.listening.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.vfsfitvnm.vimusic.listening.data.TopSongEntry
import it.vfsfitvnm.vimusic.listening.domain.formatListeningDuration
import it.vfsfitvnm.vimusic.listening.domain.formatPlural
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.shimmer
import it.vfsfitvnm.vimusic.utils.medium
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.thumbnail

/**
 * Ranked row for top songs: position, artwork, title/artist and, on the
 * trailing edge, play count and accumulated listening time.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopTrackItem(
    rank: Int,
    entry: TopSongEntry,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val (colorPalette, typography, thumbnailShape) = LocalAppearance.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = { onLongClick?.invoke() }
                    )
                } else {
                    Modifier
                }
            )
    ) {
        BasicText(
            text = rank.toString(),
            style = typography.m.semiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(28.dp)
        )

        Box(
            modifier = Modifier
                .size(thumbnailSizeDp)
                .clip(thumbnailShape)
                .background(colorPalette.background1)
        ) {
            AsyncImage(
                model = entry.thumbnailUrl?.thumbnail(thumbnailSizePx),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            BasicText(
                text = entry.title ?: entry.songId,
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

/** Placeholder used while rankings load. */
@Composable
fun TopTrackItemPlaceholder(
    thumbnailSizeDp: Dp = Dimensions.thumbnails.song
) {
    val (colorPalette, typography) = LocalAppearance.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(
            modifier = Modifier
                .size(28.dp)
                .background(colorPalette.shimmer, RoundedCornerShape(4.dp))
        )

        Spacer(
            modifier = Modifier
                .size(thumbnailSizeDp)
                .background(colorPalette.shimmer, RoundedCornerShape(4.dp))
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            BasicText(
                text = "Loading…",
                style = typography.xs.semiBold
            )
        }
    }
}
