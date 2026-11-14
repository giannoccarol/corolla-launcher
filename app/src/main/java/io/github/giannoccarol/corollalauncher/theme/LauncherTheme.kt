package io.github.giannoccarol.corollalauncher.theme

import android.app.Activity
import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.settings.Prefs

/**
 * The look of the whole app, chosen in *Settings → Themes*. Independent of light and dark
 * ([ThemeMode]): every style has both, and `values-night/themes.xml` holds the dark ones.
 */
enum class ThemeStyle(val id: String, @param:StyleRes val style: Int, @param:StringRes val labelRes: Int) {
    /** Neutral greys and blue, flat cards: the original look and layout. */
    CLASSIC("classic", R.style.Theme_Launcher, R.string.theme_style_classic),

    /** Warm sand or near-black with amber light, glass cards, its own home layout. */
    AMBRA("ambra", R.style.Theme_Launcher_Ambra, R.string.theme_style_ambra);

    companion object {
        fun fromId(id: String?): ThemeStyle = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** The typeface of the whole app. [AUTO] follows the theme: system for Classic, Manrope for Amber. */
enum class FontChoice(val id: String) {
    AUTO("auto"), SYSTEM("system"), MANROPE("manrope"), NUNITO("nunito");

    companion object {
        fun fromId(id: String?): FontChoice = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

object LauncherTheme {

    /** The font actually used: [FontChoice.AUTO] resolved for [style]. Pure, unit tested. */
    fun resolveFont(style: ThemeStyle, font: FontChoice): FontChoice = when {
        font != FontChoice.AUTO -> font
        style == ThemeStyle.AMBRA -> FontChoice.MANROPE
        else -> FontChoice.SYSTEM
    }

    @StyleRes
    fun fontOverlay(font: FontChoice): Int = when (font) {
        FontChoice.MANROPE -> R.style.ThemeOverlay_Launcher_Font_Manrope
        FontChoice.NUNITO -> R.style.ThemeOverlay_Launcher_Font_Nunito
        FontChoice.AUTO, FontChoice.SYSTEM -> R.style.ThemeOverlay_Launcher_Font_System
    }

    /** What is applied, as one string: a screen whose signature changed must be recreated. */
    fun signature(context: Context): String {
        val prefs = Prefs(context)
        return prefs.themeStyle.id + "/" + resolveFont(prefs.themeStyle, prefs.fontChoice).id
    }

    /** Call before `super.onCreate`. Returns the [signature] that was applied. */
    fun apply(activity: Activity): String {
        val prefs = Prefs(activity)
        activity.setTheme(prefs.themeStyle.style)
        activity.theme.applyStyle(fontOverlay(resolveFont(prefs.themeStyle, prefs.fontChoice)), true)
        return signature(activity)
    }
}
