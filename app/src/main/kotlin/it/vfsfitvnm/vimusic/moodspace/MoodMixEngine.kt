package it.vfsfitvnm.vimusic.moodspace

/** Mood classification is based on user tags, not invented acoustic features. */
internal enum class Mood(
    val displayName: String,
    val symbol: String,
    val subtitle: String,
    val discoveryQuery: String
) {
    Happy("Happy", "☀️", "Upbeat & feel-good", "happy upbeat feel good songs"),
    Sad("Sad", "🌧️", "Slow & reflective", "sad emotional songs"),
    Chill("Chill", "🌙", "Laid-back listening", "chill relaxing songs"),
    Focus("Focus", "🎯", "Stay in the zone", "focus instrumental study music"),
    Workout("Workout", "⚡", "Move with energy", "workout high energy songs"),
    Relax("Relax", "🍃", "Unwind & recharge", "soft relaxing songs"),
    Sleep("Sleep", "💤", "Quiet night sounds", "calm sleep ambient music"),
    NightDrive("Night Drive", "🚗", "After-dark rides", "night drive chill songs"),
    Travel("Travel", "🌍", "On the road", "road trip travel songs"),
    Energetic("Energetic", "🔥", "Turn it up", "energetic upbeat songs"),
    Melancholy("Melancholy", "🪐", "Bittersweet sounds", "melancholic mellow songs"),
    Custom("Custom", "🎵", "Your own sound", "popular songs")
}

internal data class MoodCandidate(
    val id: String,
    val artist: String?,
    val durationSeconds: Int,
    val liked: Boolean,
    val playTimeMs: Long,
    val tagged: Boolean
)

/** Pure Kotlin, deterministic scoring; does not assume access to genre, BPM or audio features. */
internal object MoodMixEngine {
    fun rank(candidates: List<MoodCandidate>, seed: Int): List<String> {
        val unique = candidates.filter { it.id.isNotBlank() && it.durationSeconds > 0 }
            .distinctBy { it.id }
        val ordered = unique.sortedWith(
            compareByDescending<MoodCandidate> { if (it.tagged) 1000 else 0 }
                .thenByDescending { (if (it.liked) 100 else 0) + (it.playTimeMs / 60000).coerceAtMost(120).toInt() }
                .thenBy { (it.id.hashCode() xor seed * 31) ushr 1 }
        ).toMutableList()
        val result = ArrayList<String>(ordered.size)
        var previousArtist: String? = null
        while (ordered.isNotEmpty()) {
            // Keep tagged songs ahead of non-tagged ones; avoid same-artist runs within a tier.
            val tierIsTagged = ordered.first().tagged
            val index = ordered.indexOfFirst { it.tagged == tierIsTagged &&
                (previousArtist == null || it.artist == null || !it.artist.equals(previousArtist, true)) }
                .takeIf { it >= 0 } ?: 0
            val candidate = ordered.removeAt(index)
            result += candidate.id
            previousArtist = candidate.artist
        }
        return result
    }
}

/**
 * No inferred BPM or mood classification. Personalization comes from observed
 * listening/favorites/explicit feedback; mood relevance comes from user tags
 * and the online mood query, not a fabricated audio-analysis model.
 */
internal object MoodPersonalizationEngine {
    fun favoriteArtist(candidates: List<MoodCandidate>, preferredIds: Set<String> = emptySet()): String? {
        return candidates.asSequence()
            .filter { it.durationSeconds > 0 && it.artist?.isNotBlank() == true }
            .mapNotNull { track ->
                val minutes = (track.playTimeMs.coerceAtLeast(0L) / 60_000L).coerceAtMost(240L)
                // Recommendations alone are NOT evidence of taste: require a listen or like.
                val score = if (track.playTimeMs > 0L || track.liked) {
                    minutes + (if (track.liked) 60 else 0) +
                        (if (track.id in preferredIds) 25 else 0)
                } else 0L
                if (score <= 0L) null else track.artist!!.trim() to score
            }
            .groupBy({ it.first.lowercase() }, { it })
            .values
            .map { tracks -> tracks.first().first to tracks.sumOf { it.second } }
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
            .firstOrNull()?.first
    }

    fun queries(mood: Mood, artist: String?, intelligenceEnabled: Boolean): List<String> {
        // Always include a mood-specific cold-start query, even for a brand-new user.
        val base = mood.discoveryQuery
        val personalized = artist?.trim()?.takeIf { it.isNotBlank() && intelligenceEnabled }
        return listOfNotNull(personalized?.let { "$base $it" }, base).distinct()
    }
}

/** Suggestions use real local metadata, with the preference-aware ranking of WEST Intelligence. */
internal object MoodSuggestionEngine {
    fun suggest(
        candidates: List<MoodCandidate>,
        taggedIds: Set<String>,
        seed: Int = 0,
        limit: Int = 8,
        preferredIds: Set<String> = emptySet(),
        mutedIds: Set<String> = emptySet()
    ): List<String> {
        if (limit <= 0) return emptyList()
        val library = candidates.asSequence()
            .filter { it.id.isNotBlank() && it.durationSeconds > 0 && it.id !in mutedIds }
            .distinctBy { it.id }
            .toList()
        val artists = library.asSequence()
            .filter { it.id in taggedIds }
            .mapNotNull { it.artist?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .toSet()
        // In a new mood only offer local tracks supported by actual user taste.
        // The online mood-query results provide mood-specific cold-start suggestions.
        val sorted = library.asSequence()
            .filterNot { it.id in taggedIds }
            .filter { taggedIds.isNotEmpty() || it.liked || it.playTimeMs > 0L }
            .sortedWith(compareByDescending<MoodCandidate> { track ->
                val matchingArtist = track.artist?.trim()?.lowercase() in artists
                (if (matchingArtist) 300 else 0) +
                    (if (track.id in preferredIds) 180 else 0) +
                    (if (track.liked) 50 else 0) +
                    (track.playTimeMs.coerceAtLeast(0L) / 60_000L).coerceAtMost(120L).toInt()
            }.thenBy { (it.id.hashCode() xor (seed * 31)) ushr 1 })
            .toList()

        val output = ArrayList<String>(limit)
        val usedArtists = HashSet<String>()
        for (track in sorted) {
            val artist = track.artist?.trim()?.lowercase().orEmpty()
            if (artist.isNotEmpty() && !usedArtists.add(artist)) continue
            output += track.id
            if (output.size == limit) return output
        }
        for (track in sorted) {
            if (track.id !in output) output += track.id
            if (output.size == limit) break
        }
        return output
    }
}
