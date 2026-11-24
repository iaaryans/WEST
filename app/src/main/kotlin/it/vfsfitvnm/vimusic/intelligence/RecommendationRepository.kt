package it.vfsfitvnm.vimusic.intelligence

import android.content.SharedPreferences
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.listening.data.listeningHistory
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.personalization.TasteProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

internal data class RecommendedSong(val song: Song, val reason: String)
internal data class IntelligenceSnapshot(
    val enabled: Boolean = true,
    val madeForYou: List<RecommendedSong> = emptyList(),
    val rediscover: List<RecommendedSong> = emptyList()
)

/** Cold flow: no continuous background recommendation worker or new database tables. */
internal object RecommendationRepository {
    private fun preferenceChanges(prefs: SharedPreferences): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("westIntelligence") == true || key == TasteProfileStore.PROFILE) trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun observe(prefs: SharedPreferences): Flow<IntelligenceSnapshot> = combine(
        Database.intelligenceLibrary(),
        Database.listeningHistory.recentForIntelligence(1200),
        preferenceChanges(prefs)
    ) { songs, sessions, _ ->
        if (!IntelligencePreferences.enabled(prefs)) return@combine IntelligenceSnapshot(enabled = false)
        val valid = songs
        val taste = TasteProfileStore.load(prefs)
        val output = RecommendationEngine.recommend(
            candidates = valid.map { s ->
                RecommendationEngine.Candidate(s.id, s.artistsText, s.totalPlayTimeMs, s.likedAt != null)
            },
            sessions = sessions.map { s ->
                RecommendationEngine.Session(s.songId, s.timestamp, s.playedMs, s.counted)
            },
            now = System.currentTimeMillis(),
            discoveryPercent = IntelligencePreferences.discovery(prefs),
            boosted = IntelligencePreferences.more(prefs),
            muted = IntelligencePreferences.less(prefs),
            preferredArtists = taste.artists.mapTo(HashSet()) { it.name },
            preferredSongs = taste.songs.mapTo(HashSet()) { it.id }
        )
        val songsById = valid.associateBy { it.id }
        fun map(entries: List<RecommendationEngine.Ranked>) = entries.mapNotNull { entry ->
            songsById[entry.id]?.let { RecommendedSong(it, entry.explanation) }
        }
        IntelligenceSnapshot(madeForYou = map(output.madeForYou), rediscover = map(output.rediscover))
    }.flowOn(Dispatchers.Default)
}
