package it.vfsfitvnm.vimusic.ui.screens.player
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

import androidx.compose.runtime.LaunchedEffect
import it.vfsfitvnm.vimusic.ui.components.themed.LocalReducedMotion
import it.vfsfitvnm.vimusic.ui.components.themed.ConfirmationDialog
import it.vfsfitvnm.vimusic.ui.screens.home.ActionLabel


import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import it.vfsfitvnm.vimusic.utils.forceSeekToPrevious
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import coil.request.ImageRequest
import coil.size.Precision
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.compose.routing.OnGlobalRoute
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.service.PlayerService
import it.vfsfitvnm.vimusic.ui.components.BottomSheet
import it.vfsfitvnm.vimusic.ui.components.BottomSheetState
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.rememberBottomSheetState
import it.vfsfitvnm.vimusic.ui.components.themed.BaseMediaItemMenu
import it.vfsfitvnm.vimusic.ui.components.themed.IconButton
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.DisposableListener
import it.vfsfitvnm.vimusic.utils.forceSeekToNext
import it.vfsfitvnm.vimusic.utils.isLandscape
import it.vfsfitvnm.vimusic.utils.positionAndDurationState
import it.vfsfitvnm.vimusic.utils.seamlessPlay
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.shouldBePlaying
import it.vfsfitvnm.vimusic.utils.thumbnail
import it.vfsfitvnm.vimusic.utils.toast
import kotlinx.coroutines.launch

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun Player(
    layoutState: BottomSheetState,
    modifier: Modifier = Modifier,
) {
    val menuState = LocalMenuState.current

    val (colorPalette, typography, thumbnailShape) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current

    binder?.player ?: return

    var nullableMediaItem by remember {
        mutableStateOf(binder.player.currentMediaItem, neverEqualPolicy())
    }

    var shouldBePlaying by remember {
        mutableStateOf(binder.player.shouldBePlaying)
    }

    binder.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                nullableMediaItem = mediaItem
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = binder.player.shouldBePlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                shouldBePlaying = binder.player.shouldBePlaying
            }
        }
    }

    val player = binder.player
    val reduce = LocalReducedMotion.current
    var undo by remember(player) { mutableStateOf<QueueSnapshot?>(null) }
    val previous = undo
    if (previous != null && nullableMediaItem == null) {
        ConfirmationDialog(text = "Queue cleared", confirmText = "Undo", cancelText = "Done",
            onDismiss = { undo = null }, onConfirm = {
                undo = null
                if (player.mediaItemCount == 0) {
                    player.setMediaItems(previous.items, previous.index, previous.position)
                    player.prepare()
                    player.playWhenReady = previous.playing
                }
            })
    }
    LaunchedEffect(nullableMediaItem) { if (nullableMediaItem != null) undo = null }
    val mediaItem = nullableMediaItem ?: return

    val swipeScope = rememberCoroutineScope()
    val dockSwipe = remember { Animatable(0f) }

    // Keep the high-frequency progress observation out of the sheet's composition.
    // Collapsed progress reads State in the draw phase; expanded controls read
    // it only in their own composable lambda instead of rebuilding the player.
    val positionAndDurationState = binder.player.positionAndDurationState()

    val windowInsets = WindowInsets.systemBars

    val horizontalBottomPaddingValues = windowInsets
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues()

    OnGlobalRoute {
        layoutState.collapseSoft()
    }

    // Store each cover in sheet-local pixels so dragging the sheet moves both
    // endpoints together. The overlay is above both the player and the dock.
    val density = LocalDensity.current
    var dock by remember(layoutState) { mutableStateOf<Rect?>(null) }
    var sleeve by remember(layoutState) { mutableStateOf<Rect?>(null) }
    val movingCover by remember(layoutState) {
        derivedStateOf { layoutState.progress > 0f && layoutState.progress < 1f && dock != null && sleeve != null }
    }
    fun Modifier.captureCover(save: (Rect) -> Unit) = onGloballyPositioned { coordinates ->
        val sheetY = with(density) { (layoutState.expandedBound - layoutState.value).roundToPx() }
        val origin = coordinates.positionInRoot() - Offset(0f, sheetY.toFloat())
        save(Rect(origin.x, origin.y, origin.x + coordinates.size.width, origin.y + coordinates.size.height))
    }

    BottomSheet(
        state = layoutState,
        modifier = modifier,
        collapsedContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontalBottomPaddingValues)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .graphicsLayer { translationX = if (reduce) 0f else dockSwipe.value }
                    .pointerInput(binder.player, reduce) {
                        var swipe = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { swipe = 0f },
                            onHorizontalDrag = { _, delta ->
                                swipe += delta
                                val resisted = (swipe * 0.12f).coerceIn(-16.dp.toPx(), 16.dp.toPx())
                                if (!reduce) swipeScope.launch { dockSwipe.snapTo(resisted) }
                            },
                            onDragEnd = {
                                val threshold = 72.dp.toPx()
                                if (swipe < -threshold) binder.skipToNextOrDiscover()
                                if (swipe > threshold) binder.player.forceSeekToPrevious()
                                swipeScope.launch {
                                    dockSwipe.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow))
                                }
                            },
                            onDragCancel = {
                                swipeScope.launch { dockSwipe.animateTo(0f, spring()) }
                            }
                        )
                    }
                    .background(colorPalette.background1)
                    .border(1.dp, colorPalette.text.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    .drawBehind {
                        val positionAndDuration = positionAndDurationState.value
                        val progress =
                            if (positionAndDuration.second > 0) (positionAndDuration.first.toFloat() / positionAndDuration.second).coerceIn(0f, 1f) else 0f

                        drawLine(
                            color = colorPalette.accent,
                            start = Offset(x = 0f, y = size.height - 1.dp.toPx()),
                            end = Offset(x = size.width * progress, y = size.height - 1.dp.toPx()),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
            ) {
                Spacer(
                    modifier = Modifier
                        .width(2.dp)
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(Dimensions.collapsedPlayer)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .captureCover { dock = it }
                            .alpha(if (movingCover) 0f else 1f)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Crossfade(
                            targetState = mediaItem.mediaMetadata.artworkUri,
                            animationSpec = tween(durationMillis = if (reduce) 0 else 280)
                        ) { artworkUri ->
                            AsyncImage(
                                model = artworkUri.thumbnail(Dimensions.thumbnails.song.px),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = Triple(
                        mediaItem.mediaId,
                        mediaItem.mediaMetadata.title?.toString().orEmpty(),
                        mediaItem.mediaMetadata.artist?.toString().orEmpty()
                    ),
                    transitionSpec = {
                        if (reduce) EnterTransition.None with ExitTransition.None else
                        (slideInHorizontally(tween(270)) { it / 8 } + fadeIn(tween(220))) with
                            (slideOutHorizontally(tween(210)) { -it / 8 } + fadeOut(tween(170)))
                    },
                    modifier = Modifier
                        .height(Dimensions.collapsedPlayer)
                        .weight(1f)
                ) { metadata ->
                    Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                        BasicText(
                            text = metadata.second,
                            style = typography.xs.semiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        BasicText(
                            text = metadata.third,
                            style = typography.xs.semiBold.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(
                    modifier = Modifier
                        .width(2.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(Dimensions.collapsedPlayer)
                ) {
                    IconButton(
                        icon = if (shouldBePlaying) R.drawable.pause else R.drawable.play,
                        color = colorPalette.text,
                        onClick = {
                            if (shouldBePlaying) {
                                binder.player.pause()
                            } else {
                                if (binder.player.playbackState == Player.STATE_IDLE) {
                                    binder.player.prepare()
                                }
                                binder.player.play()
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .size(20.dp)
                    )

                    IconButton(
                        icon = R.drawable.play_skip_forward,
                        color = colorPalette.text,
                        onClick = binder::skipToNextOrDiscover,
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .size(20.dp)
                    )
                }

                Spacer(
                    modifier = Modifier
                        .width(2.dp)
                )
            }
        },
        transitionOverlay = {
            val from = dock
            val to = sleeve
            if (movingCover && from != null && to != null && to.width > 0f && to.height > 0f) {
                AsyncImage(
                    model = mediaItem.mediaMetadata.artworkUri.thumbnail((Dimensions.thumbnails.player.song - 64.dp).px),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(with(density) { to.width.toDp() }, with(density) { to.height.toDp() })
                        .graphicsLayer {
                            val progress = layoutState.progress.coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = from.left + (to.left - from.left) * progress
                            translationY = from.top + (to.top - from.top) * progress
                            scaleX = (from.width + (to.width - from.width) * progress) / to.width
                            scaleY = (from.height + (to.height - from.height) * progress) / to.height
                        }
                        .clip(thumbnailShape)
                )
            }
        }
    ) {
        var isShowingLyrics by rememberSaveable {
            mutableStateOf(false)
        }

        var isShowingStatsForNerds by rememberSaveable {
            mutableStateOf(false)
        }
        var isImmersiveOpen by rememberSaveable { mutableStateOf(false) }
        if (isImmersiveOpen) {
            val positionAndDuration = positionAndDurationState.value
            ImmersivePlayerDialog(
                player = player,
                mediaItem = mediaItem,
                playing = shouldBePlaying,
                position = positionAndDuration.first,
                duration = positionAndDuration.second,
                onDismiss = { isImmersiveOpen = false }
            )
        }

        val playerBottomSheetState = rememberBottomSheetState(
            64.dp + horizontalBottomPaddingValues.calculateBottomPadding(),
            layoutState.expandedBound
        )

        val context = LocalContext.current
        val artwork = mediaItem.mediaMetadata.artworkUri
        val wash = remember(artwork, context) {
            ImageRequest.Builder(context).data(artwork).size(12, 12)
                .precision(Precision.EXACT).crossfade(600).build()
        }
        Box(Modifier.fillMaxSize().background(colorPalette.background0)) {
            AsyncImage(model = wash, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (colorPalette.isDark) 0.42f else 0.24f))
            Spacer(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                colorPalette.background0.copy(alpha = 0.08f), colorPalette.background0.copy(alpha = 0.88f)
            ))))
        }
        val containerModifier = Modifier.fillMaxSize()
            .padding(
                windowInsets
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    .asPaddingValues()
            )
            .padding(bottom = playerBottomSheetState.collapsedBound)

        val thumbnailContent: @Composable (modifier: Modifier) -> Unit = { modifier ->
            Thumbnail(
                isShowingLyrics = isShowingLyrics,
                onShowLyrics = { isShowingLyrics = it },
                isShowingStatsForNerds = isShowingStatsForNerds,
                onShowStatsForNerds = { isShowingStatsForNerds = it },
                artworkModifier = Modifier.captureCover { sleeve = it }.alpha(if (movingCover) 0f else 1f),
                shouldBePlaying = shouldBePlaying,
                modifier = modifier
                    .nestedScroll(layoutState.preUpPostDownNestedScrollConnection)
            )
        }

        val controlsContent: @Composable (modifier: Modifier) -> Unit = { modifier ->
            val positionAndDuration = positionAndDurationState.value
            Controls(
                mediaId = mediaItem.mediaId,
                title = mediaItem.mediaMetadata.title?.toString(),
                artist = mediaItem.mediaMetadata.artist?.toString(),
                shouldBePlaying = shouldBePlaying,
                position = positionAndDuration.first,
                duration = positionAndDuration.second,
                modifier = modifier
            )
        }

        if (isLandscape) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = containerModifier
                    .padding(top = 32.dp)
            ) {
                // Back button for landscape
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colorPalette.background2)
                        .clickable { layoutState.collapseSoft() },
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        icon = R.drawable.chevron_down,
                        color = colorPalette.text,
                        onClick = { layoutState.collapseSoft() },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(0.66f)
                        .padding(bottom = 16.dp)
                ) {
                    thumbnailContent(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                    )
                }

                controlsContent(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxHeight()
                        .weight(1f)
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = containerModifier
                    .padding(top = 8.dp)
            ) {
                // Back button row at top
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colorPalette.background2)
                            .clickable { layoutState.collapseSoft() },
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            icon = R.drawable.chevron_down,
                            color = colorPalette.text,
                            onClick = { layoutState.collapseSoft() },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.25f)
                ) {
                    thumbnailContent(
                        modifier = Modifier
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                    )
                }

                controlsContent(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }


        Queue(
            layoutState = playerBottomSheetState,
            onClear = {
                if (player.mediaItemCount > 0) {
                    undo = QueueSnapshot(List(player.mediaItemCount, player::getMediaItemAt),
                        player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1),
                        player.currentPosition.coerceAtLeast(0), player.playWhenReady)
                    binder.stopRadio()
                    player.pause()
                    player.clearMediaItems()
                    layoutState.dismiss()
                }
            },
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .fillMaxHeight()
                ) {
                    ActionLabel("Lyrics") { isShowingLyrics = !isShowingLyrics; isShowingStatsForNerds = false }
                    ActionLabel("Immersive") { isImmersiveOpen = true }
                    ActionLabel("Queue", playerBottomSheetState::expandSoft)
                    IconButton(
                        icon = R.drawable.ellipsis_horizontal,
                        color = colorPalette.text,
                        onClick = {
                            menuState.display {
                                PlayerMenu(
                                    onDismiss = menuState::hide,
                                    mediaItem = mediaItem,
                                    onTrackInfo = { isShowingStatsForNerds = true; isShowingLyrics = false },
                                    binder = binder
                                )
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .size(20.dp)
                    )

                    Spacer(
                        modifier = Modifier
                            .width(4.dp)
                    )
                }
            },
            backgroundColorProvider = { colorPalette.background2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
        )
    }
}

@ExperimentalAnimationApi
@Composable
private fun PlayerMenu(
    binder: PlayerService.Binder,
    mediaItem: MediaItem,
    onTrackInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    BaseMediaItemMenu(
        mediaItem = mediaItem,
        onTrackInfo = onTrackInfo,
        onStartRadio = {
            binder.stopRadio()
            binder.player.seamlessPlay(mediaItem)
            binder.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
        },
        onGoToEqualizer = {
            try {
                activityResultLauncher.launch(
                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, binder.player.audioSessionId)
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }
                )
            } catch (e: ActivityNotFoundException) {
                context.toast("Couldn't find an application to equalize audio")
            }
        },
        onShowSleepTimer = {},
        onDismiss = onDismiss
    )
}

private data class QueueSnapshot(val items: List<MediaItem>, val index: Int, val position: Long, val playing: Boolean)
