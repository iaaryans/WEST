package it.vfsfitvnm.vimusic.ui.screens.localplaylist

import it.vfsfitvnm.vimusic.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistShareTextTest {
    private fun song(id: String, title: String, artist: String? = null) =
        Song(id = id, title = title, artistsText = artist, durationText = null, thumbnailUrl = null)

    @Test
    fun preservesPlaylistOrderAndPublicLinks() {
        val text = playlistShareText("My mix", listOf(
            song("abcdefghijk", "First", "Artist A"),
            song("ABCDEFGHIJK", "Second", "Artist B")
        ))
        assertTrue(text.startsWith("My mix\n2 songs\n"))
        assertTrue(text.indexOf("1. First — Artist A") < text.indexOf("2. Second — Artist B"))
        assertTrue(text.contains("https://music.youtube.com/watch?v=abcdefghijk"))
        assertTrue(text.contains("https://music.youtube.com/watch?v=ABCDEFGHIJK"))
        assertTrue(text.endsWith("Shared from WEST"))
    }

    @Test
    fun nonCatalogIdsAreNeverExposed() {
        val text = playlistShareText("My mix", listOf(song("content://private/music/42", "Local track")))
        assertTrue(text.contains("1. Local track"))
        assertFalse(text.contains("content://"))
        assertFalse(text.contains("watch?v="))
    }

    @Test
    fun handlesBlankTitleAndMultilineMetadata() {
        val text = playlistShareText(" Mix\nName ", listOf(song("abcdefghijk", " ", "A\nB")))
        assertTrue(text.startsWith("Mix Name\n1 song\n"))
        assertTrue(text.contains("1. Unknown song — A B"))
    }

    @Test
    fun emptyPlaylistHasNoFakeTracks() {
        assertEquals("WEST playlist\n0 songs\n\nShared from WEST", playlistShareText("", emptyList()))
    }
}
