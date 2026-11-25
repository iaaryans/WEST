package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import it.vfsfitvnm.vimusic.utils.preferences
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import it.vfsfitvnm.compose.persist.persist
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.performance.SmoothArtwork
import it.vfsfitvnm.vimusic.ui.performance.HomeRelatedFeedCache
import it.vfsfitvnm.vimusic.ui.performance.rememberPressMotion
import it.vfsfitvnm.vimusic.ui.components.themed.HeaderIconButton
import it.vfsfitvnm.vimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.vfsfitvnm.vimusic.ui.items.AlbumItem
import it.vfsfitvnm.vimusic.ui.items.ArtistItem
import it.vfsfitvnm.vimusic.ui.items.PlaylistItem
import it.vfsfitvnm.vimusic.ui.items.SongItemPlaceholder
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.DisposableListener
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.shouldBePlaying
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WEST's Listen Now home, adapted to the BitChord Home layout from the supplied source:
 * portrait Top Picks, four-track Recents pages and artwork-led horizontal shelves.
 * WEST owns its typography, music providers, Smart Queue and Intelligence signals.
 * No Moodspace promotion or shortcut is placed on Home; it has its own nav tab.
 * See HOME-EXPLORE-ATTRIBUTION.txt for source and GPL-3.0 attribution.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun QuickPicks(
    onMix: () -> Unit,
    onFind: () -> Unit,
    onImport: () -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onInsights: () -> Unit,
    onSettings: () -> Unit,
    onOffline: () -> Unit,
) {
    val (palette, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    var trending by persist<Song?>("home/trending")
    var relatedPageResult by persist<Result<Innertube.RelatedPage?>?>("home/relatedPageResult")
    val listState = rememberLazyListState()
    val store = remember(listState) { HomeFeedStore(snapshotFlow { listState.isScrollInProgress }) }
    val discoveryPrefs = rememberHomeDiscoveryPreferences(LocalContext.current.preferences)
    val tasteFeed = rememberTasteDiscoveryFeed(store, discoveryPrefs.enabled)
    val intelligenceFeed = rememberIntelligenceFeed(store)
    val freshFeed = rememberFreshDiscoveryFeed(store, discoveryPrefs)
    var recent by persist("home/fluid/recents", emptyList<Song>())
    LaunchedEffect(store) {
        Database.recentlyPlayed().distinctUntilChanged().collectLatest { songs ->
            store.awaitIdle()
            recent = songs
        }
    }
    val hasLibrary by remember { Database.hasLibraryContent() }.collectAsState(initial = null)
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(retry, store) {
        var forceRefresh = retry > 0
        Database.trending().distinctUntilChangedBy { it?.id }.collectLatest { song ->
            val seed = song?.id ?: "J7p4bzqLvCw"
            val changed = trending?.id != song?.id
            store.awaitIdle()
            trending = song
            val warm = HomeRelatedFeedCache.peek(seed)
            if (warm != null && !forceRefresh) relatedPageResult = warm
            // Keep the last displayed feed while its new recommendations load.
            if (changed || warm == null || forceRefresh || relatedPageResult == null) {
                val next = withContext(Dispatchers.Default) { HomeRelatedFeedCache.load(seed, force = forceRefresh) }
                store.awaitIdle()
                relatedPageResult = next
            }
            forceRefresh = false
        }
    }

    val related = relatedPageResult?.getOrNull()
    val relatedSongs = remember(related) {
        HomeShelfPolicy.unique(related?.songs.orEmpty().filter {
            it.info?.endpoint?.videoId != null
        }) { it.key }
    }
    val relatedAlbums = remember(related) { HomeShelfPolicy.unique(
        related?.albums.orEmpty().filter { it.info?.endpoint?.browseId != null }
    ) { it.key } }
    val relatedArtists = remember(related) { HomeShelfPolicy.unique(
        related?.artists.orEmpty().filter { it.info?.endpoint?.browseId != null }
    ) { it.key } }
    val relatedPlaylists = remember(related) { HomeShelfPolicy.unique(
        related?.playlists.orEmpty().filter { it.info?.endpoint?.browseId != null }
    ) { it.key } }
    val recents = remember(recent) { HomeShelfPolicy.unique(recent) { it.id }.take(32) }
    val recentPages = remember(recents) { HomeShelfPolicy.pages(recents) }
    val quickRows = remember(relatedSongs, trending) {
        (listOfNotNull(trending?.let { HomePickRow.Local(it) }) +
            relatedSongs.map { HomePickRow.Online(it) }).let {
            HomeShelfPolicy.unique(it) { row -> row.id }
        }
    }
    val quickPages = remember(quickRows) { HomeShelfPolicy.pages(quickRows) }
    val quickMedia = remember(relatedSongs, trending) {
        (listOfNotNull(trending?.asMediaItem) + relatedSongs.map { it.asMediaItem })
            .distinctBy { it.mediaId }
    }
    val songArt = Dimensions.thumbnails.song
    val coverDp = 148.dp
    val coverPx = coverDp.px
    val artistDp = 116.dp
    val artistPx = artistDp.px
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    // Every playable Home shelf goes through WEST Hybrid Smart Queue, not single-track forcePlay.
    fun playLocal(song: Song, context: List<Song>) {
        binder?.playSmartQueue(song.asMediaItem, context.map(Song::asMediaItem))
    }
    fun playOnline(song: Innertube.SongItem) {
        binder?.playSmartQueue(song.asMediaItem, relatedSongs.map { it.asMediaItem })
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidth = minOf(maxWidth * 0.88f, 440.dp)
        val cardWidth = minOf(maxWidth * 0.60f, 280.dp)
        CompositionLocalProvider(LocalHomeFeedStore provides store,
            LocalHomeSinglePageFits provides (pageWidth + 32.dp <= maxWidth)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(palette.background0),
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding() + 20.dp
            )
        ) {
            item(key = "west-wordmark", contentType = "header") {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        // Compact, tightly cropped theme-aware wordmark. Both variants
                        // retain WEST's red wave, with no extra tagline on Home.
                        Column(modifier = Modifier.weight(1f)) {
                            Image(
                                painter = painterResource(
                                    if (palette.isDark) R.drawable.west_logo_wordmark_dark
                                    else R.drawable.west_logo_wordmark_light
                                ),
                                contentDescription = "WEST logo",
                                modifier = Modifier.widthIn(max = 152.dp)
                                    .fillMaxWidth()
                                    .height(40.dp),
                                alignment = Alignment.CenterStart,
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.height(2.dp))
                            BasicText("Listen now", style = typography.xs.secondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActionLabel("Insights", onInsights)
                            HeaderIconButton(onClick = onSettings, icon = R.drawable.gear,
                                color = palette.text, description = "Settings")
                        }
                    }
                }
            }

            // The lead shelf uses 3:4 portrait cards with artwork and overlaid labels.
            // Its recommendations come from WEST playback, rather than fake hardcoded songs.
            item(key = "top-picks-title", contentType = "shelf-title") {
                HomeShelfTitle(HomeShelfPolicy.TOP_PICKS, "A starting point made for your listening")
            }
            item(key = "top-picks", contentType = "portrait-shelf") {
                Column(Modifier.fillMaxWidth()) {
                    HomeHorizontalRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item(key = "station", contentType = "portrait-card") {
                            PortraitPickCard(
                                eyebrow = "MADE FOR YOU",
                                title = "Your Station",
                                subtitle = "Your favorites and similar music",
                                artworkUrl = recents.firstOrNull()?.thumbnailUrl ?: trending?.thumbnailUrl,
                                color = Color(0xFF56345D), width = cardWidth,
                                onClick = {
                                    val seed = recents.firstOrNull() ?: trending
                                    if (seed != null) playLocal(seed, recents.ifEmpty { listOf(seed) })
                                    else onFind()
                                }
                            )
                        }
                        item(key = "discovery", contentType = "portrait-card") {
                            PortraitPickCard(
                                eyebrow = "WEST INTELLIGENCE",
                                title = "Discovery Station",
                                subtitle = "Find your next favorite song",
                                artworkUrl = relatedSongs.firstOrNull()?.thumbnail?.url,
                                color = Color(0xFF244665), width = cardWidth,
                                onClick = {
                                    relatedSongs.firstOrNull()?.let(::playOnline) ?: onFind()
                                }
                            )
                        }
                        relatedAlbums.take(3).forEach { album ->
                            item(key = "album/${album.key}", contentType = "portrait-card") {
                                PortraitPickCard(
                                    eyebrow = "RECOMMENDED ALBUM",
                                    title = album.info?.name.orEmpty(),
                                    subtitle = album.authors?.joinToString(", ") { it.name.orEmpty() }.orEmpty(),
                                    artworkUrl = album.thumbnail?.url,
                                    color = Color(0xFF45573A), width = cardWidth,
                                    onClick = { onAlbumClick(album.key) }
                                )
                            }
                        }
                        relatedPlaylists.take(2).forEach { playlist ->
                            item(key = "playlist/${playlist.key}", contentType = "portrait-card") {
                                PortraitPickCard(
                                    eyebrow = "PICKED FOR YOU",
                                    title = playlist.info?.name.orEmpty(),
                                    subtitle = playlist.channel?.name.orEmpty(),
                                    artworkUrl = playlist.thumbnail?.url,
                                    color = Color(0xFF634335), width = cardWidth,
                                    onClick = { onPlaylistClick(playlist.key) }
                                )
                            }
                        }
                    }
                }
            }

            if (recents.isNotEmpty()) {
                item(key = "recents-title", contentType = "shelf-title") {
                    HomeShelfTitle(HomeShelfPolicy.RECENTS, "Pick up where you left off")
                }
                item(key = "recents", contentType = "song-shelf") { HomeRecentShelf(recents, recentPages, pageWidth) }
            }
            // Subscribes to player events only when its own LazyColumn item is visible.
            // A song/position change no longer recomposes every top-pick and discovery shelf.
            item(key = "continue-listening", contentType = "playing-shelf") { HomeNowPlayingShelf() }

            item(key = "taste-discovery", contentType = "song-shelf") { TasteDiscoverySection(tasteFeed.value, pageWidth) }
            item(key = "local-intelligence", contentType = "intelligence-shelf") {
                IntelligenceSection(intelligenceFeed.value, pageWidth, rediscover = false)
            }
            item(key = "forgotten-gems", contentType = "intelligence-shelf") {
                IntelligenceSection(intelligenceFeed.value, pageWidth, rediscover = true)
            }
            item(key = "fresh-discoveries", contentType = "song-shelf") { HomeFreshDiscoveries(freshFeed.value, pageWidth) }

            item(key = "quick-picks-title", contentType = "shelf-title") {
                HomeShelfTitle(HomeShelfPolicy.QUICK_PICKS, "Music related to what you listen to")
            }
            item(key = "quick-picks", contentType = "song-shelf") {
                if (relatedSongs.isNotEmpty() || trending != null) {
                    HomeSongPages(quickPages, pageWidth, { it.id }) { row ->
                        when (row) {
                            is HomePickRow.Local -> HomeSongRow(row.song, songArt,
                                modifier = Modifier.combinedClickable(
                                    onClick = { binder?.playSmartQueue(row.song.asMediaItem, quickMedia) },
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(onDismiss = menuState::hide,
                                                mediaItem = row.song.asMediaItem)
                                        }
                                    }))
                            is HomePickRow.Online -> HomeSongRow(row.song, songArt,
                                modifier = Modifier.combinedClickable(
                                    onClick = { binder?.playSmartQueue(row.song.asMediaItem, quickMedia) },
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(onDismiss = menuState::hide,
                                                mediaItem = row.song.asMediaItem)
                                        }
                                    }))
                        }
                    }
                } else if (relatedPageResult == null) {
                    // Static placeholders do not run a full-screen shimmer animation
                    // while the nested Home feed is scrolling.
                    Column {
                        repeat(4) { SongItemPlaceholder(thumbnailSizeDp = songArt) }
                    }
                } else {
                    BasicText("Recommendations are unavailable right now.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = typography.xs.secondary)
                    Row { ActionLabel("Retry") { relatedPageResult = null; retry++ }; ActionLabel("Offline music", onOffline) }
                }
            }

            relatedAlbums.takeIf { it.isNotEmpty() }?.let { albums ->
                item(key = "albums-title", contentType = "shelf-title") { HomeShelfTitle("Related albums") }
                item(key = "albums", contentType = "album-shelf") {
                    HomeHorizontalRow(horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(albums, key = { it.key }, contentType = { "albums-card" }) { album ->
                            AlbumItem(album, coverPx, coverDp, alternative = true, boundedArtwork = true,
                                modifier = Modifier.clickable { onAlbumClick(album.key) })
                        }
                    }
                }
            }
            relatedArtists.takeIf { it.isNotEmpty() }?.let { artists ->
                item(key = "artists-title", contentType = "shelf-title") { HomeShelfTitle("Similar artists") }
                item(key = "artists", contentType = "artist-shelf") {
                    HomeHorizontalRow(horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(artists, key = { it.key }, contentType = { "artists-card" }) { artist ->
                            ArtistItem(artist, artistPx, artistDp, alternative = true, boundedArtwork = true,
                                modifier = Modifier.clickable { onArtistClick(artist.key) })
                        }
                    }
                }
            }
            relatedPlaylists.takeIf { it.isNotEmpty() }?.let { playlists ->
                item(key = "playlists-title", contentType = "shelf-title") { HomeShelfTitle("Playlists you might like") }
                item(key = "playlists", contentType = "playlist-shelf") {
                    HomeHorizontalRow(horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(playlists, key = { it.key }, contentType = { "playlists-card" }) { playlist ->
                            PlaylistItem(playlist, coverPx, coverDp, alternative = true, boundedArtwork = true,
                                modifier = Modifier.clickable { onPlaylistClick(playlist.key) })
                        }
                    }
                }
            }

            item(key = "shortcuts-title", contentType = "shelf-title") { HomeShelfTitle("Your shortcuts") }
                item(key = "shortcuts", contentType = "shortcuts") {
                HomeHorizontalRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    item { ActionLabel("Find music", onFind) }
                    if (hasLibrary != false) item { ActionLabel("Build a timed mix", onMix) }
                    item { ActionLabel("Import from Spotify", onImport) }
                    item { ActionLabel("Offline music", onOffline) }
                }
            }
        }
        }
    }
}

private sealed class HomePickRow(val id: String) {
    class Local(val song: Song) : HomePickRow(song.id)
    class Online(val song: Innertube.SongItem) : HomePickRow(song.info?.endpoint?.videoId.orEmpty())
}

@Composable
internal fun HomeShelfTitle(title: String, subtitle: String? = null) {
    val (_, typography) = LocalAppearance.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)) {
        BasicText(title, style = typography.l.semiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            BasicText(subtitle, style = typography.xs.secondary,
                modifier = Modifier.padding(top = 3.dp), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Portrait sleeve, 3:4 aspect, with cover art and a legible bottom gradient. */
@Composable
private fun PortraitPickCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    artworkUrl: String?,
    color: Color,
    width: Dp,
    onClick: () -> Unit,
) {
    val (_, typography) = LocalAppearance.current
    val (pressSource, pressModifier) = rememberPressMotion()
    val gradient = remember { Brush.verticalGradient(listOf(
        Color.Black.copy(alpha = .24f), Color.Transparent,
        Color.Black.copy(alpha = .86f)
    )) }
    Box(
        Modifier.width(width).then(pressModifier).aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(12.dp)).background(color)
            .clickable(interactionSource = pressSource, indication = null, onClick = onClick)
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            SmoothArtwork(url = artworkUrl, targetPixels = 480,
                modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(gradient))
        BasicText(eyebrow, Modifier.align(Alignment.TopStart).padding(16.dp),
            style = typography.xxs.semiBold.copy(color = Color.White))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            BasicText(title, style = typography.l.semiBold.copy(color = Color.White),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(subtitle, modifier = Modifier.padding(top = 5.dp),
                style = typography.xs.copy(color = Color.White.copy(alpha = 0.83f)),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Isolated player-event consumer: does not invalidate the entire Home LazyColumn. */
@Composable
private fun HomeRecentShelf(recents: List<Song>, pages: List<List<Song>>, width: Dp) {
    val (_, typography) = LocalAppearance.current
    val player = LocalPlayerServiceBinder.current?.player
    val binder = LocalPlayerServiceBinder.current
    var playingId by remember(player) { mutableStateOf(player?.currentMediaItem?.mediaId) }
    var playing by remember(player) { mutableStateOf(player?.shouldBePlaying == true) }
    player?.DisposableListener {
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                // Distinct UI states: unrelated buffer/position events do not recompose the shelf.
                val id = player.currentMediaItem?.mediaId
                val active = player.shouldBePlaying
                if (playingId != id) playingId = id
                if (playing != active) playing = active
            }
        }
    }
    val art = Dimensions.thumbnails.song
    val queue = remember(recents) { recents.map(Song::asMediaItem) }
    HomeSongPages(pages, width, { it.id }) { song ->
        HomeSongRow(song = song, thumbnailSize = art,
            modifier = Modifier.clickable { binder?.playSmartQueue(song.asMediaItem, queue) },
            trailingContent = if (playingId == song.id && playing) ({
                BasicText("Playing", style = typography.xxs.secondary)
            }) else null)
    }
}

/** Only this item reads the player state; the rest of Home stays compose-skippable. */
@Composable
private fun HomeNowPlayingShelf() {
    val (palette, _) = LocalAppearance.current
    val player = LocalPlayerServiceBinder.current?.player ?: return
    var current by remember(player) { mutableStateOf(player.currentMediaItem) }
    var playing by remember(player) { mutableStateOf(player.shouldBePlaying) }
    player.DisposableListener {
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val newItem = player.currentMediaItem
                val newPlaying = player.shouldBePlaying
                if (current != newItem) current = newItem
                if (playing != newPlaying) playing = newPlaying
            }
        }
    }
    val item = current ?: return
    val art = Dimensions.thumbnails.song
    HomeShelfTitle(if (playing) "Now playing" else "Continue listening")
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        .background(palette.background1, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            HomeSongRow(item, art)
        }
        ActionLabel(if (playing) "Pause" else "Resume") {
            if (playing) player.pause() else player.run {
                if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
                if (playbackState == Player.STATE_IDLE) prepare()
                play()
            }
        }
    }
}
