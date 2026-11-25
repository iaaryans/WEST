package it.vfsfitvnm.vimusic.ui.styling

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.enums.ThumbnailRoundness
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the Appearance thumbnail-shape saver.
 *
 * The legacy encoder only recognized RoundedCornerShape(2/4/8.dp) and restored
 * every value as RoundedCornerShape(code.dp). Because ThumbnailRoundness
 * produces RectangleShape / 8 dp / 12 dp / 18 dp, Medium (12 dp) and Heavy
 * (18 dp) were saved as 0 and silently reset to square corners after process
 * death. The codec must round-trip every supported roundness exactly.
 */
class ThumbnailShapeSaverTest {

    @Test
    fun everyRoundnessRoundTripsExactly() {
        for (roundness in ThumbnailRoundness.values()) {
            val shape = roundness.shape()
            val decoded = ThumbnailShapeSaver.decode(ThumbnailShapeSaver.encode(shape))
            assertEquals("round-trip must preserve ${roundness.name}", shape, decoded)
        }
    }

    @Test
    fun eachRoundnessEncodesToItsOwnRadius() {
        assertEquals(0, ThumbnailShapeSaver.encode(ThumbnailRoundness.None.shape()))
        assertEquals(8, ThumbnailShapeSaver.encode(ThumbnailRoundness.Light.shape()))
        assertEquals(12, ThumbnailShapeSaver.encode(ThumbnailRoundness.Medium.shape()))
        assertEquals(18, ThumbnailShapeSaver.encode(ThumbnailRoundness.Heavy.shape()))
    }

    @Test
    fun legacyCodesDecodeToExactShapes() {
        assertEquals(RectangleShape, ThumbnailShapeSaver.decode(0))
        assertEquals(RoundedCornerShape(8.dp), ThumbnailShapeSaver.decode(8))
        assertEquals(RoundedCornerShape(12.dp), ThumbnailShapeSaver.decode(12))
        assertEquals(RoundedCornerShape(18.dp), ThumbnailShapeSaver.decode(18))
    }

    @Test
    fun unknownShapeFallsBackToSquareAndNegativeCodeIsSafe() {
        assertEquals(0, ThumbnailShapeSaver.encode(RoundedCornerShape(6.dp)))
        assertEquals(RectangleShape, ThumbnailShapeSaver.decode(-1))
    }
}
