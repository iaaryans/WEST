package it.vfsfitvnm.vimusic.listening.data

import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Rebuilds playable MediaItems from statistics rows, following the exact
 * construction pattern of `Song.asMediaItem` (mediaId = uri = custom cache
 * key, so the player resolves them like every other song in the app).
 */
private fun songMediaItem(
    songId: String,
    title: String?,
    artistsText: String?,
    thumbnailUrl: String?
): MediaItem =
    MediaItem.Builder()
        .setMediaId(songId)
        .setUri(songId)
        .setCustomCacheKey(songId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title ?: songId)
                .setArtist(artistsText)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setExtras(bundleOf())
                .build()
        )
        .build()

val TopSongEntry.asMediaItem: MediaItem
    get() = songMediaItem(songId, title, artistsText, thumbnailUrl)
