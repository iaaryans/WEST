package it.vfsfitvnm.vimusic.ui.screens.home

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HomeFeedStoreTest {
    @Test fun idlePublishesImmediately() = runBlocking {
        withTimeout(1000) { HomeFeedStore(MutableStateFlow(false)).awaitIdle() }
    }

    @Test fun flingDefersUntilIdle() = runBlocking {
        val scrolling = MutableStateFlow(true)
        val job = async { HomeFeedStore(scrolling, 10L).awaitIdle() }
        yield()
        assertFalse(job.isCompleted)
        scrolling.value = false
        withTimeout(1000) { job.await() }
        assertTrue(job.isCompleted)
    }

    @Test fun cancelledPublicationNeverCompletes() = runBlocking {
        val scrolling = MutableStateFlow(true)
        var published = false
        val job = launch { HomeFeedStore(scrolling, 10L).awaitIdle(); published = true }
        yield()
        job.cancelAndJoin()
        scrolling.value = false
        assertFalse(published)
    }
    @Test fun discoveryWorkIsSerialized() = runBlocking {
        val store = HomeFeedStore(MutableStateFlow(false))
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        coroutineScope {
            List(6) {
                async {
                    store.loadDiscovery {
                        val count = active.incrementAndGet()
                        maxActive.updateAndGet { maxOf(it, count) }
                        try { delay(10) } finally { active.decrementAndGet() }
                    }
                }
            }.awaitAll()
        }
        assertEquals(1, maxActive.get())
    }

    @Test fun discoveryDoesNotStartDuringFling() = runBlocking {
        val scrolling = MutableStateFlow(true)
        val job = async { HomeFeedStore(scrolling, 10L).loadDiscovery { "ready" } }
        yield()
        assertFalse(job.isCompleted)
        scrolling.value = false
        assertEquals("ready", withTimeout(1000) { job.await() })
    }
    @Test fun horizontalAndVerticalGesturesBothGatePublication() = runBlocking {
        val vertical = MutableStateFlow(true)
        val store = HomeFeedStore(vertical, 25L)
        val a = Any()
        val b = Any()
        store.setHorizontalScrolling(a, true)
        store.setHorizontalScrolling(b, true)
        val pending = async { store.awaitIdle() }
        delay(50)
        assertFalse(pending.isCompleted)
        vertical.value = false
        store.setHorizontalScrolling(a, false)
        delay(60)
        assertFalse(pending.isCompleted)
        store.setHorizontalScrolling(b, false)
        withTimeout(1000) { pending.await() }
    }

    @Test fun secondFlingRestartsSettleWindow() = runBlocking {
        val vertical = MutableStateFlow(true)
        val store = HomeFeedStore(vertical, 180L)
        val pending = async { store.awaitIdle() }
        delay(60)
        vertical.value = false
        delay(90)
        vertical.value = true
        delay(35)
        vertical.value = false
        delay(90)
        assertFalse(pending.isCompleted)
        withTimeout(1000) { pending.await() }
    }

    @Test fun staticPageOnlyWhenEntireSinglePageFits() {
        assertTrue(HomeShelfPolicy.useStaticPage(1, true))
        assertFalse(HomeShelfPolicy.useStaticPage(1, false))
        assertFalse(HomeShelfPolicy.useStaticPage(0, true))
        assertFalse(HomeShelfPolicy.useStaticPage(2, true))
    }
}
