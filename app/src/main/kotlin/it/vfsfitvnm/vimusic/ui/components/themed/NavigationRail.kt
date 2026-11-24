package it.vfsfitvnm.vimusic.ui.components.themed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.color
import it.vfsfitvnm.vimusic.utils.semiBold

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NavigationRail(
    topIconButtonId: Int,
    onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    onTabIndexChanged: (Int) -> Unit,
    landscape: Boolean,
    content: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val (palette, type) = LocalAppearance.current
    val surface = Modifier
        .clip(CircleShape)
        .background(palette.background1)
        .border(1.dp, palette.text.copy(alpha = 0.10f), CircleShape)

    val action: @Composable () -> Unit = {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onTopIconButtonClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painterResource(topIconButtonId),
                if (topIconButtonId == R.drawable.chevron_back) "Back" else "Settings",
                Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(palette.text)
            )
        }
    }

    val tabBounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val tabs: @Composable () -> Unit = {
        content { index, label, icon ->
            val active = index == tabIndex
            val requester = remember { BringIntoViewRequester() }
            LaunchedEffect(active) { if (active) requester.bringIntoView() }
            val fill by animateColorAsState(if (active) palette.accent.copy(alpha = 0.16f) else Color.Transparent)
            val tint by animateColorAsState(if (active) palette.accent else palette.textSecondary)
            val scale by animateFloatAsState(if (active) 1.10f else 1f, spring(dampingRatio = 0.8f))
            Column(
                Modifier.onGloballyPositioned { tab ->
                    if (!landscape) {
                        tabBounds[index] = tab.positionInParent().x to tab.size.width.toFloat()
                    }
                }.bringIntoViewRequester(requester)
                    .clip(CircleShape).background(if (landscape) fill else Color.Transparent)
                    .selectable(selected = active, role = Role.Tab, onClick = { onTabIndexChanged(index) })
                    .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(painterResource(icon), null,
                    Modifier.size(20.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                    colorFilter = ColorFilter.tint(tint))
                BasicText(label, style = type.xxs.semiBold.color(tint), maxLines = 1)
            }
        }
    }
    if (landscape) {
        Column(
            modifier.width(112.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            action()
            tabs()
        }
    } else {
        Row(
            modifier.fillMaxWidth().then(surface).padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            action()
            val bounds = tabBounds[tabIndex]
            val left by animateFloatAsState(bounds?.first ?: 0f, spring(dampingRatio = 0.85f, stiffness = 320f))
            val width by animateFloatAsState(bounds?.second ?: 0f, spring(dampingRatio = 0.85f, stiffness = 320f))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).drawBehind {
                if (width > 0f) drawRoundRect(
                    color = palette.accent.copy(alpha = 0.16f),
                    topLeft = Offset(left, 0f), size = Size(width, size.height),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            },
                verticalAlignment = Alignment.CenterVertically) { tabs() }
        }
    }
}
