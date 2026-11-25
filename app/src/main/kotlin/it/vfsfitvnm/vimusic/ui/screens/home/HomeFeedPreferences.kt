package it.vfsfitvnm.vimusic.ui.screens.home

import android.content.SharedPreferences
import androidx.compose.runtime.*
import it.vfsfitvnm.vimusic.intelligence.IntelligencePreferences

internal data class HomeDiscoveryPreferences(val enabled: Boolean, val muted: Set<String>)

@Composable
internal fun rememberHomeDiscoveryPreferences(prefs: SharedPreferences): HomeDiscoveryPreferences {
    fun read() = HomeDiscoveryPreferences(IntelligencePreferences.enabled(prefs), IntelligencePreferences.less(prefs))
    var settings by remember(prefs) { mutableStateOf(read()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("westIntelligence") == true) settings = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}
