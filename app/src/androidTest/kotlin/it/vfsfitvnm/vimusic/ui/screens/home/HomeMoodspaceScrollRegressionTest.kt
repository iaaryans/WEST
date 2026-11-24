package it.vfsfitvnm.vimusic.ui.screens.home

import android.os.SystemClock
import java.util.ArrayDeque
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import it.vfsfitvnm.vimusic.MainActivity
import it.vfsfitvnm.vimusic.utils.preferences
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/** UI stability smoke test; not a substitute for hardware frame-timing benchmarks. */
class HomeMoodspaceScrollRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation

    @Before fun bypassOnlyTheUnrelatedFirstLaunchFlow() {
        instrumentation.targetContext.preferences.edit()
            .putBoolean("westTasteOnboardingCompletedV1", true)
            .putBoolean("westTasteEditRequestedV1", false)
            .commit()
    }

    private fun findText(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 12_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val nodes = ArrayDeque<AccessibilityNodeInfo>()
            automation.rootInActiveWindow?.let(nodes::add)
            while (nodes.isNotEmpty()) {
                val item = nodes.removeFirst()
                if (item.text?.toString() == label || item.contentDescription?.toString() == label) return item
                for (i in 0 until item.childCount) item.getChild(i)?.let(nodes::add)
            }
            SystemClock.sleep(100)
        }
        error("Missing $label")
    }

    private fun tap(label: String) {
        var node: AccessibilityNodeInfo? = findText(label)
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            node = node.parent
        }
        error("No clickable parent for $label")
    }

    private fun scrollOnce(direction: Int) {
        val nodes = ArrayDeque<AccessibilityNodeInfo>()
        automation.rootInActiveWindow?.let(nodes::add)
        while (nodes.isNotEmpty()) {
            val node = nodes.removeFirst()
            if (node.isScrollable && node.performAction(direction)) return
            for (i in 0 until node.childCount) node.getChild(i)?.let(nodes::add)
        }
    }

    @Test fun navigatingAndScrollingHomeAndMoodspaceDoesNotCrash() {
        ActivityScenario.launch(MainActivity::class.java).use {
            repeat(3) {
                assertNotNull(findText("Top Picks for You"))
                tap("Moodspace")
                assertNotNull(findText("Explore moods & genres"))
                scrollOnce(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                scrollOnce(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                tap("Home")
            }
        }
    }
}
