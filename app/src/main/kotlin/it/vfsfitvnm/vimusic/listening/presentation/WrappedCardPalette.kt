package it.vfsfitvnm.vimusic.listening.presentation

import kotlin.math.pow

/**
 * Derives the WEST Wrapped share-card palette from the active theme palette
 * so that every text stays readable against the gradient the card is
 * actually rendered with (BUG-002).
 *
 * The card background is a vertical gradient from `accentDark` (top) to the
 * theme background (bottom). In a light theme that runs deep-navy →
 * near-white: no single text color is readable on both ends, which is why
 * the theme's own text colors cannot simply be reused. When the theme
 * background is light, the card therefore keeps a dark gradient derived from
 * the same accent and the text colors are chosen against that dark
 * background (light text), independent of the theme text.
 *
 * When the theme background is dark (the dark theme), the existing colors
 * are already readable against the gradient and are passed through
 * unchanged, preserving the dark-theme card appearance exactly.
 *
 * All functions are pure Kotlin (no android.graphics) so the contrast rules
 * are unit-testable on the JVM.
 */
object WrappedCardPalette {

    /** Backgrounds below this relative luminance count as dark. */
    const val DARK_BACKGROUND_LUMINANCE = 0.5f

    /** WCAG contrast required for the hero duration / ranks (large text). */
    const val MIN_CONTRAST_HERO = 3.0f

    /** WCAG contrast required for normal and secondary text. */
    const val MIN_CONTRAST_TEXT = 4.5f

    /** How much of the accent's darkened tone is kept for the card background. */
    private const val CARD_BACKGROUND_DARKEN = 0.35f

    /** How much the accent is darkened for the gradient's top color. */
    private const val ACCENT_DARKEN = 0.45f

    /** Lightening applied to the accent when it lacks contrast on the card. */
    private const val HERO_LIGHTEN = 0.45f

    /** Opacity of the secondary text against the card background. */
    private const val SECONDARY_OPACITY = 0.72f

    fun cardColors(
        accent: Int,
        background: Int,
        text: Int,
        textSecondary: Int,
        onAccent: Int
    ): WrappedShareCard.CardColors {
        val accentDark = darken(accent, ACCENT_DARKEN)

        if (luminance(background) < DARK_BACKGROUND_LUMINANCE) {
            // Dark theme: gradient accentDark -> dark background with the
            // theme's light text is already readable; keep it as-is.
            return WrappedShareCard.CardColors(
                accent = accent,
                accentDark = accentDark,
                background = background,
                text = text,
                textSecondary = textSecondary,
                onAccent = onAccent
            )
        }

        // Light theme: render the card on a dark gradient derived from the
        // accent and pick text colors against the generated background.
        val cardBackground = darken(accentDark, CARD_BACKGROUND_DARKEN)
        val hero = readableOnCard(accent, accentDark, cardBackground)

        return WrappedShareCard.CardColors(
            accent = hero,
            accentDark = accentDark,
            background = cardBackground,
            text = onAccent,
            textSecondary = blend(base = cardBackground, overlay = onAccent, alpha = SECONDARY_OPACITY),
            onAccent = onAccent
        )
    }

    /**
     * The hero duration and list ranks use the accent on the dark card; when
     * the theme's accent does not reach the contrast needed on either
     * gradient end, a lightened variant of the same accent is used instead.
     */
    private fun readableOnCard(accent: Int, accentDark: Int, cardBackground: Int): Int {
        val accentWorks = contrastRatio(accent, accentDark) >= MIN_CONTRAST_HERO &&
            contrastRatio(accent, cardBackground) >= MIN_CONTRAST_HERO

        return if (accentWorks) accent else lighten(accent, HERO_LIGHTEN)
    }

    // ------------------------------------------------------------------
    // Pure color math (WCAG 2.x relative luminance / contrast)
    // ------------------------------------------------------------------

    /** WCAG relative luminance of an ARGB int. */
    fun luminance(color: Int): Float {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.03928) {
                normalized / 12.92
            } else {
                ((normalized + 0.055) / 1.055).pow(2.4)
            }
        }

        val r = channel((color shr 16) and 0xFF)
        val g = channel((color shr 8) and 0xFF)
        val b = channel(color and 0xFF)

        return (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
    }

    /** WCAG contrast ratio (1 .. 21) between two ARGB colors. */
    fun contrastRatio(a: Int, b: Int): Float {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)

        return ((lighter + 0.05f) / (darker + 0.05f))
    }

    /** Multiplies every RGB channel by [factor] (matches the screen helper). */
    fun darken(color: Int, factor: Float): Int = channelMix(color) { channel ->
        (channel * factor).toInt().coerceIn(0, 255)
    }

    /** Lifts every RGB channel towards white by [factor]. */
    fun lighten(color: Int, factor: Float): Int = channelMix(color) { channel ->
        (channel + (255 - channel) * factor).toInt().coerceIn(0, 255)
    }

    /** Alpha-composites [overlay] over the opaque [base] into an opaque color. */
    fun blend(base: Int, overlay: Int, alpha: Float): Int {
        val r = blendChannel((base shr 16) and 0xFF, (overlay shr 16) and 0xFF, alpha)
        val g = blendChannel((base shr 8) and 0xFF, (overlay shr 8) and 0xFF, alpha)
        val b = blendChannel(base and 0xFF, overlay and 0xFF, alpha)
        return rgb(r, g, b)
    }

    private fun blendChannel(baseChannel: Int, overlayChannel: Int, alpha: Float): Int =
        (baseChannel * (1f - alpha) + overlayChannel * alpha).toInt().coerceIn(0, 255)

    private inline fun channelMix(color: Int, transform: (Int) -> Int): Int {
        val r = transform((color shr 16) and 0xFF)
        val g = transform((color shr 8) and 0xFF)
        val b = transform(color and 0xFF)
        return rgb(r, g, b)
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
