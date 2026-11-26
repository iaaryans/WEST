@file:OptIn(ExperimentalComposeUiApi::class)

package it.vfsfitvnm.vimusic.ui.screens.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.SeekBar
import it.vfsfitvnm.vimusic.ui.components.themed.IconButton
import it.vfsfitvnm.vimusic.ui.components.themed.LocalReducedMotion
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.formatAsDuration
import it.vfsfitvnm.vimusic.utils.forceSeekToNext
import it.vfsfitvnm.vimusic.utils.forceSeekToPrevious
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.thumbnail

/** An additional presentation of the *existing* Media3 player, not a second player. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ImmersivePlayerDialog(
    player: Player,
    mediaItem: MediaItem,
    playing: Boolean,
    position: Long,
    duration: Long,
    onDismiss: () -> Unit
) {
    val (palette, typography) = LocalAppearance.current
    val reducedMotion = LocalReducedMotion.current
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var scrubbingPosition by remember { mutableStateOf<Long?>(null) }
    // Request one bounded, sharp source for both the backdrop and album cover.
    // Do not upscale the tiny ambient thumbnail or use its cache entry.
    val metrics = context.resources.displayMetrics
    val artworkSize = maxOf(metrics.widthPixels, metrics.heightPixels).coerceIn(800, 1440)
    val ambient = immersiveArtworkRequest(mediaItem, artworkSize)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(palette.background0)) {
            // Reuse the same HD artwork request for the background and cover.
            AsyncImage(
                model = ambient, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (palette.isDark) 0.40f else 0.18f)
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                palette.background0.copy(alpha = 0.48f), palette.background0.copy(alpha = 0.86f)
            ))))
            Column(
                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    SecondaryTextButton("Close", onClick = onDismiss)
                    BasicText("WEST IMMERSIVE", style = typography.s.semiBold)
                    SecondaryTextButton(if (showControls) "Hide" else "Show", onClick = {
                        showControls = !showControls
                    })
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Crossfade(targetState = mediaItem,
                        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 300)) { displayedItem ->
                        val displayedArtwork = immersiveArtworkRequest(displayedItem, artworkSize)
                        Box(
                            Modifier.fillMaxWidth(0.94f).aspectRatio(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { if (showLyrics) showLyrics = false else showControls = !showControls }
                        ) {
                            AsyncImage(
                                model = displayedArtwork, contentDescription = "Current album artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Lyrics(
                                mediaId = displayedItem.mediaId,
                                isDisplayed = showLyrics,
                                onDismiss = { showLyrics = false },
                                size = 300.dp,
                                mediaMetadataProvider = { displayedItem.mediaMetadata },
                                durationProvider = {
                                    if (player.currentMediaItem?.mediaId == displayedItem.mediaId)
                                        player.duration else C.TIME_UNSET
                                },
                                ensureSongInserted = { Database.insert(displayedItem) }
                            )
                        }
                    }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(
                        mediaItem.mediaMetadata.title?.toString().orEmpty(),
                        style = typography.l.semiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    BasicText(
                        mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                        style = typography.s.secondary, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showControls && duration > 0L && duration != C.TIME_UNSET) {
                        SeekBar(
                            value = scrubbingPosition ?: position,
                            minimumValue = 0L,
                            maximumValue = duration,
                            onDragStart = { scrubbingPosition = it.coerceIn(0L, duration) },
                            onDrag = { delta -> scrubbingPosition = ((scrubbingPosition ?: position) + delta).coerceIn(0L, duration) },
                            onDragEnd = {
                                scrubbingPosition?.let(player::seekTo)
                                scrubbingPosition = null
                            },
                            color = palette.accent,
                            backgroundColor = palette.background2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText(formatAsDuration(scrubbingPosition ?: position), style = typography.xs.secondary)
                            BasicText(formatAsDuration(duration), style = typography.xs.secondary)
                        }
                    }
                    if (showControls) {
                        Row(horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = player::forceSeekToPrevious,
                                icon = R.drawable.play_skip_back, color = palette.text,
                                modifier = Modifier.size(30.dp), description = "Previous track")
                            IconButton(onClick = {
                                if (playing) player.pause() else {
                                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                                    player.play()
                                }
                            }, icon = if (playing) R.drawable.pause else R.drawable.play,
                                color = palette.text, modifier = Modifier.size(42.dp),
                                description = if (playing) "Pause" else "Play")
                            IconButton(onClick = { binder?.skipToNextOrDiscover() ?: player.forceSeekToNext() },
                                icon = R.drawable.play_skip_forward, color = palette.text,
                                modifier = Modifier.size(30.dp), description = "Next track")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryTextButton(if (showLyrics) "Hide lyrics" else "Lyrics", onClick = {
                            showLyrics = !showLyrics
                            if (showLyrics) showControls = true
                        })
                        if (!showControls) SecondaryTextButton("Controls", onClick = { showControls = true })
                    }
                }
            }
        }
    }
}

/** The same URL and decode size allow Coil to reuse its artwork cache. */
@Composable
private fun immersiveArtworkRequest(item: MediaItem, targetSize: Int): ImageRequest {
    val context = LocalContext.current
    val source = item.mediaMetadata.artworkUri?.toString()
    return remember<ImageRequest>(context, source, targetSize) {
        ImageRequest.Builder(context)
            .data(source.thumbnail(targetSize))
            .size(targetSize, targetSize)
            .precision(Precision.EXACT)
            .build()
    }
}
