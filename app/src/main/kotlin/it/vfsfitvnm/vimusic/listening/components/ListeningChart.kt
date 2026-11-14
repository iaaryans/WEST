package it.vfsfitvnm.vimusic.listening.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.listening.domain.ListeningTrendPoint
import it.vfsfitvnm.vimusic.listening.domain.formatListeningDuration
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlin.math.max

/**
 * Lightweight bar chart for listening-time trends, drawn with the Compose
 * Canvas: no chart library involved (free-only requirement), theme colours
 * only, so it follows light/dark and dynamic palettes automatically.
 */
@Composable
fun ListeningBarChart(
    points: List<ListeningTrendPoint>,
    modifier: Modifier = Modifier
) {
    val (colorPalette, typography) = LocalAppearance.current

    if (points.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorPalette.background1)
        ) {
            BasicText(
                text = "No listening recorded yet",
                style = typography.xxs.secondary.center
            )
        }
        return
    }

    val peak = points.maxOf { it.playedMs }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        if (peak > 0) {
            BasicText(
                text = "Peak ${formatListeningDuration(peak)}",
                style = typography.xxs.secondary
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorPalette.background1)
                .padding(0.dp)
        ) {
            val count = points.size
            if (count == 0 || peak <= 0) return@Canvas

            val horizontalPadding = 4.dp.toPx()
            val verticalPadding = 6.dp.toPx()
            val chartWidth = size.width - horizontalPadding * 2
            val chartHeight = size.height - verticalPadding * 2
            val slotWidth = chartWidth / count
            val barWidth = (slotWidth * 0.58f).coerceAtLeast(2f)

            points.forEachIndexed { index, point ->
                if (point.playedMs <= 0) return@forEachIndexed
                val barHeight = max(
                    (point.playedMs.toFloat() / peak) * chartHeight,
                    2.dp.toPx()
                )
                drawRoundRect(
                    color = colorPalette.accent.copy(alpha = 0.35f + 0.65f * (point.playedMs.toFloat() / peak)),
                    topLeft = Offset(
                        x = horizontalPadding + slotWidth * index + (slotWidth - barWidth) / 2,
                        y = verticalPadding + (chartHeight - barHeight)
                    ),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 3f)
                )
            }
        }

        // A handful of evenly spread labels so they never overlap
        val labelCount = minOf(points.size, if (points.size <= 8) points.size else 6)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        ) {
            for (slot in 0 until labelCount) {
                val index = (slot * (points.size - 1)) / (labelCount - 1).coerceAtLeast(1)
                BasicText(
                    text = points[index].label,
                    style = typography.xxs.secondary.center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
