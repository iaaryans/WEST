package it.vfsfitvnm.vimusic.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSubmissionTest {
    @Test fun blankInputLeavesFormAvailable() {
        assertNull(searchTermForSubmission("", true))
        assertNull(searchTermForSubmission("  \t\n", true))
        assertEquals("AC/DC? #1", searchTermForSubmission("  AC/DC? #1  ", true))
    }

    @Test fun oneNavigationPerFormIncludingOverlappingInputEvents() {
        assertEquals("first", searchTermForSubmission("first", true))
        assertNull(searchTermForSubmission("first", false))
        assertNull(searchTermForSubmission("second", false))
        assertEquals("second", searchTermForSubmission("second", true))
    }
}
