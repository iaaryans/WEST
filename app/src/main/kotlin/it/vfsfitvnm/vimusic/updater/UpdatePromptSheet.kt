package it.vfsfitvnm.vimusic.updater

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.vfsfitvnm.vimusic.ui.components.themed.DialogTextButton
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.medium
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import java.util.Locale

@Composable
fun UpdatePromptSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val (colorPalette, typography) = LocalAppearance.current
    val state by AppUpdateManager.state.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .padding(all = 28.dp)
                .background(
                    color = colorPalette.background1,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .fillMaxWidth()
        ) {
            when (val s = state) {
                is UpdateUiState.UpdateAvailable -> AvailableContent(
                    release = s.release,
                    onUpdate = {
                        IntelligenceUpdateBridge.checkpointForUpdate(context)
                        AppUpdateManager.startDownload(context, s.release)
                    },
                    onLater = onDismiss
                )
                is UpdateUiState.Downloading -> DownloadingContent(
                    progressPercent = s.progressPercent,
                    downloadedBytes = s.downloadedBytes,
                    totalBytes = s.totalBytes,
                    onCancel = { AppUpdateManager.cancelDownload(context) }
                )
                is UpdateUiState.ReadyToInstall -> ReadyContent(
                    release = s.release,
                    onInstall = {
                        IntelligenceUpdateBridge.checkpointForUpdate(context)
                        AppUpdateManager.launchInstall(context)
                    },
                    onLater = onDismiss
                )
                is UpdateUiState.Installing -> InstallingContent()
                is UpdateUiState.UpToDate -> UpToDateContent(onDismiss)
                is UpdateUiState.Error -> ErrorContent(
                    message = s.message,
                    onDismiss = onDismiss
                )
                UpdateUiState.Checking, UpdateUiState.Idle -> CheckingContent()
            }
        }
    }
}

@Composable
private fun AvailableContent(
    release: WestRelease,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    val (_, typography) = LocalAppearance.current
    BasicText(
        text = "WEST ${release.rawTagName}",
        style = typography.s.semiBold
    )
    Spacer(Modifier.height(4.dp))
    BasicText(
        text = "Update available - ${formatMb(release.apkSize)} MB",
        style = typography.xxs.secondary
    )
    Spacer(Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .verticalScroll(rememberScrollState())
    ) {
        BasicText(
            text = release.body.ifBlank { "Release notes are available on GitHub." },
            style = typography.xxs.medium
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        DialogTextButton(text = "Later", onClick = onLater)
        DialogTextButton(text = "Update Now", primary = true, onClick = onUpdate)
    }
}

@Composable
private fun DownloadingContent(
    progressPercent: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    BasicText(
        text = "Downloading update...",
        style = typography.s.semiBold
    )
    Spacer(Modifier.height(8.dp))
    BasicText(
        text = "$progressPercent% - ${formatMb(downloadedBytes)} / ${formatMb(totalBytes)} MB",
        style = typography.xxs.secondary
    )
    Spacer(Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(colorPalette.background0, RoundedCornerShape(3.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progressPercent.coerceIn(0, 100) / 100f)
                .height(6.dp)
                .background(colorPalette.accent, RoundedCornerShape(3.dp))
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        DialogTextButton(text = "Cancel", onClick = onCancel)
    }
}

@Composable
private fun ReadyContent(
    release: WestRelease,
    onInstall: () -> Unit,
    onLater: () -> Unit
) {
    val (_, typography) = LocalAppearance.current
    BasicText(
        text = "Ready to install ${release.rawTagName}",
        style = typography.s.semiBold
    )
    Spacer(Modifier.height(8.dp))
    BasicText(
        text = "WEST will close briefly to apply the update. Your queue, history and WEST Intelligence state are preserved.",
        style = typography.xxs.secondary
    )
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        DialogTextButton(text = "Later", onClick = onLater)
        DialogTextButton(text = "Install", primary = true, onClick = onInstall)
    }
}

@Composable
private fun InstallingContent() {
    val (_, typography) = LocalAppearance.current
    BasicText(
        text = "Installing update...",
        style = typography.s.semiBold
    )
    Spacer(Modifier.height(8.dp))
    BasicText(
        text = "Follow the system installer prompt to complete the update.",
        style = typography.xxs.secondary
    )
}

@Composable
private fun UpToDateContent(onDismiss: () -> Unit) {
    val (_, typography) = LocalAppearance.current
    BasicText(
        text = "WEST is up to date",
        style = typography.s.semiBold
    )
    Spacer(Modifier.height(8.dp))
    BasicText(
        text = "You are running the latest release.",
        style = typography.xxs.secondary
    )
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        DialogTextButton(text = "Close", primary = true, onClick = onDismiss)
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit
) {
    val (_, typography) = LocalAppearance.current
    BasicText(
        text = "Could not check for updates",
        style = typography.s.semiBold
    )
    Spacer(Modifier.height(8.dp))
    BasicText(
        text = message,
        style = typography.xxs.secondary,
        overflow = TextOverflow.Ellipsis,
        maxLines = 4
    )
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        DialogTextButton(text = "Close", primary = true, onClick = onDismiss)
    }
}

@Composable
private fun CheckingContent() {
    val (_, typography) = LocalAppearance.current
    BasicText(
        text = "Checking for updates...",
        style = typography.s.semiBold
    )
    Spacer(Modifier.height(8.dp))
    BasicText(
        text = "Contacting GitHub for the latest WEST release.",
        style = typography.xxs.secondary
    )
}

private fun formatMb(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f", mb)
}
