package it.vfsfitvnm.innertube.utils

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.MusicShelfRenderer

fun Innertube.SongItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.SongItem? {
    // Delegate to the structural MusicResponsiveListItemRenderer mapping:
    // splitting the last flex column by separator (the previous approach)
    // misreads the current search layout, where that column holds the play
    // count, producing empty artists and "7B plays" as the duration.
    val renderer = content.musicResponsiveListItemRenderer ?: return null

    val song = Innertube.SongItem.from(renderer) ?: return null

    return song.copy(thumbnail = content.thumbnail ?: song.thumbnail)
}

fun Innertube.VideoItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.VideoItem? {
    val (mainRuns, otherRuns) = content.runs

    return Innertube.VideoItem(
        info = mainRuns
            .firstOrNull()
            ?.let(Innertube::Info),
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - 2)
            ?.map(Innertube::Info),
        viewsText = otherRuns
            .getOrNull(otherRuns.lastIndex - 1)
            ?.firstOrNull()
            ?.text,
        durationText = otherRuns
            .getOrNull(otherRuns.lastIndex)
            ?.firstOrNull()
            ?.text,
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.videoId != null }
}

fun Innertube.AlbumItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.AlbumItem? {
    val (mainRuns, otherRuns) = content.runs

    return Innertube.AlbumItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - 1)
            ?.map(Innertube::Info),
        year = otherRuns
            .getOrNull(otherRuns.lastIndex)
            ?.firstOrNull()
            ?.text,
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.ArtistItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.ArtistItem? {
    val (mainRuns, otherRuns) = content.runs

    return Innertube.ArtistItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        subscribersCountText = otherRuns
            .lastOrNull()
            ?.last()
            ?.text,
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.PlaylistItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.PlaylistItem? {
    val (mainRuns, otherRuns) = content.runs

    return Innertube.PlaylistItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        channel = otherRuns
            .firstOrNull()
            ?.firstOrNull()
            ?.let(Innertube::Info),
        songCount = otherRuns
            .lastOrNull()
            ?.firstOrNull()
            ?.text
            ?.split(' ')
            ?.firstOrNull()
            ?.toIntOrNull(),
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}
