package it.vfsfitvnm.vimusic

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.vimusic.spotify.SpotifyImporter
import it.vfsfitvnm.vimusic.spotify.SpotifyImportTrack
import it.vfsfitvnm.vimusic.spotify.TrackMatcher
import it.vfsfitvnm.vimusic.spotify.toMatchableTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The Spotify import's parsing half: what counts as a playlist link, what the
 * embed page says, and what the pagination responses say. Ported from the
 * BitChord importer's behaviour; the payloads here are the shapes that
 * endpoint actually returns (trackList entries with title/subtitle,
 * pathfinder items under itemV2.data, totalCount as a string).
 */
class SpotifyImporterTest {

    private val playlistId = "37i9dQZF1DXcBWIGoYBM5M"

    // ---- Link parsing -------------------------------------------------------

    @Test
    fun `extracts the id from a canonical playlist link`() {
        assertEquals(
            playlistId,
            SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/$playlistId"),
        )
    }

    @Test
    fun `extracts the id from a link with tracking parameters`() {
        assertEquals(
            playlistId,
            SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/$playlistId?si=abc123&pt=1"),
        )
    }

    @Test
    fun `extracts the id from a spotify uri`() {
        assertEquals(
            playlistId,
            SpotifyImporter.extractPlaylistId("spotify:playlist:$playlistId"),
        )
    }

    @Test
    fun `extracts the id from a user-scoped link`() {
        assertEquals(
            playlistId,
            SpotifyImporter.extractPlaylistId("https://open.spotify.com/user/someone/playlist/$playlistId"),
        )
    }

    @Test
    fun `extracts the id from an embed link`() {
        assertEquals(
            playlistId,
            SpotifyImporter.extractPlaylistId("https://open.spotify.com/embed/playlist/$playlistId"),
        )
    }

    @Test
    fun `rejects links that are not playlist links`() {
        assertNull(SpotifyImporter.extractPlaylistId(""))
        assertNull(SpotifyImporter.extractPlaylistId("https://open.spotify.com/album/$playlistId"))
        assertNull(SpotifyImporter.extractPlaylistId("https://open.spotify.com/track/6ap2paZqZ3tZBa858JAUuk"))
        assertNull(SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/"))
        assertNull(SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/not-a-real-id"))
        assertNull(SpotifyImporter.extractPlaylistId("https://music.youtube.com/playlist/$playlistId"))
        assertNull(SpotifyImporter.extractPlaylistId("spotify:album:$playlistId"))
        assertNull(SpotifyImporter.extractPlaylistId("just some words"))
    }

    @Test
    fun `rejects a link without a scheme`() {
        assertNull(SpotifyImporter.extractPlaylistId("open.spotify.com/playlist/$playlistId"))
    }

    @Test
    fun `rejects an id that is not 22 base-62 characters`() {
        assertNull(SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/shortid"))
        assertNull(
            SpotifyImporter.extractPlaylistId(
                "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M EXTRA"
            ),
        )
    }

    // ---- Embed page parsing ---------------------------------------------------

    private fun embedHtml(pageProps: String): String {
        val json = """{"props":{"pageProps":$pageProps}}"""
        return """<html><head><script id="__NEXT_DATA__" type="application/json">$json</script></head></html>"""
    }

    private fun entityPageProps(
        name: String? = "Today's Top Hits",
        trackList: String = """[{"title":"Loser","subtitle":"Tame Impala"}]""",
        token: String? = null,
    ): String {
        val nameJson = name?.let { """"name":"$it",""" } ?: ""
        val settings = if (token != null) {
            """"settings":{"session":{"accessToken":"$token"}},"""
        } else {
            ""
        }
        return """{"state":{$settings"data":{"entity":{$nameJson"trackList":$trackList}}}}"""
    }

    @Test
    fun `parses the playlist title and track list`() {
        val page = SpotifyImporter.parseEmbedPage(
            embedHtml(
                entityPageProps(
                    name = "Road Trip",
                    trackList = """[
                        {"title":"Stop The Wedding!","subtitle":"Ashe"},
                        {"title":"Loser","subtitle":"Tame Impala"}
                    ]""",
                )
            ),
        )
        assertEquals("Road Trip", page.title)
        assertEquals(
            listOf(
                SpotifyImportTrack(title = "Stop The Wedding!", artist = "Ashe"),
                SpotifyImportTrack(title = "Loser", artist = "Tame Impala"),
            ),
            page.tracks,
        )
        assertNull(page.accessToken)
    }

    @Test
    fun `joins artist names when the track list carries an artists array`() {
        val page = SpotifyImporter.parseEmbedPage(
            embedHtml(
                entityPageProps(
                    trackList = """[{"name":"Rain On Me","artists":[{"name":"Lady Gaga"},{"name":"Ariana Grande"}]}]""",
                )
            ),
        )
        assertEquals(1, page.tracks.size)
        assertEquals("Lady Gaga, Ariana Grande", page.tracks.first().artist)
    }

    @Test
    fun `reads the pagination token from the session settings`() {
        val page = SpotifyImporter.parseEmbedPage(
            embedHtml(entityPageProps(token = "anonymous-token")),
        )
        assertEquals("anonymous-token", page.accessToken)
    }

    @Test
    fun `skips entries without a usable title`() {
        val page = SpotifyImporter.parseEmbedPage(
            embedHtml(
                entityPageProps(
                    trackList = """[
                        {"title":"  ","subtitle":"Blank title is dropped"},
                        {"subtitle":"No title at all"},
                        {"title":"Kept","subtitle":"Kept Artist"}
                    ]""",
                )
            ),
        )
        assertEquals(listOf(SpotifyImportTrack(title = "Kept", artist = "Kept Artist")), page.tracks)
    }

    @Test
    fun `fails with a parsing message when there is no next data script`() {
        try {
            SpotifyImporter.parseEmbedPage("<html><body>nothing here</body></html>")
            fail("expected an exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message, e.message!!.contains("Could not parse Spotify playlist metadata"))
        }
    }

    @Test
    fun `fails with the private playlist message when the entity is missing`() {
        try {
            SpotifyImporter.parseEmbedPage(embedHtml("""{"status":404}"""))
            fail("expected an exception")
        } catch (e: IllegalStateException) {
            assertEquals("Spotify playlist entity missing or private", e.message)
        }
    }

    @Test
    fun `fails with the empty playlist message when no track survives`() {
        try {
            SpotifyImporter.parseEmbedPage(embedHtml(entityPageProps(trackList = "[]")))
            fail("expected an exception")
        } catch (e: IllegalStateException) {
            assertEquals("No tracks found in public Spotify playlist.", e.message)
        }
    }

    // ---- Pathfinder pagination parsing ---------------------------------------

    private fun pathfinderBody(items: String, totalCount: String): String =
        """{"data":{"playlistV2":{"content":{"totalCount":"$totalCount","items":$items}}}}"""

    @Test
    fun `parses a pathfinder page with its totals`() {
        val page = SpotifyImporter.parsePathfinderPage(
            pathfinderBody(
                items = """[
                    {"itemV2":{"data":{"name":"Song One","artists":{"items":[
                        {"profile":{"name":"Artist A"}},{"profile":{"name":"Artist B"}}
                    ]}}}},
                    {"itemV2":{"data":{"name":"Song Two","artists":{"items":[
                        {"profile":{"name":"Artist C"}}
                    ]}}}}
                ]""",
                totalCount = "120",
            )
        )
        assertNotNull(page)
        assertEquals(2, page!!.itemsRead)
        assertEquals(120, page.totalCount)
        assertEquals(
            listOf(
                SpotifyImportTrack(title = "Song One", artist = "Artist A, Artist B"),
                SpotifyImportTrack(title = "Song Two", artist = "Artist C"),
            ),
            page.tracks,
        )
    }

    @Test
    fun `returns null when the page holds no content`() {
        assertNull(SpotifyImporter.parsePathfinderPage("""{"data":{"playlistV2":{}}}"""))
        assertNull(SpotifyImporter.parsePathfinderPage("""{"errors":[{"message":"expired"}]}"""))
    }

    @Test
    fun `returns null when the total count is not a number`() {
        assertNull(
            SpotifyImporter.parsePathfinderPage(
                pathfinderBody(items = """[{"itemV2":{"data":{"name":"Song"}}}]""", totalCount = "many")
            )
        )
    }

    // ---- Candidate mapping ----------------------------------------------------

    private fun songItem(
        videoId: String,
        title: String,
        artists: List<String>,
        album: String? = null,
        duration: String? = null,
    ) = Innertube.SongItem(
        info = Innertube.Info(
            name = title,
            endpoint = NavigationEndpoint.Endpoint.Watch(videoId = videoId),
        ),
        authors = artists.map {
            Innertube.Info(
                name = it,
                endpoint = NavigationEndpoint.Endpoint.Browse(browseId = "artist-$it"),
            )
        },
        album = album?.let {
            Innertube.Info(
                name = it,
                endpoint = NavigationEndpoint.Endpoint.Browse(browseId = "album-$it"),
            )
        },
        durationText = duration,
        thumbnail = null,
    )

    @Test
    fun `a search result maps onto the fields the matcher reads`() {
        val item = songItem(
            videoId = "abc123",
            title = "Paniyon Sa",
            artists = listOf("Atif Aslam", "Tulsi Kumar"),
            album = "Satyamev Jayate",
            duration = "4:07",
        )
        val track = item.toMatchableTrack()
        assertEquals("abc123", track.videoId)
        assertEquals("Paniyon Sa", track.title)
        assertEquals("Atif Aslam, Tulsi Kumar", track.artist)
        assertEquals("Satyamev Jayate", track.albumName)
        assertEquals("4:07", track.durationText)
        assertEquals(247, TrackMatcher.secondsOf(track.durationText))
    }
}
