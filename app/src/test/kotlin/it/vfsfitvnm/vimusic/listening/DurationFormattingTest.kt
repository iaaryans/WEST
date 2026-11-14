package it.vfsfitvnm.vimusic.listening

import it.vfsfitvnm.vimusic.listening.domain.formatListeningDuration
import it.vfsfitvnm.vimusic.listening.domain.formatPlural
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the compact duration formatting used across the statistics
 * screens and the WEST Wrapped card: sub-hour durations must never round
 * down to "0 hours" and whole hours must not show a trailing "0m".
 *
 * Also covers the quantity-based plural formatter (BUG-006 regression:
 * "1 plays" and "over 1 days" must never appear).
 */
class DurationFormattingTest {

    @Test
    fun `zero duration formats as 0s`() {
        assertEquals("0s", formatListeningDuration(0L))
    }

    @Test
    fun `negative duration formats as 0s`() {
        assertEquals("0s", formatListeningDuration(-1L))
    }

    @Test
    fun `35 minutes formats as 35m`() {
        assertEquals("35m", formatListeningDuration(35 * 60_000L))
    }

    @Test
    fun `54 minutes formats as 54m`() {
        assertEquals("54m", formatListeningDuration(54 * 60_000L))
    }

    @Test
    fun `59 minutes formats as 59m`() {
        assertEquals("59m", formatListeningDuration(59 * 60_000L))
    }

    @Test
    fun `exactly one hour formats as 1h without trailing 0m`() {
        assertEquals("1h", formatListeningDuration(3_600_000L))
    }

    @Test
    fun `one hour thirty minutes formats as 1h 30m`() {
        assertEquals("1h 30m", formatListeningDuration(90 * 60_000L))
    }

    @Test
    fun `five hours forty-five minutes formats as 5h 45m`() {
        assertEquals("5h 45m", formatListeningDuration(5 * 3_600_000L + 45 * 60_000L))
    }

    @Test
    fun `sub-minute duration formats as seconds`() {
        assertEquals("45s", formatListeningDuration(45_000L))
    }

    @Test
    fun `exactly one day formats as 1d without trailing 0h`() {
        assertEquals("1d", formatListeningDuration(86_400_000L))
    }

    @Test
    fun `multiple days with hours formats as d h`() {
        assertEquals("2d 3h", formatListeningDuration(2 * 86_400_000L + 3 * 3_600_000L))
    }

    @Test
    fun `multiple days with hours and minutes drops minutes`() {
        assertEquals("2d 3h", formatListeningDuration(2 * 86_400_000L + 3 * 3_600_000L + 12 * 60_000L))
    }

    // ------------------------------------------------------------------
    // Plural formatting (BUG-006)
    // ------------------------------------------------------------------

    @Test
    fun `singular counts never receive a plural suffix`() {
        assertEquals("1 play", formatPlural(1L, "play"))
        assertEquals("1 day", formatPlural(1L, "day"))
        assertEquals("1 unique song", formatPlural(1L, "unique song"))
        assertEquals("1 artist", formatPlural(1L, "artist"))
        assertEquals("1 track", formatPlural(1L, "track"))
    }

    @Test
    fun `plural counts use the plural form`() {
        assertEquals("2 plays", formatPlural(2L, "play"))
        assertEquals("3 days", formatPlural(3L, "day"))
        assertEquals("12 unique songs", formatPlural(12L, "unique song"))
        assertEquals("25 artists", formatPlural(25L, "artist"))
    }

    @Test
    fun `zero count uses the plural form`() {
        assertEquals("0 plays", formatPlural(0L, "play"))
        assertEquals("0 days", formatPlural(0L, "day"))
    }

    @Test
    fun `large counts are grouped`() {
        assertEquals("1,234 plays", formatPlural(1_234L, "play"))
    }
}
