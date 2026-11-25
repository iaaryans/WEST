package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.CancellationException

/**
 * The server-defined YouTube Music Explore taxonomy, adapted for WEST Moodspace.
 * BitChord's GPL-3.0 Explore source informed its category navigation and parser
 * (BitChord-main/shared/.../InnertubeParser.kt, parseMoodAndGenres).
 * The source tree is already GPL-3.0; preserve both projects' license notices.
 */
data class MoodExploreCategory(
    val title: String,
    val browseId: String,
    val params: String? = null,
    val stripeColor: Long? = null,
    val imageUrl: String? = null
)

data class MoodExplorePlaylist(val title: String, val browseId: String, val imageUrl: String?)
data class MoodExploreShelf(val title: String, val playlists: List<MoodExplorePlaylist>)

/** Do not require sign-in: anonymous browse contains the full mood/genre catalog. */
suspend fun Innertube.moodExploreCategories(): Result<List<MoodExploreCategory>> = try {
    val payload = client.post("/youtubei/v1/browse") {
        setBody(BrowseBody(browseId = "FEmusic_moods_and_genres"))
    }.body<JsonObject>()
    Result.success(MoodExploreParser.categories(payload).ifEmpty {
        error("No mood categories returned")
    })
} catch (cancel: CancellationException) { throw cancel }
catch (failure: Exception) { Result.failure(failure) }

suspend fun Innertube.moodExploreShelves(category: MoodExploreCategory): Result<List<MoodExploreShelf>> = try {
    require(category.browseId.isNotBlank())
    val payload = client.post("/youtubei/v1/browse") {
        setBody(BrowseBody(browseId = category.browseId, params = category.params))
    }.body<JsonObject>()
    Result.success(MoodExploreParser.shelves(payload))
} catch (cancel: CancellationException) { throw cancel }
catch (failure: Exception) { Result.failure(failure) }

/** Pure parsing: unknown/partial YouTube Music JSON shapes never crash Moodspace. */
object MoodExploreParser {
    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
    private fun JsonObject?.arr(key: String): JsonArray = (this?.get(key) as? JsonArray) ?: JsonArray(emptyList())
    private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject?.label(): String = arr("runs")
        .mapNotNull { (it as? JsonObject)?.str("text") }.joinToString("").trim()
    private fun sections(root: JsonObject): List<JsonObject> {
        val contents = root.obj("contents")
        val tabs = contents.obj("singleColumnBrowseResultsRenderer").arr("tabs")
        val tab = tabs.firstOrNull() as? JsonObject
        val list = tab.obj("tabRenderer").obj("content").obj("sectionListRenderer")
            ?: contents.obj("twoColumnBrowseResultsRenderer").obj("secondaryContents")
                .obj("sectionListRenderer")
            ?: contents.obj("sectionListRenderer")
        return list.arr("contents").take(64).mapNotNull { it as? JsonObject }
    }

    fun categories(root: JsonObject): List<MoodExploreCategory> {
        val groups = sections(root).mapNotNull { section ->
            val grid = section.obj("gridRenderer") ?: return@mapNotNull null
            val heading = grid.obj("header").obj("gridHeaderRenderer").obj("title").label()
            val items = grid.arr("items").take(150).mapNotNull { element ->
                val button = (element as? JsonObject).obj("musicNavigationButtonRenderer")
                    ?: return@mapNotNull null
                val endpoint = button.obj("clickCommand").obj("browseEndpoint")
                    ?: button.obj("navigationEndpoint").obj("browseEndpoint")
                    ?: return@mapNotNull null
                val browse = endpoint.str("browseId")?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val title = button.obj("buttonText").label().takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                MoodExploreCategory(
                    title, browse, endpoint.str("params"),
                    button.obj("solid").str("leftStripeColor")?.toLongOrNull()
                )
            }
            if (items.isEmpty()) null else heading to items
        }
        // The server sometimes includes an initial "For you" grid duplicating later buttons.
        // Show each mood once, with server ordering for the main taxonomy.
        val laterPairs = groups.drop(1).flatMap { (_, values) ->
            values.map { it.browseId to it.params }
        }.toSet()
        return groups.flatMapIndexed { index, (heading, items) ->
            if (heading.equals("For you", ignoreCase = true) ||
                (index == 0 && items.any { (it.browseId to it.params) in laterPairs })) emptyList()
            else items
        }.distinctBy { it.browseId to it.params }.take(200)
    }

    fun shelves(root: JsonObject): List<MoodExploreShelf> = sections(root).mapNotNull { section ->
        val shelf = section.obj("musicCarouselShelfRenderer")
            ?: section.obj("musicImmersiveCarouselShelfRenderer")
            ?: section.obj("gridRenderer")
            ?: return@mapNotNull null
        val title = shelf.obj("header").obj("musicCarouselShelfBasicHeaderRenderer")
            .obj("title").label().ifBlank { "Playlists" }
        val items = (if (section.obj("gridRenderer") != null) shelf.arr("items")
            else shelf.arr("contents"))
        val playlists = items.take(80).mapNotNull { element ->
            val node = element as? JsonObject ?: return@mapNotNull null
            val card = node.obj("musicTwoRowItemRenderer")
                ?: node.obj("musicResponsiveListItemRenderer") ?: return@mapNotNull null
            val firstFlex = (card.arr("flexColumns").firstOrNull() as? JsonObject)
                .obj("musicResponsiveListItemFlexColumnRenderer").obj("text")
            val titleRun = (card.obj("title").arr("runs").firstOrNull() as? JsonObject)
                ?: (firstFlex.arr("runs").firstOrNull() as? JsonObject)
            val browse = card.obj("navigationEndpoint").obj("browseEndpoint").str("browseId")
                ?: titleRun.obj("navigationEndpoint").obj("browseEndpoint").str("browseId")
                ?: return@mapNotNull null
            val label = card.obj("title").label().ifBlank { firstFlex.label() }
            if (label.isBlank()) return@mapNotNull null
            MoodExplorePlaylist(label, browse, artwork(card))
        }.distinctBy { it.browseId }
        if (playlists.isEmpty()) null else MoodExploreShelf(title, playlists)
    }

    private fun artwork(node: JsonElement?, depth: Int = 0): String? {
        if (depth > 10) return null
        return when (node) {
            is JsonObject -> {
                val thumbnails = node["thumbnails"] as? JsonArray
                val url = (thumbnails?.lastOrNull() as? JsonObject)?.str("url")
                url ?: node.values.take(60).firstNotNullOfOrNull { artwork(it, depth + 1) }
            }
            is JsonArray -> node.take(60).firstNotNullOfOrNull { artwork(it, depth + 1) }
            else -> null
        }
    }
}
