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

internal data class FreshDiscoveryFeed(
    val key: String = "",
    val songs: List<Innertube.SongItem> = emptyList(),
    val loading: Boolean = false
)

@Composable
internal fun rememberFreshDiscoveryFeed(store: HomeFeedStore, discoveryPrefs: HomeDiscoveryPreferences): State<FreshDiscoveryFeed> {
    val prefs = LocalContext.current.preferences
    val profile by remember(prefs) { TasteProfileStore.observe(prefs) }
        .collectAsState(initial = remember(prefs) { TasteProfileStore.load(prefs) })
    val enabled = discoveryPrefs.enabled
    val language = profile.languages.firstOrNull { it != "Other" }.orEmpty()
    val firstArtist = if (enabled) profile.artists.firstOrNull()?.name.orEmpty() else ""
    val query = listOf(firstArtist, language, "music songs").filter { it.isNotBlank() }
        .joinToString(" ").ifBlank { "popular music songs" }
    val state = persist("home/fluid/fresh", FreshDiscoveryFeed())
    val muted = if (enabled) discoveryPrefs.muted else emptySet()
    LaunchedEffect(store, query, enabled, muted) {
        val warm = state.value.takeIf { it.key == query }?.songs.orEmpty().filter { it.key !in muted }
        store.awaitIdle()
        state.value = FreshDiscoveryFeed(query, warm, true)
        try {
            val songs = store.loadDiscovery {
                val found = MusicBrowseCache.songs(query).filter { it.durationText != null }
                HomeShelfPolicy.unique(found.filter { it.key !in muted }) { it.key }.take(20)
            }
            store.awaitIdle()
            state.value = FreshDiscoveryFeed(query, songs.ifEmpty { warm })
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) {
            store.awaitIdle()
            state.value = FreshDiscoveryFeed(query, warm)
        }
    }
    return state
}

/** Always-available Home discovery source; rendering never starts a request. */
@Composable
internal fun HomeFreshDiscoveries(feed: FreshDiscoveryFeed, width: Dp) {
    val binder = LocalPlayerServiceBinder.current
    val (_, typography) = LocalAppearance.current
    val songs = feed.songs
    val loading = feed.loading
    val pages = remember(songs) { HomeShelfPolicy.pages(songs) }
    val queue = remember(songs) { songs.map { it.asMediaItem } }
    if (songs.isEmpty() && !loading) return
    HomeShelfTitle("Discover more", "A little beyond your usual listening")
    if (songs.isNotEmpty()) {
        val art = Dimensions.thumbnails.song
        HomeSongPages(pages, width, { it.key }) { song ->
            HomeSongRow(song, thumbnailSize = art, modifier = Modifier.clickable {
                binder?.playSmartQueue(song.asMediaItem, queue)
            })
        }
    } else if (loading) {
        BasicText("Exploring music…", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = typography.xs.secondary)
    }
}
