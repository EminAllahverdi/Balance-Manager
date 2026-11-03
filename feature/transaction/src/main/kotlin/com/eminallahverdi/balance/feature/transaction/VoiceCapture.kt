package com.eminallahverdi.balance.feature.transaction

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.eminallahverdi.balance.core.datastore.UserPreferencesRepository
import com.eminallahverdi.balance.core.designsystem.component.BtChip
import com.eminallahverdi.balance.core.designsystem.theme.BtShapes
import com.eminallahverdi.balance.core.designsystem.theme.BtTheme
import com.eminallahverdi.balance.core.model.SpokenLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where listening stands. Each problem says what to do about it. */
enum class Listening(val message: String?) {
    STARTING(null),
    LISTENING(null),
    NOTHING_HEARD("Didn't catch that. Tap the mic and try again."),
    NO_PERMISSION("Cibim needs the microphone to hear you. Allow it, then tap the mic."),
    UNAVAILABLE("This phone has no speech recognition. Type it instead."),
    LANGUAGE_MISSING("This language isn't on the phone yet. Add it in the phone's voice typing settings, or switch language."),
    NO_NETWORK("This language needs the internet on this phone. Connect, or switch language."),
    FAILED("Listening stopped. Tap the mic to try again."),
}

@HiltViewModel
class VoiceViewModel @Inject constructor(private val prefs: UserPreferencesRepository) : ViewModel() {
    private val _language = MutableStateFlow<SpokenLanguage?>(null)

    /** Null until the saved choice is read, so listening never starts in the wrong language. */
    val language: StateFlow<SpokenLanguage?> = _language.asStateFlow()

    init {
        viewModelScope.launch {
            val tag = prefs.current().voiceLanguage
            _language.value = SpokenLanguage.entries.firstOrNull { it.tag == tag } ?: SpokenLanguage.ENGLISH
        }
    }

    fun choose(language: SpokenLanguage) {
        _language.value = language
        viewModelScope.launch { prefs.setVoiceLanguage(language.tag) }
    }
}

/**
 * Listens for one sentence and hands it over as text. Nothing is saved here: [onHeard] opens quick add filled in, where the
 * user checks it and presses Save. [onType] opens quick add without listening.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCaptureRoute(onHeard: (String, SpokenLanguage) -> Unit, onType: () -> Unit, onClose: () -> Unit, viewModel: VoiceViewModel = hiltViewModel()) {
    val language = viewModel.language.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    val c = BtTheme.colors
    var status by remember { mutableStateOf(Listening.STARTING) }
    var heard by remember { mutableStateOf("") }
    val heardNow by rememberUpdatedState(onHeard)
    val listener = remember {
        SpeechListener(
            context,
            onPartial = { heard = it },
            onFinal = { text, spokenIn ->
                if (text.isNullOrBlank()) status = Listening.NOTHING_HEARD else heardNow(text, spokenIn)
            },
            onError = { status = it },
        )
    }
    DisposableEffect(Unit) { onDispose { listener.stop() } }

    fun listen() {
        heard = ""
        status = when {
            !SpeechRecognizer.isRecognitionAvailable(context) -> Listening.UNAVAILABLE
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> Listening.NO_PERMISSION
            else -> Listening.LISTENING.also { listener.start(language) }
        }
    }
    val askForMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else status = Listening.NO_PERMISSION
    }
    LaunchedEffect(language) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen() else askForMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BtShapes.sheet,
        containerColor = c.bgSurface,
        scrimColor = c.scrim,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (status == Listening.LISTENING) "Listening…" else "Say what you spent",
                    style = BtTheme.type.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                SpokenLanguage.entries.forEach { option ->
                    BtChip(option.short, option == language, { if (option != language) viewModel.choose(option) }, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                Text(
                    heard.ifEmpty { language.example },
                    style = if (heard.isEmpty()) BtTheme.type.body else BtTheme.type.titleMedium,
                    color = if (heard.isEmpty()) c.textMuted else c.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            MicButton(listening = status == Listening.LISTENING, onClick = {
                when (status) {
                    Listening.LISTENING -> listener.finish()
                    Listening.NO_PERMISSION -> askForMic.launch(Manifest.permission.RECORD_AUDIO)
                    else -> listen()
                }
            })
            Text(
                status.message ?: "Amount, category, for whom, account and shop, in any order",
                style = BtTheme.type.caption,
                color = if (status.message != null) c.moneyExpense else c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(
                Modifier.padding(top = 12.dp).heightIn(min = 44.dp).clip(BtShapes.full).clickable(role = Role.Button, onClick = onType).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Keyboard, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                Text("Type instead", style = BtTheme.type.label, color = c.textSecondary)
            }
        }
    }
}

private val SpokenLanguage.short: String get() = if (this == SpokenLanguage.ENGLISH) "EN" else "AZ"

/** What to say, in the language chosen. Invented names, the way anyone's own would be said. */
private val SpokenLanguage.example: String
    get() = when (this) {
        SpokenLanguage.ENGLISH -> "“15.40 at Bravo, groceries, for Family, from Card”"
        SpokenLanguage.AZERBAIJANI -> "“Bravodan 15 manat 40 qəpik, ərzaq, ailə üçün, kartla”"
    }

/** The mic, which breathes while it listens. Tapping it while listening stops and uses what was heard. */
@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val c = BtTheme.colors
    val pulse = rememberInfiniteTransition(label = "listening")
    val scale by pulse.animateFloat(1f, if (listening) 1.12f else 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        if (listening) Box(Modifier.size(96.dp).scale(scale).clip(CircleShape).background(c.accentFill.copy(alpha = 0.18f)))
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(if (listening) c.accentFill else c.bgSurfaceHigh).clickable(role = Role.Button, onClickLabel = if (listening) "Stop" else "Listen", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = if (listening) c.accentOn else c.textPrimary, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * Android's own speech recogniser, free and on the phone where the language allows. It hears one sentence: what it has
 * so far comes to [onPartial], the whole of it to [onFinal].
 */
private class SpeechListener(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String?, SpokenLanguage) -> Unit,
    private val onError: (Listening) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var lastPartial: String? = null

    fun start(language: SpokenLanguage) {
        stop()
        lastPartial = null
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        created.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(results: Bundle?) {
                results.firstResult()?.takeIf { it.isNotBlank() }?.let { lastPartial = it; onPartial(it) }
            }

            override fun onResults(results: Bundle?) = onFinal(results.firstResult() ?: lastPartial, language)

            override fun onError(error: Int) {
                // Stopped early with something heard: use it rather than lose it.
                val partial = lastPartial
                if (partial != null && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) return onFinal(partial, language)
                onError(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Listening.NOTHING_HEARD
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Listening.NO_PERMISSION
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> Listening.LANGUAGE_MISSING
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> Listening.NO_NETWORK
                        else -> Listening.FAILED
                    },
                )
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
        )
        recognizer = created
    }

    /** Stops listening and keeps what was said, which arrives in onResults. */
    fun finish() {
        recognizer?.stopListening()
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun Bundle?.firstResult(): String? = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
