package it.vfsfitvnm.vimusic.personalization

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import org.json.JSONArray
import org.json.JSONObject

/** A local, editable taste profile. No account or new Room migration is required. */
internal data class ArtistChoice(val name: String, val id: String? = null, val photoUrl: String? = null)
internal data class SongChoice(val id: String, val title: String)
internal data class TasteProfile(
    val languages: List<String> = emptyList(),
    val artists: List<ArtistChoice> = emptyList(),
    val songs: List<SongChoice> = emptyList(),
    val moods: List<String> = emptyList()
)

internal object TasteProfileStore {
    const val PROFILE = "westTasteProfileV1"
    const val COMPLETED = "westTasteOnboardingCompletedV1"
    const val EDIT_REQUESTED = "westTasteEditRequestedV1"

    fun completed(prefs: SharedPreferences): Boolean = prefs.getBoolean(COMPLETED, false)
    fun editRequested(prefs: SharedPreferences): Boolean = prefs.getBoolean(EDIT_REQUESTED, false)
    fun requestEdit(prefs: SharedPreferences) = prefs.edit { putBoolean(EDIT_REQUESTED, true) }
    fun dismiss(prefs: SharedPreferences) = prefs.edit {
        putBoolean(COMPLETED, true)
        putBoolean(EDIT_REQUESTED, false)
    }
    fun reset(prefs: SharedPreferences) = prefs.edit {
        remove(PROFILE)
        putBoolean(COMPLETED, true)
        putBoolean(EDIT_REQUESTED, false)
    }

    fun save(prefs: SharedPreferences, profile: TasteProfile) {
        val root = JSONObject()
        root.put("languages", JSONArray(profile.languages.distinct().take(20)))
        root.put("moods", JSONArray(profile.moods.distinct().take(20)))
        root.put("artists", JSONArray().apply {
            profile.artists.distinctBy { it.name.lowercase() }.take(100).forEach { artist ->
                put(JSONObject().put("name", artist.name).put("id", artist.id).put("photo", artist.photoUrl))
            }
        })
        root.put("songs", JSONArray().apply {
            profile.songs.distinctBy { it.id }.take(100).forEach { song ->
                put(JSONObject().put("id", song.id).put("title", song.title))
            }
        })
        prefs.edit {
            putString(PROFILE, root.toString())
            putBoolean(COMPLETED, true)
            putBoolean(EDIT_REQUESTED, false)
        }
    }

    fun load(prefs: SharedPreferences): TasteProfile = try {
        val root = JSONObject(prefs.getString(PROFILE, null) ?: "{}")
        fun strings(key: String): List<String> {
            val values = root.optJSONArray(key) ?: return emptyList()
            return (0 until values.length()).mapNotNull { i ->
                values.optString(i).trim().takeIf { it.isNotEmpty() }
            }
        }
        val artists = root.optJSONArray("artists") ?: JSONArray()
        val songs = root.optJSONArray("songs") ?: JSONArray()
        TasteProfile(
            languages = strings("languages"),
            artists = (0 until artists.length()).mapNotNull { index ->
                val artist = artists.optJSONObject(index) ?: return@mapNotNull null
                val name = artist.optString("name").trim().takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                ArtistChoice(name, artist.optString("id").takeIf { it.isNotBlank() && it != "null" },
                    artist.optString("photo").takeIf { it.isNotBlank() && it != "null" })
            },
            songs = (0 until songs.length()).mapNotNull { index ->
                val song = songs.optJSONObject(index) ?: return@mapNotNull null
                val id = song.optString("id").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SongChoice(id, song.optString("title"))
            },
            moods = strings("moods")
        )
    } catch (_: Exception) { TasteProfile() }

    /** Home sections react immediately to edits; listeners are closed when UI leaves composition. */
    fun observe(prefs: SharedPreferences): Flow<TasteProfile> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PROFILE) trySend(load(prefs))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(load(prefs))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()
}
