package io.github.giannoccarol.corollalauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.settings.Prefs
import io.github.giannoccarol.corollalauncher.status.StatusBarController
import io.github.giannoccarol.corollalauncher.theme.LauncherTheme

/**
 * Base for every screen of the launcher. It runs full screen, with the system bars hidden, and
 * drives the launcher's own status bar when the layout includes `view_status_bar`.
 */
abstract class LauncherActivity : AppCompatActivity() {

    private var statusBar: StatusBarController? = null

    /** The theme and font this screen was created with; see [LauncherTheme]. */
    private var appliedTheme: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = LauncherTheme.apply(this)
        super.onCreate(savedInstanceState)
    }

    /** Every screen is drawn at the interface size chosen in Settings. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScale.wrap(newBase))
    }

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

    override fun onResume() {
        super.onResume()
        // The interface size, theme or font changed while this screen was in the background.
        if (resources.configuration.densityDpi != UiScale.densityDpi(this, Prefs(this).uiSize) ||
            LauncherTheme.signature(this) != appliedTheme
        ) {
            recreate()
        }
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
