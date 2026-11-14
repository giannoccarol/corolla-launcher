package io.github.giannoccarol.corollalauncher.settings

import android.content.Context
import androidx.core.content.edit
import io.github.giannoccarol.corollalauncher.apps.AppIcons
import io.github.giannoccarol.corollalauncher.phone.Favorite
import io.github.giannoccarol.corollalauncher.phone.Favorites
import io.github.giannoccarol.corollalauncher.theme.FontChoice
import io.github.giannoccarol.corollalauncher.theme.ThemeMode
import io.github.giannoccarol.corollalauncher.theme.ThemeStyle
import io.github.giannoccarol.corollalauncher.ui.UiScale

/** Thin typed wrapper over SharedPreferences. Cheap to construct; no caching of its own. */
class Prefs(context: Context) {

    private val settings =
        context.applicationContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.fromId(settings.getString(KEY_THEME, null))
        set(value) = settings.edit { putString(KEY_THEME, value.id) }

    var homeAddress: String?
        get() = settings.getString(KEY_HOME_ADDRESS, null)
        set(value) = settings.edit { putString(KEY_HOME_ADDRESS, value) }

    var workAddress: String?
        get() = settings.getString(KEY_WORK_ADDRESS, null)
        set(value) = settings.edit { putString(KEY_WORK_ADDRESS, value) }

    /** Shown at the top left of the home screen; null hides the name. */
    var driverName: String?
        get() = settings.getString(KEY_DRIVER_NAME, null)?.takeIf { it.isNotBlank() }
        set(value) = settings.edit { putString(KEY_DRIVER_NAME, value?.trim()) }


    /** The assistant's AI provider; its API key lives in the assistant's KeyVault. */
    var aiProviderId: String?
        get() = settings.getString(KEY_AI_PROVIDER, null)
        set(value) = settings.edit { putString(KEY_AI_PROVIDER, value) }

    /** The model chosen for [providerId], or null for the provider's default. */
    fun aiModel(providerId: String): String? =
        settings.getString(KEY_AI_MODEL_PREFIX + providerId, null)?.takeIf { it.isNotBlank() }

    fun setAiModel(providerId: String, model: String?) =
        settings.edit { putString(KEY_AI_MODEL_PREFIX + providerId, model?.trim()) }

    var speakReplies: Boolean
        get() = settings.getBoolean(KEY_SPEAK_REPLIES, true)
        set(value) = settings.edit { putBoolean(KEY_SPEAK_REPLIES, value) }

    /** Off makes every transition instant; meant for head units too slow to animate. */
    var animations: Boolean
        get() = settings.getBoolean(KEY_ANIMATIONS, true)
        set(value) = settings.edit { putBoolean(KEY_ANIMATIONS, value) }

    /** The head unit's Android Auto / CarPlay app sits first in the bottom bar unless removed. */
    var projectionInDock: Boolean
        get() = settings.getBoolean(KEY_PROJECTION_IN_DOCK, true)
        set(value) = settings.edit { putBoolean(KEY_PROJECTION_IN_DOCK, value) }

    /** Flattened component names the user pinned to the dock, in pin order. */
    var pinnedApps: List<String>
        get() = settings.getString(KEY_PINNED, null)
            ?.split('\n')
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        set(value) = settings.edit { putString(KEY_PINNED, value.joinToString("\n")) }

    /** [AppIcons.STYLE_THEMED], [AppIcons.STYLE_ORIGINAL] or an installed icon pack's package. */
    var iconStyle: String
        get() = settings.getString(KEY_ICON_STYLE, null) ?: AppIcons.STYLE_THEMED
        set(value) = settings.edit { putString(KEY_ICON_STYLE, value) }

    /** The look of the app; light or dark is [themeMode]. */
    var themeStyle: ThemeStyle
        get() = ThemeStyle.fromId(settings.getString(KEY_THEME_STYLE, null))
        set(value) = settings.edit { putString(KEY_THEME_STYLE, value.id) }

    var fontChoice: FontChoice
        get() = FontChoice.fromId(settings.getString(KEY_FONT, null))
        set(value) = settings.edit { putString(KEY_FONT, value.id) }

    /** How large the whole interface is drawn; see [UiScale]. */
    var uiSize: UiScale.UiSize
        get() = UiScale.UiSize.fromId(settings.getString(KEY_UI_SIZE, null))
        set(value) = settings.edit { putString(KEY_UI_SIZE, value.id) }

    /** The contact in place [slot] (0 until [Favorites.SLOTS]) of the Favourites card. */
    fun favorite(slot: Int): Favorite? = Favorites.decode(settings.getString(KEY_FAVORITE_PREFIX + slot, null))

    fun setFavorite(slot: Int, favorite: Favorite?) =
        settings.edit { putString(KEY_FAVORITE_PREFIX + slot, Favorites.encode(favorite)) }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_HOME_ADDRESS = "home_address"
        const val KEY_WORK_ADDRESS = "work_address"
        const val KEY_PINNED = "pinned_apps"
        const val KEY_ANIMATIONS = "animations"
        const val KEY_PROJECTION_IN_DOCK = "projection_in_dock"
        const val KEY_DRIVER_NAME = "driver_name"
        const val KEY_AI_PROVIDER = "ai_provider"
        const val KEY_AI_MODEL_PREFIX = "ai_model_"
        const val KEY_SPEAK_REPLIES = "speak_replies"
        const val KEY_ICON_STYLE = "icon_style"
        const val KEY_FAVORITE_PREFIX = "favorite_"
        const val KEY_UI_SIZE = "ui_size"
        const val KEY_THEME_STYLE = "theme_style"
        const val KEY_FONT = "font"
    }
}
