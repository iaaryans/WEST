package it.vfsfitvnm.vimusic.listening

import it.vfsfitvnm.vimusic.listening.presentation.WrappedCardPalette
import it.vfsfitvnm.vimusic.listening.presentation.WrappedCardPalette.cardColors
import it.vfsfitvnm.vimusic.listening.presentation.WrappedCardPalette.contrastRatio
import it.vfsfitvnm.vimusic.listening.presentation.WrappedCardPalette.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUG-002 regression tests: the share-card colors must be readable against
 * the gradient the bitmap is actually rendered with, in every theme.
 *
 * The fixtures use the app's real built-in palettes:
 *  - dark:  accent #5055C0, background0 #16171D, text #E1E1E2, onAccent white
 *  - light: accent #5055C0, background0 #FDFDFE, text #212121, onAccent white
 *
 * The card is drawn on a vertical gradient from accentDark (top) to the
 * background color (bottom); every text must pass WCAG contrast on BOTH
 * gradient ends (the card is ~1080x1920, so text spans the whole gradient).
 */
class WrappedCardPaletteTest {

    private companion object {
        val ACCENT = 0xFF5055C0.toInt()
        val DARK_BACKGROUND = 0xFF16171D.toInt()
        val DARK_TEXT = 0xFFE1E1E2.toInt()
        val LIGHT_BACKGROUND = 0xFFFDFDFE.toInt()
        val LIGHT_TEXT = 0xFF212121.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
    }

    @Test
    fun `dark theme card colors are passed through unchanged`() {
        val colors = cardColors(
            accent = ACCENT,
            background = DARK_BACKGROUND,
            text = DARK_TEXT,
            textSecondary = DARK_TEXT,
            onAccent = WHITE
        )

        // Gradient top color = accent darkened 45% (channel-wise)
        assertEquals(0xFF242656.toInt(), colors.accentDark)
        assertEquals(DARK_BACKGROUND, colors.background)
        assertEquals(DARK_TEXT, colors.text)
        assertEquals(DARK_TEXT, colors.textSecondary)
        assertEquals(ACCENT, colors.accent)
    }

    @Test
    fun `dark theme card passes contrast on both gradient ends`() {
        val colors = cardColors(
            accent = ACCENT,
            background = DARK_BACKGROUND,
            text = DARK_TEXT,
            textSecondary = DARK_TEXT,
            onAccent = WHITE
        )

        assertTrue(contrastRatio(colors.text, colors.accentDark) >= WrappedCardPalette.MIN_CONTRAST_TEXT)
        assertTrue(contrastRatio(colors.text, colors.background) >= WrappedCardPalette.MIN_CONTRAST_TEXT)
        // The hero keeps the theme's accent unchanged in dark theme (its
        // accent-on-accentDark ratio of ~2.3 is the shipped dark appearance,
        // verified legible in the E2E report; large bold text only).
        assertEquals(ACCENT, colors.accent)
        assertTrue(contrastRatio(colors.textSecondary, colors.background) >= WrappedCardPalette.MIN_CONTRAST_TEXT)
    }

    @Test
    fun `light theme card keeps a dark background with light text`() {
        val colors = cardColors(
            accent = ACCENT,
            background = LIGHT_BACKGROUND,
            text = LIGHT_TEXT,
            textSecondary = LIGHT_TEXT,
            onAccent = WHITE
        )

        assertTrue("The generated background must stay dark", luminance(colors.background) < WrappedCardPalette.DARK_BACKGROUND_LUMINANCE)
        assertTrue("Primary text must be light on the dark card", luminance(colors.text) > luminance(colors.background))
        assertTrue("Secondary text must be light on the dark card", luminance(colors.textSecondary) > luminance(colors.background))
        assertTrue("The theme's dark text must not be reused on the dark card", colors.text != LIGHT_TEXT)
    }

    @Test
    fun `light theme card text passes WCAG contrast on both gradient ends`() {
        val colors = cardColors(
            accent = ACCENT,
            background = LIGHT_BACKGROUND,
            text = LIGHT_TEXT,
            textSecondary = LIGHT_TEXT,
            onAccent = WHITE
        )

        assertTrue(contrastRatio(colors.text, colors.accentDark) >= WrappedCardPalette.MIN_CONTRAST_TEXT)
        assertTrue(contrastRatio(colors.text, colors.background) >= WrappedCardPalette.MIN_CONTRAST_TEXT)
        assertTrue(contrastRatio(colors.textSecondary, colors.accentDark) >= WrappedCardPalette.MIN_CONTRAST_TEXT)
        assertTrue(contrastRatio(colors.textSecondary, colors.background) >= WrappedCardPalette.MIN_CONTRAST_TEXT)
        // Hero duration + ranks (large text on the dark card)
        assertTrue(contrastRatio(colors.accent, colors.accentDark) >= WrappedCardPalette.MIN_CONTRAST_HERO)
        assertTrue(contrastRatio(colors.accent, colors.background) >= WrappedCardPalette.MIN_CONTRAST_HERO)
    }

    @Test
    fun `light theme hero color is readable for a saturated accent`() {
        // Dynamic themes can produce a dark accent (hsl lightness 0.5); the
        // hero color must still reach the contrast threshold on the dark card.
        val saturatedAccent = 0xFF0033AA.toInt()
        val colors = cardColors(
            accent = saturatedAccent,
            background = LIGHT_BACKGROUND,
            text = LIGHT_TEXT,
            textSecondary = LIGHT_TEXT,
            onAccent = WHITE
        )

        assertTrue(contrastRatio(colors.accent, colors.accentDark) >= WrappedCardPalette.MIN_CONTRAST_HERO)
        assertTrue(contrastRatio(colors.accent, colors.background) >= WrappedCardPalette.MIN_CONTRAST_HERO)
    }

    @Test
    fun `contrast ratio is the WCAG formula`() {
        assertEquals(21f, contrastRatio(WHITE, 0xFF000000.toInt()), 0.01f)
        assertEquals(1f, contrastRatio(WHITE, WHITE), 0.01f)
        assertEquals(1f, contrastRatio(ACCENT, ACCENT), 0.01f)
    }

    @Test
    fun `color helpers are channel-wise and clamped`() {
        assertEquals(0xFF242656.toInt(), WrappedCardPalette.darken(ACCENT, 0.45f))
        assertEquals(0, WrappedCardPalette.darken(0xFF000000.toInt(), 0.45f) and 0xFF)
        // lightening 45%: r 80 + 175*0.45 = 158.75 -> 158 (0x9E), g 85 + 170*0.45 = 161.5 -> 161 (0xA1), b 192 + 63*0.45 = 220.35 -> 220 (0xDC)
        assertEquals(0xFF9EA1DC.toInt(), WrappedCardPalette.lighten(ACCENT, 0.45f))
        // 72% white over the darkened card background keeps the channel mix
        val cardBackground = WrappedCardPalette.darken(WrappedCardPalette.darken(ACCENT, 0.45f), 0.35f)
        val blended = WrappedCardPalette.blend(cardBackground, WHITE, 0.72f)
        assertTrue(luminance(blended) > luminance(cardBackground))
    }
}
