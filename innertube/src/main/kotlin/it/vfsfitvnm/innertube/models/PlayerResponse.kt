package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus?,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String?
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig?
    ) {
        @Serializable
        data class AudioConfig(
            private val loudnessDb: Double?
        ) {
            // For music clients only
            val normalizedLoudnessDb: Float?
                get() = loudnessDb?.plus(7)?.toFloat()
        }
    }

    @Serializable
    data class StreamingData(
        val adaptiveFormats: List<AdaptiveFormat>?
    ) {
        val highestQualityFormat: AdaptiveFormat?
            get() = adaptiveFormats?.findLast { (it.itag == 251 || it.itag == 140) && it.url != null }
                ?: adaptiveFormats?.firstOrNull { it.mimeType.startsWith("audio") && it.url != null }

        @Serializable
        data class AdaptiveFormat(
            val itag: Int = 0,
            val mimeType: String = "",
            val bitrate: Long? = null,
            val averageBitrate: Long? = null,
            val contentLength: Long? = null,
            val audioQuality: String? = null,
            val approxDurationMs: Long? = null,
            val lastModified: Long? = null,
            val loudnessDb: Double? = null,
            val audioSampleRate: Int? = null,
            val url: String? = null,
        )
    }

    @Serializable
    data class VideoDetails(
        val videoId: String?
    )
}
