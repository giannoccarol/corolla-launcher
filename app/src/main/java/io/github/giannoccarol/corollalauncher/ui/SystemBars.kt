package io.github.giannoccarol.corollalauncher.ui

import android.app.Activity
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.theme.ThemeManager

/** Keeps the system bars the same color as the app so the theme stays uniform edge to edge. */
object SystemBars {

    /**
     * Draws behind the system bars, colors them like the background, and pads [root] so content
     * never sits under them. With [includeIme] the padding also follows the on-screen keyboard.
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
    }
}
