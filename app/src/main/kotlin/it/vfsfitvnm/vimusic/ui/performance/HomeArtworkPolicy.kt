package it.vfsfitvnm.vimusic.ui.performance

/**
 * Home artwork should not request 800 px imagery for a 48-96 px row thumbnail.
 * Keep the URL stable for Coil's cache, and let full-screen players retain their
 * existing large-artwork policy.  Pure Kotlin so it can be regression-tested.
 */
internal object HomeArtworkPolicy {
    private val equalsSizeSuffix = Regex("=[^/?]*$")
    private val dashWidthSuffix = Regex("-w\\d+.*$")
    fun pixels(displayPixels: Int): Int = displayPixels.coerceIn(96, 768)

    fun requestUrl(url: String?, displayPixels: Int): String? {
        if (url.isNullOrBlank()) return null
        if (!url.contains("googleusercontent.com") && !url.contains("ggpht.com")) return url
        val size = pixels(displayPixels)
        val marker = "=w${size}-h${size}-l90-rj"
        return when {
            // YouTube Music / Google artwork often uses =wNNN-hNNN suffixes.
            equalsSizeSuffix.containsMatchIn(url) -> url.replace(equalsSizeSuffix, marker)
            dashWidthSuffix.containsMatchIn(url) -> url.replace(dashWidthSuffix, "-w${size}-h${size}")
            else -> "$url$marker"
        }
    }
}
