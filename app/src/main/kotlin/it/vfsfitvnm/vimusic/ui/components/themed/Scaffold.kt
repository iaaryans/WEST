package it.vfsfitvnm.vimusic.ui.components.themed

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.isLandscape

@ExperimentalAnimationApi
@Composable
fun Scaffold(
    topIconButtonId: Int,
    onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    onTabChanged: (Int) -> Unit,
    tabColumnContent: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val landscape = isLandscape
    val insets = LocalPlayerAwareWindowInsets.current
    val root = modifier.fillMaxSize().background(LocalAppearance.current.colorPalette.background0)
    val page: @Composable () -> Unit = {
        if (LocalReducedMotion.current) {
            AnimatedContent(targetState = tabIndex, transitionSpec = { EnterTransition.None with ExitTransition.None }, content = content)
        } else AnimatedContent(targetState = tabIndex, transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInHorizontally(tween(180)) { direction * it / 12 } + fadeIn(tween(140))) with
                (slideOutHorizontally(tween(160)) { -direction * it / 12 } + fadeOut(tween(120)))
        }, content = content)
    }

    if (landscape) {
        Row(root) {
            NavigationRail(topIconButtonId, onTopIconButtonClick, tabIndex, onTabChanged, true,
                tabColumnContent, Modifier.padding(insets.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start).asPaddingValues()))
            Box(Modifier.weight(1f).fillMaxHeight()) { page() }
        }
    } else {
        Column(root) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalPlayerAwareWindowInsets provides
                    insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) { page() }
            }
            NavigationRail(topIconButtonId, onTopIconButtonClick, tabIndex, onTabChanged, false,
                tabColumnContent, Modifier.padding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal).asPaddingValues())
                    .padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}
