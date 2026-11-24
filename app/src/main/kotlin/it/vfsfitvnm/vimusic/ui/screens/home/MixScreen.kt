package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.routing.Route0
import it.vfsfitvnm.vimusic.*
import it.vfsfitvnm.vimusic.models.*
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.components.themed.TextFieldDialog
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.styling.*
import it.vfsfitvnm.vimusic.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import kotlin.math.abs

internal val mixRoute = Route0("timedMix")

@Composable
internal fun MixScreen(onBack: () -> Unit) {
    val (palette, type) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val library by remember { Database.mixLibrary() }.collectAsState(initial = null)
    val songs = remember(library) { library.orEmpty().filter { seconds(it.durationText) > 0 } }
    var source by rememberSaveable { mutableStateOf("All songs") }
    val pool = remember(songs, source) {
        when (source) {
            "Favorites" -> songs.filter { it.likedAt != null }
            "Less played" -> songs.sortedBy { it.totalPlayTimeMs }.take((songs.size + 1) / 2)
            else -> songs
        }
    }
    var minutes by rememberSaveable { mutableStateOf(25) }
    var ending by rememberSaveable { mutableStateOf<String?>(null) }
    var seed by rememberSaveable { mutableStateOf(0) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var mix by remember(pool, minutes, ending, seed) { mutableStateOf(emptyList<Song>()) }
    LaunchedEffect(pool, minutes, ending, seed) {
        mix = withContext(Dispatchers.Default) { timedMix(pool, minutes, ending, seed) }
    }
    if (dialog == "time") TextFieldDialog("Minutes (1–120)", { dialog = "" }, {
        minutes = it.trim().toInt(); status = ""
    }, initialTextInput = minutes.toString(), isTextInputValid = { it.trim().toIntOrNull() in 1..120 })
    if (dialog == "search") TextFieldDialog("Search final song", { dialog = "" }, { filter = it.trim() },
        initialTextInput = filter, isTextInputValid = { true })
    if (dialog == "save") TextFieldDialog("Playlist name", { dialog = "" }, { name ->
        if (!busy && mix.isNotEmpty()) {
            busy = true
            val snapshot = mix
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        Database.internal.runInTransaction(Callable {
                            val id = Database.insert(Playlist(name = name.trim()))
                            Database.insertSongPlaylistMaps(snapshot.mapIndexed { i, song -> SongPlaylistMap(song.id, id, i) })
                        })
                    }
                    status = "Saved to Library: " + name.trim()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { status = "Could not save the playlist. Try again." }
                finally { busy = false }
            }
        }
    }, initialTextInput = "Timed mix · " + minutes + " min", isTextInputValid = { it.isNotBlank() })
    androidx.activity.compose.BackHandler(enabled = picker) { picker = false }
    LazyColumn(Modifier.fillMaxSize().background(palette.background0).padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ActionLabel("Back") { if (picker) picker = false else onBack() }
            BasicText(if (picker) "Choose final song" else "Timed mix", style = type.xxl.semiBold)
        }
        if (picker) {
            item {
                ActionLabel(if (filter.isBlank()) "Search library" else "Search: " + filter) { dialog = "search" }
                ActionLabel("Any ending") { ending = null; picker = false; status = "" }
            }
            val matches = pool.filter { it.title.contains(filter, true) || it.artistsText.orEmpty().contains(filter, true) }
            if (matches.isEmpty()) item { BasicText("No matching songs", style = type.s.secondary) }
            items(matches, key = { it.id }) { song ->
                ActionLabel(listOfNotNull(song.title, song.artistsText, song.durationText).joinToString(" · ")) {
                    ending = song.id; picker = false; status = ""
                }
            }
        } else if (songs.isEmpty()) {
            item { BasicText(if (library == null) "Loading library…" else "Play songs or add a playlist with track durations to build a mix.", style = type.s.secondary) }
        } else {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All songs", "Favorites", "Less played").forEach { choice ->
                        SecondaryTextButton(if (source == choice) "✓ " + choice else choice, onClick = {
                            if (source != choice) { source = choice; ending = null; status = "" }
                        })
                    }
                }
                ActionLabel("Time available · " + minutes + " min") { dialog = "time" }
                ActionLabel("Final song · " + (songs.find { it.id == ending }?.title ?: "Any")) { filter = ""; picker = true }
            }
            if (pool.isEmpty()) item { BasicText("No tracks in this selection yet.", style = type.s.secondary) }
            if (mix.isNotEmpty()) {
                val length = mix.sumOf { seconds(it.durationText) }
                val diff = length - minutes * 60
                fun clock(n: Int) = "%d:%02d".format(n / 60, n % 60)
                item {
                    val delta = when { diff == 0 -> "On target"; diff > 0 -> clock(diff) + " over"; else -> clock(abs(diff)) + " under" }
                    BasicText(mix.size.toString() + " tracks · " + clock(length) + " · " + delta, style = type.s.semiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecondaryTextButton("Play mix", enabled = binder != null, onClick = {
                            binder?.stopRadio()
                            binder?.player?.apply {
                                shuffleModeEnabled = false
                                repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                                forcePlayFromBeginning(mix.map { it.asMediaItem })
                            }
                            status = "Playing mix"
                        })
                        SecondaryTextButton("Save", enabled = !busy, onClick = { dialog = "save" })
                        SecondaryTextButton("Remix", onClick = { seed++; status = "" })
                    }
                    if (status.isNotEmpty()) BasicText(status, style = type.xs.secondary)
                }
                items(mix, key = { it.id }) { song ->
                    val size = Dimensions.thumbnails.song
                    SongItem(song, size.px, size)
                }
            }
        }
    }
}
