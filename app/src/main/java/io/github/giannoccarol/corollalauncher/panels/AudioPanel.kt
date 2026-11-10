package io.github.giannoccarol.corollalauncher.panels

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.home.QuickToggles
import java.util.Locale

/** Volume of each audio stream, plus a way into the equalizer. */
class AudioPanel(activity: Activity) : Sheet(activity, R.string.qs_audio) {

    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sliders = mutableMapOf<Int, SeekBar>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Follow changes made with the knob, without fighting a slider being dragged.
            sliders.forEach { (stream, slider) ->
                if (!slider.isPressed) slider.progress = audio.getStreamVolume(stream)
            }
        }
    }

    override fun build() {
        sliders.clear()
        for ((stream, title, icon) in STREAMS) {
            sliders[stream] = sliderRow(
                title, icon, audio.getStreamMaxVolume(stream), audio.getStreamVolume(stream),
                showValue = { String.format(Locale.getDefault(), "%d", it) },
            ) { level ->
                try {
                    // Flag 0: no system volume UI and no sound while dragging.
                    audio.setStreamVolume(stream, level, 0)
                } catch (e: SecurityException) {
                    // Do Not Disturb forbids changing this stream right now.
                }
            }
        }
        button(R.string.audio_equalizer) { QuickToggles(activity).openAudio() }
    }

    override fun onShown() {
        ContextCompat.registerReceiver(
            activity, receiver, IntentFilter(ACTION_VOLUME_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDismissed() {
        activity.unregisterReceiver(receiver)
    }

    private companion object {
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"

        val STREAMS = listOf(
            Triple(AudioManager.STREAM_MUSIC, R.string.audio_media, R.drawable.ic_volume),
            Triple(AudioManager.STREAM_VOICE_CALL, R.string.audio_calls, R.drawable.ic_phone),
            Triple(AudioManager.STREAM_NOTIFICATION, R.string.audio_notifications, R.drawable.ic_bell),
        )
    }
}
