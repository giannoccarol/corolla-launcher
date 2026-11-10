package io.github.giannoccarol.corollalauncher.apps

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.appcompat.widget.PopupMenu
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.settings.Prefs

/** The long-press menu shared by dock icons and app drawer tiles. */
object AppActions {

    private const val ITEM_PIN = 1
    private const val ITEM_INFO = 2

    fun showMenu(anchor: View, app: AppInfo, onPinsChanged: () -> Unit) {
        val context = anchor.context
        val prefs = Prefs(context)
        // The projection app is in the bar by default rather than by a pin.
        val isProjection = AppRepository.projectionApp()?.key == app.key
        val pinned = if (isProjection) prefs.projectionInDock else app.key in prefs.pinnedApps

        PopupMenu(context, anchor).apply {
            menu.add(0, ITEM_PIN, 0, if (pinned) R.string.app_unpin else R.string.app_pin)
            menu.add(0, ITEM_INFO, 1, R.string.app_info)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    ITEM_PIN -> {
                        if (isProjection) {
                            prefs.projectionInDock = !pinned
                        } else {
                            prefs.pinnedApps =
                                if (pinned) prefs.pinnedApps - app.key else prefs.pinnedApps + app.key
                        }
                        onPinsChanged()
                        true
                    }
                    ITEM_INFO -> {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", app.component.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            // Some head units strip the system Settings app; nothing to open.
                        }
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }
}
