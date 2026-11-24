package it.vfsfitvnm.vimusic.intelligence

/**
 * Pure Kotlin queue policy. Manual/search selections retain their ordering; only
 * automatically discovered radio candidates are personalized and re-ranked.
 * No network, Android or player dependency: deterministic and unit-testable.
 */
internal object SmartQueueRanker {
    data class Track(val id: String, val title: String = "", val artist: String = "")

    data class Signals(
        val preferredArtists: Set<String> = emptySet(),
        val preferredSongs: Set<String> = emptySet(),
        val favoriteSongs: Set<String> = emptySet(),
        val recentlyPlayed: Set<String> = emptySet(),
        val artistAffinity: Map<String, Int> = emptyMap(),
        val mutedSongs: Set<String> = emptySet(),
        val boostedSongs: Set<String> = emptySet(),
        val discoveryPercent: Int = 35
    )

    private fun normalize(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)

    /** Clicking a result plays it first, then the following results, then earlier ones. */
    fun searchQueue(selected: Track, visible: List<Track>, maxItems: Int = 12): List<Track> {
        if (selected.id.isBlank() || maxItems <= 0) return emptyList()
        val selectedPosition = visible.indexOfFirst { it.id == selected.id }
        val after = if (selectedPosition < 0) visible else visible.drop(selectedPosition + 1)
        val before = if (selectedPosition < 0) emptyList() else visible.take(selectedPosition)
        return (listOf(selected) + after + before)
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .take(maxItems)
    }

    /**
     * Rank discovery only, never a user-curated queue. The catalog supplies
     * related candidates; WEST applies the user's actual preferences and history.
     */
    fun radioQueue(
        candidates: List<Track>,
        current: Track?,
        existingIds: Set<String>,
        signals: Signals,
        limit: Int = 16
    ): List<Track> {
        if (limit <= 0) return emptyList()
        val currentArtist = normalize(current?.artist.orEmpty())
        val artists = signals.preferredArtists.map(::normalize).filter(String::isNotEmpty).toSet()
        val affinities = signals.artistAffinity.entries.associate { normalize(it.key) to it.value }
        val discovery = signals.discoveryPercent.coerceIn(0, 100) / 100.0

        val ranked = candidates.asSequence()
            .filter { it.id.isNotBlank() && it.id !in existingIds && it.id !in signals.mutedSongs }
            .distinctBy { it.id }
            .mapIndexed { position, item ->
                val artist = normalize(item.artist)
                val preferred = artist.isNotBlank() && artists.any { artist.contains(it) || it.contains(artist) }
                val sameArtist = currentArtist.isNotBlank() && artist.isNotBlank() &&
                    (artist.contains(currentArtist) || currentArtist.contains(artist))
                val affinity = affinities[artist] ?: 0
                val relevance = (if (sameArtist) 24.0 else 0.0) +
                    (if (preferred) 22.0 else 0.0) +
                    (if (item.id in signals.preferredSongs) 26.0 else 0.0) +
                    (if (item.id in signals.favoriteSongs) 22.0 else 0.0) +
                    (if (item.id in signals.boostedSongs) 20.0 else 0.0) +
                    affinity.coerceAtMost(12) * 2.0 -
                    (if (item.id in signals.recentlyPlayed) 32.0 else 0.0)
                // Provider order encodes related-track relevance. Preserve a
                // substantial contribution instead of assuming text = mood/genre.
                val providerScore = (30 - position.coerceAtMost(30)) * 0.65
                val exploration = if (preferred || sameArtist || affinity > 0) 0.0 else 12.0 * discovery
                item to relevance * (1.0 - discovery * 0.35) + providerScore + exploration
            }
            .sortedWith(compareByDescending<Pair<Track, Double>> { it.second }.thenBy { it.first.id })
            .toMutableList()

        val chosen = ArrayList<Track>(limit)
        while (chosen.size < limit && ranked.isNotEmpty()) {
            val recentArtists = chosen.takeLast(2).map { normalize(it.artist) }.filter(String::isNotEmpty)
            val best = ranked.indexOfFirst { (track, _) ->
                val artist = normalize(track.artist)
                artist.isBlank() || artist !in recentArtists
            }.takeIf { it >= 0 } ?: 0
            chosen += ranked.removeAt(best).first
        }
        return chosen
    }
}
