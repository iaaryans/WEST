package it.vfsfitvnm.vimusic.updater

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class AppUpdateRepository {
    private val httpClient = GitHubReleaseClient()

    suspend fun fetchLatestRelease(): WestRelease = httpClient.fetchLatest()

    suspend fun downloadApk(
        context: Context,
        release: WestRelease,
        onProgress: suspend (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = updatesDir(context)
        if (!updatesDir.exists()) updatesDir.mkdirs()
        cleanupExcept(updatesDir, exclude = null)
        val tmpFile = File(updatesDir, "WEST-${release.version}.apk.tmp")
        val finalFile = File(updatesDir, "WEST-${release.version}.apk")
        try {
            FileOutputStream(tmpFile).use { output ->
                val written = httpClient.streamApk(
                    url = release.apkUrl,
                    expectedSize = release.apkSize,
                    onProgress = { downloaded, total ->
                        val percent = if (total > 0L) {
                            ((downloaded * 100) / total).toInt()
                        } else 0
                        onProgress(percent, downloaded, total)
                    },
                    writeChunk = { buffer, length -> output.write(buffer, 0, length) }
                )
                output.flush()
                if (release.apkSize > 0L && written != release.apkSize) {
                    throw IOException(
                        "Downloaded $written bytes, expected ${release.apkSize}"
                    )
                }
            }
            if (finalFile.exists()) finalFile.delete()
            if (!tmpFile.renameTo(finalFile)) {
                tmpFile.copyTo(finalFile, overwrite = true)
                tmpFile.delete()
            }
            validateDownloadedApk(context, release, finalFile)
            finalFile
        } catch (t: Throwable) {
            tmpFile.delete()
            finalFile.delete()
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun validateDownloadedApk(context: Context, release: WestRelease, file: File) {
        val pm = context.packageManager
        val installed = pm.getPackageInfo(context.packageName, 0)
        val archive = pm.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IOException("Downloaded file is not a valid Android APK")
        if (archive.packageName != context.packageName) {
            throw IOException("Downloaded APK belongs to a different application")
        }
        WestVersion.validateApkVersion(
            installedName = installed.versionName ?: throw IOException("Installed version is unavailable"),
            installedCode = PackageInfoCompat.getLongVersionCode(installed),
            releaseName = release.version,
            apkName = archive.versionName,
            apkCode = PackageInfoCompat.getLongVersionCode(archive)
        )
    }

    fun updatesDir(context: Context): File =
        File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }

    fun cleanupExcept(dir: File, exclude: File?) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (exclude != null && file.absolutePath == exclude.absolutePath) return@forEach
            if (file.name.endsWith(".apk") || file.name.endsWith(".apk.tmp")) {
                file.delete()
            }
        }
    }
}
