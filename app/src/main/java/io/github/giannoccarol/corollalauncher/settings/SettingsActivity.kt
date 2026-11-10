package io.github.giannoccarol.corollalauncher.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import io.github.giannoccarol.corollalauncher.BuildConfig
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.databinding.ActivitySettingsBinding
import io.github.giannoccarol.corollalauncher.theme.ThemeManager
import io.github.giannoccarol.corollalauncher.theme.ThemeMode
import io.github.giannoccarol.corollalauncher.ui.Motion
import io.github.giannoccarol.corollalauncher.ui.SystemBars
import io.github.giannoccarol.corollalauncher.ui.TextPrompt

/** A plain list of rows, rebuilt on every resume so summaries always show the current value. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBars.apply(this, binding.root)
        prefs = Prefs(this)
        binding.backButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun finish() {
        super.finish()
        Motion.close(this)
    }

    private fun render() {
        binding.list.removeAllViews()
        val notSet = getString(R.string.settings_not_set)

        section(R.string.settings_section_appearance)
        row(R.string.settings_theme, getString(prefs.themeMode.labelRes)) { pickTheme() }
        row(R.string.settings_language, languageLabel(Languages.currentTag())) { pickLanguage() }
        row(
            R.string.settings_animations,
            getString(if (prefs.animations) R.string.settings_on else R.string.settings_off),
        ) {
            prefs.animations = !prefs.animations
            render()
        }

        section(R.string.settings_section_navigation)
        row(R.string.settings_home_address, prefs.homeAddress ?: notSet) {
            editAddress(R.string.settings_home_address, prefs.homeAddress) {
                prefs.homeAddress = it
            }
        }
        row(R.string.settings_work_address, prefs.workAddress ?: notSet) {
            editAddress(R.string.settings_work_address, prefs.workAddress) {
                prefs.workAddress = it
            }
        }

        section(R.string.settings_section_system)
        val mediaAccess =
            packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        row(
            R.string.settings_media_access,
            getString(
                if (mediaAccess) R.string.settings_media_access_on
                else R.string.settings_media_access_off,
            ),
        ) { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
        row(
            R.string.settings_default_launcher,
            getString(R.string.settings_default_launcher_summary),
        ) { open(Settings.ACTION_HOME_SETTINGS) }
        row(R.string.settings_reset_usage, getString(R.string.settings_reset_usage_summary)) {
            prefs.resetLaunchCounts()
            toast(R.string.settings_reset_done)
        }

        section(R.string.settings_section_about)
        row(
            R.string.app_name,
            getString(R.string.settings_version, BuildConfig.VERSION_NAME),
            onClick = null,
        )
    }

    private fun section(@StringRes titleRes: Int) {
        val view = layoutInflater.inflate(R.layout.item_setting_section, binding.list, false)
        (view as TextView).setText(titleRes)
        binding.list.addView(view)
    }

    private fun row(@StringRes titleRes: Int, summary: String, onClick: (() -> Unit)?) {
        val view = layoutInflater.inflate(R.layout.item_setting, binding.list, false)
        view.findViewById<TextView>(R.id.title).setText(titleRes)
        view.findViewById<TextView>(R.id.summary).text = summary
        if (onClick != null) {
            view.setOnClickListener { onClick() }
        } else {
            view.isClickable = false
            view.isFocusable = false
        }
        binding.list.addView(view)
    }

    private fun pickTheme() {
        val modes = ThemeMode.entries
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(
                modes.map { getString(it.labelRes) }.toTypedArray(),
                modes.indexOf(prefs.themeMode),
            ) { dialog, which ->
                dialog.dismiss()
                prefs.themeMode = modes[which]
                ThemeManager.apply(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickLanguage() {
        // First entry (null) means "follow the system language".
        val tags: List<String?> = listOf<String?>(null) + Languages.SUPPORTED
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(
                tags.map(::languageLabel).toTypedArray(),
                tags.indexOf(Languages.currentTag()).coerceAtLeast(0),
            ) { dialog, which ->
                dialog.dismiss()
                Languages.set(tags[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun languageLabel(tag: String?): String =
        if (tag == null) getString(R.string.language_system) else Languages.displayName(tag)

    private fun editAddress(@StringRes titleRes: Int, current: String?, save: (String?) -> Unit) {
        TextPrompt.show(this, titleRes, R.string.nav_address_hint, current, R.string.save) {
            save(it.ifEmpty { null })
            render()
        }
    }

    private fun open(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.settings_screen_unavailable)
        }
    }

    private fun toast(@StringRes messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }
}
