package io.github.giannoccarol.corollalauncher.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat

/**
 * Tracks every app that currently exposes a media session (Spotify, YouTube, a podcast app...)
 * and tells the UI when anything about them changes. All callbacks arrive on the main thread.
 *
 * Apps that play audio without a media session, which is common for the stock radio and
 * Bluetooth apps of aftermarket head units, are invisible to this class.
 */
class MediaSessionMonitor(
    private val context: Context,
    private val onChanged: () -> Unit,
) {

    private val manager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var listening = false
    private var selectedPackage: String? = null

    /** One controller per app, in the system's priority order. */
    var controllers: List<MediaController> = emptyList()
        private set

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { update(it.orEmpty()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = onChanged()
        override fun onMetadataChanged(metadata: MediaMetadata?) = onChanged()
        override fun onSessionDestroyed() = refresh()
    }

    /** The source shown in the player: the user's pick, else whatever is playing, else the first. */
    val current: MediaController?
        get() = controllers.firstOrNull { it.packageName == selectedPackage }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

    fun hasAccess(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun start() {
        if (!hasAccess()) {
            update(emptyList())
            return
        }
        try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(
                    sessionsListener, listenerComponent, handler,
                )
                listening = true
            }
            update(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            // Access was revoked between the check and the call.
            update(emptyList())
        }
    }

    fun stop() {
        if (listening) {
            manager.removeOnActiveSessionsChangedListener(sessionsListener)
            listening = false
        }
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = emptyList()
    }

    fun select(packageName: String) {
        selectedPackage = packageName
        onChanged()
    }

    private fun refresh() {
        if (!listening) return
        try {
            update(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            update(emptyList())
        }
    }

    private fun update(sessions: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = sessions.distinctBy { it.packageName }
        controllers.forEach { it.registerCallback(controllerCallback, handler) }
        if (controllers.none { it.packageName == selectedPackage }) selectedPackage = null
        onChanged()
    }
}
