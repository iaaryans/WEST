package it.vfsfitvnm.vimusic.ui.components.themed

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.isLandscape
import it.vfsfitvnm.vimusic.utils.semiBold

val LocalReducedMotion = staticCompositionLocalOf { false }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BrowseShell(index: Int, select: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    val (palette, type) = LocalAppearance.current
    val insets = LocalPlayerAwareWindowInsets.current
    // Keep per-tab LazyList position and onboarding-safe screen state while
    // composing only the active tab (no second off-screen Home tree).
    val tabStateHolder = rememberSaveableStateHolder()
    val wide = isLandscape
    val tab: @Composable (Int, Modifier) -> Unit = { i, mod ->
        val selected = i == index
        val reduced = LocalReducedMotion.current
        val tint by animateColorAsState(
            if (selected) palette.accent else palette.textSecondary,
            animationSpec = tween(if (reduced) 0 else 170)
        )
        val background by animateColorAsState(
            if (selected) palette.accent.copy(alpha = 0.14f) else palette.background1,
            animationSpec = tween(if (reduced) 0 else 170)
        )
        val scale by animateFloatAsState(
            if (selected && !reduced) 1.035f else 1f,
            animationSpec = spring(dampingRatio = .82f, stiffness = 660f)
        )
        Column(mod.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(background)
            .selectable(selected, role = Role.Tab, onClick = { select(i) }).heightIn(min = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painterResource(listOf(R.drawable.home_outline, R.drawable.sparkles, R.drawable.search, R.drawable.library)[i]), null,
                Modifier.size(20.dp), colorFilter = ColorFilter.tint(tint))
            BasicText(listOf("Home", "Moodspace", "Search", "Library")[i], style = type.xxs.semiBold.copy(color = tint))
        }
    }
    val page: @Composable () -> Unit = {
        // Animate only the incoming tab. AnimatedContent retained two entire
        // heavyweight browsing trees simultaneously and caused frame drops.
        key(index) {
            val reduced = LocalReducedMotion.current
            val reveal = remember { Animatable(if (reduced) 1f else 0f) }
            LaunchedEffect(index, reduced) {
                if (reduced) reveal.snapTo(1f)
                else reveal.animateTo(1f, tween(durationMillis = 170, easing = FastOutSlowInEasing))
            }
            val enterDistance = with(LocalDensity.current) { 9.dp.toPx() }
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = if (reduced) 1f else reveal.value
                translationX = if (reduced) 0f else (1f - reveal.value) * enterDistance
            }) {
                tabStateHolder.SaveableStateProvider(index) { content(index) }
            }
        }
    }
    if (wide) Row(Modifier.fillMaxSize()) {
        Column(Modifier.padding(insets.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical).asPaddingValues())
            .width(86.dp).verticalScroll(rememberScrollState())) {
            repeat(4) { tab(it, Modifier.fillMaxWidth()) }
        }
        Box(Modifier.weight(1f)) { page() }
    } else Column(Modifier.fillMaxSize().background(palette.background0)) {
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)) { page() }
        }
        Row(Modifier.fillMaxWidth().padding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues())
            .padding(horizontal = 10.dp, vertical = 4.dp).background(palette.background1, CircleShape).padding(3.dp)) {
            repeat(4) { tab(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
fun FilterPage(labels: List<String>, index: Int, select: (Int) -> Unit, back: (() -> Unit)? = null,
               header: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val (palette, type) = LocalAppearance.current
    val insets = LocalPlayerAwareWindowInsets.current
    Column(Modifier.fillMaxSize().background(palette.background0)
        .padding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues())) {
        header()
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (back != null) IconButton(icon = R.drawable.chevron_back, color = palette.text, onClick = back,
                description = "Back", modifier = Modifier.padding(12.dp).size(22.dp))
            labels.forEachIndexed { i, text ->
                BasicText(text, Modifier.clip(CircleShape)
                    .background(if (i == index) palette.background2 else palette.background0)
                    .selectable(i == index, role = Role.Tab, onClick = { select(i) }).padding(horizontal = 12.dp, vertical = 12.dp),
                    style = type.xs.semiBold.copy(color = if (i == index) palette.accent else palette.textSecondary))
            }
        }
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides insets.only(WindowInsetsSides.Bottom)) { content() }
        }
    }
}
