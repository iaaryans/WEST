package it.vfsfitvnm.vimusic.ui.screens.home
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import it.vfsfitvnm.compose.routing.Route
import it.vfsfitvnm.vimusic.ui.screens.search.searchTermForSubmission
import it.vfsfitvnm.vimusic.enums.BuiltInPlaylist
import it.vfsfitvnm.vimusic.ui.components.themed.BrowseShell
import it.vfsfitvnm.vimusic.ui.components.themed.FilterPage


import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import it.vfsfitvnm.compose.persist.PersistMapCleanup
import it.vfsfitvnm.compose.routing.RouteHandler
import it.vfsfitvnm.compose.routing.defaultStacking
import it.vfsfitvnm.compose.routing.defaultStill
import it.vfsfitvnm.compose.routing.defaultUnstacking
import it.vfsfitvnm.compose.routing.isStacking
import it.vfsfitvnm.compose.routing.isUnknown
import it.vfsfitvnm.compose.routing.isUnstacking
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.SearchQuery
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.ui.components.themed.Scaffold
import it.vfsfitvnm.vimusic.ui.screens.albumRoute
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.ui.screens.builtInPlaylistRoute
import it.vfsfitvnm.vimusic.ui.screens.builtinplaylist.BuiltInPlaylistScreen
import it.vfsfitvnm.vimusic.ui.screens.globalRoutes
import it.vfsfitvnm.vimusic.ui.screens.localPlaylistRoute
import it.vfsfitvnm.vimusic.listening.presentation.ListeningInsightsScreen
import it.vfsfitvnm.vimusic.listening.presentation.listeningStatsRoute
import it.vfsfitvnm.vimusic.ui.screens.localplaylist.LocalPlaylistScreen
import it.vfsfitvnm.vimusic.ui.screens.playlistRoute
import it.vfsfitvnm.vimusic.ui.screens.search.SearchFlow
import it.vfsfitvnm.vimusic.ui.screens.searchResultRoute
import it.vfsfitvnm.vimusic.ui.screens.searchRoute
import it.vfsfitvnm.vimusic.ui.screens.searchresult.SearchResultScreen
import it.vfsfitvnm.vimusic.ui.screens.settings.SettingsScreen
import it.vfsfitvnm.vimusic.ui.screens.settingsRoute
import it.vfsfitvnm.vimusic.ui.screens.spotify.SpotifyImportScreen
import it.vfsfitvnm.vimusic.ui.screens.spotify.spotifyImportRoute
import it.vfsfitvnm.vimusic.utils.homeScreenTabIndexKey
import it.vfsfitvnm.vimusic.utils.pauseSearchHistoryKey
import it.vfsfitvnm.vimusic.utils.preferences
import it.vfsfitvnm.vimusic.utils.rememberPreference

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeScreen(onPlaylistUrl: (String) -> Unit) {
    val saveableStateHolder = rememberSaveableStateHolder()

    // Regression fix: this used a plain rememberSaveable, so the selected tab
    // reset to Listen Now after process death while homeScreenTabIndexKey (the
    // dedicated persisted key) stayed unused.
    val (index, select) = rememberPreference(homeScreenTabIndexKey, 0)
    PersistMapCleanup("home/")

    RouteHandler(
        listenToGlobalEmitter = true,
        transitionSpec = {
            when {
                isStacking -> defaultStacking
                isUnstacking -> defaultUnstacking
                isUnknown -> when {
                    initialState.route == searchRoute && targetState.route == searchResultRoute -> defaultStacking
                    initialState.route == searchResultRoute && targetState.route == searchRoute -> defaultUnstacking
                    else -> defaultStill
                }

                else -> defaultStill
            }
        }
    ) {
        globalRoutes()

        settingsRoute {
            SettingsScreen()
        }

        mixRoute { MixScreen(onBack = pop) }

        moodspaceRoute { MoodspaceScreen(onBack = pop, onBrowseClick = { browseId ->
            when (MoodspaceSafety.browseTarget(browseId)) {
                MoodspaceSafety.BrowseTarget.ALBUM -> albumRoute(browseId)
                MoodspaceSafety.BrowseTarget.ARTIST -> artistRoute(browseId)
                MoodspaceSafety.BrowseTarget.PLAYLIST -> playlistRoute(browseId)
                null -> Unit // Skip unsupported holiday/seasonal navigation endpoints.
            }
        }) }

        spotifyImportRoute {
            SpotifyImportScreen(
                onViewPlaylist = { playlistId ->
                    localPlaylistRoute(playlistId)
                }
            )
        }

        listeningStatsRoute {
            ListeningInsightsScreen()
        }

        localPlaylistRoute { playlistId ->
            LocalPlaylistScreen(
                playlistId = playlistId ?: error("playlistId cannot be null")
            )
        }

        builtInPlaylistRoute { builtInPlaylist ->
            BuiltInPlaylistScreen(
                builtInPlaylist = builtInPlaylist
            )
        }

        searchRoute { input ->
            SearchFlow(initialInput = input, onViewPlaylist = onPlaylistUrl)
        }

        host {
            BrowseShell(index, select) { tab ->
                saveableStateHolder.SaveableStateProvider(tab) {
                    when (tab) {
                        0 -> QuickPicks(onMix = { mixRoute() }, onFind = { select(2) }, onImport = { spotifyImportRoute() },
                            onAlbumClick = { albumRoute(it) }, onArtistClick = { artistRoute(it) },
                            onPlaylistClick = { playlistRoute(it) }, onInsights = { listeningStatsRoute() },
                            onSettings = { settingsRoute() }, onOffline = { builtInPlaylistRoute(BuiltInPlaylist.Offline) })
                        1 -> MoodspaceScreen(onBrowseClick = { browseId ->
                            when (MoodspaceSafety.browseTarget(browseId)) {
                                MoodspaceSafety.BrowseTarget.ALBUM -> albumRoute(browseId)
                                MoodspaceSafety.BrowseTarget.ARTIST -> artistRoute(browseId)
                                MoodspaceSafety.BrowseTarget.PLAYLIST -> playlistRoute(browseId)
                                null -> Unit
                            }
                        })
                        2 -> SearchFlow(initialInput = "", onViewPlaylist = onPlaylistUrl)
                        3 -> {
                            val (filter, choose) = rememberSaveable { mutableStateOf(0) }
                            val lists = rememberSaveableStateHolder()
                            FilterPage(listOf("Playlists", "Songs", "Artists", "Albums"), filter, choose, header = {
                                LibraryShortcuts({ builtInPlaylistRoute(BuiltInPlaylist.Favorites) },
                                    { builtInPlaylistRoute(BuiltInPlaylist.Offline) })
                            }) {
                                lists.SaveableStateProvider(filter) {
                                    when (filter) {
                                        0 -> HomePlaylists(onPlaylistClick = { localPlaylistRoute(it.id) },
                                            onImportFromSpotify = { spotifyImportRoute() })
                                        1 -> HomeSongs()
                                        2 -> HomeArtistList(onArtistClick = { artistRoute(it.id) })
                                        3 -> HomeAlbums(onAlbumClick = { albumRoute(it.id) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
