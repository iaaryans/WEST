package it.vfsfitvnm.vimusic.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import it.vfsfitvnm.vimusic.BuildConfig
import it.vfsfitvnm.vimusic.utils.preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
object AppUpdateManager {
    const val PREF_LAST_CHECK_MS = "westUpdateLastCheckMs"
    const val PREF_AUTO_CHECK = "westUpdateAutoCheck"

    const val FOREGROUND_POLL_MS = 120_000L
    private const val MIN_AUTO_CHECK_GAP_MS = 15_000L
    private const val RATE_LIMIT_BACKOFF_MS = 15 * 60_000L

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    private val repository = AppUpdateRepository()

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    private val _promptVisible = MutableStateFlow(false)
    val promptVisible: StateFlow<Boolean> = _promptVisible.asStateFlow()
    @Volatile private var dismissedTag: String? = null
    @Volatile private var lastAutoCheck = -MIN_AUTO_CHECK_GAP_MS
    @Volatile private var rateLimitedUntil = 0L
    @Volatile private var awaitingInstallPermission = false

    @Volatile private var pendingInstallApk: File? = null
    @Volatile private var pendingInstallRelease: WestRelease? = null
    @Volatile private var downloadJob: Job? = null

    fun maybeAutoCheck(context: Context) {
        if (!context.preferences.getBoolean(PREF_AUTO_CHECK, true)) return
        checkNow(context, manual = false)
    }

    @Synchronized
    fun checkNow(context: Context, manual: Boolean = true) {
        val app = context.applicationContext
        if (manual) _promptVisible.value = true
        val previous = _state.value
        if (previous is UpdateUiState.Checking ||
            previous is UpdateUiState.Downloading ||
            previous is UpdateUiState.ReadyToInstall ||
            previous is UpdateUiState.Installing
        ) return

        val now = SystemClock.elapsedRealtime()
        if (!manual && (_promptVisible.value || now < rateLimitedUntil ||
            now - lastAutoCheck < MIN_AUTO_CHECK_GAP_MS)
        ) return
        lastAutoCheck = now
        _state.value = UpdateUiState.Checking
        scope.launch {
            try {
                val release = repository.fetchLatestRelease()
                if (WestVersion.isNewer(BuildConfig.VERSION_NAME, release.version)) {
                    _state.value = UpdateUiState.UpdateAvailable(release)
                    if (app.preferences.getBoolean(PREF_AUTO_CHECK, true) &&
                        dismissedTag != release.rawTagName
                    ) {
                        _promptVisible.value = true
                    }
                } else {
                    _state.value = UpdateUiState.UpToDate
                }
                app.preferences.edit {
                    putLong(PREF_LAST_CHECK_MS, System.currentTimeMillis())
                }
                rateLimitedUntil = 0L
            } catch (t: CancellationException) {
                _state.value = UpdateUiState.Idle
                throw t
            } catch (t: Throwable) {
                if (t is GitHubApiRateLimitException) {
                    rateLimitedUntil = SystemClock.elapsedRealtime() + RATE_LIMIT_BACKOFF_MS
                }
                _state.value = UpdateUiState.Error(
                    friendlyErrorMessage(t) ?: "Update check failed"
                )
            }
        }
    }

    fun dismissPrompt() {
        (_state.value as? UpdateUiState.UpdateAvailable)?.let {
            dismissedTag = it.release.rawTagName
        }
        _promptVisible.value = false
    }

    fun startDownload(context: Context, release: WestRelease) {
        if (_state.value is UpdateUiState.Downloading) return
        _state.value = UpdateUiState.Downloading(0, 0L, release.apkSize)
        downloadJob = scope.launch {
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
        _promptVisible.value = false
        downloadJob?.cancel()
        downloadJob = null
    }

    fun launchInstall(context: Context) {
        val apk = pendingInstallApk ?: return
        pendingInstallRelease ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                awaitingInstallPermission = true
                context.startActivity(intent)
            } catch (_: Throwable) {
                awaitingInstallPermission = false
                _state.value = UpdateUiState.Error(
                    "Cannot open install permission settings"
                )
            }
            return
        }
        fireInstallIntent(context, apk)
    }

    fun maybeResumeInstall(context: Context) {
        if (_state.value is UpdateUiState.Installing) {
            _state.value = UpdateUiState.Idle
            _promptVisible.value = false
        }
        if (!awaitingInstallPermission) return
        awaitingInstallPermission = false
        val apk = pendingInstallApk ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) return
        fireInstallIntent(context, apk)
    }

    fun dismiss() = dismissPrompt()

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

    private fun friendlyErrorMessage(t: Throwable): String? = when (t) {
        is GitHubApiRateLimitException ->
            "GitHub rate limit reached. Try again in a few minutes."
        is java.net.UnknownHostException ->
            "No internet connection. WEST will retry on the next launch."
        is java.net.SocketTimeoutException ->
            "Update check timed out. Try again later."
        else -> t.message
    }

}
