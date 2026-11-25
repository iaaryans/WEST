package it.vfsfitvnm.vimusic.utils

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline

val Player.currentWindow: Timeline.Window?
    get() = if (mediaItemCount == 0) null else currentTimeline.getWindow(currentMediaItemIndex, Timeline.Window())

val Timeline.mediaItems: List<MediaItem>
    get() = List(windowCount) {
        getWindow(it, Timeline.Window()).mediaItem
    }

inline val Timeline.windows: List<Timeline.Window>
    get() = List(windowCount) {
        getWindow(it, Timeline.Window())
    }

val Player.shouldBePlaying: Boolean
    get() = !(playbackState == Player.STATE_ENDED || !playWhenReady)

private data class ReplacedQueue(
    val items: List<MediaItem>, val index: Int, val position: Long,
    val playing: Boolean, val savedAt: Long
)

private val replacedQueues = java.util.WeakHashMap<Player, ReplacedQueue>()

private fun Player.rememberQueue() {
    if (currentMediaItemIndex !in 0 until mediaItemCount) return
    replacedQueues[this] = ReplacedQueue(currentTimeline.mediaItems, currentMediaItemIndex,
        currentPosition.coerceAtLeast(0), shouldBePlaying, android.os.SystemClock.elapsedRealtime())
}

internal fun Player.canRestoreQueue(): Boolean {
    val saved = replacedQueues[this] ?: return false
    if (android.os.SystemClock.elapsedRealtime() - saved.savedAt < 300_000L) return true
    replacedQueues.remove(this)
    return false
}

internal fun Player.restorePreviousQueue() {
    if (!canRestoreQueue()) return
    val saved = replacedQueues.remove(this) ?: return
    setMediaItems(saved.items, saved.index, saved.position)
    prepare()
    playWhenReady = saved.playing
}

fun Player.seamlessPlay(mediaItem: MediaItem) {
    if (mediaItem.mediaId == currentMediaItem?.mediaId) {
        if (mediaItemCount > 1) rememberQueue()
        if (currentMediaItemIndex > 0) removeMediaItems(0, currentMediaItemIndex)
        if (currentMediaItemIndex < mediaItemCount - 1) removeMediaItems(currentMediaItemIndex + 1, mediaItemCount)
    } else {
        forcePlay(mediaItem)
    }
}

fun Player.shuffleQueue() {
    val mediaItems = currentTimeline.mediaItems.toMutableList().apply { removeAt(currentMediaItemIndex) }
    if (currentMediaItemIndex > 0) removeMediaItems(0, currentMediaItemIndex)
    if (currentMediaItemIndex < mediaItemCount - 1) removeMediaItems(currentMediaItemIndex + 1, mediaItemCount)
    addMediaItems(mediaItems.shuffled())
}

fun Player.forcePlay(mediaItem: MediaItem) {
    rememberQueue()
    setMediaItem(mediaItem, true)
    playWhenReady = true
    prepare()
}

fun Player.forcePlayAtIndex(mediaItems: List<MediaItem>, mediaItemIndex: Int) {
    if (mediaItems.isEmpty()) return

    rememberQueue()
    setMediaItems(mediaItems, mediaItemIndex, C.TIME_UNSET)
    playWhenReady = true
    prepare()
}

fun Player.forcePlayFromBeginning(mediaItems: List<MediaItem>) =
    forcePlayAtIndex(mediaItems, 0)

fun Player.forceSeekToPrevious() {
    if (hasPreviousMediaItem() || currentPosition > maxSeekToPreviousPosition) {
        seekToPrevious()
    } else if (mediaItemCount > 1) {
        seekTo(mediaItemCount - 1, C.TIME_UNSET)
    }
}

/** Never replay a lone search result when Next is pressed. Radio may still be loading. */
fun Player.forceSeekToNext() {
    if (mediaItemCount <= 1) return
    if (hasNextMediaItem()) seekToNext()
}

fun Player.addNext(mediaItem: MediaItem) {
    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlay(mediaItem)
    } else {
        addMediaItem(currentMediaItemIndex + 1, mediaItem)
    }
}

fun Player.enqueue(mediaItem: MediaItem) {
    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlay(mediaItem)
    } else {
        addMediaItem(mediaItemCount, mediaItem)
    }
}

fun Player.enqueue(mediaItems: List<MediaItem>) {
    if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        forcePlayFromBeginning(mediaItems)
    } else {
        addMediaItems(mediaItemCount, mediaItems)
    }
}

fun Player.findNextMediaItemById(mediaId: String): MediaItem? {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) {
            return getMediaItemAt(i)
        }
    }
    return null
}
