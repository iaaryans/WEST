package it.vfsfitvnm.vimusic.ui.screens.settings

import android.text.format.DateUtils
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import it.vfsfitvnm.vimusic.BuildConfig
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.updater.AppUpdateManager
import it.vfsfitvnm.vimusic.updater.UpdateUiState
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.preferences
import it.vfsfitvnm.vimusic.utils.secondary

@ExperimentalAnimationApi
@Composable
fun About() {
    val (colorPalette, typography) = LocalAppearance.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val prefs = remember { context.preferences }

    val updateState by AppUpdateManager.state.collectAsState()

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
        Header(title = "About") {
            BasicText(
                text = "v${BuildConfig.VERSION_NAME} by Aaryan & Adarsh",
                style = typography.s.secondary
            )
        }

        SettingsEntryGroupText(title = "UPDATES")

        SettingsEntry(
            title = "Check for updates",
            text = updateStatusText(updateState, prefs),
            onClick = {
                AppUpdateManager.checkNow(context)
            }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = "DEVELOPER")

        SettingsEntry(
            title = "Aaryan",
            text = "Lead Developer & Maintainer",
            onClick = {
                uriHandler.openUri("https://github.com/iaaryans/")
            }
        )

        SettingsEntry(
            title = "Adarsh",
            text = "Developer, Designer & Maintainer",
            onClick = {
                uriHandler.openUri("https://github.com/adarsh01-cyber")
            }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = "SOCIAL")

        SettingsEntry(
            title = "GitHub — Aaryan",
            text = "https://github.com/iaaryans",
            onClick = {
                uriHandler.openUri("https://github.com/iaaryans/")
            }
        )

        SettingsEntry(
            title = "GitHub — Adarsh",
            text = "https://github.com/adarsh01-cyber",
            onClick = {
                uriHandler.openUri("https://github.com/adarsh01-cyber")
            }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = "TROUBLESHOOTING")

        SettingsEntry(
            title = "Report an issue",
            text = "You will be redirected to GitHub",
            onClick = {
                uriHandler.openUri("https://github.com/iaaryans/")
            }
        )

        SettingsEntry(
            title = "Request a feature or suggest an idea",
            text = "You will be redirected to GitHub",
            onClick = {
                uriHandler.openUri("https://github.com/iaaryans/")
            }
        )
    }
}

private fun updateStatusText(
    state: UpdateUiState,
    prefs: android.content.SharedPreferences
): String {
    val version = "v${BuildConfig.VERSION_NAME}"
    val suffix = when (state) {
        is UpdateUiState.UpdateAvailable -> "Update available"
        is UpdateUiState.Checking -> "Checking..."
        is UpdateUiState.Downloading -> "Downloading ${state.progressPercent}%"
        is UpdateUiState.ReadyToInstall -> "Ready to install"
        is UpdateUiState.Installing -> "Installing..."
        is UpdateUiState.UpToDate -> "WEST is up to date"
        is UpdateUiState.Error -> "Last check failed"
        UpdateUiState.Idle -> {
            val last = prefs.getLong(AppUpdateManager.PREF_LAST_CHECK_MS, 0L)
            if (last == 0L) {
                "Tap to check GitHub"
            } else {
                "Checked " + DateUtils.getRelativeTimeSpanString(last)
            }
        }
    }
    return "$version - $suffix"
}
