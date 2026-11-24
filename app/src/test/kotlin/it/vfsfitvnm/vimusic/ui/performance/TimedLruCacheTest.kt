package it.vfsfitvnm.vimusic.ui.performance

import org.junit.Assert.*
import org.junit.Test

class TimedLruCacheTest {
    @Test fun expiresEntriesPreciselyAtTtlBoundary() {
        var now = 100L
        val cache = TimedLruCache<String, String>(3) { now }
        cache.put("mood", "Happy", 300)
        now = 399L
        assertEquals("Happy", cache.get("mood"))
        now = 400L
        assertNull(cache.get("mood"))
    }

    @Test fun evictsLeastRecentlyUsedEntryNotMostRecent() {
        val cache = TimedLruCache<String, Int>(2)
        cache.put("one", 1, 30_000)
        cache.put("two", 2, 30_000)
        assertEquals(1, cache.get("one"))
        cache.put("three", 3, 30_000)
        assertNull(cache.get("two"))
        assertEquals(1, cache.get("one"))
        assertEquals(3, cache.get("three"))
    }

    @Test fun invalidationAndClearNeverReturnStaleContent() {
        val cache = TimedLruCache<String, String>(2)
        cache.put("query", "old", 10_000)
        cache.invalidate("query")
        assertNull(cache.get("query"))
        cache.put("query", "new", 10_000)
        cache.clear()
        assertNull(cache.get("query"))
        assertEquals(0, cache.size())
    }

    @Test fun boundedCacheCannotRetainUnlimitedSearchTerms() {
        val cache = TimedLruCache<Int, Int>(12)
        (1..120).forEach { cache.put(it, it, 60_000) }
        assertEquals(12, cache.size())
        assertNull(cache.get(1))
        assertEquals(120, cache.get(120))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cacheRejectsInvalidCapacity() { TimedLruCache<String, Int>(0) }
}
