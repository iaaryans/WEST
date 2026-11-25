@file:OptIn(ExperimentalComposeUiApi::class)

package it.vfsfitvnm.vimusic.voice

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.models.Song
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold

/** Push-to-talk voice *music search*; intentionally no assistant or playback commands. */
@Composable
internal fun VoiceSearchDialog(onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    val (palette, typography) = LocalAppearance.current
    val hasDeviceRecognizer = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    }
    var deviceOnly by remember { mutableStateOf(hasDeviceRecognizer) }
    var language by remember { mutableStateOf("System") }
    var status by remember { mutableStateOf("Preparing…") }
    var transcript by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var rms by remember { mutableStateOf(0f) }
    var submitted by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    val library by remember { Database.mixLibrary() }.collectAsState(initial = emptyList<Song>())
    val vocabulary = remember(library) {
        library.asSequence().flatMap { sequenceOf(it.title, it.artistsText.orEmpty()) }
            .filter(String::isNotBlank).distinct().take(800).toList()
    }
    val currentVocabulary by rememberUpdatedState(vocabulary)

    fun submit() {
        val query = VoiceMusicQuery.normalize(transcript)
        if (!submitted && query.isNotBlank()) {
            submitted = true
            onSearch(query) // Reuse the verified SearchFlow submission path.
        }
    }

    // Recognizers must be created, called and destroyed on the main thread.
    val recognizer = remember(context, deviceOnly) {
        runCatching {
            if (deviceOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.getOrNull()
    }
    DisposableEffect(recognizer) {
        if (recognizer != null) {
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status = "Listening…" }
                override fun onBeginningOfSpeech() { status = "Listening…" }
                override fun onRmsChanged(rmsdB: Float) {
                    if (kotlin.math.abs(rms - rmsdB) > 0.8f) rms = rmsdB.coerceIn(0f, 10f)
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { status = "Processing speech…" }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (status != "Ready to search") {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        if (partial.isNotBlank()) transcript = partial
                    }
                }
                override fun onResults(results: Bundle?) {
                    val recognized = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.map(VoiceMusicQuery::normalize)?.filter(String::isNotBlank)?.distinct().orEmpty()
                    val corrections = recognized.firstOrNull()?.let { MusicQueryMatcher.suggest(it, currentVocabulary) }.orEmpty()
                    suggestions = (recognized + corrections).distinct().take(4)
                    if (recognized.isEmpty()) {
                        status = "No speech recognized. Try again."
                        return
                    }
                    transcript = recognized.first()
                    status = "Ready to search"
                    val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        ?.firstOrNull() ?: -1f
                    // Auto-submit only explicitly high-confidence single-candidate recognition.
                    if (confidence >= 0.92f && recognized.size == 1 && corrections.isEmpty()) submit()
                }
                override fun onError(error: Int) {
                    status = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match. Try speaking again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission unavailable."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Recognition network unavailable."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy. Try again."
                        else -> "Recognition unavailable ($error). Retry or type your search."
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        } else status = "Speech recognition isn't available on this device."
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }
    LaunchedEffect(recognizer, retry, language) {
        if (recognizer == null) return@LaunchedEffect
        transcript = ""
        suggestions = emptyList()
        rms = 0f
        status = "Starting microphone…"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
            if (deviceOnly) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            when (language) {
                "English" -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                "Hindi" -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            }
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure { status = "Unable to start microphone: ${it.javaClass.simpleName}" }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp)
                .background(palette.background1, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText("WEST Voice · Music search", style = typography.l.semiBold)
            BasicText(status, style = typography.s.secondary)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.height(50.dp)) {
                repeat(13) { index ->
                    val distance = kotlin.math.abs(index - 6)
                    val height = (8f + (rms / 10f) * (42 - distance * 4)).coerceIn(8f, 46f)
                    Box(Modifier.width(4.dp).height(height.dp)
                        .background(palette.accent, RoundedCornerShape(5.dp)))
                }
            }
            BasicText("Say a song, artist, album or playlist name", style = typography.xs.secondary)
            BasicTextField(
                value = transcript,
                onValueChange = { transcript = it },
                textStyle = typography.l,
                cursorBrush = SolidColor(palette.accent),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().background(palette.background2, RoundedCornerShape(12.dp)).padding(12.dp),
                decorationBox = { inner ->
                    Box {
                        if (transcript.isEmpty()) BasicText("Recognized music query…", style = typography.s.secondary)
                        inner()
                    }
                }
            )
            suggestions.filter { it != transcript }.forEach { option ->
                SecondaryTextButton(option.take(75), onClick = { transcript = option })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("System", "English", "Hindi").forEach { choice ->
                    SecondaryTextButton(if (language == choice) "✓ $choice" else choice, onClick = {
                        if (language != choice) { recognizer?.cancel(); language = choice; retry++ }
                    })
                }
            }
            if (hasDeviceRecognizer) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryTextButton(if (deviceOnly) "✓ On-device" else "On-device", onClick = {
                        if (!deviceOnly) { deviceOnly = true; retry++ }
                    })
                    SecondaryTextButton(if (!deviceOnly) "✓ Standard" else "Standard", onClick = {
                        if (deviceOnly) { deviceOnly = false; retry++ }
                    })
                }
            }
            if (!deviceOnly) BasicText("The selected speech service may process audio online.", style = typography.xs.secondary)
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                SecondaryTextButton("Cancel", onClick = onDismiss)
                SecondaryTextButton("Retry", onClick = {
                    recognizer?.cancel()
                    retry++
                })
                SecondaryTextButton("Search", enabled = transcript.isNotBlank() && !submitted, onClick = ::submit)
            }
        }
    }
}
