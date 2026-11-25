package it.vfsfitvnm.vimusic.ui.screens.search

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.MainActivity
import it.vfsfitvnm.vimusic.models.SearchQuery
import java.util.UUID
import it.vfsfitvnm.vimusic.utils.preferences
import org.junit.Before
import org.junit.Test

class SearchNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation
    private val term = "WEST-" + UUID.randomUUID().toString().take(6)

    @Before fun prepareExistingUserForSearchRegressions() {
        // The first-launch taste onboarding is tested separately; Search crash
        // regressions must reach Search instead of stopping at onboarding.
        instrumentation.targetContext.preferences.edit()
            .putBoolean("westTasteOnboardingCompletedV1", true)
            .putBoolean("westTasteEditRequestedV1", false)
            .commit()
    }

    private fun find(label: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        android.util.Log.i("WestDeviceTest", "Waiting for $label")
        val until = SystemClock.elapsedRealtime() + 20000
        var seen = emptyList<String>()
        while (SystemClock.elapsedRealtime() < until) {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            automation.rootInActiveWindow?.let { nodes.add(it) }
            var i = 0
            while (i < nodes.size) {
                val n = nodes[i++]
                if (predicate(n)) return n
                for (j in 0 until n.childCount) n.getChild(j)?.let(nodes::add)
            }
            seen = nodes.mapNotNull { (it.text ?: it.contentDescription)?.toString() }
            SystemClock.sleep(150)
        }
        error("Missing $label. Visible labels: $seen")
    }

    private fun click(label: String) {
        var n: AccessibilityNodeInfo? = find(label) { it.text?.toString() == label || it.contentDescription?.toString() == label }
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            n = n.parent
        }
        error("No clickable ancestor for $label")
    }

    @Test fun historyOnDevice() = runSearch(true)
    @Test fun imeOnDevice() = runSearch(false)

    @Test fun pinnedQuerySurvivesRecreationAndCanBeRemoved() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            click("Search")
            val field = find("Search input") { it.isEditable }
            check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, term)
            }))
            click("Pin search")
            find("pin confirmation") { it.text?.toString() == "Unpin search" }
            click("Clear")
            find("pinned list") { it.text?.toString() == "Pinned searches" }
            activity.recreate()
            click(term)
            find("result category") { it.text?.toString() == "Songs" }
            find("pinned result") { !it.isEditable && it.text?.toString() == term }
            activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            click("Unpin")
            find("Search input") { it.isEditable }
            check(term !in instrumentation.targetContext.preferences.getStringSet("pinnedSearchTerms", emptySet()).orEmpty())
        }
    }

    @Test fun pasteSubmitsTrimmedClipboardText() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            click("Search")
            find("Search input") { it.isEditable }
            activity.onActivity {
                val clipboard = it.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("query", "  $term  "))
            }
            click("Paste & search")
            find("result category") { it.text?.toString() == "Songs" }
            find("pasted result") { !it.isEditable && it.text?.toString() == term }
        }
    }

    @Test fun smartSearchFindsOfflineHistoryAfterTypo() {
        ActivityScenario.launch(MainActivity::class.java).use {
            Database.insert(SearchQuery(query = "Moonlight Sonata"))
            click("Search")
            click("Smart")
            find("Smart search placeholder") { it.text?.toString() == "Search songs, artists, playlists or lyrics" }
            find("Smart search input") { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            find("focused smart input") {
                it.isEditable && it.isFocused && it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Moonlgiht")
                })
            }
            find("typed typo") { it.isEditable && it.text?.toString() == "Moonlgiht" }
            click("Moonlight Sonata")
            click("Search again")
            find("song results") { it.text?.toString() == "Songs" }
            find("submitted title") { it.text?.toString() == "Moonlight Sonata" && !it.isEditable }
        }
    }

    private fun runSearch(history: Boolean) {
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            if (history) Database.insert(SearchQuery(query = term))
            click("Search")
            val field = find("Search input") { it.isEditable }
            if (history) click(term) else {
                field.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, term)
                }))
                find("typed query") { it.isEditable && it.text?.toString() == term }
                activity.onActivity { screen ->
                    val connection = screen.currentFocus?.onCreateInputConnection(EditorInfo())
                        ?: error("Focused search field has no input connection")
                    check(connection.performEditorAction(EditorInfo.IME_ACTION_SEARCH))
                }
            }
            find("result category") { it.text?.toString() == "Songs" }
            find("submitted result header") { !it.isEditable && it.text?.toString() == term }
            activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            find("Search after Back") { it.isEditable }
        }
    }
}
