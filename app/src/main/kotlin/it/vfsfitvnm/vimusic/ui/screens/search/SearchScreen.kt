package it.vfsfitvnm.vimusic.ui.screens.search
import it.vfsfitvnm.vimusic.ui.components.themed.FilterPage


import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import it.vfsfitvnm.vimusic.voice.VoiceSearchDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import it.vfsfitvnm.compose.persist.PersistMapCleanup
import it.vfsfitvnm.compose.routing.RouteHandler
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.ui.components.themed.Scaffold
import it.vfsfitvnm.vimusic.ui.screens.globalRoutes
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.secondary

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun SearchScreen(
    initialTextInput: String,
    onSearch: (String) -> Unit,
    onViewPlaylist: (String) -> Unit
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val context = LocalContext.current
    var voiceVisible by rememberSaveable { mutableStateOf(false) }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) voiceVisible = true
    }
    val requestVoiceSearch: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceVisible = true
        } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    if (voiceVisible) VoiceSearchDialog(onDismiss = { voiceVisible = false }, onSearch = { query ->
        voiceVisible = false
        onSearch(query)
    })

    val (tabIndex, onTabChanged) = rememberSaveable {
        mutableStateOf(0)
    }

    val (textFieldValue, onTextFieldValueChanged) = rememberSaveable(
        initialTextInput,
        stateSaver = TextFieldValue.Saver
    ) {
        mutableStateOf(
            TextFieldValue(
                text = initialTextInput,
                selection = TextRange(initialTextInput.length)
            )
        )
    }

    PersistMapCleanup(tagPrefix = "search/")

    val decorationBox: @Composable (@Composable () -> Unit) -> Unit = { innerTextField ->
        Box {
            AnimatedVisibility(
                visible = textFieldValue.text.isEmpty(),
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
            ) {
                BasicText(
                    text = "Search music",
                    maxLines = 1,
                    style = LocalAppearance.current.typography.xxl.secondary
                )
            }

            innerTextField()
        }
    }

    val currentTabIndex = tabIndex
    FilterPage(listOf("All", "Library", "When I heard it", "Smart"), tabIndex, onTabChanged) {
        saveableStateHolder.SaveableStateProvider(currentTabIndex) {
            when (currentTabIndex) {
                0 -> OnlineSearch(
                    textFieldValue = textFieldValue,
                    onTextFieldValueChanged = onTextFieldValueChanged,
                    onSearch = onSearch,
                    onVoiceSearch = requestVoiceSearch,
                    onViewPlaylist = onViewPlaylist,
                    decorationBox = decorationBox
                )

                3 -> IntelligentSearch(onSearch)
                        2 -> HeardHistory()
                1 -> LocalSongSearch(
                    textFieldValue = textFieldValue,
                    onTextFieldValueChanged = onTextFieldValueChanged,
                    decorationBox = decorationBox
                )
            }
        }
    }
}
