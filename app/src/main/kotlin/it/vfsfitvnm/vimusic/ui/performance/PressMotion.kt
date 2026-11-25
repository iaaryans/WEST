package it.vfsfitvnm.vimusic.ui.performance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import it.vfsfitvnm.vimusic.ui.components.themed.LocalReducedMotion

/** Short tactile feedback only on interaction; no infinite animations while scrolling. */
@Composable
internal fun rememberPressMotion(): Pair<MutableInteractionSource, Modifier> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (reduced || !pressed) 1f else .982f,
        animationSpec = spring(dampingRatio = .78f, stiffness = 800f)
    )
    return source to Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
}
