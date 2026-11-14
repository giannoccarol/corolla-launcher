package io.github.giannoccarol.corollalauncher.remote

import android.content.Context
import androidx.annotation.StringRes
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.phone.Favorites
import io.github.giannoccarol.corollalauncher.remote.HttpParser.escapeHtml
import io.github.giannoccarol.corollalauncher.theme.FontChoice
import io.github.giannoccarol.corollalauncher.theme.ThemeMode
import io.github.giannoccarol.corollalauncher.theme.ThemeStyle
import io.github.giannoccarol.corollalauncher.ui.UiScale

/**
 * The phone setup page: one self-contained HTML form, sized for a phone, in the launcher's
 * current colours and language. Every value written into it is escaped. API keys are never
 * sent to the phone: the page only says whether one is saved and its last four characters,
 * as *Settings* does.
 */
class SetupPage(private val context: Context, private val palette: Palette) {

    /** The launcher's colours as CSS hex, taken from the theme when the session starts. */
    data class Palette(
        val bg: String, val surface: String, val surface2: String, val text: String,
        val text2: String, val line: String, val accent: String, val onAccent: String,
        val warn: String,
    )

    data class Option(val id: String, val label: String)

    data class Provider(val id: String, val name: String, val models: List<String>, val defaultModel: String)

    /** What the form offers, beyond the current values. */
    data class Choices(
        val iconStyles: List<Option>,
        val providers: List<Provider>,
        /** Last four characters of the saved key per provider id; absent when none is saved. */
        val keyEndings: Map<String, String>,
    )

    enum class Banner { NONE, SAVED, PROBLEMS }

    fun form(
        token: String,
        values: SetupValues,
        choices: Choices,
        problems: Map<String, SetupForm.Problem> = emptyMap(),
        banner: Banner = Banner.NONE,
    ): String = page {
        append("<h1>").append(s(R.string.settings_phone_setup)).append("</h1>")
        append("<p class=\"intro\">").append(s(R.string.web_intro)).append("</p>")
        when (banner) {
            Banner.SAVED -> append("<p class=\"banner ok\" role=\"status\">").append(s(R.string.web_saved)).append("</p>")
            Banner.PROBLEMS -> append("<p class=\"banner bad\" role=\"alert\">").append(s(R.string.web_problems)).append("</p>")
            Banner.NONE -> Unit
        }
        append("<form method=\"post\" action=\"/save\" autocomplete=\"off\">")
        append("<input type=\"hidden\" name=\"t\" value=\"").append(escapeHtml(token)).append("\">")

        section(R.string.settings_section_appearance) {
            text(SetupForm.NAME, R.string.settings_driver_name, values.driverName, R.string.settings_driver_name_hint)
        }
        section(R.string.settings_section_themes) {
            select(SetupForm.THEME, R.string.settings_theme, ThemeStyle.entries.map { Option(it.id, s(it.labelRes)) }, values.themeStyle.id)
            select(SetupForm.MODE, R.string.settings_theme_mode, ThemeMode.entries.map { Option(it.id, s(it.labelRes)) }, values.themeMode.id)
            select(SetupForm.FONT, R.string.settings_font, FontChoice.entries.map { Option(it.id, fontLabel(it)) }, values.font.id)
            select(SetupForm.SIZE, R.string.settings_ui_size, UiScale.UiSize.entries.map { Option(it.id, s(sizeLabel(it))) }, values.uiSize.id)
            select(SetupForm.ICONS, R.string.settings_icons, choices.iconStyles, values.iconStyle)
        }
        section(R.string.settings_section_navigation) {
            text(SetupForm.HOME, R.string.settings_home_address, values.homeAddress, R.string.nav_address_hint)
            text(SetupForm.WORK, R.string.settings_work_address, values.workAddress, R.string.nav_address_hint)
        }
        section(R.string.favorites_title) {
            for (slot in 0 until Favorites.SLOTS) {
                val favorite = values.favorites.getOrNull(slot)
                append("<fieldset><legend>").append(escapeHtml(context.getString(R.string.web_favorite_n, slot + 1))).append("</legend>")
                text(SetupForm.favoriteName(slot), R.string.favorites_name_title, favorite?.name, R.string.favorites_name_hint)
                text(
                    SetupForm.favoriteNumber(slot), R.string.favorites_number_title, favorite?.number,
                    R.string.favorites_number_hint, type = "tel",
                    error = problems[SetupForm.favoriteNumber(slot)]?.let { s(R.string.favorites_invalid) },
                )
                append("</fieldset>")
            }
        }
        section(R.string.settings_section_assistant) {
            select(SetupForm.PROVIDER, R.string.settings_ai_provider, choices.providers.map { Option(it.id, it.name) }, values.providerId)
            val provider = choices.providers.firstOrNull { it.id == values.providerId }
            append("<label for=\"model\">").append(s(R.string.settings_ai_model)).append("</label>")
            append("<input id=\"model\" name=\"model\" list=\"models\" value=\"").append(escapeHtml(values.model.orEmpty()))
                .append("\" placeholder=\"").append(escapeHtml(provider?.defaultModel.orEmpty())).append("\">")
            append("<datalist id=\"models\">")
            provider?.models?.forEach { append("<option value=\"").append(escapeHtml(it)).append("\">") }
            append("</datalist>")
            append("<label for=\"key\">").append(s(R.string.settings_ai_key)).append("</label>")
            append("<input id=\"key\" name=\"key\" type=\"password\" autocomplete=\"new-password\" spellcheck=\"false\">")
            val ending = choices.keyEndings[values.providerId]
            append("<p class=\"hint\">")
            append(if (ending != null) escapeHtml(context.getString(R.string.web_key_set, ending)) else s(R.string.web_key_none))
            append("</p>")
            if (ending != null) {
                append("<label class=\"check\"><input type=\"checkbox\" name=\"remove_key\"> ").append(s(R.string.web_key_remove)).append("</label>")
            }
        }
        append("<div class=\"save\"><button type=\"submit\">").append(s(R.string.save)).append("</button></div>")
        append("</form>")
    }

    /** Shown for a wrong, old or missing token. */
    fun expired(): String = page {
        append("<h1>").append(s(R.string.settings_phone_setup)).append("</h1>")
        append("<p class=\"banner bad\">").append(s(R.string.web_link_expired)).append("</p>")
    }

    private fun page(body: StringBuilder.() -> Unit): String = buildString {
        append("<!doctype html><html lang=\"").append(escapeHtml(context.resources.configuration.locales[0].language)).append("\"><head>")
        append("<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<meta name=\"referrer\" content=\"no-referrer\"><meta name=\"robots\" content=\"noindex\">")
        append("<title>").append(s(R.string.settings_phone_setup)).append("</title><style>").append(css()).append("</style></head><body><main>")
        body()
        append("</main></body></html>")
    }

    private fun css(): String = with(palette) {
        """
        *{box-sizing:border-box}
        body{margin:0;background:$bg;color:$text;font:17px/1.45 system-ui,-apple-system,Roboto,sans-serif}
        main{max-width:560px;margin:0 auto;padding:20px 16px 110px}
        h1{font-size:26px;margin:4px 0 6px}
        h2{font-size:15px;text-transform:uppercase;letter-spacing:.06em;color:$text2;margin:0 0 10px}
        .intro,.hint{color:$text2;font-size:15px;margin:6px 0 14px}
        section{background:$surface;border:1px solid $line;border-radius:22px;padding:18px 16px 8px;margin:14px 0}
        fieldset{border:0;border-top:1px solid $line;margin:6px 0 0;padding:10px 0 0}
        legend{color:$text2;font-size:14px;padding:0 6px 0 0}
        label{display:block;font-weight:600;font-size:15px;margin:8px 0 6px}
        input,select{width:100%;height:52px;border-radius:14px;border:1px solid $line;background:$surface2;color:$text;font:inherit;padding:0 14px;margin-bottom:10px}
        input:focus,select:focus{outline:2px solid $accent;outline-offset:1px}
        .check{display:flex;align-items:center;gap:10px;font-weight:400}
        .check input{width:24px;height:24px;margin:0}
        .error{color:$warn;font-size:14px;margin:-4px 0 10px}
        .banner{padding:14px 16px;border-radius:16px;font-weight:600}
        .ok{background:$surface2;border:1px solid $accent}
        .bad{background:$surface2;border:1px solid $warn}
        .save{position:fixed;left:0;right:0;bottom:0;padding:12px 16px 18px;background:linear-gradient(transparent,$bg 35%)}
        .save button{display:block;width:100%;max-width:528px;margin:0 auto;height:58px;border:0;border-radius:29px;background:$accent;color:$onAccent;font:inherit;font-weight:700;font-size:18px}
        """.trimIndent().replace("\n", "")
    }

    private fun StringBuilder.section(@StringRes title: Int, content: StringBuilder.() -> Unit) {
        append("<section><h2>").append(s(title)).append("</h2>")
        content()
        append("</section>")
    }

    private fun StringBuilder.text(
        name: String,
        @StringRes label: Int,
        value: String?,
        @StringRes hint: Int,
        type: String = "text",
        error: String? = null,
    ) {
        append("<label for=\"").append(name).append("\">").append(s(label)).append("</label>")
        append("<input id=\"").append(name).append("\" name=\"").append(name).append("\" type=\"").append(type)
            .append("\" value=\"").append(escapeHtml(value.orEmpty())).append("\" placeholder=\"").append(s(hint)).append("\">")
        if (error != null) append("<p class=\"error\">").append(escapeHtml(error)).append("</p>")
    }

    private fun StringBuilder.select(name: String, @StringRes label: Int, options: List<Option>, selected: String) {
        append("<label for=\"").append(name).append("\">").append(s(label)).append("</label>")
        append("<select id=\"").append(name).append("\" name=\"").append(name).append("\">")
        for (option in options) {
            append("<option value=\"").append(escapeHtml(option.id)).append('"')
            if (option.id == selected) append(" selected")
            append('>').append(escapeHtml(option.label)).append("</option>")
        }
        append("</select>")
    }

    private fun s(@StringRes id: Int): String = escapeHtml(context.getString(id))

    private fun fontLabel(font: FontChoice): String = when (font) {
        FontChoice.AUTO -> context.getString(R.string.font_auto)
        FontChoice.SYSTEM -> context.getString(R.string.font_system)
        FontChoice.MANROPE -> "Manrope"
        FontChoice.NUNITO -> "Nunito"
    }

    @StringRes
    private fun sizeLabel(size: UiScale.UiSize): Int = when (size) {
        UiScale.UiSize.NORMAL -> R.string.ui_size_normal
        UiScale.UiSize.LARGE -> R.string.ui_size_large
        UiScale.UiSize.EXTRA_LARGE -> R.string.ui_size_extra_large
    }
}
