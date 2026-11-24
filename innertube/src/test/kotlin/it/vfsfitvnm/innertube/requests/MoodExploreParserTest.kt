package it.vfsfitvnm.innertube.requests

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class MoodExploreParserTest {
    @Test fun exploreParsesMoodsAndGenresWithoutDuplicatingPersonalShortcuts() {
        val json = Json.parseToJsonElement("""{
          "contents": {"singleColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {
            "content": {"sectionListRenderer": {"contents": [
              {"gridRenderer": {"header": {"gridHeaderRenderer": {"title": {"runs": [{"text":"For you"}]}}},
                "items": [{"musicNavigationButtonRenderer": {"buttonText": {"runs": [{"text":"Happy"}]},
                "clickCommand": {"browseEndpoint": {"browseId":"M_HAPPY", "params":"abc"}}}}]}},
              {"gridRenderer": {"header": {"gridHeaderRenderer": {"title": {"runs": [{"text":"Moods"}]}}},
                "items": [{"musicNavigationButtonRenderer": {"buttonText": {"runs": [{"text":"Happy"}]},
                "clickCommand": {"browseEndpoint": {"browseId":"M_HAPPY", "params":"abc"}},
                "solid":{"leftStripeColor":"4294901760"}}}]}},
              {"gridRenderer": {"header": {"gridHeaderRenderer": {"title": {"runs": [{"text":"Genres"}]}}},
                "items": [{"musicNavigationButtonRenderer": {"buttonText": {"runs": [{"text":"Rock"}]},
                "navigationEndpoint": {"browseEndpoint": {"browseId":"M_ROCK", "params":"x"}}}}]}}
            ]}}}}]}}
        }""").jsonObject
        val categories = MoodExploreParser.categories(json)
        assertEquals(listOf("Happy", "Rock"), categories.map { it.title })
        assertEquals("abc", categories.first().params)
        assertEquals(4294901760L, categories.first().stripeColor)
    }

    @Test fun categoryShelvesPreserveFullPlaylistBrowseIdentifiers() {
        val json = Json.parseToJsonElement("""{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Made for you"}]}}},"contents":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Chill Mix"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLPL123"}},"thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.com/small.jpg"},{"url":"https://example.com/big.jpg"}]}}}}}]}}]}}}}]}}}""").jsonObject
        val shelves = MoodExploreParser.shelves(json)
        assertEquals(1, shelves.size)
        assertEquals("Made for you", shelves.first().title)
        assertEquals("VLPL123", shelves.first().playlists.single().browseId)
        assertEquals("https://example.com/big.jpg", shelves.first().playlists.single().imageUrl)
    }

    @Test fun partialResponsesDoNotCrashOrProduceFakePlaylists() {
        val json = Json.parseToJsonElement("""{"contents": {}}""").jsonObject
        assertTrue(MoodExploreParser.categories(json).isEmpty())
        assertTrue(MoodExploreParser.shelves(json).isEmpty())
    }
    @Test fun hanukkahPlaylistResponseWithRepeatedUnnamedShelvesDoesNotLoseData() {
        val json = Json.parseToJsonElement("""{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicCarouselShelfRenderer":{"contents":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Holiday Mix 0"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLPL_HANUKKAH_0"}}}}]}},{"musicCarouselShelfRenderer":{"contents":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Holiday Mix 1"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLPL_HANUKKAH_1"}}}}]}}]}}}}]}}}""").jsonObject
        val shelves = MoodExploreParser.shelves(json)
        assertEquals(2, shelves.size)
        assertEquals(listOf("Playlists", "Playlists"), shelves.map { it.title })
        assertEquals(listOf("VLPL_HANUKKAH_0", "VLPL_HANUKKAH_1"),
            shelves.flatMap { it.playlists }.map { it.browseId })
        // The UI must use index-based unique shelf keys, NOT these repeated titles.
    }

}
