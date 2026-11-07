package org.siloserver.silo.tv.ui.screens.search

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class TvVoicePhase { Permission, Preparing, Listening, Processing, Error }

internal data class TvVoiceSearchState(
    val phase: TvVoicePhase,
    val transcript: String = "",
    val message: String = "",
    val retryable: Boolean = true,
    val openSettings: Boolean = false,
)

internal interface TvSpeechListener {
    fun ready()
    fun partial(text: String)
    fun processing()
    fun result(text: String)
    fun error(message: String)
}

internal interface TvSpeechSession {
    fun start(listener: TvSpeechListener)
    fun close()
}

/** One user-initiated session. Every terminal transition invalidates late callbacks. */
internal class TvSearchVoiceController(
    private val scope: CoroutineScope,
    private val createSession: () -> TvSpeechSession?,
    private val onResult: (String) -> Unit,
) {
    private val _state = MutableStateFlow<TvVoiceSearchState?>(null)
    val state = _state.asStateFlow()
    private var generation = 0L
    private var session: TvSpeechSession? = null
    private var timeout: Job? = null

    private fun release() {
        generation++
        timeout?.cancel()
        timeout = null
        val previous = session
        session = null
        runCatching { previous?.close() }
    }

    fun awaitPermission() {
        release()
        _state.value = TvVoiceSearchState(TvVoicePhase.Permission)
    }

    fun permissionDenied(permanent: Boolean) {
        fail(
            if (permanent) "Allow microphone access in app settings to use voice search."
            else "Microphone access is needed for voice search. You can allow it or keep typing.",
            openSettings = permanent,
        )
    }

    fun start() {
        release()
        val token = generation
        transition(TvVoiceSearchState(TvVoicePhase.Preparing))
        try {
            val created = createSession()
            if (created == null) {
                fail("Voice search isn't available on this TV. You can still use the keyboard.", retryable = false)
                return
            }
            session = created
            created.start(object : TvSpeechListener {
                override fun ready() {
                    if (token == generation) transition(TvVoiceSearchState(TvVoicePhase.Listening))
                }
                override fun partial(text: String) {
                    if (token == generation) _state.value = _state.value?.copy(transcript = limitTvSearchText(text))
                }
                override fun processing() {
                    if (token == generation) transition(
                        TvVoiceSearchState(TvVoicePhase.Processing, transcript = _state.value?.transcript.orEmpty()),
                    )
                }
                override fun result(text: String) {
                    if (token != generation) return
                    val query = limitTvSearchText(text.replace(Regex("[\\r\\n]+"), " ").trim())
                    if (query.isBlank()) {
                        fail("Didn't catch that. Try speaking your search again.")
                    } else {
                        release()
                        _state.value = null
                        onResult(query)
                    }
                }
                override fun error(message: String) {
                    if (token == generation) fail(message)
                }
            })
        } catch (_: SecurityException) {
            permissionDenied(permanent = false)
        } catch (_: RuntimeException) {
            fail("Couldn't start voice search. Try again or use the keyboard.")
        }
    }

    private fun transition(value: TvVoiceSearchState) {
        _state.value = value
        timeout?.cancel()
        timeout = scope.launch {
            delay(if (value.phase == TvVoicePhase.Listening) 30_000L else 15_000L)
            fail("Voice search timed out. Try again or use the keyboard.")
        }
    }

    private fun fail(message: String, retryable: Boolean = true, openSettings: Boolean = false) {
        release()
        _state.value = TvVoiceSearchState(TvVoicePhase.Error, message = message, retryable = retryable, openSettings = openSettings)
    }

    fun cancel() {
        release()
        _state.value = null
    }
}

/** The framework adapter is kept separate so session ownership can be tested without a microphone. */
internal fun createTvSpeechSession(context: Context): TvSpeechSession? {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    return object : TvSpeechSession {
        override fun start(listener: TvSpeechListener) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Microphone permission is required")
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = listener.ready()
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = listener.processing()
                override fun onError(error: Int) = listener.error(when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try speaking your search again."
                    SpeechRecognizer.ERROR_AUDIO -> "Couldn't hear the microphone. Check your remote or microphone and try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access isn't available. Check this app's microphone permission."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice search couldn't connect. Check your connection and try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Try again in a moment."
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Voice recognition isn't available for this TV's language."
                    else -> "Voice search couldn't finish. Try again or use the keyboard."
                })
                override fun onResults(results: Bundle?) = listener.result(
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }.orEmpty(),
                )
                override fun onPartialResults(partialResults: Bundle?) = listener.partial(
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(),
                )
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            })
        }
        override fun close() {
            try {
                recognizer.cancel()
            } finally {
                recognizer.destroy()
            }
        }
    }
}
