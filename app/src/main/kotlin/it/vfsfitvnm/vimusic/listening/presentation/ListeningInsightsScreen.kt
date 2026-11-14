package it.vfsfitvnm.vimusic.listening.presentation

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.routing.Route0
import it.vfsfitvnm.compose.routing.RouteHandler
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.listening.components.ListeningBarChart
import it.vfsfitvnm.vimusic.listening.components.StatsCardData
import it.vfsfitvnm.vimusic.listening.components.StatsCard
import it.vfsfitvnm.vimusic.listening.components.StatsCardRow
import it.vfsfitvnm.vimusic.listening.components.TopTrackItem
import it.vfsfitvnm.vimusic.listening.data.ListeningSettings
import it.vfsfitvnm.vimusic.listening.data.TopSongEntry
import it.vfsfitvnm.vimusic.listening.data.asMediaItem
import it.vfsfitvnm.vimusic.listening.domain.ListeningPeriodType
import it.vfsfitvnm.vimusic.listening.domain.formatCount
import it.vfsfitvnm.vimusic.listening.domain.formatListeningDuration
import it.vfsfitvnm.vimusic.listening.domain.formatPlural
import it.vfsfitvnm.vimusic.ui.components.themed.ConfirmationDialog
import it.vfsfitvnm.vimusic.ui.components.themed.DialogTextButton
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.Scaffold
import it.vfsfitvnm.vimusic.ui.components.themed.ValueSelectorDialog
import it.vfsfitvnm.vimusic.ui.screens.albumRoute
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.ui.screens.globalRoutes
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.shimmer
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.forcePlayAtIndex
import it.vfsfitvnm.vimusic.utils.preferences
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val listeningStatsRoute = Route0("listeningStatsRoute")

private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * WEST Listening Insights dashboard.
 *
 * One single route is added to the existing navigation; everything else
 * lives inside this screen as tabs, mirroring how the settings screen is
 * structured, so the integration footprint stays minimal.
 */
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun ListeningInsightsScreen() {
    val saveableStateHolder = rememberSaveableStateHolder()

    var tabIndex by rememberSaveable { mutableStateOf(0) }

    RouteHandler(listenToGlobalEmitter = true) {
        globalRoutes()

        host {
            Scaffold(
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = tabIndex,
                onTabChanged = { tabIndex = it },
                tabColumnContent = { Item ->
                    Item(0, "Overview", R.drawable.sparkles)
                    Item(1, "Songs", R.drawable.musical_notes)
                    Item(2, "Artists", R.drawable.person)
                    Item(3, "Albums", R.drawable.disc)
                    Item(4, "Calendar", R.drawable.calendar)
                    Item(5, "Wrapped", R.drawable.star)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> ListeningOverviewTab(
                            onOpenTab = { tabIndex = it },
                            onArtistClick = { artistRoute(it) }
                        )

                        1 -> TopSongsScreen()
                        2 -> TopArtistsScreen(
                            onArtistClick = { artistRoute(it) }
                        )

                        3 -> TopAlbumsScreen(
                            onAlbumClick = { albumRoute(it) }
                        )

                        4 -> ListeningCalendarScreen()
                        5 -> WestWrappedScreen()
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Overview tab
// ----------------------------------------------------------------------

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
private fun ListeningOverviewTab(
    onOpenTab: (Int) -> Unit,
    onArtistClick: (String) -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val viewModel = rememberListeningStatsViewModel()

    var showThresholdDialog by remember { mutableStateOf(false) }
    var showRangeDialog by remember { mutableStateOf(false) }

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px

    if (showThresholdDialog) {
        ValueSelectorDialog(
            onDismiss = { showThresholdDialog = false },
            title = "Count a play after",
            selectedValue = ListeningSettings.playCountThresholdMs(context),
            values = ListeningSettings.PLAY_COUNT_THRESHOLD_CHOICES,
            onValueSelected = { value ->
                context.preferences.edit()
                    .putLong(ListeningSettings.playCountThresholdKey, value)
                    .apply()
            },
            valueText = { formatListeningDuration(it) }
        )
    }

    if (showRangeDialog) {
        DateRangePickerDialog(
            onDismiss = { showRangeDialog = false },
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showRangeDialog = false
            },
            initialStart = viewModel.customStart ?: LocalDate.now().minusDays(29),
            initialEnd = viewModel.customEnd ?: LocalDate.now()
        )
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            .asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        item(key = "header", contentType = 0) {
            Header(
                title = "Listening Insights",
                modifier = Modifier.padding(end = 16.dp)
            ) {
                HeaderIconButton(
                    icon = R.drawable.time,
                    color = colorPalette.text,
                    onClick = { showThresholdDialog = true }
                )
            }
        }

        item(key = "period", contentType = 1) {
            PeriodSelectorRow(
                viewModel = viewModel,
                onCustomClick = { showRangeDialog = true }
            )
        }

        item(key = "overview", contentType = 2) {
            val overview = viewModel.overview
            if (overview == null) {
                LoadingSection()
            } else if (overview.isEmpty) {
                EmptyState(
                    title = "Nothing here yet",
                    text = "Play some music and your listening statistics will appear here."
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    StatsCardRow(
                        first = StatsCardData(
                            title = "Listening time",
                            value = formatListeningDuration(overview.totalListeningMs),
                            iconId = R.drawable.time
                        ),
                        second = StatsCardData(
                            title = "Songs played",
                            value = formatCount(overview.totalPlays),
                            subtitle = "counted plays",
                            iconId = R.drawable.musical_notes
                        )
                    )

                    StatsCardRow(
                        first = StatsCardData(
                            title = "Unique songs",
                            value = formatCount(overview.uniqueSongs),
                            iconId = R.drawable.star
                        ),
                        second = StatsCardData(
                            title = "Artists listened",
                            value = formatCount(overview.uniqueArtists),
                            subtitle = if (overview.unmappedArtistPlays > 0) {
                                formatPlural(overview.unmappedArtistPlays, "play") + " without artist info"
                            } else {
                                null
                            },
                            iconId = R.drawable.person
                        )
                    )

                    StatsCardRow(
                        first = StatsCardData(
                            title = "Average daily",
                            value = formatListeningDuration(overview.averageDailyMs),
                            subtitle = "over " + formatPlural(overview.periodDays, "day"),
                            iconId = R.drawable.trending
                        ),
                        second = StatsCardData(
                            title = "Active days",
                            value = formatCount(overview.activeDays),
                            subtitle = "days with listening",
                            iconId = R.drawable.calendar
                        )
                    )

                    StatsCardRow(
                        first = StatsCardData(
                            title = "Current streak",
                            value = formatPlural(overview.currentStreakDays.toLong(), "day"),
                            iconId = R.drawable.sparkles
                        ),
                        second = StatsCardData(
                            title = "Longest streak",
                            value = formatPlural(overview.longestStreakDays.toLong(), "day"),
                            iconId = R.drawable.star
                        )
                    )
                }
            }
        }

        item(key = "chart", contentType = 3) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                SectionTitle("Listening time")
                ListeningBarChart(points = viewModel.trend)
            }
        }

        item(key = "top-songs", contentType = 4) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                SectionTitle("Top songs")
                if (viewModel.topSongs.isEmpty()) {
                    BasicText(
                        text = "No songs counted in this period yet",
                        style = typography.xxs.secondary
                    )
                } else {
                    viewModel.topSongs.forEachIndexed { index, entry ->
                        TopTrackItem(
                            rank = index + 1,
                            entry = entry,
                            thumbnailSizePx = thumbnailSizePx,
                            thumbnailSizeDp = thumbnailSizeDp,
                            onClick = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(
                                    viewModel.topSongs.map(TopSongEntry::asMediaItem),
                                    index
                                )
                            }
                        )
                    }

                    ShowAllButton(onClick = { onOpenTab(1) })
                }
            }
        }

        item(key = "top-artists", contentType = 5) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                SectionTitle("Top artists")
                if (viewModel.topArtists.isEmpty()) {
                    BasicText(
                        text = "No artists counted in this period yet",
                        style = typography.xxs.secondary
                    )
                } else {
                    viewModel.topArtists.forEachIndexed { index, artist ->
                        TopArtistRow(
                            rank = index + 1,
                            name = artist.name ?: artist.artistId,
                            detail = formatListeningDuration(artist.totalPlayedMs) +
                                " · " + formatPlural(artist.playCount, "play"),
                            thumbnailUrl = artist.thumbnailUrl,
                            onClick = { onArtistClick(artist.artistId) }
                        )
                    }

                    ShowAllButton(onClick = { onOpenTab(2) })
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Shared pieces
// ----------------------------------------------------------------------

@Composable
internal fun SectionTitle(text: String) {
    val (_, typography) = LocalAppearance.current
    BasicText(
        text = text,
        style = typography.s.semiBold
    )
}

@Composable
internal fun ShowAllButton(
    onClick: () -> Unit,
    text: String = "Show all"
) {
    val (colorPalette, typography) = LocalAppearance.current
    BasicText(
        text = text,
        style = typography.xxs.semiBold.let { it.copy(color = colorPalette.accent) },
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
internal fun EmptyState(title: String, text: String) {
    val (colorPalette, typography) = LocalAppearance.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorPalette.background1)
            .padding(all = 24.dp)
    ) {
        BasicText(
            text = title,
            style = typography.m.semiBold.center
        )
        BasicText(
            text = text,
            style = typography.xs.secondary.center
        )
    }
}

@Composable
private fun LoadingSection() {
    val (colorPalette) = LocalAppearance.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorPalette.shimmer)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorPalette.shimmer)
                )
            }
        }
    }
}

@Composable
internal fun PeriodSelectorRow(
    viewModel: ListeningStatsViewModel,
    onCustomClick: () -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            ListeningPeriodType.values().forEach { type ->
                val selected = viewModel.periodType == type
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected) colorPalette.accent else colorPalette.background1)
                        .clickable {
                            if (type == ListeningPeriodType.Custom) {
                                onCustomClick()
                            } else {
                                viewModel.selectPeriod(type)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    BasicText(
                        text = type.label,
                        style = typography.xxs.semiBold.let {
                            it.copy(
                                color = if (selected) colorPalette.onAccent else colorPalette.textSecondary
                            )
                        }
                    )
                }
            }
        }

        if (viewModel.periodType == ListeningPeriodType.Custom &&
            viewModel.customStart != null && viewModel.customEnd != null
        ) {
            BasicText(
                text = "${viewModel.customStart!!.format(shortDate)} – ${viewModel.customEnd!!.format(shortDate)}",
                style = typography.xxs.secondary
            )
        }
    }
}

@Composable
internal fun TopArtistRow(
    rank: Int,
    name: String,
    detail: String,
    thumbnailUrl: String?,
    onClick: (() -> Unit)? = null
) {
    val (colorPalette, typography) = LocalAppearance.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        BasicText(
            text = rank.toString(),
            style = typography.m.semiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(28.dp)
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colorPalette.background1)
        ) {
            BasicText(
                text = name.take(1).uppercase(Locale.ENGLISH),
                style = typography.s.semiBold.let { it.copy(color = colorPalette.accent) }
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            BasicText(
                text = name,
                style = typography.xs.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = detail,
                style = typography.xxs.secondary,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

// ----------------------------------------------------------------------
// Custom date range dialog
// ----------------------------------------------------------------------

/**
 * Minimal date-range picker (the app targets Compose 1.3 without Material3,
 * so there is no ready-made picker): one month grid, tap once for the start
 * day, tap again for the end day; the selection restarts afterwards.
 */
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    initialStart: LocalDate,
    initialEnd: LocalDate
) {
    val (colorPalette, typography) = LocalAppearance.current

    var month by remember { mutableStateOf(initialStart.withDayOfMonth(1)) }
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }

    val days = buildList {
        val first = month.withDayOfMonth(1)
        repeat(first.dayOfWeek.value - 1) { add(null) }
        for (day in 1..month.lengthOfMonth()) add(month.withDayOfMonth(day))
        while (size % 7 != 0) add(null)
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(all = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorPalette.background1)
                .padding(all = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                HeaderIconButton(
                    icon = R.drawable.chevron_back,
                    color = colorPalette.text,
                    onClick = { month = month.minusMonths(1) }
                )

                BasicText(
                    text = month.month.getDisplayName(
                        java.time.format.TextStyle.FULL,
                        Locale.ENGLISH
                    ) + " " + month.year,
                    style = typography.s.semiBold
                )

                HeaderIconButton(
                    icon = R.drawable.chevron_forward,
                    color = colorPalette.text,
                    onClick = { month = month.plusMonths(1) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            days.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { date ->
                        val selected = date != null && !date.isBefore(start) && !date.isAfter(end)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        selected -> colorPalette.accent
                                        date != null -> colorPalette.background2
                                        else -> colorPalette.background1
                                    }
                                )
                                .clickable(enabled = date != null) {
                                    date ?: return@clickable
                                    if (start == end) {
                                        // second tap: complete the range
                                        if (date < start) {
                                            end = start
                                            start = date
                                        } else {
                                            end = date
                                        }
                                    } else {
                                        // start a new selection
                                        start = date
                                        end = date
                                    }
                                }
                        ) {
                            if (date != null) {
                                BasicText(
                                    text = date.dayOfMonth.toString(),
                                    style = typography.xxs.semiBold.let {
                                        it.copy(
                                            color = if (selected) colorPalette.onAccent else colorPalette.text
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            BasicText(
                text = "${start.format(shortDate)} – ${end.format(shortDate)}",
                style = typography.xs.secondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                DialogTextButton(
                    text = "Cancel",
                    onClick = onDismiss
                )

                DialogTextButton(
                    text = "Apply",
                    primary = true,
                    onClick = {
                        onConfirm(start, end)
                    }
                )
            }
        }
    }
}
