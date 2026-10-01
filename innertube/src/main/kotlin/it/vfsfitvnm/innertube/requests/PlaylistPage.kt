package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.ContinuationResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.Runs
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.playlistPage(body: BrowseBody) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        mask("contents,header,microformat")
    }.body<BrowseResponse>()

    // Case 1: singleColumnBrowseResultsRenderer
    val singleColumnSectionList = response
        .contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer

    // Case 2: twoColumnBrowseResultsRenderer
    val twoColumnRenderer = response.contents?.twoColumnBrowseResultsRenderer

    val twoColumnHeaderSectionList = twoColumnRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer

    val twoColumnSecondarySectionList = twoColumnRenderer
        ?.secondaryContents
        ?.sectionListRenderer

    val responsiveHeaderRenderer = twoColumnHeaderSectionList
        ?.contents
        ?.firstOrNull()
        ?.musicResponsiveHeaderRenderer

    val musicDetailHeaderRenderer = response
        .header
        ?.musicDetailHeaderRenderer

    val musicShelfRenderer = singleColumnSectionList
        ?.contents
        ?.firstOrNull()
        ?.musicShelfRenderer
        ?: twoColumnSecondarySectionList
            ?.contents
            ?.firstOrNull()
            ?.musicShelfRenderer
        ?: response.contents?.sectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.musicShelfRenderer

    val musicCarouselShelfRenderer = singleColumnSectionList
        ?.contents
        ?.getOrNull(1)
        ?.musicCarouselShelfRenderer
        ?: twoColumnSecondarySectionList
            ?.contents
            ?.getOrNull(1)
            ?.musicCarouselShelfRenderer

    val title = musicDetailHeaderRenderer?.title?.text
        ?: responsiveHeaderRenderer?.title?.text

    val thumbnail = (musicDetailHeaderRenderer?.thumbnail
        ?: responsiveHeaderRenderer?.thumbnail)
        ?.musicThumbnailRenderer
        ?.thumbnail
        ?.thumbnails
        ?.lastOrNull()

    val authors = (musicDetailHeaderRenderer?.subtitle
        ?.splitBySeparator()
        ?.getOrNull(1)
        ?.map(Innertube::Info))
        ?: responsiveHeaderRenderer?.straplineTextOne
            ?.runs
            ?.map<Runs.Run, Innertube.Info<NavigationEndpoint.Endpoint.Browse>>(Innertube::Info)
            ?.filter { it.endpoint != null }

    val year = musicDetailHeaderRenderer
        ?.subtitle
        ?.splitBySeparator()
        ?.getOrNull(2)
        ?.firstOrNull()
        ?.text
        ?: responsiveHeaderRenderer
            ?.subtitle
            ?.runs
            ?.lastOrNull()
            ?.text

    Innertube.PlaylistOrAlbumPage(
        title = title,
        thumbnail = thumbnail,
        authors = authors,
        year = year,
        url = response
            .microformat
            ?.microformatDataRenderer
            ?.urlCanonical,
        songsPage = musicShelfRenderer
            ?.toSongsPage(),
        otherVersions = musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from)
    )
}

suspend fun Innertube.playlistPage(body: ContinuationBody) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        mask("continuationContents.musicPlaylistShelfContinuation(continuations,contents.$musicResponsiveListItemRendererMask)")
    }.body<ContinuationResponse>()

    response
        .continuationContents
        ?.musicShelfContinuation
        ?.toSongsPage()
}

private fun MusicShelfRenderer?.toSongsPage() =
    Innertube.ItemsPage(
        items = this
            ?.contents
            ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from),
        continuation = this
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
    )
