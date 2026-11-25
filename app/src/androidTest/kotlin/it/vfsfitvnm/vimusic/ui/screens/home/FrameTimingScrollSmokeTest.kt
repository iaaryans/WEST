package it.vfsfitvnm.vimusic.ui.screens.home

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import it.vfsfitvnm.vimusic.MainActivity
import it.vfsfitvnm.vimusic.utils.preferences
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentLinkedQueue

/** Instrumented before/after telemetry: log actual p50/p95, no made-up FPS. */
class FrameTimingScrollSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation

    @Test fun collectRealFrameTimingWhileScrollingBothFeeds() {
        assumeTrue("FrameMetrics requires Android 7+", Build.VERSION.SDK_INT >= 24)
        instrumentation.targetContext.preferences.edit()
            .putBoolean("westTasteOnboardingCompletedV1", true)
            .putBoolean("westTasteEditRequestedV1", false)
            .commit()
        val timings = ConcurrentLinkedQueue<Long>()
        val thread = HandlerThread("WEST-FrameMetrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val nanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (nanos > 0) timings.add(nanos)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
                }
                SystemClock.sleep(800)
                repeat(3) {
                    scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                }
                tap("Moodspace")
                SystemClock.sleep(500)
                repeat(3) {
                    scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                }
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            }
            val measurements = timings.toList().sorted()
            assertTrue("No frames collected", measurements.isNotEmpty())
            fun percentile(p: Double): Double = measurements[((measurements.size - 1) * p).toInt()] / 1_000_000.0
            val slow60 = measurements.count { it > 16_666_667L }
            Log.i("WEST_Performance", "frames=${measurements.size} p50=${percentile(.5)}ms " +
                "p95=${percentile(.95)}ms >16.7ms=$slow60")
        } finally {
            thread.quitSafely()
        }
    }

    private fun tap(label: String) {
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val nodes = ArrayDeque<AccessibilityNodeInfo>()
            automation.rootInActiveWindow?.let(nodes::add)
            while (nodes.isNotEmpty()) {
                val current = nodes.removeFirst()
                if (current.text?.toString() == label || current.contentDescription?.toString() == label) {
                    var parent: AccessibilityNodeInfo? = current
                    while (parent != null) {
                        if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                        parent = parent.parent
                    }
                }
                repeat(current.childCount) { index -> current.getChild(index)?.let(nodes::add) }
            }
            SystemClock.sleep(100)
        }
        error("Unable to tap $label")
    }

    private fun scroll(direction: Int) {
        val nodes = ArrayDeque<AccessibilityNodeInfo>()
        automation.rootInActiveWindow?.let(nodes::add)
        while (nodes.isNotEmpty()) {
            val current = nodes.removeFirst()
            if (current.isScrollable && current.performAction(direction)) {
                SystemClock.sleep(180)
                return
            }
            repeat(current.childCount) { index -> current.getChild(index)?.let(nodes::add) }
        }
    }
}
