package it.vfsfitvnm.vimusic.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalContext
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.models.SearchQuery
import it.vfsfitvnm.vimusic.query
import it.vfsfitvnm.vimusic.ui.screens.searchresult.SearchResultScreen
import it.vfsfitvnm.vimusic.utils.pauseSearchHistoryKey
import it.vfsfitvnm.vimusic.utils.preferences

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun SearchFlow(initialInput: String, onViewPlaylist: (String) -> Unit) {
    val context = LocalContext.current
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var seed by rememberSaveable { mutableStateOf(initialInput) }
    val formState = rememberSaveableStateHolder()
    val term = result
    BackHandler(enabled = term != null) { result = null }
    if (term == null) {
        formState.SaveableStateProvider("entry") {
            SearchScreen(initialTextInput = seed, onSearch = { raw ->
                searchTermForSubmission(raw, result == null)?.let { accepted ->
                    result = accepted
                    if (!context.preferences.getBoolean(pauseSearchHistoryKey, false)) {
                        query { Database.insert(SearchQuery(query = accepted)) }
                    }
                }
            }, onViewPlaylist = onViewPlaylist)
        }
    } else {
        key(term) {
            SearchResultScreen(query = term, onSearchAgain = {
                formState.removeState("entry")
                seed = term
                result = null
            })
        }
    }
}
