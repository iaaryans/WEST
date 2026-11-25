package it.vfsfitvnm.vimusic.ui.components

import androidx.compose.animation.core.Animatable
import it.vfsfitvnm.vimusic.ui.components.themed.LocalReducedMotion
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun MusicBars(
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 4.dp,
    cornerRadius: Dp = 16.dp
) {
    val animatablesWithSteps = remember<List<Pair<Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, List<Float>>>> {
        listOf(
            Animatable(0f) to listOf(
                0.2f,
                0.8f,
                0.1f,
                0.1f,
                0.3f,
                0.1f,
                0.2f,
                0.8f,
                0.7f,
                0.2f,
                0.4f,
                0.9f,
                0.7f,
                0.6f,
                0.1f,
                0.3f,
                0.1f,
                0.4f,
                0.1f,
                0.8f,
                0.7f,
                0.9f,
                0.5f,
                0.6f,
                0.3f,
                0.1f
            ),
            Animatable(0f) to listOf(
                0.2f,
                0.5f,
                1.0f,
                0.5f,
                0.3f,
                0.1f,
                0.2f,
                0.3f,
                0.5f,
                0.1f,
                0.6f,
                0.5f,
                0.3f,
                0.7f,
                0.8f,
                0.9f,
                0.3f,
                0.1f,
                0.5f,
                0.3f,
                0.6f,
                1.0f,
                0.6f,
                0.7f,
                0.4f,
                0.1f
            ),
            Animatable(0f) to listOf(
                0.6f,
                0.5f,
                1.0f,
                0.6f,
                0.5f,
                1.0f,
                0.6f,
                0.5f,
                1.0f,
                0.5f,
                0.6f,
                0.7f,
                0.2f,
                0.3f,
                0.1f,
                0.5f,
                0.4f,
                0.6f,
                0.7f,
                0.1f,
                0.4f,
                0.3f,
                0.1f,
                0.4f,
                0.3f,
                0.7f
            )
        )
    }

    val reducedMotion = LocalReducedMotion.current
    // Stop all three infinite animation loops for reduced-motion/low-RAM devices.
    // A static equalizer retains the playback affordance without per-frame work.
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        animatablesWithSteps.forEach { (animatable, steps) ->
            launch {
                while (true) {
                    steps.forEach { step -> animatable.animateTo(step) }
                }
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
    ) {
        animatablesWithSteps.forEach { (animatable) ->
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(barWidth)
            ) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x = 0f, y = size.height * (1 - if (reducedMotion) .42f else animatable.value)),
                    size = size.copy(height = (if (reducedMotion) .42f else animatable.value) * size.height),
                    cornerRadius = CornerRadius(cornerRadius.toPx())
                )
            }
        }
    }
}
