package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnail(
    val url: String? = null,
    val height: Int? = null,
    val width: Int? = null
) {
    val isResizable: Boolean
        get() = url?.startsWith("https://i.ytimg.com") == false

    fun size(size: Int): String? {
        return when {
            url == null -> null
            url.startsWith("https://lh3.googleusercontent.com") -> "$url-w$size-h$size"
            url.startsWith("https://yt3.ggpht.com") -> "$url-s$size"
            else -> url
        }
    }
}
