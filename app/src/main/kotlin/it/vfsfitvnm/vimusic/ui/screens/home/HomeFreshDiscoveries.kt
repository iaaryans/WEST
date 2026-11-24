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
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.ui.performance.MusicBrowseCache
import it.vfsfitvnm.vimusic.intelligence.IntelligencePreferences
import it.vfsfitvnm.vimusic.personalization.TasteProfileStore
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.preferences
import it.vfsfitvnm.vimusic.utils.secondary
import kotlinx.coroutines.CancellationException

/** Always-available Home discovery source, including on a brand-new install with no history. */
@Composable
internal fun HomeFreshDiscoveries() {
    val prefs = LocalContext.current.preferences
    val profile by remember(prefs) { TasteProfileStore.observe(prefs) }
        .collectAsState(initial = TasteProfileStore.load(prefs))
    val enabled = IntelligencePreferences.enabled(prefs)
    val language = profile.languages.firstOrNull { it != "Other" }.orEmpty()
    val firstArtist = if (enabled) profile.artists.firstOrNull()?.name.orEmpty() else ""
    val query = listOf(firstArtist, language, "music songs").filter { it.isNotBlank() }
        .joinToString(" ").ifBlank { "popular music songs" }
    val binder = LocalPlayerServiceBinder.current
    val (_, typography) = LocalAppearance.current
    var songs by remember(query) { mutableStateOf(emptyList<Innertube.SongItem>()) }
    var loading by remember(query) { mutableStateOf(false) }
    LaunchedEffect(query) {
        loading = true
        try {
            val found = MusicBrowseCache.songs(query).filter { it.durationText != null }
            val muted = if (enabled) IntelligencePreferences.less(prefs) else emptySet()
            songs = HomeShelfPolicy.unique(found.filter { it.key !in muted }) { it.key }.take(20)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { songs = emptyList() }
        finally { loading = false }
    }
    if (songs.isEmpty() && !loading) return
    HomeShelfTitle("Discover more", "A little beyond your usual listening")
    if (songs.isNotEmpty()) {
        val art = Dimensions.thumbnails.song
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = minOf(maxWidth * 0.88f, 440.dp)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(HomeShelfPolicy.pages(songs), key = { it.first().key }) { page ->
                    Column(Modifier.width(width)) {
                        page.forEach { song ->
                            HomeSongRow(song = song, thumbnailSize = art,
                                modifier = Modifier.clickable {
                                    binder?.playSmartQueue(song.asMediaItem,
                                        songs.map { it.asMediaItem })
                                })
                        }
                    }
                }
            }
        }
    } else if (loading) {
        BasicText("Exploring music…", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = typography.xs.secondary)
    }
}
