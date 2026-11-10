package io.github.giannoccarol.corollalauncher.theme

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.settings.Prefs
import io.github.giannoccarol.corollalauncher.ui.ThemeTransition
import java.util.Calendar

enum class ThemeMode(val id: String, @param:StringRes val labelRes: Int) {
    DARK("dark", R.string.theme_dark),
    LIGHT("light", R.string.theme_light),
    SYSTEM("system", R.string.theme_system),
    AUTO_TIME("auto_time", R.string.theme_auto_time);

    companion object {
        /** Dark is the default: it is the theme the home screen was designed around. */
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: DARK
    }
}

/**
 * Light/dark handling. The theme is always uniform: every surface follows the same mode, and
 * all colors come from `values/colors.xml` (light) and `values-night/colors.xml` (dark).
 */
object ThemeManager {

    const val NIGHT_START_HOUR = 18
    const val NIGHT_END_HOUR = 7

    /** Applies the saved mode. Safe to call often: AppCompat ignores unchanged values. */
    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(nightModeFor(Prefs(context).themeMode))
    }

    fun nightModeFor(mode: ThemeMode, hourOfDay: Int = currentHour()): Int = when (mode) {
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.AUTO_TIME ->
            if (isNightHour(hourOfDay)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
    }

    fun isNightHour(hourOfDay: Int): Boolean =
        hourOfDay >= NIGHT_START_HOUR || hourOfDay < NIGHT_END_HOUR

    /** True when the given context is currently rendered with the dark palette. */
    fun isDark(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * Applies the saved mode with a crossfade when it actually changes what [activity] shows:
     * user-initiated switches and the evening switch of the time-based mode.
     */
    fun applyAnimated(activity: Activity) {
        val mode = Prefs(activity).themeMode
        if (willBeDark(mode) != isDark(activity)) ThemeTransition.capture(activity)
        apply(activity)
    }

    /** Flips between explicit dark and explicit light, whatever the previous mode was. */
    fun toggle(activity: Activity) {
        Prefs(activity).themeMode = if (isDark(activity)) ThemeMode.LIGHT else ThemeMode.DARK
        applyAnimated(activity)
    }

    private fun willBeDark(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO_TIME -> isNightHour(currentHour())
        ThemeMode.SYSTEM -> Resources.getSystem().configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    private fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
