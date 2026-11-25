package it.vfsfitvnm.vimusic.ui.screens.home

import it.vfsfitvnm.innertube.requests.MoodExploreCategory

/** Offline and first-frame Explore categories; replaced with the live catalog when reachable. */
internal object MoodspaceCatalog {
    private val titles = listOf(
        "Feel good", "Energize", "Chill", "Focus", "Relax", "Sleep",
        "Sad", "Romance", "Party", "Workout", "Commute", "Unwind",
        "Morning", "Night Drive", "Travel", "Motivation", "Melancholy", "Happy",
        "Pop", "Hip-Hop", "Rock", "Indie", "R&B", "Electronic",
        "Dance", "Jazz", "Classical", "Lo-fi", "Ambient", "Acoustic",
        "Bollywood", "Punjabi", "Tamil", "Telugu", "K-Pop", "J-Pop",
        "Latin", "Afrobeats", "Instrumental", "Soul"
    )
    val fallback: List<MoodExploreCategory> = titles.map { MoodExploreCategory(it, browseId = "") }

    fun discoveryQueries(category: String, artist: String?, language: String?, intelligence: Boolean): List<String> {
        val name = category.trim().take(80)
        val base = when (name.lowercase()) {
            "focus" -> "focus instrumental study music"
            "sleep" -> "calm sleep ambient music"
            "feel good", "happy" -> "happy feel good songs"
            "energize", "workout" -> "energetic workout songs"
            "chill", "unwind", "relax" -> "chill relaxing songs"
            "commute", "travel" -> "road trip songs"
            "night drive" -> "night drive music"
            "sad", "melancholy" -> "sad emotional songs"
            else -> "$name music songs"
        }
        return buildList {
            if (intelligence && !artist.isNullOrBlank()) add("$name ${artist.trim()} songs")
            if (intelligence && !language.isNullOrBlank()) add("$base ${language.trim()}")
            add(base)
        }.distinct().take(3)
    }

    /** Personalize order without deleting any server-supplied genres. */
    fun prioritize(categories: List<MoodExploreCategory>, favoriteMoods: List<String>): List<MoodExploreCategory> {
        val preferred = favoriteMoods.map(String::lowercase).toSet()
        return categories.distinctBy { category ->
            if (category.browseId.isBlank()) "local/${category.title.lowercase()}"
            else "${category.browseId}|${category.params.orEmpty()}"
        }.sortedWith(compareByDescending<MoodExploreCategory> { it.title.lowercase() in preferred })
    }
}
