package it.vfsfitvnm.vimusic.ui.screens.localplaylist

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import it.vfsfitvnm.vimusic.models.PlaylistWithSongs
import it.vfsfitvnm.vimusic.models.Song
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val youtubeVideoId = Regex("[A-Za-z0-9_-]{11}")
private const val MAX_INLINE_CHARS = 60_000

private fun singleLine(value: String): String =
    value.replace(Regex("[\\r\\n\\t]+"), " ").trim()

/** A local WEST playlist has no public playlist URL. Share only its public track metadata. */
internal fun playlistShareText(name: String, songs: List<Song>): String = buildString {
    append(singleLine(name).ifBlank { "WEST playlist" })
    append("\n")
    append(songs.size)
    append(if (songs.size == 1) " song" else " songs")
    append("\n\n")
    songs.forEachIndexed { index, song ->
        append(index + 1)
        append(". ")
        append(singleLine(song.title).ifBlank { "Unknown song" })
        song.artistsText?.let(::singleLine)?.takeIf { it.isNotBlank() }?.let {
            append(" — ")
            append(it)
        }
        append("\n")
        if (youtubeVideoId.matches(song.id)) {
            append("https://music.youtube.com/watch?v=")
            append(song.id)
            append("\n")
        }
        append("\n")
    }
    append("Shared from WEST")
}

internal suspend fun shareLocalPlaylist(context: Context, playlist: PlaylistWithSongs) {
    try {
        val intent = withContext(Dispatchers.IO) {
            val text = playlistShareText(playlist.playlist.name, playlist.songs)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, singleLine(playlist.playlist.name).take(200))
                if (text.length <= MAX_INLINE_CHARS) {
                    putExtra(Intent.EXTRA_TEXT, text)
                } else {
                    // Avoid oversized Binder transactions; share the complete list as a text attachment.
                    val directory = File(context.cacheDir, "shared")
                    check(directory.isDirectory || directory.mkdirs()) { "Cannot create share directory" }
                    val file = File.createTempFile("WEST-playlist-", ".txt", directory)
                    try {
                        file.writeText(text, Charsets.UTF_8)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("WEST playlist", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        file.delete()
                        throw e
                    }
                }
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share playlist"))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share playlist", Toast.LENGTH_SHORT).show()
    }
}
