package io.github.giannoccarol.corollalauncher.assistant

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Reads the assistant's replies aloud with the system text-to-speech engine. Speech uses the
 * assistant audio usage, so the head unit can duck the music under it as it does for navigation.
 */
class Speaker(context: Context) {

    private var ready = false
    private var pending: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    // USAGE_ASSISTANT exists from Android 8; before that, navigation guidance
                    // is the usage that ducks music the same way.
                    .setUsage(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AudioAttributes.USAGE_ASSISTANT
                        else AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            pending?.let { say(it) }
            pending = null
        }
    }

    fun say(text: String, locale: Locale = Locale.getDefault()) {
        if (!ready) {
            pending = text
            return
        }
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "assistant"
    }
}
