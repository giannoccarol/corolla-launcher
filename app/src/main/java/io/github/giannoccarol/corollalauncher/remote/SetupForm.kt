package io.github.giannoccarol.corollalauncher.remote

import io.github.giannoccarol.corollalauncher.phone.Favorite
import io.github.giannoccarol.corollalauncher.phone.Favorites
import io.github.giannoccarol.corollalauncher.theme.FontChoice
import io.github.giannoccarol.corollalauncher.theme.ThemeMode
import io.github.giannoccarol.corollalauncher.theme.ThemeStyle
import io.github.giannoccarol.corollalauncher.ui.UiScale

/** Everything the phone setup page can change. */
data class SetupValues(
    val driverName: String?,
    val homeAddress: String?,
    val workAddress: String?,
    val favorites: List<Favorite?>,
    val themeStyle: ThemeStyle,
    val themeMode: ThemeMode,
    val font: FontChoice,
    val uiSize: UiScale.UiSize,
    val iconStyle: String,
    val providerId: String,
    val model: String?,
    /** A new key to save; null keeps the saved one. */
    val newApiKey: String? = null,
    val removeApiKey: Boolean = false,
)

/**
 * The setup page's form fields to [SetupValues], and back. Unknown or invalid values keep what
 * is saved, so a stale or tampered form can never break the settings. Pure, unit tested.
 */
object SetupForm {

    enum class Problem { FAVORITE_NUMBER }

    data class Result(val values: SetupValues, val problems: Map<String, Problem>)

    const val NAME = "name"
    const val HOME = "home"
    const val WORK = "work"
    const val THEME = "theme"
    const val MODE = "mode"
    const val FONT = "font"
    const val SIZE = "size"
    const val ICONS = "icons"
    const val PROVIDER = "provider"
    const val MODEL = "model"
    /** A typed model name; when filled in it wins over the list. */
    const val MODEL_CUSTOM = "model_custom"
    const val KEY = "key"
    const val REMOVE_KEY = "remove_key"
    fun favoriteName(slot: Int) = "fav${slot}_name"
    fun favoriteNumber(slot: Int) = "fav${slot}_number"

    /** Longest text kept from a free-text field: enough for an address, short for abuse. */
    private const val MAX_TEXT = 200

    fun parse(
        fields: Map<String, String>,
        current: SetupValues,
        providerIds: Set<String>,
        iconStyles: Set<String>,
    ): Result {
        val problems = LinkedHashMap<String, Problem>()
        val favorites = (0 until Favorites.SLOTS).map { slot ->
            val name = text(fields[favoriteName(slot)])
            val number = text(fields[favoriteNumber(slot)])
            when {
                favoriteName(slot) !in fields && favoriteNumber(slot) !in fields -> current.favorites.getOrNull(slot)
                name == null && number == null -> null
                else -> Favorites.decode(Favorites.encode(Favorite(name ?: number.orEmpty(), number.orEmpty())))
                    ?: run {
                        problems[favoriteNumber(slot)] = Problem.FAVORITE_NUMBER
                        current.favorites.getOrNull(slot)
                    }
            }
        }
        val provider = fields[PROVIDER]?.takeIf { it in providerIds } ?: current.providerId
        val values = SetupValues(
            driverName = if (NAME in fields) text(fields[NAME]) else current.driverName,
            homeAddress = if (HOME in fields) text(fields[HOME]) else current.homeAddress,
            workAddress = if (WORK in fields) text(fields[WORK]) else current.workAddress,
            favorites = favorites,
            themeStyle = fields[THEME]?.let { id -> ThemeStyle.entries.firstOrNull { it.id == id } } ?: current.themeStyle,
            themeMode = fields[MODE]?.let { id -> ThemeMode.entries.firstOrNull { it.id == id } } ?: current.themeMode,
            font = fields[FONT]?.let { id -> FontChoice.entries.firstOrNull { it.id == id } } ?: current.font,
            uiSize = fields[SIZE]?.let { id -> UiScale.UiSize.entries.firstOrNull { it.id == id } } ?: current.uiSize,
            iconStyle = fields[ICONS]?.takeIf { it in iconStyles } ?: current.iconStyle,
            providerId = provider,
            model = text(fields[MODEL_CUSTOM]) ?: if (MODEL in fields) text(fields[MODEL]) else current.model,
            newApiKey = fields[KEY]?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT),
            removeApiKey = fields[REMOVE_KEY] == "on",
        )
        return Result(values, problems)
    }

    /** Trimmed, length-capped, single-line; blank means "not set". */
    private fun text(value: String?): String? =
        value?.replace('\n', ' ')?.replace('\r', ' ')?.trim()?.take(MAX_TEXT)?.takeIf { it.isNotEmpty() }
}
