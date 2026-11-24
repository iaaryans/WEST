package it.vfsfitvnm.vimusic.voice

import java.text.Normalizer
import java.util.Locale

/** Bounded fuzzy correction against actual local music names. Suggestions never silently replace speech. */
internal object MusicQueryMatcher {
    private fun key(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun distance(a: String, b: String, cutoff: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > cutoff) return cutoff + 1
        var prev = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            var minimum = current[0]
            for (j in b.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    prev[j + 1] + 1,
                    prev[j] + if (a[i] == b[j]) 0 else 1
                )
                minimum = minOf(minimum, current[j + 1])
            }
            if (minimum > cutoff) return cutoff + 1
            val temp = prev
            prev = current
            current = temp
        }
        return prev[b.length]
    }

    fun suggest(query: String, metadata: Collection<String>, limit: Int = 3): List<String> {
        val input = key(query)
        if (input.length < 4) return emptyList()
        val maxDistance = (input.length / 4).coerceIn(1, 4)
        return metadata.asSequence().filter(String::isNotBlank).distinct().take(800)
            .mapNotNull { name ->
                val normalized = key(name)
                if (normalized == input || normalized.length !in (input.length - maxDistance)..(input.length + maxDistance)) null
                else {
                    val score = distance(input, normalized, maxDistance)
                    if (score <= maxDistance) name to score else null
                }
            }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.length })
            .take(limit.coerceIn(0, 5)).map { it.first }.toList()
    }
}
