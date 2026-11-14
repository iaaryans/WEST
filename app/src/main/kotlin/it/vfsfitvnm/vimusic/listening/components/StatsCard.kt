package it.vfsfitvnm.vimusic.listening.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold

/**
 * A single statistic tile used across the listening insights dashboard:
 * small caption, prominent value, optional supporting line.
 */
@Composable
fun StatsCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes iconId: Int? = null
) {
    val (colorPalette, typography) = LocalAppearance.current

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorPalette.background1)
            .padding(all = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            iconId?.let { icon ->
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                    modifier = Modifier.size(14.dp)
                )
            }

            BasicText(
                text = title,
                style = typography.xxs.semiBold.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        BasicText(
            text = value,
            style = typography.l.semiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        if (subtitle != null) {
            BasicText(
                text = subtitle,
                style = typography.xxs.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Two [StatsCard]s side by side, filling the available width. */
@Composable
fun StatsCardRow(
    first: StatsCardData,
    second: StatsCardData,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        StatsCard(
            title = first.title,
            value = first.value,
            subtitle = first.subtitle,
            iconId = first.iconId,
            modifier = Modifier.weight(1f)
        )
        StatsCard(
            title = second.title,
            value = second.value,
            subtitle = second.subtitle,
            iconId = second.iconId,
            modifier = Modifier.weight(1f)
        )
    }
}

data class StatsCardData(
    val title: String,
    val value: String,
    val subtitle: String? = null,
    val iconId: Int? = null
)
