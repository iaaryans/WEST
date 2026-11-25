package it.vfsfitvnm.vimusic.updater

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import it.vfsfitvnm.vimusic.BuildConfig
import it.vfsfitvnm.vimusic.intelligence.IntelligencePreferences
import it.vfsfitvnm.vimusic.listening.tracker.ListeningTracker
import it.vfsfitvnm.vimusic.personalization.TasteProfileStore
import it.vfsfitvnm.vimusic.ui.performance.MusicBrowseCache
import it.vfsfitvnm.vimusic.utils.preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IntelligenceUpdateBridge {
    const val PREF_LAST_INSTALLED_VERSION = "westUpdateLastInstalledVersion"
    const val PREF_PRE_UPDATE_TIMESTAMP = "westUpdatePreUpdateTimestamp"

    fun checkpointForUpdate(context: Context) {
        val prefs = context.preferences
        ListeningTracker.active?.checkpointNow()
        prefs.edit {
            putString(PREF_LAST_INSTALLED_VERSION, BuildConfig.VERSION_NAME)
            putLong(PREF_PRE_UPDATE_TIMESTAMP, System.currentTimeMillis())
        }
    }

    suspend fun applyPostUpdateSync(context: Context): UpgradeResult? =
        withContext(Dispatchers.Default) {
            val prefs = context.preferences
            val current = BuildConfig.VERSION_NAME
            val last = prefs.getString(PREF_LAST_INSTALLED_VERSION, null)
            if (last == null) {
                prefs.edit { putString(PREF_LAST_INSTALLED_VERSION, current) }
                return@withContext null
            }
            if (last == current) return@withContext null
            rebindIntelligence(prefs)
            invalidateBrowseCache()
            prefs.edit { putString(PREF_LAST_INSTALLED_VERSION, current) }
            UpgradeResult(previousVersion = last, newVersion = current)
        }

    private fun rebindIntelligence(prefs: SharedPreferences) {
        val taste = TasteProfileStore.load(prefs)
        TasteProfileStore.save(prefs, taste)
        val discovery = IntelligencePreferences.discovery(prefs)
        prefs.edit { putInt(IntelligencePreferences.DISCOVERY, discovery) }
    }

    private fun invalidateBrowseCache() {
        MusicBrowseCache.invalidateAll()
    }
}

data class UpgradeResult(
    val previousVersion: String,
    val newVersion: String
)
