package it.vfsfitvnm.vimusic.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WestVersionTest {
    @Test fun newerReleaseIsDetected() {
        assertTrue(WestVersion.isNewer("0.5.5", "v0.5.6"))
        assertTrue(WestVersion.isNewer("0.5.9", "v0.5.10"))
        assertTrue(WestVersion.isNewer("0.9.9", "1.0.0"))
    }
    @Test fun equalAndOlderReleasesAreIgnored() {
        assertFalse(WestVersion.isNewer("0.5.6", "v0.5.6"))
        assertFalse(WestVersion.isNewer("0.5.6", "0.5.5"))
    }
    @Test fun matchingNewApkIsAccepted() {
        WestVersion.validateApkVersion("0.5.8", 24L, "v0.5.10", "0.5.10", 25L)
        assertFalse(WestVersion.isNewer("0.5.10", "v0.5.10"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun renamedOldApkIsRejected() {
        WestVersion.validateApkVersion("0.5.8", 24L, "v0.5.9", "0.5.8", 24L)
    }
    @Test(expected = IllegalArgumentException::class)
    fun unchangedVersionCodeIsRejected() {
        WestVersion.validateApkVersion("0.5.8", 24L, "v0.5.10", "0.5.10", 24L)
    }
    @Test(expected = IllegalArgumentException::class)
    fun missingApkVersionIsRejected() {
        WestVersion.validateApkVersion("0.5.8", 24L, "v0.5.10", null, 25L)
    }
    @Test(expected = IllegalArgumentException::class)
    fun malformedReleaseIsNotReportedAsUpToDate() {
        WestVersion.isNewer("0.5.6", "latest")
    }
}
