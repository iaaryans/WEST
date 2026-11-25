package it.vfsfitvnm.vimusic.updater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.vfsfitvnm.vimusic.BuildConfig
import it.vfsfitvnm.vimusic.MainActivity
import it.vfsfitvnm.vimusic.utils.preferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class UpdateBackgroundWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = context.preferences
        if (!prefs.getBoolean(AppUpdateManager.PREF_AUTO_CHECK, true)) return Result.success()
        val client = GitHubReleaseClient()
        return try {
            val release = client.fetchLatest()
            if (WestVersion.isNewer(BuildConfig.VERSION_NAME, release.version) &&
                prefs.getBoolean(AppUpdateManager.PREF_AUTO_CHECK, true) &&
                prefs.getString(PREF_NOTIFIED_TAG, null) != release.rawTagName &&
                notifyRelease(context, release)
            ) {
                prefs.edit { putString(PREF_NOTIFIED_TAG, release.rawTagName) }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: GitHubApiRateLimitException) {
            Result.success()
        } catch (e: IllegalArgumentException) {
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        } finally {
            client.close()
        }
    }

    private fun notifyRelease(context: Context, release: WestRelease): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return false

        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "WEST updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("WEST ${release.rawTagName} is available")
            .setContentText("Tap to open WEST and install the update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            notifications.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            return false
        }
        return true
    }

    companion object {
        private const val WORK_NAME = "west-periodic-release-check"
        private const val CHANNEL = "west_release_updates"
        private const val NOTIFICATION_ID = 701
        private const val PREF_NOTIFIED_TAG = "westUpdateLastNotifiedTag"

        fun schedule(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateBackgroundWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
