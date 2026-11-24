package it.vfsfitvnm.vimusic

import it.vfsfitvnm.vimusic.voice.VoiceMusicQuery
import it.vfsfitvnm.vimusic.voice.MusicQueryMatcher
import org.junit.Assert.*
import org.junit.Test

class VoiceMusicQueryTest {
    @Test fun conservativeMusicNormalization() {
        assertEquals("Night Changes", VoiceMusicQuery.normalize("Play Night Changes"))
        assertEquals("Arijit Singh", VoiceMusicQuery.normalize("Arijit Singh ke gaane"))
        assertEquals("the Weeknd", VoiceMusicQuery.normalize("Search for the Weeknd"))
        assertEquals("", VoiceMusicQuery.normalize("    "))
    }
    @Test fun fuzzyNamesAreSuggestionsOnly() {
        val suggestions = MusicQueryMatcher.suggest("Night Change", listOf("Night Changes", "Piano Man"))
        assertTrue("Night Changes" in suggestions)
        assertTrue(MusicQueryMatcher.suggest("Night Changes", listOf("Night Changes")).isEmpty())
    }
}
