package it.vfsfitvnm.vimusic.ui.screens.home
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.semiBold

@Composable
internal fun ActionLabel(text: String, action: () -> Unit) {
    val (palette, type) = LocalAppearance.current
    BasicText(text, Modifier.clickable(role = Role.Button, onClick = action).padding(12.dp),
        style = type.xs.semiBold.copy(color = palette.accent))
}
@Composable
internal fun LibraryShortcuts(favorites: () -> Unit, offline: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        ActionLabel("Favorites", favorites)
        ActionLabel("Offline", offline)
    }
}
