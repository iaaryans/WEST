package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.ui.performance.MusicBrowseCache
import it.vfsfitvnm.vimusic.personalization.TasteProfileStore
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.preferences
import it.vfsfitvnm.vimusic.utils.secondary
import kotlinx.coroutines.CancellationException
import it.vfsfitvnm.compose.persist.persist

internal data class TasteDiscoveryFeed(
    val key: List<String> = emptyList(),
    val enabled: Boolean = false,
    val moods: List<String> = emptyList(),
    val songs: List<Innertube.SongItem> = emptyList(),
    val loading: Boolean = false
)

@Composable
internal fun rememberTasteDiscoveryFeed(store: HomeFeedStore, intelligenceEnabled: Boolean): State<TasteDiscoveryFeed> {
    val prefs = LocalContext.current.preferences
    val profile by remember(prefs) { TasteProfileStore.observe(prefs) }
        .collectAsState(initial = remember(prefs) { TasteProfileStore.load(prefs) })
    val enabled = intelligenceEnabled &&
        (profile.artists.isNotEmpty() || profile.songs.isNotEmpty())
    val artists = profile.artists.take(3).map { it.name }
    val titles = profile.songs.take(1).map { it.title }
    val language = profile.languages.firstOrNull { it != "Other" }
    val seeds = (artists + titles).filter { it.isNotBlank() }.take(3)
    val key = listOf(language.orEmpty()) + seeds
    val moods = profile.moods.take(3)
    val state = persist("home/fluid/taste", TasteDiscoveryFeed())
    LaunchedEffect(store, key, enabled, moods) {
        if (!enabled || seeds.isEmpty()) {
            state.value = TasteDiscoveryFeed()
            return@LaunchedEffect
        }
        val warm = state.value.takeIf { it.key == key && it.enabled }
        store.awaitIdle()
        state.value = TasteDiscoveryFeed(key, true, moods, warm?.songs.orEmpty(), true)
        try {
            val songs = store.loadDiscovery {
                val found = LinkedHashMap<String, Innertube.SongItem>()
                for (name in seeds) {
                    val query = listOfNotNull(name, language, "songs").joinToString(" ")
                    MusicBrowseCache.songs(query).forEach { item ->
                        val id = item.info?.endpoint?.videoId
                        if (!id.isNullOrBlank() && item.durationText != null && id !in found) found[id] = item
                    }
                    if (found.size >= 18) break
                }
                found.values.take(20)
            }
            store.awaitIdle()
            state.value = TasteDiscoveryFeed(key, true, moods, songs.ifEmpty { warm?.songs.orEmpty() })
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) {
            store.awaitIdle()
            state.value = TasteDiscoveryFeed(key, true, moods, warm?.songs.orEmpty())
        }
    }
    return state
}

/** Rendering only: no network work starts or stops when this lazy item enters/leaves view. */
@Composable
internal fun TasteDiscoverySection(feed: TasteDiscoveryFeed, width: Dp) {
    if (!feed.enabled) return
    val (_, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val results = feed.songs
    val loading = feed.loading
    val pages = remember(results) { HomeShelfPolicy.pages(results) }
    val queue = remember(results) { results.map { it.asMediaItem } }
    HomeShelfTitle("Picked for you", "Based on your artists and favorite songs · WEST Intelligence")
    if (feed.moods.isNotEmpty()) {
        BasicText("Your vibes: ${feed.moods.joinToString(" · ")}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = typography.xs.secondary)
    }
    if (results.isNotEmpty()) {
        val art = Dimensions.thumbnails.song
        HomeSongPages(pages, width, { it.key }) { song ->
            HomeSongRow(song, art, modifier = Modifier.clickable {
                binder?.playSmartQueue(song.asMediaItem, queue)
            })
        }
    } else {
        BasicText(if (loading) "Finding music for you…" else "Your personalized discoveries are temporarily unavailable.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = typography.xs.secondary)
    }
}
