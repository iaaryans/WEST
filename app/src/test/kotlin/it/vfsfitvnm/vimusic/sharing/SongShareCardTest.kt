package it.vfsfitvnm.vimusic.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongShareCardTest {
    @Test fun catalogIdProducesPublicLink() {
        assertEquals("https://music.youtube.com/watch?v=abcdefghijk", songShareLink("abcdefghijk"))
    }
    @Test fun privateOrMalformedIdsAreNotShared() {
        assertNull(songShareLink("content://private/music"))
        assertNull(songShareLink("abcdefghijk&x=y"))
        assertNull(songShareLink(""))
    }
    @Test fun metadataFallbacksAndNewlines() {
        assertEquals("Unknown song", shareLabel("  ", "Unknown song"))
        assertEquals("A B", shareLabel("A\nB", "Unknown artist"))
    }
    @Test fun captionIncludesLinkOnlyWhenAvailable() {
        val link = songShareLink("abcdefghijk")
        assertTrue(songShareCaption("Song", "Artist", link).contains(requireNotNull(link)))
        assertFalse(songShareCaption("Song", "Artist", null).contains("https://"))
    }
}
