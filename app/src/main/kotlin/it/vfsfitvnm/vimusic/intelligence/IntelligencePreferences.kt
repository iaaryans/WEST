package it.vfsfitvnm.vimusic.intelligence

import android.content.SharedPreferences
import androidx.core.content.edit

/** Preferences are kept outside the Room schema to preserve upgrade compatibility. */
internal object IntelligencePreferences {
    const val ENABLED = "westIntelligenceEnabled"
    const val DISCOVERY = "westIntelligenceDiscovery"
    private const val MORE = "westIntelligenceMoreIds"
    private const val LESS = "westIntelligenceLessIds"

    fun enabled(prefs: SharedPreferences) = prefs.getBoolean(ENABLED, true)
    fun discovery(prefs: SharedPreferences) = prefs.getInt(DISCOVERY, 35).coerceIn(0, 100)
    fun more(prefs: SharedPreferences): Set<String> = prefs.getStringSet(MORE, emptySet()).orEmpty().toSet()
    fun less(prefs: SharedPreferences): Set<String> = prefs.getStringSet(LESS, emptySet()).orEmpty().toSet()

    fun favor(prefs: SharedPreferences, id: String) {
        prefs.edit {
            putStringSet(MORE, more(prefs) + id)
            putStringSet(LESS, less(prefs) - id)
        }
    }

    fun suppress(prefs: SharedPreferences, id: String) {
        prefs.edit {
            putStringSet(LESS, less(prefs) + id)
            putStringSet(MORE, more(prefs) - id)
        }
    }

    fun reset(prefs: SharedPreferences) {
        prefs.edit {
            remove(MORE)
            remove(LESS)
            remove(DISCOVERY)
        }
    }
}
