package io.github.giannoccarol.corollalauncher.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import io.github.giannoccarol.corollalauncher.R

sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data class Level(val value: Float) : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Error(@StringRes val message: Int, val silent: Boolean = false) : SpeechEvent
}

/**
 * The system speech recogniser in the app's language, with partial results and the microphone
 * level. Ported from the owner's Monetine app (`voice/SpeechController.kt`).
 */
class SpeechController(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String, onEvent: (SpeechEvent) -> Unit) {
        release()
        val instance = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = instance
        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onEvent(SpeechEvent.Ready)
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) =
                onEvent(SpeechEvent.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = onEvent(SpeechEvent.Level(0f))
            override fun onError(error: Int) = onEvent(mapError(error))
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onEvent(if (text.isBlank()) SpeechEvent.Error(R.string.speech_no_match) else SpeechEvent.Final(text))
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onEvent(SpeechEvent.Partial(text))
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }
        instance.startListening(intent)
    }

    /** Stops listening and delivers the final result. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.apply {
            cancel()
            destroy()
        }
        recognizer = null
    }

    private fun mapError(code: Int): SpeechEvent.Error = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> SpeechEvent.Error(R.string.speech_no_match)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechEvent.Error(R.string.speech_timeout)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechEvent.Error(R.string.assistant_mic_needed)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechEvent.Error(R.string.speech_network)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechEvent.Error(R.string.speech_busy)
        SpeechRecognizer.ERROR_CLIENT -> SpeechEvent.Error(R.string.speech_failed, silent = true)
        else -> SpeechEvent.Error(R.string.speech_failed)
    }
}
