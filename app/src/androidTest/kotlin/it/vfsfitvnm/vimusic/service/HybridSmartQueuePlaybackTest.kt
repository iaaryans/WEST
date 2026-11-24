package it.vfsfitvnm.vimusic.service

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import it.vfsfitvnm.vimusic.utils.forceSeekToNext
import it.vfsfitvnm.vimusic.utils.forceSeekToPrevious
import org.junit.Assert.assertEquals
import org.junit.Test

/** Queue navigation regressions without dependence on a live music provider. */
class HybridSmartQueuePlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun item(id: String) = MediaItem.Builder()
        .setMediaId(id)
        .setUri("https://example.invalid/$id")
        .build()

    @Test fun nextNeverWrapsALoneSong() {
        instrumentation.runOnMainSync {
            val player = ExoPlayer.Builder(instrumentation.targetContext).build()
            try {
                player.setMediaItem(item("only"))
                player.seekTo(12_000)
                val before = player.currentPosition
                player.forceSeekToNext()
                assertEquals("only", player.currentMediaItem?.mediaId)
                assertEquals(before, player.currentPosition)
            } finally { player.release() }
        }
    }

    @Test fun nextAndPreviousNavigateDistinctTracks() {
        instrumentation.runOnMainSync {
            val player = ExoPlayer.Builder(instrumentation.targetContext).build()
            try {
                player.setMediaItems(listOf(item("first"), item("second"), item("third")))
                player.forceSeekToNext()
                assertEquals("second", player.currentMediaItem?.mediaId)
                player.forceSeekToNext()
                assertEquals("third", player.currentMediaItem?.mediaId)
                player.forceSeekToPrevious()
                assertEquals("second", player.currentMediaItem?.mediaId)
            } finally { player.release() }
        }
    }
}
