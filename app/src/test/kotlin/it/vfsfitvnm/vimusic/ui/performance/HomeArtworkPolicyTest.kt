package it.vfsfitvnm.vimusic.ui.performance

import org.junit.Assert.*
import org.junit.Test

class HomeArtworkPolicyTest {
    @Test fun tinyRowsNeverRequestLegacyEightHundredPixelArt() {
        assertEquals(96, HomeArtworkPolicy.pixels(40))
        assertEquals(144, HomeArtworkPolicy.pixels(144))
        assertEquals(768, HomeArtworkPolicy.pixels(1200))
    }

    @Test fun googleArtworkUsesStableRequestedResolution() {
        val original = "https://lh3.googleusercontent.com/cover=w800-h800-l90-rj"
        assertEquals("https://lh3.googleusercontent.com/cover=w144-h144-l90-rj",
            HomeArtworkPolicy.requestUrl(original, 144))
        assertEquals(HomeArtworkPolicy.requestUrl(original, 144),
            HomeArtworkPolicy.requestUrl(original, 144))
    }

    @Test fun otherImageSourcesAreNeverRewritten() {
        val original = "https://cdn.example.org/art.jpg?size=800"
        assertEquals(original, HomeArtworkPolicy.requestUrl(original, 96))
    }

    @Test fun emptyImageIdsAreSafe() {
        assertNull(HomeArtworkPolicy.requestUrl(null, 144))
        assertNull(HomeArtworkPolicy.requestUrl("", 144))
    }

    @Test fun legacyGoogleDashWidthIsNormalized() {
        assertEquals("https://example.ggpht.com/abc-w180-h180",
            HomeArtworkPolicy.requestUrl("https://example.ggpht.com/abc-w800-h800", 180))
    }
}
