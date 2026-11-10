package io.github.giannoccarol.corollalauncher.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * In-app language selection. AppCompat stores the choice and applies it on every Android
 * version, including the old ones head units ship with.
 *
 * Adding a language: see `.claude/skills/add-language/SKILL.md`.
 */
object Languages {

    /** Must match `res/xml/locales_config.xml` and the `values-xx` folders. English is the base. */
    val SUPPORTED = listOf("en", "it", "de", "es", "fr")

    /** The language's own name, e.g. "Italiano" for "it". */
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** The language chosen in the app, or null when the app follows the system. */
    fun currentTag(): String? {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) null else locales[0]?.language
    }

    /** Pass null to follow the system language again. */
    fun set(tag: String?) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag),
        )
    }
}
