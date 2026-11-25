package it.vfsfitvnm.vimusic.ui.screens.search

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.items.SongItem
import it.vfsfitvnm.vimusic.ui.styling.*
import it.vfsfitvnm.vimusic.utils.*
import java.util.Calendar
import java.text.DateFormat

internal fun historyBounds(day: Long, startHour: Int, endHour: Int): Pair<Long, Long> {
    val c = Calendar.getInstance().apply {
        timeInMillis = day
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = c.clone() as Calendar
    if (endHour == 24) end.add(Calendar.DATE, 1) else end.set(Calendar.HOUR_OF_DAY, endHour)
    c.set(Calendar.HOUR_OF_DAY, startHour)
    return c.timeInMillis to end.timeInMillis
}

@Composable
internal fun HeardHistory() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    var day by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var index by rememberSaveable { mutableStateOf(0) }
    val starts = listOf(0, 0, 6, 12, 18)
    val ends = listOf(24, 6, 12, 18, 24)
    val bounds = historyBounds(day, starts[index], ends[index])
    val sessions = key(bounds) { remember { Database.heardBetween(bounds.first, bounds.second) }.collectAsState(initial = null).value }
    val type = LocalAppearance.current.typography
    val clock = android.text.format.DateFormat.getTimeFormat(context)
    LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
        item {
            SecondaryTextButton(DateFormat.getDateInstance().format(java.util.Date(day)), modifier = Modifier.padding(12.dp), onClick = {
                val c = Calendar.getInstance().apply { timeInMillis = day }
                DatePickerDialog(context, { _, y, m, d -> c.set(y, m, d); day = c.timeInMillis },
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                }.show()
            })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All day", "Night", "Morning", "Afternoon", "Evening").forEachIndexed { i, label ->
                    SecondaryTextButton(if (i == index) "✓ $label" else label, onClick = { index = i })
                }
            }
        }
        if (sessions != null && sessions.isEmpty()) item {
            BasicText("No listening sessions in this period", Modifier.padding(16.dp), style = type.s.secondary)
        }
        items(sessions.orEmpty(), key = { it.sessionId }) { row ->
            val size = Dimensions.thumbnails.song
            SongItem(row.song, size.px, size, Modifier.clickable {
                binder?.playSmartQueue(
                    row.song.asMediaItem,
                    sessions.orEmpty().map { it.song.asMediaItem }
                )
            }, trailingContent = { BasicText(clock.format(java.util.Date(row.started)), style = type.xxs.secondary) })
        }
    }
}
