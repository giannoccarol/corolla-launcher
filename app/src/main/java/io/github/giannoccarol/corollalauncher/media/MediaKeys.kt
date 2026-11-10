package io.github.giannoccarol.corollalauncher.media

import android.media.AudioManager
import android.view.KeyEvent

/**
 * Fallback transport control: sends a media button press to whichever app holds audio focus.
 * Needs no permission, so play/pause/next still work when media access was not granted or the
 * playing app has no media session.
 */
object MediaKeys {

    fun send(audioManager: AudioManager, keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
