package it.vfsfitvnm.vimusic.updater

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

internal class GitHubReleaseClient(
    private val apiBaseUrl: String = DEFAULT_LATEST_ENDPOINT
) {
    private val client = HttpClient(OkHttp) {
        BrowserUserAgent()
        expectSuccess = false
    }

    suspend fun fetchLatest(): WestRelease = withContext(Dispatchers.IO) {
        val response = client.get(apiBaseUrl)
        when (response.status) {
            HttpStatusCode.Forbidden, HttpStatusCode.TooManyRequests ->
                throw GitHubApiRateLimitException(response.status.value)
            HttpStatusCode.OK -> Unit
            else -> throw IOException("GitHub API returned HTTP ${response.status.value}")
        }
        parseRelease(response.bodyAsText())
    }

    suspend fun streamApk(
        url: String,
        expectedSize: Long,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit,
        writeChunk: suspend (buffer: ByteArray, length: Int) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            throw IOException("APK download failed: HTTP ${response.status.value}")
        }
        val channel = response.bodyAsChannel()
        val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: expectedSize
        val buffer = ByteArray(BUFFER_SIZE)
        var downloaded = 0L
        while (true) {
            ensureActive()
            val read = channel.readAvailable(buffer)
            if (read == -1) break
            writeChunk(buffer, read)
            downloaded += read
            onProgress(downloaded, total)
        }
        if (total > 0L && downloaded != total) {
            throw IOException("APK size mismatch: downloaded $downloaded / expected $total bytes")
        }
        downloaded
    }

    fun close() {
        client.close()
    }

    private fun parseRelease(text: String): WestRelease {
        val root = JSONObject(text)
        val tagName = root.optString("tag_name")
        val version = tagName.trimStart('v', 'V')
        val name = root.optString("name").ifBlank { tagName }
        val body = root.optString("body").orEmpty()
        val publishedAt = root.optString("published_at").takeIf { it.isNotBlank() }
        val assets = root.optJSONArray("assets")
            ?: throw IOException("Latest release has no assets")
        var apkUrl: String? = null
        var apkSize = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val contentType = asset.optString("content_type")
            val downloadUrl = asset.optString("browser_download_url")
            val size = asset.optLong("size", 0L)
            val isApk = contentType.equals(APK_MIME, ignoreCase = true) ||
                downloadUrl.endsWith(".apk", ignoreCase = true)
            if (!isApk) continue
            apkUrl = downloadUrl
            apkSize = size
            break
        }
        if (apkUrl.isNullOrBlank()) {
            throw IOException("No APK asset found in latest release")
        }
        return WestRelease(
            version = version,
            rawTagName = tagName,
            title = name,
            body = body,
            apkUrl = apkUrl,
            apkSize = apkSize,
            publishedAt = publishedAt
        )
    }

    private companion object {
        const val DEFAULT_LATEST_ENDPOINT = "https://api.github.com/repos/iaaryans/WEST/releases/latest"
        const val BUFFER_SIZE = 64 * 1024
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}

class GitHubApiRateLimitException(val httpCode: Int) :
    IOException("GitHub API rate limit hit (HTTP $httpCode)")
