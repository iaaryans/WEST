package it.vfsfitvnm.vimusic.ui.screens.spotify

import it.vfsfitvnm.vimusic.spotify.SpotifyMatch
import it.vfsfitvnm.compose.persist.persist
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vfsfitvnm.compose.routing.Route0
import it.vfsfitvnm.compose.routing.RouteHandler
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.internal
import it.vfsfitvnm.vimusic.models.Playlist
import it.vfsfitvnm.vimusic.models.SongPlaylistMap
import it.vfsfitvnm.vimusic.spotify.SpotifyImportTrack
import it.vfsfitvnm.vimusic.spotify.SpotifyImporter
import it.vfsfitvnm.vimusic.ui.components.themed.Scaffold
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.screens.globalRoutes
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.medium
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import java.util.concurrent.Callable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val spotifyImportRoute = Route0("spotifyImportRoute")

/**
 * Paste a Spotify playlist link, match its songs on YouTube Music and keep
 * the result as a local playlist. Adapted from the playlist-import dialog,
 * restyled as a screen to fit how WEST navigates.
 *
 * Only public playlists can be read (no Spotify account is involved, by
 * design), every track is matched by the ported TrackMatcher before it is
 * considered found, and the songs that matched nothing are listed at the end
 * instead of being dropped without a word. Cancelling — with the button or
 * by leaving the screen — stops the import; nothing is half-written, because
 * the playlist is only inserted once every track has been matched.
 */
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun SpotifyImportScreen(
    onViewPlaylist: (Long) -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current
    val scope = rememberCoroutineScope()
    val preferences = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("spotify_versions", 0)
    fun versionKey(track: SpotifyImportTrack, language: String?): String =
        org.json.JSONArray(listOf(track.artist.trim().lowercase(java.util.Locale.ROOT),
            track.title.trim().lowercase(java.util.Locale.ROOT), language.orEmpty().trim().lowercase(java.util.Locale.ROOT))).toString()
    var rememberVersion by rememberSaveable { mutableStateOf(true) }

    // Status bar height, used to line the compact toolbar title up with the
    // sidebar's back button (both centered on the same row).
    val topInset = LocalPlayerAwareWindowInsets.current
        .asPaddingValues().calculateTopPadding()

    var link by rememberSaveable { mutableStateOf("") }
    var selectedLanguage by rememberSaveable { mutableStateOf("No Preference") }
    var customLanguageInput by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var progressDone by rememberSaveable { mutableStateOf(0) }
    var progressTotal by rememberSaveable { mutableStateOf(0) }
    var result by persist<ImportResult?>("spotify/result")
    var draft by persist<List<SpotifyMatch>?>("spotify/review")
    var draftName by persist("spotify/name", "")
    var draftLanguage by persist<String?>("spotify/language")
    var importJob by remember { mutableStateOf<Job?>(null) }

    suspend fun finish(rows: List<SpotifyMatch>) {
        val songs = rows.mapNotNull { it.selected }.distinctBy { it.key }
        require(songs.isNotEmpty()) { "Select at least one track to import" }
        withContext(kotlinx.coroutines.NonCancellable) {
            val playlistId = withContext(Dispatchers.IO) {
                Database.internal.runInTransaction(
                    Callable {
                        val id1 = Database.insert(Playlist(name = draftName))
                        songs
                            .map { it.asMediaItem }
                            .onEach(Database::insert)
                            .mapIndexed { index, mediaItem ->
                                SongPlaylistMap(
                                    songId = mediaItem.mediaId,
                                    playlistId = id1,
                                    position = index
                                )
                            }
                            .let(Database::insertSongPlaylistMaps)
                        id1
                    }
                )
            }
            result = ImportResult(
                playlistId = playlistId,
                playlistName = draftName,
                totalTracks = rows.size,
                matchedCount = songs.size,
                unmatched = rows.filter { it.selected == null }.map { it.track },
            )
            draft = null
            status = null
        }
    }

    fun reviewAction(retry: Boolean) {
        val rows = draft ?: return
        working = true
        importJob = scope.launch {
            try {
                if (retry) {
                    val pending = rows.indices.filter { rows[it].selected == null && !rows[it].skipped }
                    val matches = SpotifyImporter.resolveToSongs(pending.map { rows[it].track }, draftLanguage, { preferences.getString(versionKey(it, draftLanguage), null) }) { _, _ -> }
                    draft = rows.toMutableList().apply { pending.forEachIndexed { i, index -> this[index] = matches[i] } }
                } else finish(rows)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = e.message ?: "Import failed" }
            finally { working = false }
        }
    }

    fun start() {
        val id = SpotifyImporter.extractPlaylistId(link)
        if (id == null) {
            status = "Not a valid Spotify playlist link"
            failed = true
            return
        }
        val effectiveLanguage = if (selectedLanguage == "Manual / Other") {
            customLanguageInput.trim().ifBlank { null }
        } else {
            selectedLanguage
        }
        importJob = scope.launch {
            working = true
            failed = false
            progressDone = 0
            progressTotal = 0
            status = "Fetching playlist…"
            try {
                val (name, tracks) = SpotifyImporter.fetchPlaylistTracks(id)
                draftName = name
                draftLanguage = effectiveLanguage
                val rows = SpotifyImporter.resolveToSongs(tracks, effectiveLanguage, { preferences.getString(versionKey(it, effectiveLanguage), null) }) { done, total ->
                    progressDone = done
                    progressTotal = total
                    status = "Matching tracks ($done/$total)…"
                }
                require(rows.isNotEmpty()) { "This playlist has no readable tracks" }
                draft = rows
                status = null
                if (rows.all { it.selected != null }) finish(rows)
            } catch (e: CancellationException) {
                status = null
                throw e
            } catch (e: Exception) {
                status = e.message ?: "Import failed"
                failed = true
            } finally {
                working = false
            }
        }
    }

    RouteHandler(listenToGlobalEmitter = true) {
        globalRoutes()

        host {
            Scaffold(
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = 0,
                onTabChanged = { },
                tabColumnContent = { Item ->
                    Item(0, "Spotify Import", R.drawable.playlist)
                }
            ) { _ ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorPalette.background0)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            LocalPlayerAwareWindowInsets.current
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                .asPaddingValues()
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Compact toolbar: a smaller title that sits on the same row
                    // as the sidebar's back button instead of the oversized
                    // scrolling header. The sidebar icon is centered 71dp below
                    // the status bar (48dp offset + 12dp padding + 11dp half
                    // icon); the row mirrors that center: 16dp column padding +
                    // 33dp + half of the 44dp row height. The title itself is
                    // pushed to the trailing (right) edge, staying vertically
                    // centered with the back button and clear of the rail.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = topInset + 33.dp)
                            .height(44.dp)
                    ) {
                        BasicText(
                            text = "Spotify Import",
                            style = typography.m.semiBold.copy(color = colorPalette.text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    BasicText(
                        text = "Paste a public Spotify playlist link. " +
                            "Each track is matched on YouTube Music.",
                        style = typography.xxs.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val outcome = result
                    if (outcome != null) {
                        ImportSummary(
                            result = outcome,
                            onViewPlaylist = { onViewPlaylist(outcome.playlistId) },
                            onImportAnother = {
                                result = null
                                link = ""
                                status = null
                                failed = false
                                progressDone = 0
                                progressTotal = 0
                            }
                        )
                    } else if (draft != null) {
                        val rows = draft.orEmpty()
                        val pending = rows.indices.filter { rows[it].selected == null && !rows[it].skipped }
                        BasicText("Review matches", style = typography.m.semiBold)
                        BasicText("${rows.count { it.selected != null }} of ${rows.size} selected", style = typography.xs.secondary)
                        val index = pending.firstOrNull()
                        if (index != null) {
                            val row = rows[index]
                            BasicText("${row.track.title} · ${row.track.artist}", style = typography.s.semiBold)
                            if (row.candidates.isEmpty()) BasicText("No candidates found", style = typography.xs.secondary)
                            if (row.candidates.isNotEmpty()) SecondaryTextButton(
                                if (rememberVersion) "Remember choice: on" else "Remember choice: off",
                                onClick = { rememberVersion = !rememberVersion })
                            row.candidates.forEach { candidate ->
                                val label = listOfNotNull(candidate.info?.name,
                                    candidate.authors?.joinToString(", ") { it.name.orEmpty() }, candidate.durationText).joinToString(" · ")
                                SecondaryTextButton(label, enabled = !working, onClick = {
                                    val key = versionKey(row.track, draftLanguage)
                                    preferences.edit().apply {
                                        if (rememberVersion) putString(key, candidate.key) else remove(key)
                                    }.apply()
                                    draft = rows.toMutableList().apply { this[index] = row.copy(selected = candidate) }
                                })
                            }
                            SecondaryTextButton("Skip this track", enabled = !working, onClick = {
                                draft = rows.toMutableList().apply { this[index] = row.copy(skipped = true) }
                            })
                            SecondaryTextButton(if (working) "Working…" else "Retry pending (${pending.size})",
                                enabled = !working, onClick = { reviewAction(true) })
                        }
                        SecondaryTextButton("Import selected tracks", enabled = !working && rows.any { it.selected != null },
                            onClick = { reviewAction(false) })
                        SecondaryTextButton("Start over", enabled = !working, onClick = { draft = null; status = null })
                        status?.let { BasicText(it, style = typography.xs.secondary) }
                    } else {
                        var resetChoices by remember { mutableStateOf(false) }
                        if (preferences.all.isNotEmpty()) SecondaryTextButton("Reset remembered versions", onClick = { resetChoices = true })
                        if (resetChoices) it.vfsfitvnm.vimusic.ui.components.themed.ConfirmationDialog(
                            text = "Forget all saved song versions?", confirmText = "Reset",
                            onDismiss = { resetChoices = false }, onConfirm = { preferences.edit().clear().apply(); resetChoices = false })
                        BasicTextField(
                            value = link,
                            onValueChange = {
                                link = it
                                if (failed) {
                                    status = null
                                    failed = false
                                }
                            },
                            textStyle = typography.xs.semiBold.copy(color = colorPalette.text),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { if (link.isNotBlank() && !working) start() }
                            ),
                            cursorBrush = SolidColor(colorPalette.text),
                            decorationBox = { innerTextField ->
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colorPalette.background1)
                                        .padding(all = 16.dp)
                                ) {
                                    if (link.isEmpty()) {
                                        BasicText(
                                            text = "https://open.spotify.com/playlist/…",
                                            style = typography.xs.semiBold.secondary,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        BasicText(
                            text = "Language preference",
                            style = typography.xxs.semiBold.copy(color = colorPalette.textSecondary)
                        )

                        SpotifyImporter.IMPORT_LANGUAGES
                            .chunked(4)
                            .forEach { rowLanguages ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowLanguages.forEach { language ->
                                        val selected = language == selectedLanguage
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(percent = 50))
                                                .background(
                                                    if (selected) colorPalette.accent
                                                    else colorPalette.background1
                                                )
                                                .clickable(enabled = !working) {
                                                    selectedLanguage = language
                                                }
                                                .padding(vertical = 7.dp)
                                        ) {
                                            BasicText(
                                                text = language,
                                                style = typography.xxs.medium.copy(
                                                    color = if (selected) colorPalette.onAccent
                                                    else colorPalette.textSecondary,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                        if (selectedLanguage == "Manual / Other") {
                            BasicTextField(
                                value = customLanguageInput,
                                onValueChange = { customLanguageInput = it },
                                textStyle = typography.xs.semiBold.copy(color = colorPalette.text),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                cursorBrush = SolidColor(colorPalette.text),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colorPalette.background1)
                                            .padding(all = 16.dp)
                                    ) {
                                        if (customLanguageInput.isEmpty()) {
                                            BasicText(
                                                text = "Type a language",
                                                style = typography.xs.semiBold.secondary
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        val currentStatus = status
                        if (currentStatus != null) {
                            BasicText(
                                text = currentStatus,
                                style = typography.xxs.medium.copy(
                                    color = if (failed) colorPalette.red else colorPalette.text
                                )
                            )
                        }

                        if (working && progressTotal > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(colorPalette.background2)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressDone.toFloat() / progressTotal)
                                        .height(4.dp)
                                        .background(colorPalette.accent)
                                )
                            }
                        }

                        SecondaryTextButton(
                            text = if (working) "Cancel" else "Import",
                            onClick = {
                                if (working) importJob?.cancel() else start()
                            },
                            enabled = link.isNotBlank() || working,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

/** What a finished import produced, shown in place of the form. */
private data class ImportResult(
    val playlistId: Long,
    val playlistName: String,
    val totalTracks: Int,
    val matchedCount: Int,
    val unmatched: List<SpotifyImportTrack>,
)

@Composable
private fun ImportSummary(
    result: ImportResult,
    onViewPlaylist: () -> Unit,
    onImportAnother: () -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        BasicText(
            text = "Imported \"${result.playlistName}\"",
            style = typography.s.semiBold.copy(color = colorPalette.text)
        )

        BasicText(
            text = "${result.matchedCount} of ${result.totalTracks} tracks matched and imported",
            style = typography.xxs.medium.copy(color = colorPalette.textSecondary)
        )

        if (result.unmatched.isNotEmpty()) {
            BasicText(
                text = "Left out of this import: ${result.unmatched.size} " +
                    if (result.unmatched.size == 1) "track" else "tracks",
                style = typography.xxs.medium.copy(color = colorPalette.textSecondary)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorPalette.background1)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                result.unmatched.forEach { track ->
                    BasicText(
                        text = if (track.artist.isBlank()) track.title else "${track.title} · ${track.artist}",
                        style = typography.xxs.secondary
                    )
                }
            }
        } else {
            BasicText(
                text = "All tracks matched.",
                style = typography.xxs.medium.copy(color = colorPalette.textSecondary)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SecondaryTextButton(
                text = "View playlist",
                onClick = onViewPlaylist,
                modifier = Modifier.weight(1f)
            )
            SecondaryTextButton(
                text = "Import another",
                onClick = onImportAnother,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
