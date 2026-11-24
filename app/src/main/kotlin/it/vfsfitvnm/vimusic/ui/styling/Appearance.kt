package it.vfsfitvnm.vimusic.ui.styling

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

data class Appearance(
    val colorPalette: ColorPalette,
    val typography: Typography,
    val thumbnailShape: Shape,
) {
    companion object : Saver<Appearance, List<Any>> {
        @Suppress("UNCHECKED_CAST")
        override fun restore(value: List<Any>): Appearance {
            return Appearance(
                colorPalette = ColorPalette.restore(value[0] as List<Any>),
                typography = Typography.restore(value[1] as List<Any>),
                thumbnailShape = ThumbnailShapeSaver.decode(value[2] as Int)
            )
        }

        override fun SaverScope.save(value: Appearance) =
            listOf(
                with (ColorPalette.Companion) { save(value.colorPalette) },
                with (Typography.Companion) { save(value.typography) },
                ThumbnailShapeSaver.encode(value.thumbnailShape)
            )
    }
}

/**
 * Stable encode/decode for the persisted thumbnail shape.
 *
 * Regression fix: the legacy encoder only recognized 2/4/8 dp corners, so
 * ThumbnailRoundness Medium (12 dp) and Heavy (18 dp) were saved as 0 and
 * silently reset to square corners after process death. Codes are the corner
 * radius in dp; 0 means RectangleShape.
 */
internal object ThumbnailShapeSaver {
    fun encode(shape: Shape): Int = when (shape) {
        RectangleShape -> 0
        RoundedCornerShape(8.dp) -> 8
        RoundedCornerShape(12.dp) -> 12
        RoundedCornerShape(18.dp) -> 18
        else -> 0
    }

    fun decode(code: Int): Shape = if (code <= 0) RectangleShape else RoundedCornerShape(code.dp)
}

val LocalAppearance = staticCompositionLocalOf<Appearance> { TODO() }
