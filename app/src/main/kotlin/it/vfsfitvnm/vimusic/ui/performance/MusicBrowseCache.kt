package it.vfsfitvnm.vimusic.ui.performance

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.MoodExploreCategory
import it.vfsfitvnm.innertube.requests.MoodExploreShelf
import it.vfsfitvnm.innertube.requests.moodExploreCategories
import it.vfsfitvnm.innertube.requests.moodExploreShelves
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal object MusicBrowseCache {
    private const val TEN_MINUTES = 10L * 60 * 1000
    private const val FIVE_MINUTES = 5L * 60 * 1000
    private const val RETRY_AFTER = 30_000L
    private val categories = TimedLruCache<String, List<MoodExploreCategory>>(2)
    private val shelves = TimedLruCache<String, List<MoodExploreShelf>>(48)
    private val songs = TimedLruCache<String, List<Innertube.SongItem>>(45)
    private val requestLocks = Array(64) { Mutex() }
    private val artworkRequests = Semaphore(2)
    private val artworks = TimedLruCache<String, String>(64)
    private fun artworkKey(category: MoodExploreCategory) =
        "artwork/${category.browseId}/${category.params.orEmpty()}"

    fun peekExploreCategories(): List<MoodExploreCategory>? = categories.get("explore/all")
    fun peekCategoryArtwork(category: MoodExploreCategory): String? =
        category.imageUrl ?: artworks.get(artworkKey(category))

    fun invalidateAll() {
        categories.clear()
        shelves.clear()
        songs.clear()
        artworks.clear()
    }

    private suspend fun <V : Any> cached(
        key: String,
        cache: TimedLruCache<String, V>,
        force: Boolean = false,
        loader: suspend () -> V,
        isValid: (V) -> Boolean = { true }
    ): V {
        if (!force) cache.get(key)?.let { return it }
        val mutex = requestLocks[(key.hashCode() and Int.MAX_VALUE) % requestLocks.size]
        return mutex.withLock {
            if (!force) cache.get(key)?.let { return@withLock it }
            val value = loader()
            cache.put(key, value, if (isValid(value)) TEN_MINUTES else RETRY_AFTER)
            value
        }
    }

    suspend fun exploreCategories(force: Boolean = false): List<MoodExploreCategory> =
        cached("explore/all", categories, force, {
            Innertube.moodExploreCategories().getOrNull().orEmpty()
        }, { it.isNotEmpty() })

    suspend fun categoryShelves(category: MoodExploreCategory, force: Boolean = false): List<MoodExploreShelf> {
        if (category.browseId.isBlank()) return emptyList()
        val key = "shelves/${category.browseId}/${category.params.orEmpty()}"
        return cached(key, shelves, force, {
            Innertube.moodExploreShelves(category).getOrNull().orEmpty()
        }, { it.isNotEmpty() })
    }

    suspend fun categoryArtwork(category: MoodExploreCategory): String? {
        peekCategoryArtwork(category)?.let { return it }
        if (category.browseId.isBlank()) return null
        return artworkRequests.withPermit {
            peekCategoryArtwork(category)?.let { return@withPermit it }
            val image = categoryShelves(category).asSequence()
                .flatMap { it.playlists.asSequence() }
                .mapNotNull { it.imageUrl }.firstOrNull()
            if (!image.isNullOrBlank()) artworks.put(artworkKey(category), image, TEN_MINUTES)
            image
        }
    }

    suspend fun songs(query: String, force: Boolean = false): List<Innertube.SongItem> = cached(
        "songs/$query", songs, force = force, loader = {
            Innertube.searchPage(
                SearchBody(query = query, params = Innertube.SearchFilter.Song.value),
                Innertube.SongItem.Companion::from
            )?.getOrNull()?.items.orEmpty()
                .filter { it.info?.endpoint?.videoId != null }
                .distinctBy { it.key }
                .take(36)
        }, isValid = { it.isNotEmpty() })
}
