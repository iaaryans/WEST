package it.vfsfitvnm.vimusic.ui.screens.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.intelligence.IntelligencePreferences
import it.vfsfitvnm.vimusic.personalization.TasteProfileStore
import it.vfsfitvnm.vimusic.utils.preferences
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.service.PlayerMediaBrowserService
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.updater.AppUpdateManager
import it.vfsfitvnm.vimusic.utils.isAtLeastAndroid12
import it.vfsfitvnm.vimusic.utils.isAtLeastAndroid6
import it.vfsfitvnm.vimusic.utils.isIgnoringBatteryOptimizations
import it.vfsfitvnm.vimusic.utils.isInvincibilityEnabledKey
import it.vfsfitvnm.vimusic.utils.pauseSearchHistoryKey
import it.vfsfitvnm.vimusic.utils.rememberPreference
import it.vfsfitvnm.vimusic.utils.toast
import kotlinx.coroutines.flow.distinctUntilChanged

@SuppressLint("BatteryLife")
@ExperimentalAnimationApi
@Composable
fun OtherSettings() {
    val context = LocalContext.current
    val (colorPalette) = LocalAppearance.current

    var isAndroidAutoEnabled by remember {
        val component = ComponentName(context, PlayerMediaBrowserService::class.java)
        val disabledFlag = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val enabledFlag = PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        mutableStateOf(
            value = context.packageManager.getComponentEnabledSetting(component) == enabledFlag,
            policy = object : SnapshotMutationPolicy<Boolean> {
                override fun equivalent(a: Boolean, b: Boolean): Boolean {
                    context.packageManager.setComponentEnabledSetting(
                        component,
                        if (b) enabledFlag else disabledFlag,
                        PackageManager.DONT_KILL_APP
                    )
                    return a == b
                }
            }
        )
    }

    var isInvincibilityEnabled by rememberPreference(isInvincibilityEnabledKey, false)

    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(context.isIgnoringBatteryOptimizations)
    }

    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations
        }

    Column(
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    .asPaddingValues()
            )
    ) {
        Header(title = "Devices & background")

        SettingsEntryGroupText(title = "ANDROID AUTO")

        SettingsDescription(text = "Remember to enable \"Unknown sources\" in the Developer Settings of Android Auto.")

        SwitchSettingEntry(
            title = "Android Auto",
            text = "Enable Android Auto support",
            isChecked = isAndroidAutoEnabled,
            onCheckedChange = { isAndroidAutoEnabled = it }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = "SERVICE LIFETIME")

        ImportantSettingsDescription(text = "If battery optimizations are applied, the playback notification can suddenly disappear when paused.")

        if (isAtLeastAndroid12) {
            SettingsDescription(text = "Since Android 12, disabling battery optimizations is required for the \"Invincible service\" option to take effect.")
        }

        SettingsEntry(
            title = "Ignore battery optimizations",
            isEnabled = !isIgnoringBatteryOptimizations,
            text = if (isIgnoringBatteryOptimizations) {
                "Already unrestricted"
            } else {
                "Disable background restrictions"
            },
            onClick = {
                if (!isAtLeastAndroid6) return@SettingsEntry

                try {
                    activityResultLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                } catch (e: ActivityNotFoundException) {
                    try {
                        activityResultLauncher.launch(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    } catch (e: ActivityNotFoundException) {
                        context.toast("Couldn't find battery optimization settings, please whitelist ViMusic manually")
                    }
                }
            }
        )

        SwitchSettingEntry(
            title = "Invincible service",
            text = "When turning off battery optimizations is not enough",
            isChecked = isInvincibilityEnabled,
            onCheckedChange = { isInvincibilityEnabled = it }
        )
    }
}

@ExperimentalAnimationApi
@Composable
fun PrivacySettings() {
    val prefs = LocalContext.current.preferences
    var intelligenceEnabled by rememberPreference(IntelligencePreferences.ENABLED, true)
    var intelligenceDiscovery by rememberPreference(IntelligencePreferences.DISCOVERY, 35)
    var pauseSearchHistory by rememberPreference(pauseSearchHistoryKey, false)
    var autoCheckUpdates by rememberPreference(AppUpdateManager.PREF_AUTO_CHECK, true)

    val queriesCount by remember {
        Database.queriesCount().distinctUntilChanged()
    }.collectAsState(initial = 0)

    val eventsCount by remember {
        Database.eventsCount().distinctUntilChanged()
    }.collectAsState(initial = 0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(title = "Privacy & history")
        SettingsEntryGroupText(title = "SEARCH HISTORY")

        SwitchSettingEntry(
            title = "Pause search history",
            text = "Neither save new searched queries nor show history",
            isChecked = pauseSearchHistory,
            onCheckedChange = { pauseSearchHistory = it }
        )

        SettingsEntry(
            title = "Clear search history",
            text = if (queriesCount > 0) {
                "Delete $queriesCount search queries"
            } else {
                "History is empty"
            },
            isEnabled = queriesCount > 0,
            onClick = { query(Database::clearQueries) }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = "WEST INTELLIGENCE")

        SwitchSettingEntry(
            title = "Personalized recommendations",
            text = "Use favorites and listening history to make local music recommendations",
            isChecked = intelligenceEnabled,
            onCheckedChange = { intelligenceEnabled = it }
        )

        ValueSelectorSettingsEntry(
            title = "New music discovery",
            selectedValue = intelligenceDiscovery,
            values = listOf(0, 25, 35, 50, 75, 100),
            onValueSelected = { intelligenceDiscovery = it },
            valueText = { "$it% exploration" }
        )

        SettingsEntry(
            title = "Edit music taste & favorite artists",
            text = "Change your languages, artists, songs and Moodspace preferences",
            onClick = { TasteProfileStore.requestEdit(prefs) }
        )

        SettingsEntry(
            title = "Reset music taste profile",
            text = "Clear onboarding choices without clearing listening history or favorites",
            onClick = { TasteProfileStore.reset(prefs) }
        )

        SettingsEntry(
            title = "Reset WEST Intelligence feedback",
            text = "Clear More like this / Less like this choices; keep your favorites and history",
            onClick = {
                IntelligencePreferences.reset(prefs)
                intelligenceDiscovery = 35
            }
        )

        SettingsGroupSpacer()
        SettingsEntryGroupText(title = "UPDATES")

        SwitchSettingEntry(
            title = "Auto-check for updates on launch",
            text = "Check GitHub for new WEST releases at most once every 6 hours",
            isChecked = autoCheckUpdates,
            onCheckedChange = { autoCheckUpdates = it }
        )

        SettingsGroupSpacer()
        SettingsEntryGroupText(title = "EXISTING QUICK PICKS")

        SettingsEntry(
            title = "Reset quick picks",
            text = if (eventsCount > 0) {
                "Delete $eventsCount playback events"
            } else {
                "Quick picks are cleared"
            },
            isEnabled = eventsCount > 0,
            onClick = { query(Database::clearEvents) }
        )

        SettingsGroupSpacer()

    }
}
