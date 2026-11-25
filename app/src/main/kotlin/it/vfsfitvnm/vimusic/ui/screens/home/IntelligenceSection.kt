package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import it.vfsfitvnm.compose.persist.persist
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import it.vfsfitvnm.vimusic.intelligence.IntelligenceSnapshot
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.intelligence.IntelligencePreferences
import it.vfsfitvnm.vimusic.intelligence.RecommendationRepository
import it.vfsfitvnm.vimusic.intelligence.RecommendedSong
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.preferences

/** WEST Intelligence shelves remain local-first, displayed in horizontal discovery pages. */
@Composable
internal fun rememberIntelligenceFeed(store: HomeFeedStore): State<IntelligenceSnapshot> {
    val prefs = LocalContext.current.preferences
    val state = persist("home/fluid/intelligence", IntelligenceSnapshot())
    LaunchedEffect(prefs, store) {
        RecommendationRepository.observe(prefs, beforeRanking = { store.awaitIdle() }).distinctUntilChanged().collectLatest { snapshot ->
            store.awaitIdle()
            state.value = snapshot
        }
    }
    return state
}

/** Each recommendation shelf is its own vertical lazy item. */
@Composable
internal fun IntelligenceSection(state: IntelligenceSnapshot, width: Dp, rediscover: Boolean) {
    val prefs = LocalContext.current.preferences
    if (!state.enabled) return
    val binder = LocalPlayerServiceBinder.current
    val art = Dimensions.thumbnails.song
    val entries = remember(state, rediscover) {
        (if (rediscover) state.rediscover else state.madeForYou).take(12)
    }
    if (entries.isEmpty()) return
    val pages = remember(entries) { HomeShelfPolicy.pages(entries) }
    val queue = remember(entries) { entries.map { it.song.asMediaItem } }
    HomeShelfTitle(if (rediscover) "Forgotten gems" else "Made for you",
        if (rediscover) "Songs worth hearing again" else "Your evolving listening profile")
    HomeSongPages(pages, width, { it.song.id }) { entry ->
        HomeSongRow(song = entry.song, thumbnailSize = art,
            modifier = Modifier.clickable { binder?.playSmartQueue(entry.song.asMediaItem, queue) },
            trailingContent = {
                Row {
                    ActionLabel("More") { IntelligencePreferences.favor(prefs, entry.song.id) }
                    ActionLabel("Less") { IntelligencePreferences.suppress(prefs, entry.song.id) }
                }
            })
    }
}
