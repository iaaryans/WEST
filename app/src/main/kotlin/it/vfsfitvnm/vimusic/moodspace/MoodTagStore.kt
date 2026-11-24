package it.vfsfitvnm.vimusic.moodspace

import android.content.SharedPreferences

/** User-assigned mood tags make mixes honest without unreliable genre guessing. */
internal object MoodTagStore {
    private fun key(mood: Mood) = "westMoodspace.tags.${mood.name}"

    fun tracks(prefs: SharedPreferences, mood: Mood): Set<String> =
        prefs.getStringSet(key(mood), emptySet())?.toSet().orEmpty()

    /** Idempotent add so repeated taps cannot accidentally remove a track. */
    fun add(prefs: SharedPreferences, mood: Mood, songId: String): Set<String> {
        if (songId.isBlank()) return tracks(prefs, mood)
        val next = tracks(prefs, mood).toMutableSet()
        if (next.add(songId)) prefs.edit().putStringSet(key(mood), next).apply()
        return next.toSet()
    }

    fun toggle(prefs: SharedPreferences, mood: Mood, songId: String): Set<String> {
        val next = tracks(prefs, mood).toMutableSet()
        if (!next.add(songId)) next.remove(songId)
        prefs.edit().putStringSet(key(mood), next).apply()
        return next.toSet()
    }
}
