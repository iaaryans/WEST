package it.vfsfitvnm.vimusic.intelligence

import kotlin.math.ln
import kotlin.math.max

/**
 * Pure Kotlin, deterministic, bounded recommendation scorer. It uses only information
 * that WEST already records; it never invents genres, moods, BPM, or skip counts.
 * The candidates are tracks already in the user's local WEST library. Related online
 * recommendations continue to be provided by the existing Quick Picks flow.
 */
internal object RecommendationEngine {
    data class Candidate(
        val id: String,
        val artist: String?,
        val totalPlayTimeMs: Long,
        val favorite: Boolean
    )

    data class Session(
        val songId: String,
        val startedAt: Long,
        val playedMs: Long,
        val counted: Boolean
    )

    data class Ranked(
        val id: String,
        val score: Double,
        val explanation: String
    )

    data class Output(val madeForYou: List<Ranked>, val rediscover: List<Ranked>)

    fun recommend(
        candidates: List<Candidate>,
        sessions: List<Session>,
        now: Long,
        discoveryPercent: Int = 35,
        boosted: Set<String> = emptySet(),
        muted: Set<String> = emptySet(),
        preferredArtists: Set<String> = emptySet(),
        preferredSongs: Set<String> = emptySet(),
        maxPerSection: Int = 6
    ): Output {
        if (candidates.isEmpty() || maxPerSection <= 0) return Output(emptyList(), emptyList())
        val recentWindowMs = 90L * 86_400_000L
        val bySong = sessions.asSequence()
            .filter { it.startedAt <= now && it.startedAt >= now - recentWindowMs }
            .groupBy { it.songId }
        val lastPlayed = sessions.groupBy { it.songId }.mapValues { (_, plays) ->
            plays.maxOf { it.startedAt }
        }
        val artistAffinity = HashMap<String, Double>()
        for (song in candidates) {
            val artist = song.artist?.trim()?.lowercase().orEmpty()
            if (artist.isNotEmpty()) {
                val count = bySong[song.id].orEmpty().count { it.counted }
                val strength = (if (song.favorite) 2.0 else 0.0) + count.coerceAtMost(10) * 0.35
                artistAffinity[artist] = (artistAffinity[artist] ?: 0.0) + strength
            }
        }
        val discovery = discoveryPercent.coerceIn(0, 100) / 100.0
        fun normalizedArtist(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
        val onboardingArtists = preferredArtists.map(::normalizedArtist).filter { it.isNotBlank() }.toSet()
        val boostedArtists = candidates.asSequence()
            .filter { it.id in boosted }
            .mapNotNull { it.artist?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .toSet()
        val ranked = candidates.asSequence()
            .filterNot { it.id in muted }
            .distinctBy { it.id }
            .map { track ->
                val plays = bySong[track.id].orEmpty()
                val completed = plays.count { it.counted }
                val duration = plays.sumOf { max(0L, it.playedMs) }
                val artistScore = artistAffinity[track.artist?.trim()?.lowercase().orEmpty()] ?: 0.0
                val daysSincePlay = lastPlayed[track.id]?.let { ((now - it).coerceAtLeast(0L) / 86_400_000L).toInt() }
                val chosenArtist = track.artist?.let { artist ->
                    val normalized = normalizedArtist(artist)
                    onboardingArtists.any { name -> normalized.contains(name) }
                } == true
                val familiar =
                    (if (track.id in preferredSongs) 35.0 else 0.0) +
                    (if (chosenArtist) 18.0 else 0.0) +
                    (if (track.favorite) 24.0 else 0.0) +
                    ln(1.0 + track.totalPlayTimeMs.coerceAtLeast(0L) / 60_000.0) * 5.0 +
                    completed.coerceAtMost(10) * 2.0 +
                    ln(1.0 + duration / 60_000.0) * 2.0 +
                    ln(1.0 + artistScore) * 5.0 +
                    (if (track.id in boosted) 30.0 else 0.0) +
                    (if (track.artist?.trim()?.lowercase() in boostedArtists) 6.0 else 0.0)
                val exploration = if (track.totalPlayTimeMs == 0L && completed == 0) {
                    22.0 + ln(1.0 + artistScore) * 8.0
                } else 5.0 + ln(1.0 + artistScore) * 4.0
                val recentPenalty = when {
                    daysSincePlay == null -> 0.0
                    daysSincePlay == 0 -> 22.0
                    daysSincePlay < 3 -> 13.0
                    daysSincePlay < 7 -> 5.0
                    else -> 0.0
                }
                val score = familiar * (1.0 - discovery * 0.6) + exploration * discovery - recentPenalty
                val reason = when {
                    track.id in boosted -> "You requested more like this"
                    track.id in preferredSongs -> "Selected during setup"
                    chosenArtist -> "A favorite artist from your setup"
                    track.favorite -> "One of your favorites"
                    completed >= 2 -> "Based on your listening"
                    artistScore >= 2.0 -> "Related to artists you enjoy"
                    track.totalPlayTimeMs == 0L -> "Discover something in your library"
                    else -> "From your music library"
                }
                Triple(track, Ranked(track.id, score, reason), daysSincePlay)
            }
            .sortedWith(compareByDescending<Triple<Candidate, Ranked, Int?>> { it.second.score }.thenBy { it.first.id })
            .toList()

        // Artist diversity is intentionally enforced only when enough distinct artists exist.
        fun diverse(input: List<Ranked>, limit: Int): List<Ranked> {
            val chosen = ArrayList<Ranked>(limit)
            val usedArtists = HashSet<String>()
            val artistById = candidates.associateBy { it.id }
            for (entry in input) {
                val artist = artistById[entry.id]?.artist?.trim()?.lowercase().orEmpty()
                if (artist.isNotEmpty() && artist in usedArtists) continue
                chosen += entry
                if (artist.isNotEmpty()) usedArtists += artist
                if (chosen.size >= limit) return chosen
            }
            for (entry in input) {
                if (entry !in chosen) chosen += entry
                if (chosen.size >= limit) break
            }
            return chosen
        }

        val main = diverse(ranked.map { it.second }, maxPerSection)
        val mainIds = main.mapTo(HashSet()) { it.id }
        val forgotten = ranked.asSequence()
            .filter { (_, _, days) -> days != null && days >= 21 }
            .filter { (song, _, _) -> song.id !in mainIds }
            .sortedWith(compareByDescending<Triple<Candidate, Ranked, Int?>> { it.third }.thenByDescending { it.second.score })
            .map { (_, recommendation, _) -> recommendation.copy(explanation = "Not played recently") }
            .toList()
        return Output(main, diverse(forgotten, maxPerSection))
    }
}
