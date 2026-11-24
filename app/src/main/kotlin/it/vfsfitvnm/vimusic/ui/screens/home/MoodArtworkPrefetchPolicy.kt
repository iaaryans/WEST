package it.vfsfitvnm.vimusic.ui.screens.home

import it.vfsfitvnm.innertube.requests.MoodExploreCategory

/** LazyColumn item zero is the header; grid rows begin at index one. */
internal object MoodArtworkPrefetchPolicy {
    fun visible(
        categories: List<MoodExploreCategory>,
        columns: Int,
        visibleItemIndices: List<Int>,
        maxRequests: Int = 6
    ): List<MoodExploreCategory> {
        if (columns <= 0 || maxRequests <= 0 || visibleItemIndices.isEmpty()) return emptyList()
        val rowCount = (categories.size + columns - 1) / columns
        val visibleRows = visibleItemIndices.map { it - 1 }
            .filter { it in 0 until rowCount }
        // At startup the header may be the only visible item; warm the first row.
        val start = visibleRows.minOrNull() ?: 0
        val last = visibleRows.maxOrNull() ?: 0
        return (start..minOf(last + 1, rowCount - 1)).asSequence()
            .flatMap { row -> (row * columns until minOf((row + 1) * columns, categories.size)).asSequence() }
            .map { categories[it] }
            .filter { it.browseId.isNotBlank() && it.imageUrl == null }
            .distinctBy { it.browseId to it.params }
            .take(maxRequests)
            .toList()
    }
}
