package io.github.giannoccarol.corollalauncher.settings

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.apps.AppRepository
import io.github.giannoccarol.corollalauncher.media.MediaListenerService
import io.github.giannoccarol.corollalauncher.ui.UiScale

/**
 * The facts needed to fix the launcher on a head unit nobody can plug a cable into: screen,
 * Android version, the Android Auto app, and what it publishes while it plays. Shown on
 * screen only, so the driver can photograph it; nothing is stored or sent.
 */
object DeviceInfo {

    fun text(context: Context): String {
        val lines = ArrayList<String>()
        lines += context.getString(R.string.diag_model, "${Build.MANUFACTURER} ${Build.MODEL}")
        lines += context.getString(R.string.diag_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)

        val real = context.applicationContext.resources.displayMetrics
        val shortSide = UiScale.shortSidePx(context)
        val longSide = maxOf(real.widthPixels, real.heightPixels).coerceAtLeast(shortSide)
        lines += context.getString(R.string.diag_screen, longSide, shortSide, real.densityDpi)
        val config = context.resources.configuration
        lines += context.getString(R.string.diag_interface, config.densityDpi, config.screenWidthDp, config.screenHeightDp)

        val projection = AppRepository.projectionApp()
        lines += context.getString(
            R.string.diag_projection,
            projection?.let { "${it.label} (${it.component.packageName})" } ?: context.getString(R.string.diag_not_found),
        )

        val access = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        lines += context.getString(if (access) R.string.diag_access_on else R.string.diag_access_off)
        if (access) {
            lines += ""
            lines += context.getString(R.string.diag_sessions)
            lines += sessions(context).ifEmpty { listOf(context.getString(R.string.diag_none)) }
            if (projection != null) {
                lines += ""
                lines += context.getString(R.string.diag_notifications, projection.label)
                val posted = MediaListenerService.notificationsOf(projection.component.packageName)
                lines += posted?.ifEmpty { null } ?: listOf(context.getString(R.string.diag_none))
            }
        }
        return lines.joinToString("\n")
    }

    /** "package: state, title" for every active media session. */
    private fun sessions(context: Context): List<String> = try {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager.getActiveSessions(ComponentName(context, MediaListenerService::class.java)).map { controller ->
            // Platform constant names: technical identifiers, the same in every language.
            val state = when (val code = controller.playbackState?.state) {
                PlaybackState.STATE_PLAYING -> "STATE_PLAYING"
                PlaybackState.STATE_PAUSED -> "STATE_PAUSED"
                PlaybackState.STATE_STOPPED -> "STATE_STOPPED"
                null -> "—"
                else -> "state $code"
            }
            val title = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            "• ${controller.packageName}: $state" + if (title.isEmpty()) "" else ", $title"
        }
    } catch (e: SecurityException) {
        emptyList()
    }
}
