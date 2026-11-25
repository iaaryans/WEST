package it.vfsfitvnm.vimusic.ui.components.themed
import it.vfsfitvnm.vimusic.R


import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    description: String? = null
) {
    IconButton(
        icon = icon,
        color = color,
        onClick = onClick,
        enabled = enabled,
        description = description,
        indication = indication,
        modifier = modifier
            .padding(all = 4.dp)
            .size(18.dp)
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    description: String? = null
) {
    Image(
        painter = painterResource(icon),
        contentDescription = description ?: when (icon) {
            R.drawable.play -> "Play"
            R.drawable.pause -> "Pause"
            R.drawable.play_skip_forward -> "Next track"
            R.drawable.play_skip_back -> "Previous track"
            R.drawable.chevron_back -> "Back"
            R.drawable.ellipsis_horizontal -> "More options"
            else -> androidx.compose.ui.platform.LocalContext.current.resources.getResourceEntryName(icon).replace('_', ' ')
        },
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier
            .clickable(
                indication = indication ?: rememberRipple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled,
                onClick = onClick
            )
            .then(modifier)
    )
}
