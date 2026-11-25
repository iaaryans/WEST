package it.vfsfitvnm.vimusic.ui.performance

/** Small, bounded memory cache: reusable without Android or Compose dependencies. */
internal class TimedLruCache<K : Any, V : Any>(
    private val capacity: Int,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class CacheRecord<V>(val value: V, val expiresAt: Long)
    private val entries = object : LinkedHashMap<K, CacheRecord<V>>(capacity.coerceAtLeast(1), .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, CacheRecord<V>>?): Boolean =
            size > capacity
    }

    init { require(capacity > 0) }

    @Synchronized fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (clock() >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized fun put(key: K, value: V, ttlMs: Long) {
        require(ttlMs > 0)
        entries[key] = CacheRecord(value, clock() + ttlMs)
    }

    @Synchronized fun invalidate(key: K) { entries.remove(key) }
    @Synchronized fun clear() { entries.clear() }
    @Synchronized fun size(): Int = entries.size
}
