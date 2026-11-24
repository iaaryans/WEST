package it.vfsfitvnm.vimusic.ui.screens.search

import org.junit.Assert.*
import org.junit.Test

class DiscoveryTest {
    private val song=DiscoveryRow("a","Moonlight Sonata","The Artist","Songs","3:15",400,800,"dream under silver skies")
    @Test fun typoAndRanking() {
        assertTrue(discoveryScore(song,discoveryRequest("Moonlgiht"),false)>0)
        assertTrue(discoveryScore(song,discoveryRequest("Moonlight Sonata"),false)>discoveryScore(song,discoveryRequest("Moonligt"),false))
        assertEquals(-1,discoveryScore(song,discoveryRequest("unrelated"),false))
    }
    @Test fun lyricPhrase() {
        assertTrue(discoveryScore(song,discoveryRequest("lyrics: silver skies"),false)>0)
        assertEquals(-1,discoveryScore(song,discoveryRequest("lyrics: Moonlight"),false))
    }
    @Test fun interpretedConstraints() {
        val q=discoveryRequest("show me offline relaxing Hindi songs by Arijit under 4 minutes")
        assertTrue(q.offline);assertEquals(240,q.maxSeconds);assertEquals("arijit",q.artist)
        assertEquals("relaxing hindi Arijit",q.remoteQuery)
        assertEquals(-1,discoveryScore(song,q,true))
        assertEquals(-1,discoveryScore(song,discoveryRequest("songs under 3 minutes"),true))
        assertEquals(-1,discoveryScore(song,discoveryRequest("Moonlight",excluded="live,The Artist"),true))
    }
    @Test fun unicodeAndVersions() {
        assertEquals("हिंदी गाने",foldSearch("हिंदी गाने"))
        assertEquals(groupOf(song),groupOf(song.copy(id="b",title="Moonlight Sonata (Live)")))
        assertNotEquals(groupOf(song),groupOf(song.copy(artist="Someone Else")))
        assertEquals("acoustic",versionOf("Moonlight Sonata - Acoustic"))
        assertNull(secondsOf("2:81"));assertEquals(195,secondsOf("3:15"))
    }
}
