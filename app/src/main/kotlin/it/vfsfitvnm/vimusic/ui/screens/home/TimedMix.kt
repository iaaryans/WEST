package it.vfsfitvnm.vimusic.ui.screens.home

import it.vfsfitvnm.vimusic.models.Song
import kotlin.math.abs
import kotlin.random.Random

internal fun seconds(value: String?): Int {
    val parts = value?.trim()?.split(':') ?: return 0
    if (parts.size !in 2..3) return 0
    var total = 0
    for ((index, part) in parts.withIndex()) {
        val n = part.toIntOrNull() ?: return 0
        if (n !in 0..999 || (index > 0 && n >= 60)) return 0
        total = total * 60 + n
    }
    return total
}

internal fun timedMix(library: List<Song>, minutes: Int, ending: String?, seed: Int): List<Song> {
    val last = library.firstOrNull { it.id == ending && seconds(it.durationText) > 0 }
    val target = minutes.coerceIn(1, 120) * 60 - seconds(last?.durationText)
    if (target <= 0) return listOfNotNull(last)
    val pool = library.filter { it.id != ending && seconds(it.durationText) > 0 }.shuffled(Random(seed)).take(160)
    // Above twice the target even an empty prefix would be closer.
    val limit = target * 2
    val parent = IntArray(limit + 1) { -1 }
    val lengths = pool.map { seconds(it.durationText) }
    parent[0] = -2
    for ((index, length) in lengths.withIndex()) {
        for (sum in limit downTo length) {
            if (parent[sum] == -1 && parent[sum - length] != -1) parent[sum] = index
        }
        if (parent[target] != -1) break
    }
    var sum = (0..limit).filter { parent[it] != -1 && (last != null || it > 0) }.minByOrNull { abs(it - target) }
        ?: return listOfNotNull(last ?: pool.minByOrNull { seconds(it.durationText) })
    val tracks = mutableListOf<Song>()
    while (sum > 0) {
        val index = parent[sum]
        tracks.add(pool[index])
        sum -= lengths[index]
    }
    return tracks.asReversed() + listOfNotNull(last)
}
