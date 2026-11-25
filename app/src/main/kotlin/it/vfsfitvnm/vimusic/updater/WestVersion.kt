package it.vfsfitvnm.vimusic.updater

internal object WestVersion {
    private val pattern = Regex("[vV]?(\\d+)\\.(\\d+)\\.(\\d+)")
    private fun parse(value: String): List<Int>? =
        pattern.matchEntire(value.trim())?.groupValues?.drop(1)?.map {
            it.toIntOrNull() ?: return null
        }

    fun isNewer(current: String, remote: String): Boolean {
        val installed = requireNotNull(parse(current)) { "Invalid installed version: $current" }
        val published = requireNotNull(parse(remote)) { "Unsupported GitHub release tag: $remote" }
        for (i in installed.indices) {
            val difference = published[i].compareTo(installed[i])
            if (difference != 0) return difference > 0
        }
        return false
    }
}
