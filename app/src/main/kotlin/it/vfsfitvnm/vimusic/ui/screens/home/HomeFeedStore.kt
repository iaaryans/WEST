package it.vfsfitvnm.vimusic.ui.screens.home

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Both horizontal and vertical gestures gate non-urgent feed publication. */
internal class HomeFeedStore(verticalScrolling: Flow<Boolean>, private val settleMs: Long = 120L) {
    private val discoveryMutex = Mutex()
    private val horizontal = MutableStateFlow<Set<Any>>(emptySet())
    private val scrolling = combine(verticalScrolling, horizontal) { vertical, active ->
        vertical || active.isNotEmpty()
    }.distinctUntilChanged()

    // UI registrations are called from the main dispatcher; synchronization also
    // makes simultaneous cancellation/registration safe in isolated policy tests.
    @Synchronized
    fun setHorizontalScrolling(token: Any, active: Boolean) {
        val previous = horizontal.value
        val next = if (active) previous + token else previous - token
        if (next != previous) horizontal.value = next
    }

    suspend fun <T> loadDiscovery(load: suspend () -> T): T = discoveryMutex.withLock {
        awaitIdle()
        withContext(Dispatchers.Default) { load() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun awaitIdle() {
        // Keep observing throughout the settle window, rather than missing a
        // second short fling that starts and ends during a fixed delay.
        var hadMotion = false
        scrolling.transformLatest { busy ->
            if (busy) hadMotion = true
            else {
                if (hadMotion) delay(settleMs)
                emit(Unit)
            }
        }.first()
    }
}
