package it.vfsfitvnm.vimusic.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.utils.thumbnail
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val videoId = Regex("[A-Za-z0-9_-]{11}")

internal fun songShareLink(id: String): String? =
    if (videoId.matches(id)) "https://music.youtube.com/watch?v=$id" else null

internal fun shareLabel(value: CharSequence?, fallback: String): String =
    value?.toString()?.replace(Regex("[\\r\\n\\t]+"), " ")?.trim()
        ?.takeIf { it.isNotEmpty() } ?: fallback

internal fun songShareCaption(title: String, artist: String, link: String?): String =
    listOfNotNull("$title — $artist", link, "Shared from WEST").joinToString("\n")

/** Every song share includes a branded image; never silently fall back to a text-only share. */
internal object SongShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    suspend fun share(context: Context, item: MediaItem) {
        val metadata = item.mediaMetadata
        val title = shareLabel(metadata.title, "Unknown song")
        val artist = shareLabel(metadata.artist, "Unknown artist")
        val link = songShareLink(item.mediaId)
        val intent = withContext(Dispatchers.IO) {
            val artwork = loadArtwork(context, metadata.artworkUri?.toString())
            val bitmap = render(context, artwork, title, artist, link)
            try {
                val dir = File(context.cacheDir, "shared")
                check(dir.isDirectory || dir.mkdirs()) { "Could not create share directory" }
                val file = File.createTempFile("WEST-song-", ".png", dir)
                try {
                    file.outputStream().use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Could not encode card" }
                    }
                    check(file.length() > 0L)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, songShareCaption(title, artist, link))
                        clipData = ClipData.newRawUri("WEST song card", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } catch (e: Exception) {
                    file.delete()
                    throw e
                }
            } finally {
                bitmap.recycle()
            }
        }
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, "Share WEST song"))
        }
    }

    private suspend fun loadArtwork(context: Context, source: String?): Bitmap? {
        if (source.isNullOrBlank()) return null
        suspend fun request(url: String?, timeout: Long): Bitmap? = try {
            withTimeoutOrNull(timeout) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(960, 960)
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request).drawable?.toBitmap()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val resized = source.thumbnail(960)
        return request(resized, 5_000L) ?: if (resized != source) request(source, 3_000L) else null
    }

    private fun render(context: Context, art: Bitmap?, title: String, artist: String, link: String?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val neutral = Color.rgb(47, 44, 65)
        val base = if (art == null) neutral else runCatching {
            Palette.from(art).maximumColorCount(12).generate().getDominantColor(neutral)
        }.getOrDefault(neutral)
        val top = ColorUtils.blendARGB(base, Color.BLACK, 0.62f)
        val bottom = Color.rgb(13, 14, 19)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val artRect = RectF(126f, 180f, 954f, 1008f)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(artRect, 38f, 38f, Path.Direction.CW) })
        if (art != null) {
            val side = minOf(art.width, art.height)
            val x = (art.width - side) / 2
            val y = (art.height - side) / 2
            canvas.drawBitmap(art, Rect(x, y, x + side, y + side), artRect, paint)
        } else {
            canvas.drawColor(ColorUtils.blendARGB(base, Color.BLACK, 0.32f))
            val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 250f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("♪", artRect.centerX(), artRect.centerY() - (note.ascent() + note.descent()) / 2f, note)
        }
        canvas.restore()

        val logo = ResourcesCompat.getDrawable(context.resources, R.drawable.west_logo_wordmark_dark, context.theme)
        if (logo != null) {
            val sw = logo.intrinsicWidth.coerceAtLeast(1)
            val sh = logo.intrinsicHeight.coerceAtLeast(1)
            val scale = minOf(260f / sw, 82f / sh)
            logo.setBounds(126, 55, 126 + (sw * scale).toInt().coerceAtLeast(1), 55 + (sh * scale).toInt().coerceAtLeast(1))
            logo.draw(canvas)
        } else {
            canvas.drawText("WEST", 126f, 116f, textPaint(context, R.font.poppins_w700, 58f, Color.WHITE))
        }

        val titlePaint = textPaint(context, R.font.poppins_w700, 62f, Color.WHITE)
        while (titlePaint.measureText(title) > 828f && titlePaint.textSize > 42f) titlePaint.textSize -= 2f
        canvas.drawText(fit(title, titlePaint, 828f), 126f, 1100f, titlePaint)
        val artistPaint = textPaint(context, R.font.poppins_w400, 38f, Color.rgb(214, 214, 222))
        canvas.drawText(fit(artist, artistPaint, 828f), 126f, 1163f, artistPaint)
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(75, 255, 255, 255)
            strokeWidth = 2f
        }
        canvas.drawLine(126f, 1201f, 954f, 1201f, divider)
        val footer = textPaint(context, R.font.poppins_w500, 28f, Color.rgb(210, 210, 220))
        canvas.drawText("Shared from WEST", 126f, 1250f, footer)
        if (link != null) canvas.drawText(fit(link.removePrefix("https://"), footer, 828f), 126f, 1293f, footer)
        return bitmap
    }

    private fun textPaint(context: Context, font: Int, size: Float, argb: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, font) ?: Typeface.DEFAULT
            textSize = size
            color = argb
        }

    private fun fit(text: String, paint: Paint, width: Float): String =
        TextUtils.ellipsize(text, TextPaint(paint), width, TextUtils.TruncateAt.END).toString()
}
