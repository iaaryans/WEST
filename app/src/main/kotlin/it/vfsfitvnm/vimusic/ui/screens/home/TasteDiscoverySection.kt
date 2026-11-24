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

/** Personalized, horizontally paged discovery, matching WEST's new Listen Now shelves. */
@Composable
internal fun TasteDiscoverySection() {
    val prefs = LocalContext.current.preferences
    val profile by remember(prefs) { TasteProfileStore.observe(prefs) }
        .collectAsState(initial = TasteProfileStore.load(prefs))
    if (!IntelligencePreferences.enabled(prefs) ||
        (profile.artists.isEmpty() && profile.songs.isEmpty())) return
    val (_, typography) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val artistNames = profile.artists.take(3).map { it.name }
    val songTitles = profile.songs.take(1).map { it.title }
    val language = profile.languages.firstOrNull { it != "Other" }
    var results by remember(artistNames, songTitles, language) {
        mutableStateOf(emptyList<Innertube.SongItem>())
    }
    var loading by remember(artistNames, songTitles, language) { mutableStateOf(false) }
    LaunchedEffect(artistNames, songTitles, language) {
        val seeds = (artistNames + songTitles).filter { it.isNotBlank() }
        if (seeds.isEmpty()) return@LaunchedEffect
        loading = true
        try {
            val found = LinkedHashMap<String, Innertube.SongItem>()
            for (name in seeds.take(3)) {
                val query = listOfNotNull(name, language, "songs").joinToString(" ")
                MusicBrowseCache.songs(query).forEach { item ->
                    val id = item.info?.endpoint?.videoId
                    if (!id.isNullOrBlank() && item.durationText != null && id !in found) found[id] = item
                }
                results = found.values.take(20)
                if (found.size >= 18) break
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { results = emptyList() }
        finally { loading = false }
    }
    HomeShelfTitle("Picked for you", "Based on your artists and favorite songs · WEST Intelligence")
    if (profile.moods.isNotEmpty()) {
        BasicText("Your vibes: ${profile.moods.take(3).joinToString(" · ")}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = typography.xs.secondary)
    }
    if (results.isNotEmpty()) {
        val art = Dimensions.thumbnails.song
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = minOf(maxWidth * 0.88f, 440.dp)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(results.chunked(4), key = { it.first().key }) { page ->
                    Column(Modifier.width(width)) {
                        page.forEach { song ->
                            HomeSongRow(song, art, modifier = Modifier.clickable {
                                binder?.playSmartQueue(song.asMediaItem, results.map { it.asMediaItem })
                            })
                        }
                    }
                }
            }
        }
    } else {
        BasicText(if (loading) "Finding music for you…" else "Your personalized discoveries are temporarily unavailable.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = typography.xs.secondary)
    }
}
