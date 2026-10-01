package it.vfsfitvnm.innertube.requests

import io.ktor.http.Url
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.bodies.BrowseBody

suspend fun Innertube.albumPage(body: BrowseBody): Result<Innertube.PlaylistOrAlbumPage>? {
    val initial = playlistPage(body) ?: return null
    return initial.map { album ->
        if (album.songsPage?.items?.isNotEmpty() == true) {
            album
        } else {
            album.url?.let { Url(it).parameters["list"] }?.let { playlistId ->
                playlistPage(BrowseBody(browseId = "VL$playlistId"))?.getOrNull()?.let { playlist ->
                    album.copy(songsPage = playlist.songsPage)
                }
            } ?: album
        }
    }.map { album ->
        val albumInfo = Innertube.Info(
            name = album.title,
            endpoint = NavigationEndpoint.Endpoint.Browse(
                browseId = body.browseId,
                params = body.params
            )
        )

        album.copy(
            songsPage = album.songsPage?.copy(
                items = album.songsPage.items?.map { song ->
                    song.copy(
                        authors = song.authors ?: album.authors,
                        album = albumInfo,
                        thumbnail = album.thumbnail ?: song.thumbnail
                    )
                }
            )
        )
    }
}
