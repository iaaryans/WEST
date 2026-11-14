package it.vfsfitvnm.innertube.utils

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.Runs

/**
 * Song rows are mapped structurally instead of by fixed column positions:
 * YouTube Music serves several valid layouts (all verified against live
 * responses), and reading "column 1 = artists", "fixed column = duration"
 * corrupts metadata whenever the layout shifts.
 *
 * Known layouts:
 *  1. Current song search: `title` column, one merged column
 *     "artist • album • duration", then a "N plays" count column, no
 *     fixed column.
 *  2. Album/playlist shelf rows: `title` column, empty second column,
 *     "N plays" column, duration inside the fixed column, no artist data
 *     at row level.
 *  3. Legacy rows: `title`, `artists`, `album` columns + duration in the
 *     fixed column.
 *
 * Field identification rules:
 *  - artists  = runs linking to a MUSIC_PAGE_TYPE_ARTIST browse page;
 *    fallback: a second column that contains neither an album link nor a
 *    duration (legacy rows where artist runs carry no endpoint);
 *  - album    = first run linking to a MUSIC_PAGE_TYPE_ALBUM browse page;
 *  - duration = first run shaped like `m:ss` (play counts such as "7B plays"
 *    or view counts can never match, so they are never stored as durations);
 *  - the title column is always excluded from the byline search.
 */
private val durationRunShape = Regex("^\\d{1,2}:\\d{2}$")

private fun Runs.Run.artistEndpoint(): NavigationEndpoint.Endpoint.Browse? =
    navigationEndpoint?.endpoint as? NavigationEndpoint.Endpoint.Browse

private fun Runs.Run.isArtistRun(): Boolean = artistEndpoint()?.type == "MUSIC_PAGE_TYPE_ARTIST"

private fun Runs.Run.isAlbumRun(): Boolean =
    artistEndpoint()?.type == "MUSIC_PAGE_TYPE_ALBUM" ||
        artistEndpoint()?.browseId?.startsWith("MPREb_") == true

private fun Runs.Run.isDurationRun(): Boolean = durationRunShape.matches(text.orEmpty())

fun Innertube.SongItem.Companion.from(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem? {
    val flexRuns = renderer.flexColumns.map { column ->
        column.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
    }
    val fixedRuns = renderer.fixedColumns.orEmpty().map { column ->
        column.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
    }

    val info: Innertube.Info<NavigationEndpoint.Endpoint.Watch>? = flexRuns
        .getOrNull(0)
        ?.getOrNull(0)
        ?.let { Innertube.Info<NavigationEndpoint.Endpoint.Watch>(it) }

    // Every run that can carry byline data: all columns after the title
    // column plus the fixed columns, concatenated in response order.
    val bylineRuns = flexRuns.drop(1).flatten() + fixedRuns.flatten()

    val authors = bylineRuns
        .filter(Runs.Run::isArtistRun)
        .map<Runs.Run, Innertube.Info<NavigationEndpoint.Endpoint.Browse>>(Innertube::Info)
        .takeIf(List<Any>::isNotEmpty)
        ?: flexRuns
            .getOrNull(1)
            ?.takeUnless { runs ->
                // Legacy artist columns are pure artist lists; a column that
                // mixes in an album link or a duration is a merged byline
                // whose artist runs must carry their own endpoints instead.
                runs.isEmpty() || runs.any { it.isAlbumRun() || it.isDurationRun() }
            }
            ?.map<Runs.Run, Innertube.Info<NavigationEndpoint.Endpoint.Browse>>(Innertube::Info)
            ?.takeIf(List<Any>::isNotEmpty)

    val durationText = bylineRuns
        .firstOrNull(Runs.Run::isDurationRun)
        ?.text

    return Innertube.SongItem(
        info = info,
        authors = authors,
        durationText = durationText,
        album = bylineRuns
            .firstOrNull(Runs.Run::isAlbumRun)
            ?.let(Innertube::Info),
        thumbnail = renderer
            .thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.lastOrNull()
    ).takeIf { it.info?.endpoint?.videoId != null }
}
