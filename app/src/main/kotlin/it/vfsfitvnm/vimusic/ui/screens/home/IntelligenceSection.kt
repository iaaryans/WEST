package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.intelligence.IntelligencePreferences
import it.vfsfitvnm.vimusic.intelligence.RecommendationRepository
import it.vfsfitvnm.vimusic.intelligence.RecommendedSong
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.preferences

/** WEST Intelligence shelves remain local-first, now in BitChord-style horizontal pages. */
@Composable
internal fun IntelligenceSection() {
    val prefs = LocalContext.current.preferences
    val flow = remember(prefs) { RecommendationRepository.observe(prefs) }
    val state by flow.collectAsState(initial = it.vfsfitvnm.vimusic.intelligence.IntelligenceSnapshot())
    if (!state.enabled) return
    val binder = LocalPlayerServiceBinder.current
    val art = Dimensions.thumbnails.song

    @Composable
    fun Recommendations(title: String, entries: List<RecommendedSong>, subtitle: String) {
        if (entries.isEmpty()) return
        HomeShelfTitle(title, subtitle)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = minOf(maxWidth * 0.88f, 440.dp)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(entries.chunked(4), key = { it.first().song.id }) { page ->
                    Column(Modifier.width(width)) {
                        page.forEach { entry ->
                            HomeSongRow(song = entry.song, thumbnailSize = art,
                                modifier = Modifier.clickable {
                                    binder?.playSmartQueue(entry.song.asMediaItem,
                                        entries.map { it.song.asMediaItem })
                                },
                                trailingContent = {
                                    Row {
                                        ActionLabel("More") { IntelligencePreferences.favor(prefs, entry.song.id) }
                                        ActionLabel("Less") { IntelligencePreferences.suppress(prefs, entry.song.id) }
                                    }
                                })
                        }
                    }
                }
            }
        }

    }

    Recommendations("Made for you", state.madeForYou.take(12), "Your evolving listening profile")
    Recommendations("Forgotten gems", state.rediscover.take(12), "Songs worth hearing again")
}
