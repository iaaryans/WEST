package it.vfsfitvnm.innertube

import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.utils.from
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG-003 regression tests: song metadata mapping against real YouTube Music
 * response layouts. The fixtures mirror verified live payloads:
 *
 *  A. Current song-search rows: title, merged "artist • album • duration"
 *     column, play-count column, no fixed column.
 *  B. Legacy rows: title, artists, album columns and a fixed duration column.
 *  C. Album shelf rows: title, empty column, play-count column, duration in
 *     the fixed column, no artist data on the row.
 *  D. Merged bylines with multiple artists.
 *  E. Row variant with the play count inside the fixed duration column.
 *
 * The mapping must identify fields structurally so play counts can never
 * become durations and artists are never lost.
 */
@OptIn(ExperimentalSerializationApi::class)
class MusicResponsiveListItemRendererMappingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun decode(jsonText: String): MusicResponsiveListItemRenderer =
        json.decodeFromString(MusicResponsiveListItemRenderer.serializer(), jsonText)

    private fun artistRun(text: String, browseId: String) = """
        {"text": "$text", "navigationEndpoint": {"browseEndpoint": {
            "browseId": "$browseId",
            "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_ARTIST"}}
        }}}
    """.trimIndent()

    private fun albumRun(text: String, browseId: String) = """
        {"text": "$text", "navigationEndpoint": {"browseEndpoint": {
            "browseId": "$browseId",
            "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_ALBUM"}}
        }}}
    """.trimIndent()

    private fun titleRun(text: String, videoId: String) = """
        {"text": "$text", "navigationEndpoint": {"watchEndpoint": {"videoId": "$videoId"}}}
    """.trimIndent()

    // ------------------------------------------------------------------
    // A. Current search layout
    // ------------------------------------------------------------------

    @Test
    fun `current search layout maps artist album and duration`() {
        val renderer = decode("""
        {"flexColumns": [
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [${titleRun("Believer", "Kx7B-XvmFtE")}]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                ${artistRun("Imagine Dragons", "UC0aXrjVxG5pZr99v77wZdPQ")},
                {"text": " • "},
                ${albumRun("Evolve", "MPREb_q16Gzaa1WK8")},
                {"text": " • "},
                {"text": "3:25"}
            ]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "7B plays"}]}}}
        ]}
        """.trimIndent())

        val song = Innertube.SongItem.from(renderer)!!

        assertEquals("Believer", song.info?.name)
        assertEquals(listOf("Imagine Dragons"), song.authors?.map { it.name })
        assertEquals("Evolve", song.album?.name)
        assertEquals("3:25", song.durationText)
        assertTrue(
            "no artist name may contain a play count",
            song.authors?.mapNotNull { it.name }.orEmpty().none { it.contains("plays") }
        )
    }

    // ------------------------------------------------------------------
    // B. Legacy layout (artists/album columns + fixed duration column)
    // ------------------------------------------------------------------

    @Test
    fun `legacy layout with fixed duration column maps correctly`() {
        val renderer = decode("""
        {"flexColumns": [
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [${titleRun("Believer", "Kx7B-XvmFtE")}]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [${artistRun("Imagine Dragons", "UC0aXrjVxG5pZr99v77wZdPQ")}]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [${albumRun("Evolve", "MPREb_q16Gzaa1WK8")}]}}}
        ], "fixedColumns": [
            {"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "3:24"}]}}}
        ]}
        """.trimIndent())

        val song = Innertube.SongItem.from(renderer)!!

        assertEquals(listOf("Imagine Dragons"), song.authors?.map { it.name })
        assertEquals("Evolve", song.album?.name)
        assertEquals("3:24", song.durationText)
    }

    // ------------------------------------------------------------------
    // C. Album shelf layout (play-count column, duration in fixed column)
    // ------------------------------------------------------------------

    @Test
    fun `album shelf row with play count column never corrupts metadata`() {
        val renderer = decode("""
        {"flexColumns": [
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [${titleRun("Believer", "7wtfhZwyrcc")}]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "7B plays"}]}}}
        ], "fixedColumns": [
            {"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "3:25"}]}}}
        ]}
        """.trimIndent())

        val song = Innertube.SongItem.from(renderer)!!

        assertEquals("Believer", song.info?.name)
        assertNull("No artist data exists on the row, none may be invented", song.authors)
        assertNull("The play count is not an album", song.album)
        assertEquals("The play count is never stored as a duration", "3:25", song.durationText)
    }

    // ------------------------------------------------------------------
    // D. Merged byline with multiple artists
    // ------------------------------------------------------------------

    @Test
    fun `merged byline with multiple artists maps every artist`() {
        val renderer = decode("""
        {"flexColumns": [
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [${titleRun("Sucker for Pain", "fw7uzVW7IUo")}]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                ${artistRun("Lil Wayne", "UCN_gZ9wU4We5qF3A6K6SpwA")},
                {"text": ", "},
                ${artistRun("Wiz Khalifa", "UCf6JSpiderOJd80YYGBUUhFw")},
                {"text": " & "},
                ${artistRun("Imagine Dragons", "UC0aXrjVxG5pZr99v77wZdPQ")},
                {"text": " • "},
                ${albumRun("Sucker for Pain", "MPREb_1234abcd")},
                {"text": " • "},
                {"text": "4:04"}
            ]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "1.2B plays"}]}}}
        ]}
        """.trimIndent())

        val song = Innertube.SongItem.from(renderer)!!

        assertEquals(
            listOf("Lil Wayne", "Wiz Khalifa", "Imagine Dragons"),
            song.authors?.map { it.name }
        )
        assertEquals("4:04", song.durationText)
    }

    // ------------------------------------------------------------------
    // E. Play count inside the fixed duration column
    // ------------------------------------------------------------------

    @Test
    fun `play count in the fixed duration slot is never stored as duration`() {
        val renderer = decode("""
        {"flexColumns": [
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [${titleRun("Believer", "Kx7B-XvmFtE")}]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {}}}
        ], "fixedColumns": [
            {"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "7B plays"}]}}}
        ]}
        """.trimIndent())

        val song = Innertube.SongItem.from(renderer)!!

        assertNull("durationText must stay null rather than hold a play count", song.durationText)
        assertNull(song.authors)
    }

    // ------------------------------------------------------------------
    // Guards
    // ------------------------------------------------------------------

    @Test
    fun `duration shaped title is not mistaken for the duration`() {
        val renderer = decode("""
        {"flexColumns": [
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [${titleRun("3:30", "abc123")}]}}},
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [${artistRun("Imagine Dragons", "UC0aXrjVxG5pZr99v77wZdPQ")}]}}}
        ]}
        """.trimIndent())

        val song = Innertube.SongItem.from(renderer)!!

        assertEquals("3:30", song.info?.name)
        assertNull("Only byline and fixed columns are searched for durations", song.durationText)
    }

    @Test
    fun `row without a video id is rejected`() {
        val renderer = decode("""
        {"flexColumns": [
            {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "No video"}]}}}
        ]}
        """.trimIndent())

        assertNull(Innertube.SongItem.from(renderer))
    }

    // ------------------------------------------------------------------
    // Content-level mapper used by searchPage
    // ------------------------------------------------------------------

    @Test
    fun `search shelf content delegates to the renderer mapping`() {
        val content = json.decodeFromString(
            MusicShelfRenderer.Content.serializer(),
            """
            {"musicResponsiveListItemRenderer": {"flexColumns": [
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [${titleRun("Believer", "Kx7B-XvmFtE")}]}}},
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [
                    ${artistRun("Imagine Dragons", "UC0aXrjVxG5pZr99v77wZdPQ")},
                    {"text": " • "},
                    {"text": "3:25"}
                ]}}},
                {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "7B plays"}]}}}
            ]}}
            """.trimIndent()
        )

        val song = Innertube.SongItem.from(content)!!

        assertEquals("Believer", song.info?.name)
        assertEquals(listOf("Imagine Dragons"), song.authors?.map { it.name })
        assertEquals("3:25", song.durationText)
        assertNull("A play-count column is not an album", song.album)
    }
}
