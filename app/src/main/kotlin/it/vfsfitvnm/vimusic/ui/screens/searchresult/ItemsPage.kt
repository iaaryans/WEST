package it.vfsfitvnm.vimusic.ui.screens.searchresult

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.compose.persist.persist
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.utils.plus
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.ui.components.ShimmerHost
import it.vfsfitvnm.vimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.center
import it.vfsfitvnm.vimusic.utils.secondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@ExperimentalAnimationApi
@Composable
fun <T : Innertube.Item> ItemsPage(
    tag: String,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    itemContent: @Composable LazyItemScope.(T) -> Unit,
    itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    excludedKeys: Set<String> = emptySet(),
    onItemsChanged: ((List<T>) -> Unit)? = null,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = "No items found",
    itemsPageProvider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null,
) {
    val (_, typography) = LocalAppearance.current

    val updatedItemsPageProvider by rememberUpdatedState(itemsPageProvider)

    val lazyListState = rememberLazyListState()

    var itemsPage by key(tag) { persist<Innertube.ItemsPage<T>?>(tag) }
    val loadedItems = itemsPage?.items.orEmpty().filterNot { it.key in excludedKeys }
    val updatedOnItemsChanged by rememberUpdatedState(onItemsChanged)
    LaunchedEffect(tag, loadedItems) { updatedOnItemsChanged?.invoke(loadedItems) }

    var loadFailed by remember(tag) { mutableStateOf(false) }
    LaunchedEffect(lazyListState, tag, itemsPageProvider != null, loadFailed) {
        if (loadFailed || updatedItemsPageProvider == null) return@LaunchedEffect

        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } to itemsPage?.continuation
        }.collect { (visible, continuation) ->
            if (!visible || loadFailed || (itemsPage != null && continuation == null)) return@collect
            val provider = updatedItemsPageProvider ?: return@collect
            val result = withContext(Dispatchers.IO) { provider(continuation) }
            if (result == null) {
                coroutineContext.ensureActive()
                loadFailed = true
                return@collect
            }
            result.onSuccess { page ->
                val next = page ?: Innertube.ItemsPage<T>(null, null)
                itemsPage += if (next.continuation == continuation) next.copy(continuation = null) else next
            }.onFailure { loadFailed = true }
        }
    }

    Box {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
            modifier = modifier
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = "header",
            ) {
                headerContent(null)
            }

            items(
                items = loadedItems,
                key = { "item/${it.key}" },
                itemContent = itemContent
            )

            if (itemsPage != null && itemsPage?.items.isNullOrEmpty()) {
                item(key = "empty") {
                    BasicText(
                        text = emptyItemsText,
                        style = typography.xs.secondary.center,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 32.dp)
                            .fillMaxWidth()
                    )
                }
            }

            if (loadFailed) {
                item(key = "retry") {
                    BasicText(
                        text = "Couldn’t load results. Tap to retry.",
                        style = typography.xs.secondary.center,
                        modifier = Modifier.fillMaxWidth().clickable { loadFailed = false }.padding(16.dp)
                    )
                }
            } else if (!(itemsPage != null && itemsPage?.continuation == null)) {
                item(key = "loading") {
                    val isFirstLoad = itemsPage?.items.isNullOrEmpty()
                    ShimmerHost(
                        modifier = Modifier
                            .run {
                                if (isFirstLoad) fillParentMaxSize() else this
                            }
                    ) {
                        repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                            itemPlaceholderContent()
                        }
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}
