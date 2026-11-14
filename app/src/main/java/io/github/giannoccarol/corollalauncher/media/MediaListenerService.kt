package io.github.giannoccarol.corollalauncher.media

import android.app.Notification
import android.service.notification.NotificationListenerService

/**
 * Android only lets an app list other apps' media sessions when it owns an enabled
 * notification listener, so this service exists to be granted that access. It reacts to no
 * notification and stores or forwards nothing. The one read is [notificationsOf], used only
 * when the driver opens *Settings → Device info*, to show on screen what the Android Auto app
 * posts while it plays.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: MediaListenerService? = null

        /** Title and text of [packageName]'s active notifications; null when not connected. */
        fun notificationsOf(packageName: String): List<String>? {
            val service = instance ?: return null
            val active = try {
                service.activeNotifications
            } catch (e: SecurityException) {
                return null
            } ?: return null
            return active.filter { it.packageName == packageName }.map { posted ->
                val extras = posted.notification.extras
                listOfNotNull(
                    extras.getCharSequence(Notification.EXTRA_TITLE),
                    extras.getCharSequence(Notification.EXTRA_TEXT),
                ).joinToString(" · ")
            }
        }
    }
}
