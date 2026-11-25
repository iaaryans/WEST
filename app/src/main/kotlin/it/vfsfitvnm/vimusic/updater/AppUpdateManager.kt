package it.vfsfitvnm.vimusic.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import it.vfsfitvnm.vimusic.BuildConfig
import it.vfsfitvnm.vimusic.utils.preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

object AppUpdateManager {
    const val PREF_LAST_CHECK_MS = "westUpdateLastCheckMs"
    const val PREF_AUTO_CHECK = "westUpdateAutoCheck"

    private const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    private val repository = AppUpdateRepository()

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    @Volatile private var pendingInstallApk: File? = null
    @Volatile private var pendingInstallRelease: WestRelease? = null
    @Volatile private var downloadJob: Job? = null

    fun maybeAutoCheck(context: Context) {
        val prefs = context.preferences
        if (!prefs.getBoolean(PREF_AUTO_CHECK, true)) return
        val now = System.currentTimeMillis()
        val last = prefs.getLong(PREF_LAST_CHECK_MS, 0L)
        if (now - last < AUTO_CHECK_INTERVAL_MS) return
        checkNow(context)
    }

    fun checkNow(context: Context) {
        if (_state.value is UpdateUiState.Checking) return
        _state.value = UpdateUiState.Checking
        scope.launch {
            try {
                val release = repository.fetchLatestRelease()
                if (isUpdateAvailable(BuildConfig.VERSION_NAME, release.version)) {
                    _state.value = UpdateUiState.UpdateAvailable(release)
                } else {
                    _state.value = UpdateUiState.UpToDate
                }
                context.preferences.edit {
                    putLong(PREF_LAST_CHECK_MS, System.currentTimeMillis())
                }
            } catch (t: Throwable) {
                _state.value = UpdateUiState.Error(
                    friendlyErrorMessage(t) ?: "Update check failed"
                )
            }
        }
    }

    fun startDownload(context: Context, release: WestRelease) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _state.value = UpdateUiState.Downloading(0, 0L, release.apkSize)
            try {
                val file = repository.downloadApk(context, release) { percent, downloaded, total ->
                    _state.value = UpdateUiState.Downloading(percent, downloaded, total)
                }
                pendingInstallApk = file
                pendingInstallRelease = release
                _state.value = UpdateUiState.ReadyToInstall(release, file)
            } catch (t: CancellationException) {
                cleanupStaleApks(context)
                _state.value = UpdateUiState.Idle
                throw t
            } catch (t: Throwable) {
                cleanupStaleApks(context)
                _state.value = UpdateUiState.Error(
                    friendlyErrorMessage(t) ?: "Download failed"
                )
            }
        }
    }

    fun cancelDownload(context: Context) {
        downloadJob?.cancel()
        downloadJob = null
        cleanupStaleApks(context)
        _state.value = UpdateUiState.Idle
    }

    fun launchInstall(context: Context) {
        val apk = pendingInstallApk ?: return
        val release = pendingInstallRelease ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Throwable) {
                _state.value = UpdateUiState.Error(
                    "Cannot open install permission settings"
                )
            }
            return
        }
        fireInstallIntent(context, apk)
    }

    fun maybeResumeInstall(context: Context) {
        val apk = pendingInstallApk ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) return
        fireInstallIntent(context, apk)
    }

    fun dismiss() {
        _state.value = UpdateUiState.Idle
    }

    fun cleanupStaleApks(context: Context) {
        val dir = repository.updatesDir(context)
        repository.cleanupExcept(dir, exclude = pendingInstallApk)
    }

    private fun fireInstallIntent(context: Context, apk: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            _state.value = UpdateUiState.Installing
            context.startActivity(intent)
            pendingInstallApk = null
            pendingInstallRelease = null
        } catch (t: Throwable) {
            _state.value = UpdateUiState.Error(
                "Cannot start installer: ${t.message ?: t.javaClass.simpleName}"
            )
        }
    }

    private fun isUpdateAvailable(currentVersion: String, remoteVersion: String): Boolean {
        val current = parseSemver(currentVersion) ?: return false
        val remote = parseSemver(remoteVersion) ?: return false
        return remote > current
    }

    private fun parseSemver(value: String): Semver? {
        val cleaned = value.trim().trimStart('v', 'V')
        val parts = cleaned.split('.')
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        return Semver(major, minor, patch)
    }

    private fun friendlyErrorMessage(t: Throwable): String? = when (t) {
        is GitHubApiRateLimitException ->
            "GitHub rate limit reached. Try again in a few minutes."
        is java.net.UnknownHostException ->
            "No internet connection. WEST will retry on the next launch."
        is java.net.SocketTimeoutException ->
            "Update check timed out. Try again later."
        else -> t.message
    }

    private data class Semver(
        val major: Int,
        val minor: Int,
        val patch: Int
    ) : Comparable<Semver> {
        override fun compareTo(other: Semver): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            return patch.compareTo(other.patch)
        }
    }
}
