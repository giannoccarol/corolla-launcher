package io.github.giannoccarol.corollalauncher.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import io.github.giannoccarol.corollalauncher.BuildConfig
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.apps.AppIcons
import io.github.giannoccarol.corollalauncher.apps.AppRepository
import io.github.giannoccarol.corollalauncher.apps.IconPack
import io.github.giannoccarol.corollalauncher.assistant.KeyVault
import io.github.giannoccarol.corollalauncher.assistant.ai.AiProvider
import io.github.giannoccarol.corollalauncher.assistant.ai.AiProviders
import io.github.giannoccarol.corollalauncher.databinding.ActivitySettingsBinding
import io.github.giannoccarol.corollalauncher.keys.NavKeyService
import io.github.giannoccarol.corollalauncher.theme.ThemeManager
import io.github.giannoccarol.corollalauncher.trip.TripTracker
import io.github.giannoccarol.corollalauncher.theme.ThemeMode
import io.github.giannoccarol.corollalauncher.ui.LauncherActivity
import io.github.giannoccarol.corollalauncher.ui.Motion
import io.github.giannoccarol.corollalauncher.ui.TextPrompt

/** A plain list of rows, rebuilt on every resume so summaries always show the current value. */
class SettingsActivity : LauncherActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private lateinit var vault: KeyVault

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpWindow(binding.root)
        prefs = Prefs(this)
        vault = KeyVault(this)
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
        row(R.string.settings_driver_name, prefs.driverName ?: notSet) {
            TextPrompt.show(
                this, R.string.settings_driver_name, R.string.settings_driver_name_hint,
                prefs.driverName, R.string.save,
            ) {
                prefs.driverName = it.ifEmpty { null }
                render()
            }
        }
        row(R.string.settings_theme, getString(prefs.themeMode.labelRes)) { pickTheme() }
        row(R.string.settings_language, languageLabel(Languages.currentTag())) { pickLanguage() }
        row(R.string.settings_icons, iconStyleLabel(prefs.iconStyle, IconPack.installed(this))) {
            pickIconStyle()
        }
        row(
            R.string.settings_animations,
            getString(if (prefs.animations) R.string.settings_on else R.string.settings_off),
        ) {
            prefs.animations = !prefs.animations
            render()
        }

        section(R.string.settings_section_assistant)
        val provider = AiProviders.byId(prefs.aiProviderId)
        row(R.string.settings_ai_provider, provider.name) { pickProvider() }
        row(R.string.settings_ai_key, vault.masked(provider.id) ?: notSet) { editKey(provider.id) }
        row(R.string.settings_ai_model, prefs.aiModel(provider.id) ?: provider.defaultModel) {
            pickModel(provider)
        }
        row(
            R.string.settings_ai_speak,
            getString(if (prefs.speakReplies) R.string.settings_on else R.string.settings_off),
        ) {
            prefs.speakReplies = !prefs.speakReplies
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
        row(
            R.string.settings_location,
            getString(
                if (TripTracker.hasPermission(this)) R.string.settings_location_on
                else R.string.settings_location_off,
            ),
        ) { openAppDetails() }
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
            R.string.settings_nav_keys,
            getString(
                if (NavKeyService.isEnabled) R.string.settings_nav_keys_on
                else R.string.settings_nav_keys_off,
            ),
        ) { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        row(
            R.string.settings_default_launcher,
            getString(R.string.settings_default_launcher_summary),
        ) { open(Settings.ACTION_HOME_SETTINGS) }

        section(R.string.settings_section_about)
        row(R.string.settings_data_credits, getString(R.string.settings_data_credits_summary), onClick = null)
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
                ThemeManager.applyAnimated(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickProvider() {
        val providers = AiProviders.all
        val current = providers.indexOf(AiProviders.byId(prefs.aiProviderId))
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_ai_provider)
            .setSingleChoiceItems(providers.map { it.name }.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                prefs.aiProviderId = providers[which].id
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Saving an empty key removes it. */
    private fun editKey(providerId: String) {
        TextPrompt.show(
            this, getString(R.string.settings_ai_key), R.string.settings_ai_key_hint,
            initial = null, positiveRes = R.string.save, password = true,
        ) {
            vault.set(providerId, it)
            render()
        }
    }

    private fun pickModel(provider: AiProvider) {
        val models = provider.suggestedModels
        val labels = (models + getString(R.string.settings_ai_model_other)).toTypedArray()
        val current = models.indexOf(prefs.aiModel(provider.id) ?: provider.defaultModel)
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_ai_model)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                if (which < models.size) {
                    prefs.setAiModel(provider.id, models[which])
                    render()
                } else {
                    TextPrompt.show(
                        this, getString(R.string.settings_ai_model), R.string.settings_ai_model_hint,
                        prefs.aiModel(provider.id), R.string.save, password = false,
                    ) {
                        prefs.setAiModel(provider.id, it.ifEmpty { null })
                        render()
                    }
                }
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

    /** Themed, monochrome, original, or one of the icon packs installed from the Play Store. */
    private fun pickIconStyle() {
        val packs = IconPack.installed(this)
        val styles = listOf(AppIcons.STYLE_THEMED, AppIcons.STYLE_MONO, AppIcons.STYLE_ORIGINAL) +
            packs.map { it.packageName }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_icons)
            .setSingleChoiceItems(
                styles.map { iconStyleLabel(it, packs) }.toTypedArray(),
                styles.indexOf(prefs.iconStyle).coerceAtLeast(0),
            ) { dialog, which ->
                dialog.dismiss()
                prefs.iconStyle = styles[which]
                AppRepository.reload(this)
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** An uninstalled pack falls back to the themed icons, and is shown as such. */
    private fun iconStyleLabel(style: String, packs: List<IconPack.Installed>): String = when (style) {
        AppIcons.STYLE_ORIGINAL -> getString(R.string.settings_icons_original)
        AppIcons.STYLE_THEMED -> getString(R.string.settings_icons_themed)
        AppIcons.STYLE_MONO -> getString(R.string.settings_icons_mono)
        else -> packs.firstOrNull { it.packageName == style }?.label
            ?: getString(R.string.settings_icons_themed)
    }

    private fun languageLabel(tag: String?): String =
        if (tag == null) getString(R.string.language_system) else Languages.displayName(tag)

    private fun editAddress(@StringRes titleRes: Int, current: String?, save: (String?) -> Unit) {
        TextPrompt.show(this, titleRes, R.string.nav_address_hint, current, R.string.save) {
            save(it.ifEmpty { null })
            render()
        }
    }

    private fun openAppDetails() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null)),
            )
        } catch (e: ActivityNotFoundException) {
            toast(R.string.settings_screen_unavailable)
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
