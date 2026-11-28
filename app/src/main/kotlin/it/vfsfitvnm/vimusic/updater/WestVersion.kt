package it.vfsfitvnm.vimusic.updater

internal object WestVersion {
    private val pattern = Regex("[vV]?(\\d+)\\.(\\d+)\\.(\\d+)")
    private fun parse(value: String): List<Int>? =
        pattern.matchEntire(value.trim())?.groupValues?.drop(1)?.map {
            it.toIntOrNull() ?: return null
        }

    // The APK must match the advertised release and be a real upgrade.
    fun validateApkVersion(
        installedName: String,
        installedCode: Long,
        releaseName: String,
        apkName: String?,
        apkCode: Long
    ) {
        val expected = requireNotNull(parse(releaseName)) { "Invalid release version: $releaseName" }
        require(apkName != null && parse(apkName) == expected) {
            "Release $releaseName contains APK version ${apkName ?: "unknown"}. Please report this release."
        }
        require(isNewer(installedName, releaseName) && apkCode > installedCode) {
            "Release $releaseName is not a valid upgrade (APK version code $apkCode, installed $installedCode)."
        }
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
