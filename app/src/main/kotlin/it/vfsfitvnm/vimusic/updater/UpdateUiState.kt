package it.vfsfitvnm.vimusic.updater

import java.io.File

data class WestRelease(
    val version: String,
    val rawTagName: String,
    val title: String,
    val body: String,
    val apkUrl: String,
    val apkSize: Long,
    val publishedAt: String?
)

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class UpdateAvailable(val release: WestRelease) : UpdateUiState()
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState()
    data class ReadyToInstall(val release: WestRelease, val apkFile: File) : UpdateUiState()
    object Installing : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
