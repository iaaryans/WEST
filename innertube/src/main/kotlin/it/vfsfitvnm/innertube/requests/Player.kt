package it.vfsfitvnm.innertube.requests

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}

private fun executePlayerRequest(videoId: String, visitorData: String?): PlayerResponse? {
    return try {
        val payload = """
        {
            "videoId": "$videoId",
            "playbackContext": {
                "contentPlaybackContext": {
                    "html5Preference": "HTML5_PREF_WANTS",
                    "signatureTimestamp": 20717
                }
            },
            "contentCheckOk": true,
            "racyCheckOk": true,
            "context": {
                "client": {
                    "clientName": "VISIONOS",
                    "clientVersion": "1.02",
                    "deviceMake": "Apple",
                    "deviceModel": "RealityDevice17,1",
                    "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                    "osName": "visionOS",
                    "osVersion": "26.5.23O471",
                    "visitorData": "${visitorData ?: ""}",
                    "hl": "en",
                    "gl": "US"
                }
            }
        }
        """.trimIndent()

        val conn = (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-YouTube-Client-Name", "101")
            setRequestProperty("X-YouTube-Client-Version", "1.02")
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
            if (!visitorData.isNullOrBlank()) {
                setRequestProperty("X-Goog-Visitor-Id", visitorData)
            }
            setRequestProperty("X-Goog-FieldMask", "playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId")
        }

        conn.outputStream.use { os ->
            os.write(payload.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        val responseText = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }

        println("[PlayerResolver] HTTP $code from YouTube player for $videoId: ${responseText.take(200)}")
        json.decodeFromString<PlayerResponse>(responseText)
    } catch (e: Exception) {
        println("[PlayerResolver] Error fetching player for $videoId: $e")
        null
    }
}

private fun executeAndroidVrRequest(videoId: String): PlayerResponse? {
    return try {
        val payload = """
        {
            "videoId": "$videoId",
            "context": {
                "client": {
                    "clientName": "ANDROID_VR",
                    "clientVersion": "1.60.19",
                    "hl": "en",
                    "gl": "US"
                }
            }
        }
        """.trimIndent()

        val conn = (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("X-Goog-FieldMask", "playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId")
        }

        conn.outputStream.use { os ->
            os.write(payload.toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        val responseText = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }

        json.decodeFromString<PlayerResponse>(responseText)
    } catch (e: Exception) {
        null
    }
}

suspend fun Innertube.player(body: PlayerBody) = runCatchingNonCancellable {
    withContext(Dispatchers.IO) {
        // 1. Try VisionOS with cached visitorData
        var visitorData = getOrFetchVisitorData()
        var resp = executePlayerRequest(body.videoId, visitorData)

        if (resp?.playabilityStatus?.status == "OK" && resp.streamingData?.highestQualityFormat != null) {
            return@withContext resp
        }

        // If LOGIN_REQUIRED, refresh visitorData and retry once
        if (resp?.playabilityStatus?.status == "LOGIN_REQUIRED" || resp == null) {
            visitorData = getOrFetchVisitorData(forceRefresh = true)
            resp = executePlayerRequest(body.videoId, visitorData)
            if (resp?.playabilityStatus?.status == "OK" && resp.streamingData?.highestQualityFormat != null) {
                return@withContext resp
            }
        }

        // 2. Try Android VR client fallback
        val vrResp = executeAndroidVrRequest(body.videoId)
        if (vrResp?.playabilityStatus?.status == "OK" && vrResp.streamingData?.highestQualityFormat != null) {
            return@withContext vrResp
        }

        // Return whatever response was obtained (or empty response with UNPLAYABLE)
        resp ?: vrResp ?: PlayerResponse(
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "UNPLAYABLE"),
            playerConfig = null,
            streamingData = null,
            videoDetails = PlayerResponse.VideoDetails(videoId = body.videoId)
        )
    }
}
