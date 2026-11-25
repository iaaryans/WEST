package it.vfsfitvnm.vimusic.ui.screens.home

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.routing.Route0
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.requests.MoodExploreCategory
import it.vfsfitvnm.innertube.requests.MoodExplorePlaylist
import it.vfsfitvnm.innertube.requests.MoodExploreShelf
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.intelligence.IntelligencePreferences
import it.vfsfitvnm.vimusic.intelligence.RecommendationRepository
import it.vfsfitvnm.vimusic.intelligence.IntelligenceSnapshot
import it.vfsfitvnm.vimusic.ui.performance.MusicBrowseCache
import it.vfsfitvnm.vimusic.ui.performance.SmoothArtwork
import it.vfsfitvnm.vimusic.ui.performance.rememberPressMotion
import it.vfsfitvnm.vimusic.personalization.TasteProfileStore
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.preferences
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.supervisorScope

internal val moodspaceRoute = Route0("moodspace")

/**
 * WEST Moodspace Explore rebuild. The two-column, 1.72:1 tinted sleeves,
 * category browse pages and artwork loading follow BitChord Explore's GPL-3.0
 * visual treatment. The page uses WEST's typography, player and intelligence.
 * See MOODSPACE-EXPLORE-ATTRIBUTION.txt for source attribution.
 */
@Composable
internal fun MoodspaceScreen(onBack: (() -> Unit)? = null, onBrowseClick: (String) -> Unit = {}) {
    val (palette, typography) = LocalAppearance.current
    val prefs = LocalContext.current.preferences
    val binder = LocalPlayerServiceBinder.current
    val profile by remember(prefs) { TasteProfileStore.observe(prefs) }
        .collectAsState(initial = TasteProfileStore.load(prefs))
    val enabled = IntelligencePreferences.enabled(prefs)
    var categories by remember {
        mutableStateOf(MusicBrowseCache.peekExploreCategories()
            ?.takeIf { it.isNotEmpty() } ?: MoodspaceCatalog.fallback)
    }
    var refresh by rememberSaveable { mutableStateOf(0) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf(false) }
    val artwork = remember { mutableStateMapOf<String, String>() }
    // Maintain a bounded, per-screen negative/positive lookup ledger; don't launch
    // network work from each card composition while the user is flinging.
    // The calculation returns a mutable set, not Unit; older Compose lint misidentifies it.
    @Suppress("RememberReturnType")
    val attemptedArtworks = remember { mutableSetOf<String>() }
    // The offline catalog is rendered immediately, before the network has responded.
    LaunchedEffect(refresh) {
        catalogLoading = true
        catalogError = false
        try {
            if (refresh > 0) { artwork.clear(); attemptedArtworks.clear() }
            val remote = MusicBrowseCache.exploreCategories(force = refresh > 0)
            if (remote.isNotEmpty()) categories = remote else catalogError = true
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            Log.w("WEST_Moodspace", "Category catalog failed; using offline options", error)
            catalogError = true
        }
        finally { catalogLoading = false }
    }
    val sorted = remember(categories, profile.moods) {
        MoodspaceCatalog.prioritize(categories, profile.moods)
    }

    // A category is a subpage of Moodspace, not a new bottom navigation tab.
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember<MoodExploreCategory?>(sorted, selectedKey) {
        sorted.firstOrNull { MoodspaceSafety.categoryId(it) == selectedKey }
    }
    val listState = rememberLazyListState()
    var lastOpenedCategory by remember { mutableStateOf(selectedKey) }
    LaunchedEffect(selectedKey) {
        if (lastOpenedCategory != selectedKey) listState.scrollToItem(0)
        lastOpenedCategory = selectedKey
    }
    // Avoid collecting 1,200 listening sessions and re-ranking the library
    // while the user is just scrolling through the mood-card grid.
    val intelligence = if (selectedKey != null) {
        val current by remember(prefs) { RecommendationRepository.observe(prefs) }
            .collectAsState(initial = IntelligenceSnapshot())
        current
    } else IntelligenceSnapshot()
    val closeCategory = { selectedKey = null }
    BackHandler(enabled = selectedKey != null) { closeCategory() }

    var shelves by remember(selectedKey) { mutableStateOf(emptyList<MoodExploreShelf>()) }
    var shelvesLoading by remember(selectedKey) { mutableStateOf(false) }
    var songs by remember(selectedKey) { mutableStateOf(emptyList<Innertube.SongItem>()) }
    var songsLoading by remember(selectedKey) { mutableStateOf(false) }
    var resultsFailed by remember(selectedKey) { mutableStateOf(false) }
    var categoryRefresh by rememberSaveable { mutableStateOf(0) }
    val tasteArtist = profile.artists.firstOrNull()?.name
    val tasteLanguage = profile.languages.firstOrNull { it != "Other" }
    val recommendationIds = intelligence.madeForYou.map { it.song.id }.toSet() +
        IntelligencePreferences.more(prefs)
    val mutedIds = if (enabled) IntelligencePreferences.less(prefs) else emptySet()

    LaunchedEffect(selected?.browseId, selected?.params, categoryRefresh) {
        val item = selected ?: return@LaunchedEffect
        shelvesLoading = true
        shelves = emptyList()
        try {
            if (item.browseId.isNotBlank()) {
                shelves = MoodspaceSafety.sections(
                    MusicBrowseCache.categoryShelves(item, force = categoryRefresh > 0)
                )
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            Log.w("WEST_Moodspace", "Shelf loading failed for ${item.title}; using search", error)
            // Search-based discovery remains available even if the browse payload is malformed.
        }
        finally { shelvesLoading = false }
    }
    LaunchedEffect(selectedKey, tasteArtist, tasteLanguage, enabled, categoryRefresh) {
        val name = selected?.title ?: return@LaunchedEffect
        songs = emptyList()
        songsLoading = true
        resultsFailed = false
        try {
            val seen = LinkedHashMap<String, Innertube.SongItem>()
            val queries = MoodspaceCatalog.discoveryQueries(name, tasteArtist, tasteLanguage, enabled)
            for (query in queries) {
                val found = MusicBrowseCache.songs(query, force = categoryRefresh > 0)
                found.forEach { song ->
                    val id = song.info?.endpoint?.videoId
                    if (!id.isNullOrBlank() && song.durationText != null && id !in mutedIds)
                        if (id !in seen) seen[id] = song
                }
                // Show the first results immediately; do not freeze the screen
                // while a second or third personalized query is still running.
                songs = seen.values.sortedWith(compareByDescending<Innertube.SongItem> {
                    enabled && it.key in recommendationIds
                }).take(24)
                if (seen.size >= 18) break
            }
            resultsFailed = songs.isEmpty()
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            Log.w("WEST_Moodspace", "Track discovery failed for $name", error)
            resultsFailed = true
        }
        finally { songsLoading = false }
    }

    val imageKey: (MoodExploreCategory) -> String = { MoodspaceSafety.categoryId(it) }
    val songQueue = remember(songs) { songs.map { it.asMediaItem } }
    val screenPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.background0)) {
        val columns = (((maxWidth - 32.dp + 12.dp) / (220.dp + 12.dp)).toInt()).coerceIn(2, 6)
        val rows = remember<List<List<MoodExploreCategory>>>(sorted, columns) { sorted.chunked(columns) }
        // Only resolve covers for rows actually on screen (plus the next row).
        // During a fling the work is cancelled; the tinted cards remain visible.
        LaunchedEffect(selectedKey, rows, columns, refresh) {
            if (selectedKey != null) return@LaunchedEffect
            snapshotFlow {
                listState.isScrollInProgress to
                    listState.layoutInfo.visibleItemsInfo.map { it.index }
            }.distinctUntilChanged().collectLatest { (scrolling, visible) ->
                if (scrolling || visible.isEmpty()) return@collectLatest
                delay(170) // Do not compete with scrolling/frame composition.
                val planned = MoodArtworkPrefetchPolicy.visible(
                    sorted, columns, visible, maxRequests = 6
                ).filter { category ->
                    val id = imageKey(category)
                    category.imageUrl == null && category.browseId.isNotBlank() &&
                        id !in artwork && id !in attemptedArtworks
                }
                supervisorScope {
                    planned.map { category ->
                        async {
                            val id = imageKey(category)
                            if (!attemptedArtworks.add(id)) return@async
                            var completed = false
                            try {
                                val cover = MusicBrowseCache.categoryArtwork(category)
                                if (!cover.isNullOrBlank()) artwork[id] = cover
                                completed = true
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (error: Exception) {
                                Log.w("WEST_Moodspace", "Artwork fallback: ${category.title}", error)
                                completed = true
                            } finally {
                                if (!completed) attemptedArtworks.remove(id)
                            }
                        }
                    }.awaitAll()
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp, end = 0.dp,
                top = screenPadding.calculateTopPadding() + 8.dp,
                bottom = screenPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (selected == null) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        onBack?.let { ActionLabel("Back", it) }
                        BasicText("Moodspace", style = typography.xxl.semiBold)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText("Explore moods & genres", style = typography.xs.secondary)
                            ActionLabel("Refresh") { refresh++ }
                        }
                        if (catalogError) BasicText(
                            "Showing available categories offline · Refresh to retry online discovery",
                            style = typography.xxs.secondary
                        )
                        if (catalogLoading) BasicText("Updating categories…", style = typography.xxs.secondary)
                        Spacer(Modifier.height(14.dp))
                    }
                }
                itemsIndexed(rows, key = { index, _ -> "moodspace/grid-row/$index" }) { _, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { category ->
                            val key = imageKey(category)
                            // Loading artwork is scoped to visible cards, not all catalog entries.
                            MoodExploreCard(
                                category = category,
                                imageUrl = artwork[key] ?: MusicBrowseCache.peekCategoryArtwork(category),
                                modifier = Modifier.weight(1f),
                                onOpen = { selectedKey = MoodspaceSafety.categoryId(category) }
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ActionLabel("‹ All moods & genres") { closeCategory() }
                        BasicText(selected.title, style = typography.xxl.semiBold)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText("Explore playlists and songs", style = typography.xs.secondary)
                            ActionLabel("Refresh") { categoryRefresh++ }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
                if (shelvesLoading && shelves.isEmpty()) item {
                    BasicText("Finding ${selected.title} playlists…",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = typography.xs.secondary)
                }
                itemsIndexed(shelves, key = { index, _ ->
                    MoodspaceSafety.shelfKey(selectedKey.orEmpty(), index)
                }) { shelfIndex, shelf ->
                    Column(Modifier.padding(bottom = 18.dp)) {
                        BasicText(shelf.title,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = typography.l.semiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)) {
                            itemsIndexed(shelf.playlists, key = { position, _ ->
                                MoodspaceSafety.playlistKey(selectedKey.orEmpty(), shelfIndex, position)
                            }) { _, playlist ->
                                MoodExplorePlaylistTile(playlist) {
                                    if (MoodspaceSafety.browseTarget(playlist.browseId) != null) {
                                        onBrowseClick(playlist.browseId)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            BasicText("Recommended songs", style = typography.l.semiBold)
                            BasicText(if (enabled) "Personalized by WEST Intelligence" else "Discover ${selected.title} tracks",
                                style = typography.xxs.secondary)
                        }
                        ActionLabel("Play all") {
                            songs.firstOrNull()?.let { first ->
                                binder?.playSmartQueue(first.asMediaItem, songQueue)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (songsLoading && songs.isEmpty()) item {
                    BasicText("Discovering music…", modifier = Modifier.padding(16.dp),
                        style = typography.xs.secondary)
                }
                if (resultsFailed && songs.isEmpty()) item {
                    BasicText("Recommendations unavailable. Check your connection and try Refresh.",
                        modifier = Modifier.padding(16.dp), style = typography.xs.secondary)
                }
                items(songs, key = { "mood/${it.key}" }) { song ->
                    val thumbnail = Dimensions.thumbnails.song
                    SongItem(song, thumbnail.px, thumbnail,
                        modifier = Modifier.clickable {
                            binder?.playSmartQueue(song.asMediaItem, songQueue)
                        })
                }
            }
        }
    }
}

@Composable
private fun MoodExploreCard(
    category: MoodExploreCategory,
    imageUrl: String?,
    modifier: Modifier,
    onOpen: () -> Unit
) {
    val (_, typography) = LocalAppearance.current
    val tone = remember(category.stripeColor, category.title) { moodExploreTone(category) }
    val (pressSource, pressModifier) = rememberPressMotion()
    Box(modifier.then(pressModifier).aspectRatio(1.72f).clip(RoundedCornerShape(18.dp))
        .background(tone.stripe).clickable(interactionSource = pressSource, indication = null, onClick = onOpen)) {
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.72f)
            .background(tone.placeholder)) {
            if (imageUrl != null) SmoothArtwork(
                url = imageUrl, targetPixels = 320,
                colorFilter = tone.duotone, modifier = Modifier.fillMaxSize()
            )
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.Black.copy(alpha = .24f),
                .62f to Color.Transparent)))
        BasicText(category.title,
            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 14.dp, vertical = 12.dp),
            style = typography.s.semiBold.copy(
                color = Color.White, fontWeight = FontWeight.Bold,
                shadow = Shadow(Color.Black.copy(alpha = .35f), Offset(0f, 1f), 6f)
            ), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MoodExplorePlaylistTile(playlist: MoodExplorePlaylist, onClick: () -> Unit) {
    val (palette, typography) = LocalAppearance.current
    Column(Modifier.width(136.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(136.dp).clip(RoundedCornerShape(14.dp))
            .background(palette.background2)) {
            SmoothArtwork(url = playlist.imageUrl, targetPixels = 272,
                modifier = Modifier.fillMaxSize())
        }
        BasicText(playlist.title, style = typography.xs.semiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private class MoodExploreTone(val stripe: Color, val placeholder: Color, val duotone: ColorFilter)

private fun moodExploreTone(category: MoodExploreCategory): MoodExploreTone {
    val base = category.stripeColor?.let { Color(it.toInt()) } ?: when (
        (category.title.hashCode() and Int.MAX_VALUE) % 8
    ) {
        0 -> Color(0xFFCC6A55)
        1 -> Color(0xFFC07A92)
        2 -> Color(0xFF9C8AC0)
        3 -> Color(0xFF8090C8)
        4 -> Color(0xFFD0A060)
        5 -> Color(0xFF6A88B0)
        6 -> Color(0xFF7AAED0)
        else -> Color(0xFF86B890)
    }
    val shadow = lerp(base, Color.Black, .68f)
    val highlight = lerp(base, Color.White, .32f)
    fun channel(start: Float, finish: Float): FloatArray {
        val width = finish - start
        return floatArrayOf(width * .299f, width * .587f, width * .114f, 0f, start * 255f)
    }
    val matrix = ColorMatrix(channel(shadow.red, highlight.red) +
        channel(shadow.green, highlight.green) + channel(shadow.blue, highlight.blue) +
        floatArrayOf(0f, 0f, 0f, 1f, 0f))
    return MoodExploreTone(
        stripe = lerp(base, Color.Black, .08f),
        placeholder = lerp(shadow, highlight, .45f),
        duotone = ColorFilter.colorMatrix(matrix)
    )
}
