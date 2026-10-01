package it.vfsfitvnm.innertube.models.bodies

import it.vfsfitvnm.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlayerBody(
    val context: Context = Context.DefaultVisionOS,
    val videoId: String,
    val playlistId: String? = null,
    val playbackContext: PlaybackContext? = PlaybackContext(),
    val contentCheckOk: Boolean? = true,
    val racyCheckOk: Boolean? = true,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext? = ContentPlaybackContext()
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val html5Preference: String = "HTML5_PREF_WANTS",
            val signatureTimestamp: Int = 20717
        )
    }
}
