package it.vfsfitvnm.vimusic.ui.screens.localplaylist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.screens.home.seconds
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.secondary
import kotlinx.coroutines.*

@Composable
internal fun OfflinePlaylistAction(id: Long, onPlay: (List<Song>) -> Unit) {
    val binder = LocalPlayerServiceBinder.current ?: return
    val tracks by remember(id) { Database.playlistForOfflineCheck(id) }.collectAsState(initial = emptyList())
    if (tracks.isEmpty()) return
    val scope = rememberCoroutineScope()
    var ready by remember(tracks, binder) { mutableStateOf<List<Song>?>(null) }
    suspend fun available() = withContext(Dispatchers.IO) {
        tracks.filter { row -> row.contentLength?.let { it > 0 && binder.cache.isCached(row.song.id, 0, it) } == true }.map { it.song }
    }
    LaunchedEffect(tracks, binder) {
        while (isActive) {
            ready = available()
            delay(10000)
        }
    }
    val playable = ready ?: return
    val durations = playable.map { seconds(it.durationText) }
    val duration = if (durations.isNotEmpty() && durations.all { it > 0 }) " · " + durations.sumOf { it.toLong() } / 60 + " min" else ""
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        BasicText("${playable.size} of ${tracks.size} ready offline$duration", style = LocalAppearance.current.typography.xs.secondary)
        if (playable.isNotEmpty()) SecondaryTextButton("Play offline tracks", onClick = {
            scope.launch { val current = available(); ready = current; if (current.isNotEmpty()) onPlay(current) }
        })
    }
}
