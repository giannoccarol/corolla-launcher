package io.github.giannoccarol.corollalauncher.ui

import android.app.Activity
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.theme.ThemeManager

/**
 * The launcher draws its own status bar, so the system bars are hidden while it is in front.
 * When they do appear (a swipe from the edge, a dialog), they take the app's colors so the
 * theme stays uniform.
 */
object SystemBars {

    /**
     * Goes full screen and pads [root] by whatever insets remain (a display cutout, bars shown
     * temporarily and, with [includeIme], the on-screen keyboard).
     */
    fun apply(activity: Activity, root: View, includeIme: Boolean = false) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val background = ContextCompat.getColor(activity, R.color.bg)
        @Suppress("DEPRECATION")
        window.statusBarColor = background
        @Suppress("DEPRECATION")
        window.navigationBarColor = background

        val lightBars = !ThemeManager.isDark(activity)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }

        var types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        if (includeIme) types = types or WindowInsetsCompat.Type.ime()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(types)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        hide(activity)
    }

    /** Hides the status and navigation bars; a swipe from the edge shows them briefly. */
    fun hide(activity: Activity) {
        val window = activity.window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
