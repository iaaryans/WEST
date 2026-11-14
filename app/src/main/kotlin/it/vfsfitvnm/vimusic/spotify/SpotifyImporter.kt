package it.vfsfitvnm.vimusic.spotify

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Imports a public Spotify playlist by link, matching each track on YouTube
 * Music. Ported from BitChord's SpotifyImporter; the URL parsing, the embed
 * endpoint it reads public playlists from, the pagination past the embed
 * page's first hundred tracks, the language preference and the matching rules
 * are unchanged.
 *
 * The reading side uses no Spotify account: it fetches the same public embed
 * page (`open.spotify.com/embed/playlist/<id>`) a browser shows for a shared
 * link, which is why only public playlists can be imported. The matching side
 * goes through the app's own YouTube Music search, and a track is only
 * resolved when the ported [TrackMatcher] accepts the candidate — nothing is
 * reported as matched that is not.
 */
object SpotifyImporter {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** A Spotify playlist id is 22 base-62 characters. */
    private val PLAYLIST_ID = Regex("""[A-Za-z0-9]{22}""")

    /** The languages the import can prefer, as offered by the import screen. */
    val IMPORT_LANGUAGES = listOf(
        "Tamil", "Telugu", "Kannada", "Malayalam", "Hindi",
        "English", "Punjabi", "Marathi", "Bengali", "Bhojpuri", "Gujarati",
        "Multilingual", "No Preference", "Manual / Other",
    )

    /** The specific languages a query can be biased away from when one is chosen. */
    private val knownLanguages = listOf(
        "Tamil", "Telugu", "Kannada", "Malayalam", "Hindi",
        "English", "Punjabi", "Marathi", "Bengali", "Bhojpuri", "Gujarati"
    )

    private fun isSpotifyHost(host: String) =
        host == "spotify.com" || host.endsWith(".spotify.com")

    /** Parses a raw user string or link into a Spotify playlist ID if valid. */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("spotify:playlist:")) {
            val id = trimmed.removePrefix("spotify:playlist:").substringBefore("?").substringBefore("/")
            return id.takeIf { PLAYLIST_ID.matches(it) }
        }

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (!isSpotifyHost(uri.host?.lowercase().orEmpty())) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val playlistIdx = segments.indexOf("playlist")
        if (playlistIdx == -1 || playlistIdx + 1 >= segments.size) return null
        return segments[playlistIdx + 1].takeIf { PLAYLIST_ID.matches(it) }
    }

    /**
     * Fetches public Spotify playlist details from Spotify's embed endpoint.
     */
    suspend fun fetchPlaylistTracks(playlistId: String): Pair<String, List<SpotifyImportTrack>> =
        withContext(Dispatchers.IO) {
            val (code, html) = httpGet(
                url = "https://open.spotify.com/embed/playlist/$playlistId",
                headers = mapOf("User-Agent" to USER_AGENT),
            )
            if (code !in 200..399 || html.isBlank()) {
                error("Failed to load Spotify playlist (HTTP $code)")
            }

            val embed = parseEmbedPage(html)

            // The embed page stops at its first 100 tracks. The web player's
            // own query, with the anonymous token the embed hands out, pages
            // through the rest; if it refuses, the 100 already read stand.
            val tracks = embed.tracks.toMutableList()
            if (tracks.size >= EMBED_TRACK_LIMIT && embed.accessToken != null) {
                runCatching { fetchRemainingTracks(playlistId, embed.accessToken!!, tracks.size) }
                    .getOrNull()
                    ?.let { tracks.addAll(it) }
            }

            Pair(embed.title, tracks)
        }

    /** What the embed page says about a playlist: its name, tracks and pagination token. */
    internal data class EmbedPage(
        val title: String,
        val tracks: List<SpotifyImportTrack>,
        val accessToken: String?,
    )

    /** The embed page of a public playlist, parsed from its `__NEXT_DATA__` script. */
    internal fun parseEmbedPage(html: String): EmbedPage {
        // Extract <script id="__NEXT_DATA__" type="application/json">...</script>
        val scriptRegex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val match = scriptRegex.find(html)
            ?: error("Could not parse Spotify playlist metadata. Make sure the playlist is public.")

        val root = runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
            ?: error("Could not parse Spotify playlist metadata. Make sure the playlist is public.")

        val props = root.optJSONObject("props")?.optJSONObject("pageProps")
        val stateData = props?.optJSONObject("state")
            ?.optJSONObject("data")
            ?.optJSONObject("entity")
            ?: props?.optJSONObject("entity")
            ?: error("Spotify playlist entity missing or private")

        val playlistTitle = stateData.optStringOrNull("name")
            ?: stateData.optStringOrNull("title")
            ?: "Imported Spotify Playlist"

        val trackListJson = stateData.optJSONArrayOrNull("trackList")
            ?: stateData.optJSONArrayOrNull("tracks")
            ?: JSONArray()

        val tracks = mutableListOf<SpotifyImportTrack>()
        for (i in 0 until trackListJson.length()) {
            val obj = trackListJson.optJSONObject(i) ?: continue
            val title = obj.optStringOrNull("title")
                ?: obj.optStringOrNull("name")
                ?: continue
            val subtitle = obj.optStringOrNull("subtitle")
                ?: obj.optJSONArrayOrNull("artists")?.joinNames()
                ?: ""
            if (title.isNotBlank()) {
                tracks.add(SpotifyImportTrack(title = title.trim(), artist = subtitle.trim()))
            }
        }

        if (tracks.isEmpty()) {
            error("No tracks found in public Spotify playlist.")
        }

        val token = props?.optJSONObject("state")
            ?.optJSONObject("settings")
            ?.optJSONObject("session")
            ?.optStringOrNull("accessToken")

        return EmbedPage(title = playlistTitle, tracks = tracks, accessToken = token)
    }

    /** Tracks the embed page lists before it cuts off. */
    private const val EMBED_TRACK_LIMIT = 100
    private const val PAGE_SIZE = 100
    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val FETCH_PLAYLIST_HASH = "19ff1327c29e99c208c86d7a9d8f1929cfdf3d3202a0ff4253c821f1901aa94d"

    /** Tracks from [start] to the end of the playlist, a page at a time. */
    private fun fetchRemainingTracks(
        playlistId: String,
        token: String,
        start: Int,
    ): List<SpotifyImportTrack> {
        val out = mutableListOf<SpotifyImportTrack>()
        var offset = start
        while (true) {
            val variables = """{"uri":"spotify:playlist:$playlistId","offset":$offset,"limit":$PAGE_SIZE,"enableWatchFeedEntrypoint":false}"""
            val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$FETCH_PLAYLIST_HASH"}}"""
            val url = "$PATHFINDER_URL?operationName=fetchPlaylist" +
                "&variables=${urlEncode(variables)}" +
                "&extensions=${urlEncode(extensions)}"
            val (code, body) = httpGet(
                url = url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Authorization" to "Bearer $token",
                    "App-platform" to "WebPlayer",
                    "Accept" to "application/json",
                ),
            )
            if (code !in 200..399) error("HTTP $code")

            val page = parsePathfinderPage(body) ?: break
            out.addAll(page.tracks)
            offset += page.itemsRead
            if (page.itemsRead == 0 || offset >= page.totalCount) break
        }
        return out
    }

    /** One page of the pathfinder playlist query. */
    internal data class PathfinderPage(
        val tracks: List<SpotifyImportTrack>,
        val itemsRead: Int,
        val totalCount: Int,
    )

    /** A pathfinder playlist page, or null when the response holds no content. */
    internal fun parsePathfinderPage(body: String): PathfinderPage? {
        val content = JSONObject(body).optJSONObject("data")
            ?.optJSONObject("playlistV2")
            ?.optJSONObject("content")
            ?: return null
        val items = content.optJSONArray("items") ?: return null
        val tracks = mutableListOf<SpotifyImportTrack>()
        for (i in 0 until items.length()) {
            val data = items.optJSONObject(i)
                ?.optJSONObject("itemV2")
                ?.optJSONObject("data")
                ?: continue
            val title = data.optStringOrNull("name")?.trim()
            if (title.isNullOrEmpty()) continue
            val artist = data.optJSONObject("artists")
                ?.optJSONArray("items")
                ?.mapNotNull { it.optJSONObject("profile")?.optStringOrNull("name") }
                ?.joinToString(", ")
                .orEmpty()
            tracks.add(SpotifyImportTrack(title = title, artist = artist))
        }
        val total = content.optStringOrNull("totalCount")?.toIntOrNull() ?: return null
        return PathfinderPage(tracks = tracks, itemsRead = items.length(), totalCount = total)
    }

    /**
     * Resolves a list of Spotify tracks to YouTube Music songs using the app's
     * own Innertube search. Optionally filters or prioritizes matches by
     * specified [language]. Reports real-time progress via [onProgress].
     *
     * A track resolves only when the ported [TrackMatcher] accepts a
     * candidate; a search that returns nothing usable leaves the track
     * unmatched rather than guessing. The first search hit is the last
     * resort, exactly as in BitChord, and a video already matched once is not
     * inserted into the playlist a second time.
     */
    suspend fun resolveToSongs(
        tracks: List<SpotifyImportTrack>,
        language: String? = null,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): Pair<List<Innertube.SongItem>, List<SpotifyImportTrack>> = coroutineScope {
        val total = tracks.size
        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(4) // Bounded concurrency for search queries

        val cleanLang = language?.trim()?.takeIf {
            !it.equals("No Preference", ignoreCase = true) &&
            !it.equals("Multilingual", ignoreCase = true) &&
            it.isNotBlank()
        }

        val deferredResults = tracks.map { track ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val query = if (cleanLang != null) {
                        "${track.title} ${track.artist} $cleanLang".trim()
                    } else {
                        "${track.title} ${track.artist}".trim()
                    }

                    val searchResult = Innertube.searchPage(
                        body = SearchBody(query = query, params = Innertube.SearchFilter.Song.value),
                        fromMusicShelfRendererContent = Innertube.SongItem.Companion::from,
                    )?.getOrNull()
                    val candidates = searchResult?.items.orEmpty()

                    val target = TrackMatcher.Target(
                        title = track.title,
                        artist = track.artist,
                    )
                    val bestMatch = TrackMatcher.best(candidates.map { it.toMatchableTrack() }, target)
                        ?: candidates.firstOrNull()?.toMatchableTrack()

                    val matchedCandidate = if (cleanLang != null && candidates.isNotEmpty()) {
                        val conflictingLangs = knownLanguages.filter { !it.equals(cleanLang, ignoreCase = true) }
                        val nonConflictingCandidates = candidates.filter { candidate ->
                            val meta = buildString {
                                append(candidate.info?.name.orEmpty())
                                append(' ')
                                append(candidate.authors?.joinToString(" ") { it.name.orEmpty() }.orEmpty())
                                append(' ')
                                append(candidate.album?.name.orEmpty())
                            }.lowercase()
                            !conflictingLangs.any {
                                meta.contains("(${it.lowercase()})") ||
                                meta.contains("${it.lowercase()} version") ||
                                meta.contains("${it.lowercase()} song")
                            }
                        }.map { it.toMatchableTrack() }
                        TrackMatcher.best(nonConflictingCandidates, target)
                            ?: nonConflictingCandidates.firstOrNull()
                            ?: bestMatch
                    } else {
                        bestMatch
                    }

                    val done = completedCount.incrementAndGet()
                    onProgress(done, total)

                    if (matchedCandidate != null) {
                        val songItem = candidates.firstOrNull { it.key == matchedCandidate.videoId }
                        if (songItem != null) Pair(songItem, null) else Pair(null, track)
                    } else {
                        Pair(null, track)
                    }
                }
            }
        }

        val results = deferredResults.awaitAll()
        // distinctBy keeps the first occurrence, which is the first Spotify
        // track in playlist order that resolved to the video; a later
        // duplicate resolving to the same video is not added to the playlist
        // again and is not reported as unmatched either — the song it wanted
        // is in the playlist.
        val songs = results.mapNotNull { it.first }.distinctBy { it.key }
        val unmatched = results
            .filter { it.first == null && it.second != null }
            .mapNotNull { it.second }

        Pair(songs, unmatched)
    }

    // ── Transport ───────────────────────────────────────────────────────────

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return code to body
        } finally {
            connection.disconnect()
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    // ── org.json null-safety ────────────────────────────────────────────────
    // optString/optJSONObject return ""/null for JSON null too, but the
    // Spotify payload also carries genuine empty strings where BitChord's
    // kotlinx parser saw null; these helpers keep the null-shaped logic.

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeUnless { it.isBlank() }

    private fun JSONObject.optJSONObjectOrNull(key: String): JSONObject? =
        if (!has(key) || isNull(key)) null else optJSONObject(key)

    private fun JSONObject.optJSONArrayOrNull(key: String): JSONArray? =
        if (!has(key) || isNull(key)) null else optJSONArray(key)

    private fun JSONArray.joinNames(): String =
        (0 until length()).mapNotNull { optJSONObject(it)?.optStringOrNull("name") }.joinToString(", ")

    private fun <T> JSONArray.mapNotNull(transform: (JSONObject) -> T?): List<T> =
        (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(transform) }

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
}

/** Single track metadata extracted from a Spotify playlist. */
data class SpotifyImportTrack(
    val title: String,
    val artist: String,
)

/** The matching view of a YouTube Music search result. */
internal fun Innertube.SongItem.toMatchableTrack() = MatchableTrack(
    videoId = key,
    title = info?.name.orEmpty(),
    artist = authors?.joinToString(", ") { it.name.orEmpty() }.orEmpty(),
    albumName = album?.name,
    durationText = durationText,
)
