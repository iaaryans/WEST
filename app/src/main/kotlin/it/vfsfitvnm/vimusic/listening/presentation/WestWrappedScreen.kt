package it.vfsfitvnm.vimusic.listening.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import it.vfsfitvnm.compose.persist.persist
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.listening.data.ListeningStatsRepository
import it.vfsfitvnm.vimusic.listening.domain.WrappedSummary
import it.vfsfitvnm.vimusic.listening.domain.formatListeningDuration
import it.vfsfitvnm.vimusic.listening.domain.formatPlural
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.ValueSelectorDialog
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.shimmer
import it.vfsfitvnm.vimusic.utils.medium
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.toast
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * WEST Wrapped: the annual listening summary with a shareable statistics
 * card. The card is themed from the active palette and exported as a PNG
 * entirely on-device (no third-party services).
 */
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun WestWrappedScreen() {
    val (colorPalette, typography, thumbnailShape) = LocalAppearance.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentYear = LocalDate.now().year
    var selectedYear by rememberSaveable { mutableStateOf(currentYear) }
    var availableYears by rememberSaveable { mutableStateOf(listOf(currentYear)) }
    var showYearDialog by rememberSaveable { mutableStateOf(false) }
    var isSharing by rememberSaveable { mutableStateOf(false) }

    var wrapped by persist<WrappedSummary?>("listening/wrapped")

    LaunchedEffect(Unit) {
        ListeningStatsRepository.availableYears().collect { availableYears = it }
    }

    LaunchedEffect(selectedYear) {
        ListeningStatsRepository.wrapped(selectedYear).collect { wrapped = it }
    }

    if (showYearDialog) {
        ValueSelectorDialog(
            onDismiss = { showYearDialog = false },
            title = "Wrapped year",
            selectedValue = selectedYear,
            values = availableYears,
            onValueSelected = { selectedYear = it },
            valueText = { it.toString() }
        )
    }

    Box(
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header", contentType = 0) {
                Header(
                    title = "WEST Wrapped",
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    HeaderIconButton(
                        icon = R.drawable.calendar,
                        color = colorPalette.text,
                        onClick = { showYearDialog = true }
                    )

                    HeaderIconButton(
                        icon = R.drawable.share_social,
                        color = if (wrapped?.totalPlays ?: 0L > 0L && !isSharing) {
                            colorPalette.text
                        } else {
                            colorPalette.textDisabled
                        },
                        enabled = (wrapped?.totalPlays ?: 0L) > 0L && !isSharing,
                        onClick = {
                            val summary = wrapped ?: return@HeaderIconButton
                            isSharing = true
                            scope.launch {
                                try {
                                    val bitmap = WrappedShareCard.createBitmap(
                                        context = context,
                                        wrapped = summary,
                                        // The share card is a fixed-canvas PNG: its
                                        // text colors must come from the gradient
                                        // that is actually rendered, not from the
                                        // theme text colors (BUG-002).
                                        colors = WrappedCardPalette.cardColors(
                                            accent = colorPalette.accent.toArgb(),
                                            background = colorPalette.background0.toArgb(),
                                            text = colorPalette.text.toArgb(),
                                            textSecondary = colorPalette.textSecondary.toArgb(),
                                            onAccent = colorPalette.onAccent.toArgb()
                                        )
                                    )
                                    WrappedShareCard.share(context, bitmap, summary.year)
                                } catch (_: Exception) {
                                    context.toast("Could not create the share card")
                                } finally {
                                    isSharing = false
                                }
                            }
                        }
                    )
                }
            }

            item(key = "card", contentType = 1) {
                val summary = wrapped
                if (summary == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorPalette.shimmer)
                    )
                } else if (summary.totalPlays == 0L) {
                    EmptyState(
                        title = "Nothing in $selectedYear",
                        text = "Listen to some music and come back for your Wrapped."
                    )
                } else {
                    WrappedCard(summary = summary)
                }
            }
        }
    }
}

@Composable
private fun WrappedCard(summary: WrappedSummary) {
    val (colorPalette, typography) = LocalAppearance.current

    var storyOpen by rememberSaveable(summary.year) { mutableStateOf(false) }
    if (storyOpen) WrappedStory(summary) { storyOpen = false }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        colorPalette.accent.copy(alpha = 0.55f),
                        colorPalette.background2
                    )
                )
            )
            .padding(all = 24.dp)
    ) {
        BasicText(
            text = "${summary.year} Wrapped",
            style = typography.xxl.semiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        BasicText(
            text = formatListeningDuration(summary.totalListeningMs),
            style = typography.l.semiBold.let { it.copy(color = colorPalette.accent) }
        )

        BasicText(
            text = formatPlural(summary.totalPlays, "play") + " · " +
                formatPlural(summary.uniqueSongs, "unique song") + " · " +
                formatPlural(summary.uniqueArtists, "artist"),
            style = typography.xs.secondary.medium
        )

        BasicText(
            "Play year in review",
            Modifier.clip(CircleShape).background(colorPalette.accent)
                .clickable { storyOpen = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            style = typography.xs.semiBold.copy(color = colorPalette.onAccent)
        )

        summary.mostActiveMonth?.let { month ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(
                    text = "MOST ACTIVE MONTH",
                    style = typography.xxs.semiBold.secondary
                )
                BasicText(
                    text = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    style = typography.m.semiBold
                )
            }
        }

        if (summary.topSongs.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    text = "TOP SONGS",
                    style = typography.xxs.semiBold.secondary
                )
                summary.topSongs.forEachIndexed { index, song ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BasicText(
                            text = "${index + 1}",
                            style = typography.s.semiBold.let { it.copy(color = colorPalette.accent) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(
                                text = song.title ?: song.songId,
                                style = typography.xs.semiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            BasicText(
                                text = song.artistsText.orEmpty(),
                                style = typography.xxs.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                        BasicText(
                            text = formatPlural(song.playCount, "play"),
                            style = typography.xxs.secondary.medium
                        )
                    }
                }
            }
        }

        if (summary.topArtists.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    text = "TOP ARTISTS",
                    style = typography.xxs.semiBold.secondary
                )
                summary.topArtists.forEachIndexed { index, artist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BasicText(
                            text = "${index + 1}",
                            style = typography.s.semiBold.let { it.copy(color = colorPalette.accent) }
                        )
                        BasicText(
                            text = artist.name ?: artist.artistId,
                            style = typography.xs.semiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        BasicText(
                            text = formatPlural(artist.playCount, "play"),
                            style = typography.xxs.secondary.medium
                        )
                    }
                }
            }
        }

        if (summary.topAlbums.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    text = "TOP ALBUMS",
                    style = typography.xxs.semiBold.secondary
                )
                summary.topAlbums.forEachIndexed { index, album ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BasicText(
                            text = "${index + 1}",
                            style = typography.s.semiBold.let { it.copy(color = colorPalette.accent) }
                        )
                        BasicText(
                            text = album.title ?: album.albumId,
                            style = typography.xs.semiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        BasicText(
            text = "WEST · Listening Insights",
            style = typography.xxs.secondary.center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WrappedStory(summary: WrappedSummary, close: () -> Unit) {
    val (palette, type) = LocalAppearance.current
    val slides = remember<List<Pair<String, String>>>(summary) {
        buildList {
            add("Listening time" to formatListeningDuration(summary.totalListeningMs))
            add("Tracks played" to formatPlural(summary.totalPlays, "play"))
            summary.topSongs.firstOrNull()?.let { add("Top song" to (it.title ?: it.songId)) }
            summary.topArtists.firstOrNull()?.let { add("Top artist" to (it.name ?: it.artistId)) }
            summary.topAlbums.firstOrNull()?.let { add("Top album" to (it.title ?: it.albumId)) }
        }
    }
    var index by rememberSaveable(summary.year) { mutableStateOf(0) }
    val page = index.coerceIn(slides.indices)
    var paused by rememberSaveable { mutableStateOf(false) }
    var elapsedMs by remember(page) { mutableStateOf(0f) }

    LaunchedEffect(page, paused, slides.size) {
        if (paused) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (elapsedMs < 14_000f) {
            val now = withFrameNanos { it }
            elapsedMs += ((now - previous) / 1_000_000f).coerceAtMost(100f)
            previous = now
        }
        if (page == slides.lastIndex) close() else index = page + 1
    }

    Dialog(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(palette.background2, palette.background0)))
            .padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { slot ->
                    val fraction = when {
                        slot < page -> 1f
                        slot == page -> (elapsedMs / 14_000f).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).background(palette.textDisabled)) {
                        Spacer(Modifier.fillMaxWidth(fraction).height(3.dp).background(palette.accent))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            BasicText("WEST Wrapped · ${summary.year}", style = type.xs.semiBold.secondary)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Crossfade(targetState = slides[page]) { slide ->
                    Column {
                        BasicText(slide.first, style = type.s.secondary)
                        BasicText(slide.second, style = type.xxl.semiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("Back", Modifier.clickable(enabled = page > 0) { index = page - 1 }.padding(vertical = 12.dp), style = type.xs)
                BasicText(if (paused) "Resume" else "Pause", Modifier.clickable { paused = !paused }.padding(12.dp), style = type.xs)
                BasicText(if (page == slides.lastIndex) "Done" else "Next", Modifier.clickable {
                    if (page == slides.lastIndex) close() else index = page + 1
                }.padding(vertical = 12.dp), style = type.xs)
            }
        }
    }
}
