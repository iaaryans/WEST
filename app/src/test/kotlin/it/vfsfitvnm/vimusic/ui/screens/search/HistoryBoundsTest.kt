package it.vfsfitvnm.vimusic.ui.screens.search

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryBoundsTest {
    @Test fun localDaysIncludeClockChangesAndPeriodsAreAdjacent() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val day = Calendar.getInstance().apply { set(2025, Calendar.MARCH, 9, 12, 30) }.timeInMillis
            val full = historyBounds(day, 0, 24)
            assertEquals(23 * 3600000L, full.second - full.first)
            assertEquals(historyBounds(day, 0, 6).second, historyBounds(day, 6, 12).first)
            assertEquals(full.second, historyBounds(day, 18, 24).second)
        } finally { TimeZone.setDefault(original) }
    }
}
