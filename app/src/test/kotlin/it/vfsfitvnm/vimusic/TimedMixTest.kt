package it.vfsfitvnm.vimusic

import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.ui.screens.home.seconds
import it.vfsfitvnm.vimusic.ui.screens.home.timedMix
import org.junit.Assert.*
import org.junit.Test

class TimedMixTest {
    private fun song(id: String, d: String?) = Song(id, id, null, d, null)
    @Test fun exactTargetKeepsEndingLast() {
        val library = listOf(song("a","2:00"), song("b","3:00"), song("c","4:00"), song("d","1:00"))
        for (seed in 0..20) {
            val mix = timedMix(library, 6, "d", seed)
            assertEquals(360, mix.sumOf { seconds(it.durationText) })
            assertEquals("d", mix.last().id)
            assertEquals(mix.size, mix.distinctBy { it.id }.size)
        }
    }
    @Test fun endingLongerThanTargetPlaysAlone() {
        val last = song("last","6:25")
        assertEquals(listOf(last), timedMix(listOf(last,song("other","1:30")), 3, last.id, 0))
    }
    @Test fun handlesMissingDurationsAndShortLibraries() {
        assertTrue(timedMix(listOf(song("a", null)), 10, null, 0).isEmpty())
        assertEquals(0, seconds("3:99"))
        assertEquals(3721, seconds("1:02:01"))
        assertEquals("long", timedMix(listOf(song("long","10:00")), 1, null, 0).single().id)
    }
}
