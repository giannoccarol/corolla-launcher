package io.github.giannoccarol.corollalauncher.media

import android.service.notification.NotificationListenerService

/**
 * Deliberately empty. Android only lets an app list other apps' media sessions when it owns an
 * enabled notification listener, so this service exists to be granted that access. It never
 * reads, stores or forwards notifications.
 */
class MediaListenerService : NotificationListenerService()
