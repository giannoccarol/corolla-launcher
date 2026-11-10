package io.github.giannoccarol.corollalauncher.ui

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.status.StatusBarController

/**
 * Base for every screen of the launcher. It runs full screen, with the system bars hidden, and
 * drives the launcher's own status bar when the layout includes `view_status_bar`.
 */
abstract class LauncherActivity : AppCompatActivity() {

    private var statusBar: StatusBarController? = null

    /** Call once from `onCreate`, after `setContentView`. */
    protected fun setUpWindow(root: View, includeIme: Boolean = false) {
        SystemBars.apply(this, root, includeIme)
        root.findViewById<View>(R.id.statusBar)?.let { statusBar = StatusBarController(it) }
        // After a theme switch, the old screen fades away over the new one.
        root.post { ThemeTransition.reveal(this) }
    }

    override fun onStart() {
        super.onStart()
        statusBar?.start()
    }

    override fun onStop() {
        statusBar?.stop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs and the keyboard bring the system bars back; hide them again on return.
        if (hasFocus) SystemBars.hide(this)
    }
}
