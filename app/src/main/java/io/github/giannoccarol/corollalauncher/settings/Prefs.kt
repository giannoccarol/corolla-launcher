package io.github.giannoccarol.corollalauncher.settings

import android.content.Context
import androidx.core.content.edit
import io.github.giannoccarol.corollalauncher.theme.ThemeMode

/** Thin typed wrapper over SharedPreferences. Cheap to construct; no caching of its own. */
class Prefs(context: Context) {

    private val settings =
        context.applicationContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val counts =
        context.applicationContext.getSharedPreferences("launch_counts", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.fromId(settings.getString(KEY_THEME, null))
        set(value) = settings.edit { putString(KEY_THEME, value.id) }

    var homeAddress: String?
        get() = settings.getString(KEY_HOME_ADDRESS, null)
        set(value) = settings.edit { putString(KEY_HOME_ADDRESS, value) }

    var workAddress: String?
        get() = settings.getString(KEY_WORK_ADDRESS, null)
        set(value) = settings.edit { putString(KEY_WORK_ADDRESS, value) }

    /** Flattened component names the user pinned to the dock, in pin order. */
    var pinnedApps: List<String>
        get() = settings.getString(KEY_PINNED, null)
            ?.split('\n')
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        set(value) = settings.edit { putString(KEY_PINNED, value.joinToString("\n")) }

    fun launchCounts(): Map<String, Int> =
        counts.all.mapNotNull { (key, value) -> (value as? Int)?.let { key to it } }.toMap()

    fun incrementLaunch(appKey: String) {
        counts.edit { putInt(appKey, counts.getInt(appKey, 0) + 1) }
    }

    fun resetLaunchCounts() {
        counts.edit { clear() }
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_HOME_ADDRESS = "home_address"
        const val KEY_WORK_ADDRESS = "work_address"
        const val KEY_PINNED = "pinned_apps"
    }
}
