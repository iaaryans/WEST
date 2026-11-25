package it.vfsfitvnm.vimusic.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

internal val LocalHomeFeedStore = compositionLocalOf<HomeFeedStore?> { null }
internal val LocalHomeSinglePageFits = compositionLocalOf { false }

/** A scroll-aware row; lifecycle cancellation removes its registration. */
@Composable
internal fun HomeHorizontalRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: LazyListScope.() -> Unit
) {
    val state = rememberLazyListState()
    val store = LocalHomeFeedStore.current
    val token = remember { Any() }
    LaunchedEffect(store, state, token) {
        if (store == null) return@LaunchedEffect
        try {
            snapshotFlow { state.isScrollInProgress }.collect { store.setHorizontalScrolling(token, it) }
        } finally {
            store.setHorizontalScrolling(token, false)
        }
    }
    LazyRow(modifier = modifier, state = state, contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement, content = content)
}

/** One page that fits needs no nested lazy subcomposition or horizontal gesture handler. */
@Composable
internal fun <T> HomeSongPages(
    pages: List<List<T>>, width: Dp, id: (T) -> String,
    row: @Composable (T) -> Unit
) {
    if (pages.isEmpty()) return
    if (HomeShelfPolicy.useStaticPage(pages.size, LocalHomeSinglePageFits.current)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.width(width)) {
                pages.first().forEach { song -> key(id(song)) { row(song) } }
            }
        }
    } else {
        HomeHorizontalRow(contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(pages, key = { id(it.first()) }, contentType = { "song-page" }) { page ->
                Column(Modifier.width(width)) {
                    page.forEach { song -> key(id(song)) { row(song) } }
                }
            }
        }
    }
}
