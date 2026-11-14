package it.vfsfitvnm.vimusic.listening.presentation

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.listening.domain.WrappedSummary
import it.vfsfitvnm.vimusic.listening.domain.formatListeningDuration
import it.vfsfitvnm.vimusic.listening.domain.formatPlural
import java.io.File
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the WEST Wrapped shareable statistics card with the plain Android
 * graphics stack (deterministic, no Compose capture APIs needed) and shares
 * it as a PNG through a FileProvider.
 *
 * All colours come from the active theme palette, so the card matches the
 * app in both light and dark mode.
 */
object WrappedShareCard {

    // 9:16 story-format share card
    private const val WIDTH = 1080
    private const val HEIGHT = 1920

    data class CardColors(
        val accent: Int,
        val accentDark: Int,
        val background: Int,
        val text: Int,
        val textSecondary: Int,
        val onAccent: Int
    )

    suspend fun createBitmap(
        context: Context,
        wrapped: WrappedSummary,
        colors: CardColors
    ): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Deterministic design grid: the card is a fixed 1080px canvas laid
        // out as a 540-unit grid scaled x2. The rendering device's screen
        // density is deliberately ignored so the shareable PNG always looks
        // identical, no matter which phone exported it.
        fun px(value: Int) = value * (WIDTH / 540f)

        val regular = typeface(context, R.font.poppins_w400)
        val medium = typeface(context, R.font.poppins_w500)
        val semiBold = typeface(context, R.font.poppins_w600)
        val bold = typeface(context, R.font.poppins_w700)

        // Background gradient: accent-tinted vertical gradient
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, HEIGHT.toFloat(),
                colors.accentDark,
                colors.background,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), backgroundPaint)

        val padding = px(36)
        val contentWidth = WIDTH - padding * 2
        var y = px(32)

        // Draws one line with its top at the current cursor and advances the
        // cursor below the line's descender plus a gap: measured with the
        // paint's own font metrics, so consecutive lines can never overlap.
        fun line(
            value: String,
            paint: Paint,
            gap: Float,
            x: Float = padding,
            maxWidth: Float = contentWidth
        ) {
            val metrics = paint.fontMetrics
            val baseline = y - metrics.ascent
            canvas.drawText(ellipsize(value, paint, maxWidth), x, baseline, paint)
            y = baseline + metrics.descent + gap
        }

        // Draws "rank  value" with the rank in the accent colour, mirroring
        // the numbered lists of the in-app Wrapped card.
        fun rankedLine(
            rank: String,
            value: String,
            paint: Paint,
            rankPaint: Paint,
            gap: Float
        ) {
            val metrics = paint.fontMetrics
            val baseline = y - metrics.ascent
            canvas.drawText(rank, padding, baseline, rankPaint)
            canvas.drawText(
                ellipsize(value, paint, contentWidth - px(30)),
                padding + px(30),
                baseline,
                paint
            )
            y = baseline + metrics.descent + gap
        }

        // Shrinks the paint until the value fits the given width (down to a
        // minimum), so headlines never clip; anything still too long after
        // this is ellipsized by [line].
        fun fit(paint: Paint, value: String, maxWidth: Float, minSize: Float) {
            while (paint.measureText(value) > maxWidth && paint.textSize > minSize) {
                paint.textSize -= px(1)
            }
        }

        // Header badge: "WEST" pill in the accent colour
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.onAccent
            textSize = px(24)
            typeface = bold
        }
        val badgeHeight = px(30)
        canvas.drawRoundRect(
            RectF(padding, y, padding + measure(badgePaint, "WEST") + px(32), y + badgeHeight),
            badgeHeight / 2, badgeHeight / 2,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.accent }
        )
        canvas.drawText(
            "WEST",
            padding + px(16),
            y + badgeHeight / 2 - (badgePaint.ascent() + badgePaint.descent()) / 2,
            badgePaint
        )
        y += badgeHeight + px(28)

        // Title + headline numbers
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.text
            textSize = px(56)
            typeface = bold
        }
        val titleText = "${wrapped.year} Wrapped"
        fit(titlePaint, titleText, contentWidth, px(36))
        line(titleText, titlePaint, px(6))

        val hoursPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.accent
            textSize = px(72)
            typeface = bold
        }
        val hoursText = formatListeningDuration(wrapped.totalListeningMs)
        fit(hoursPaint, hoursText, contentWidth, px(44))
        line(hoursText, hoursPaint, px(4))

        val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = px(23)
            typeface = medium
        }
        val statsText =
            "${formatPlural(wrapped.totalPlays, "play")} · ${formatPlural(wrapped.uniqueSongs, "unique song")}" +
                " · ${formatPlural(wrapped.uniqueArtists, "artist")}"
        fit(statsPaint, statsText, contentWidth, px(18))
        line(statsText, statsPaint, px(16))

        // Top song: artwork on the left, caption / title / artist beside it
        wrapped.topSongs.firstOrNull()?.let { topSong ->
            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.textSecondary
                textSize = px(18)
                typeface = semiBold
                letterSpacing = 0.1f
            }
            val songPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.text
                textSize = px(30)
                typeface = bold
            }
            val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.textSecondary
                textSize = px(22)
                typeface = regular
            }

            val artwork = loadArtwork(context, topSong.thumbnailUrl)
            val artworkSize = if (artwork != null) px(100) else 0f
            val textX = padding + artworkSize + if (artwork != null) px(18) else 0f
            val textMaxWidth = WIDTH - padding - textX

            if (artwork != null) {
                val rect = RectF(padding, y, padding + artworkSize, y + artworkSize)
                canvas.save()
                canvas.clipPath(
                    Path().apply { addRoundRect(rect, px(14), px(14), Path.Direction.CW) }
                )
                canvas.drawBitmap(artwork, null, rect, null)
                canvas.restore()
            }

            val blockTop = y
            line("TOP SONG", captionPaint, px(3), x = textX, maxWidth = textMaxWidth)
            line(topSong.title ?: topSong.songId, songPaint, px(3), x = textX, maxWidth = textMaxWidth)
            line(
                listOfNotNull(
                    topSong.artistsText?.ifBlank { null },
                    formatPlural(topSong.playCount, "play")
                ).joinToString(" · "),
                artistPaint,
                0f,
                x = textX,
                maxWidth = textMaxWidth
            )
            y = maxOf(y, blockTop + artworkSize) + px(18)
        }

        // Top artists
        if (wrapped.topArtists.isNotEmpty()) {
            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.textSecondary
                textSize = px(18)
                typeface = semiBold
                letterSpacing = 0.1f
            }
            val entryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.text
                textSize = px(24)
                typeface = medium
            }
            val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.accent
                textSize = px(24)
                typeface = bold
            }
            line("TOP ARTISTS", captionPaint, px(5))
            wrapped.topArtists.take(3).forEachIndexed { index, artist ->
                rankedLine(
                    "${index + 1}",
                    "${artist.name ?: artist.artistId} — ${formatListeningDuration(artist.totalPlayedMs)}",
                    entryPaint,
                    rankPaint,
                    px(5)
                )
            }
            y += px(11)
        }

        // Most active month
        wrapped.mostActiveMonth?.let { month ->
            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.textSecondary
                textSize = px(18)
                typeface = semiBold
                letterSpacing = 0.1f
            }
            val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.text
                textSize = px(28)
                typeface = bold
            }
            line("MOST ACTIVE MONTH", captionPaint, px(5))
            line(
                Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) +
                    " · ${formatListeningDuration(wrapped.mostActiveMonthMs)}",
                monthPaint,
                px(11)
            )
        }

        // Top songs list
        if (wrapped.topSongs.size > 1) {
            val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.textSecondary
                textSize = px(18)
                typeface = semiBold
                letterSpacing = 0.1f
            }
            val entryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.text
                textSize = px(22)
                typeface = medium
            }
            val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.accent
                textSize = px(22)
                typeface = bold
            }
            line("TOP SONGS", captionPaint, px(5))
            wrapped.topSongs.drop(1).take(4).forEachIndexed { index, song ->
                rankedLine(
                    "${index + 2}",
                    "${song.title ?: song.songId} — ${formatPlural(song.playCount, "play")}",
                    entryPaint,
                    rankPaint,
                    px(5)
                )
            }
        }

        // Footer, anchored to the bottom with a subtle divider
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textSecondary
            textSize = px(20)
            typeface = regular
        }
        canvas.drawLine(
            padding, HEIGHT - px(58),
            WIDTH - padding, HEIGHT - px(58),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.textSecondary
                alpha = 60
                strokeWidth = 2f
            }
        )
        val footerText = "WEST · Listening Insights"
        canvas.drawText(
            footerText,
            (WIDTH - measure(footerPaint, footerText)) / 2,
            HEIGHT - px(30),
            footerPaint
        )

        bitmap
    }

    /**
     * Encodes the card to a PNG, verifies the file is complete and decodable,
     * then launches the system share sheet.
     *
     * The stream URI is attached both through [Intent.EXTRA_STREAM] and
     * [Intent.clipData]: a flag-only grant is scoped to the task that
     * receives the intent and is revoked when that task goes away, so a
     * target app that continues in its own task (for example Instagram's
     * full-screen Story editor) loses access and the image disappears
     * mid-flow. A ClipData-based grant persists until the device reboots,
     * which keeps the image readable for the whole share experience.
     *
     * A unique file per share prevents a second share from overwriting a PNG
     * a target app is still reading; the file is deliberately never deleted
     * here (cacheDir is reclaimed by the system when storage is needed).
     */
    suspend fun share(context: Context, bitmap: Bitmap, year: Int) =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "shared")
            directory.mkdirs()
            val file = File(
                directory,
                "west-wrapped-$year-${System.currentTimeMillis()}.png"
            )

            file.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException(
                        "Could not encode the WEST Wrapped card as PNG"
                    )
                }
            }

            // PNG generation must complete and produce a valid, decodable
            // image of the card's dimensions before any target is launched.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            check(
                bounds.outWidth == bitmap.width &&
                    bounds.outHeight == bitmap.height &&
                    file.length() > 0L
            ) { "The encoded WEST Wrapped card is not a valid PNG" }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(intent, "Share WEST Wrapped $year")
            )
        }

    private fun typeface(context: Context, fontRes: Int): Typeface =
        ResourcesCompat.getFont(context, fontRes) ?: Typeface.DEFAULT

    private fun measure(paint: Paint, value: String): Float = paint.measureText(value)

    private fun ellipsize(value: String, paint: Paint, maxWidth: Float): String {
        if (measure(paint, value) <= maxWidth) return value
        var text = value
        while (text.isNotEmpty() && measure(paint, text + "…") > maxWidth) {
            text = text.dropLast(1)
        }
        return text + "…"
    }

    private suspend fun loadArtwork(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(400)
                .build()
            context.imageLoader.execute(request).drawable?.toBitmap()
        }.getOrNull()
    }
}
