package it.vfsfitvnm.vimusic.ui.performance

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.NextBody
import it.vfsfitvnm.innertube.requests.relatedPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Avoids repeating Home's expensive 2-stage Related browse on returning to Home.
 * Caches successful responses for 10 minutes and errors briefly, so failed
 * networks do not trigger a tight retry loop. One in-flight feed load at a time.
 */
internal object HomeRelatedFeedCache {
    private val cache = TimedLruCache<String, Result<Innertube.RelatedPage?>>(8)
    private val mutex = Mutex()

    fun peek(videoId: String): Result<Innertube.RelatedPage?>? = cache.get(videoId)

    suspend fun load(videoId: String, force: Boolean = false): Result<Innertube.RelatedPage?> {
        if (!force) cache.get(videoId)?.let { return it }
        return mutex.withLock {
            if (!force) cache.get(videoId)?.let { return@withLock it }
            try {
                // relatedPage returns a nullable Result: null means the request was
                // cancelled. Propagate cancellation instead of caching a fake failure.
                val result = Innertube.relatedPage(NextBody(videoId = videoId))
                    ?: throw CancellationException("Related page request was cancelled")
                val page = result.getOrNull()
                val hasMusic = page?.songs.orEmpty().isNotEmpty() ||
                    page?.albums.orEmpty().isNotEmpty() ||
                    page?.playlists.orEmpty().isNotEmpty() ||
                    page?.artists.orEmpty().isNotEmpty()
                cache.put(videoId, result, if (hasMusic) 600_000L else 30_000L)
                result
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (cause: Exception) {
                val result = Result.failure<Innertube.RelatedPage?>(cause)
                cache.put(videoId, result, 30_000L)
                result
            }
        }
    }
}
