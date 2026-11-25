package it.vfsfitvnm.vimusic.ui.screens.search

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.preferences

@Composable
internal fun SearchShortcuts(input: String, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val prefs = context.preferences
    val key = "pinnedSearchTerms"
    var pins by remember(prefs) { mutableStateOf(prefs.getStringSet(key, emptySet()).orEmpty().toSet()) }
    val term = input.trim()
    fun toggle(value: String) {
        val current = prefs.getStringSet(key, emptySet()).orEmpty()
        val next = if (value in current) current - value else current + value
        prefs.edit().putStringSet(key, next).apply()
        pins = next
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        if (term.isNotEmpty()) {
            SecondaryTextButton(text = if (term in pins) "Unpin search" else "Pin search",
                onClick = { toggle(term) })
        }
        Spacer(Modifier.weight(1f))
        SecondaryTextButton(text = "Paste & search", onClick = {
            val copied = clipboard.getText()?.text?.trim().orEmpty()
            if (copied.isNotEmpty()) onSearch(copied)
            else Toast.makeText(context, "Copy some text to search", Toast.LENGTH_SHORT).show()
        })
    }
    if (term.isEmpty() && pins.isNotEmpty()) {
        BasicText("Pinned searches", modifier = Modifier.padding(start = 16.dp),
            style = LocalAppearance.current.typography.xs)
        val sorted = remember(pins) { pins.sortedWith(String.CASE_INSENSITIVE_ORDER.thenBy { it }) }
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            items(sorted, key = { it }) { saved ->
                SecondaryTextButton(text = saved, onClick = { onSearch(saved) })
                SecondaryTextButton(text = "Unpin", onClick = { toggle(saved) })
            }
        }
    }
}
