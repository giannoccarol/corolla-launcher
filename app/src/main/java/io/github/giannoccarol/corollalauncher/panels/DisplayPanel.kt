package io.github.giannoccarol.corollalauncher.panels

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.settings.Prefs
import io.github.giannoccarol.corollalauncher.theme.ThemeManager
import io.github.giannoccarol.corollalauncher.theme.ThemeMode

/**
 * Screen brightness and theme. Changing the system brightness needs the one-time "modify system
 * settings" grant; until then the panel offers the button that opens it.
 */
class DisplayPanel(activity: Activity) : Sheet(activity, R.string.qs_display) {

    private val resolver = activity.contentResolver
    private val prefs = Prefs(activity)

    override fun build() {
        if (Settings.System.canWrite(activity)) {
            val auto = read(Settings.System.SCREEN_BRIGHTNESS_MODE, MANUAL) == AUTOMATIC
            switchRow(R.string.display_auto_brightness, null, auto) {
                write(Settings.System.SCREEN_BRIGHTNESS_MODE, if (auto) MANUAL else AUTOMATIC)
                render()
            }
            sliderRow(
                R.string.display_brightness, R.drawable.ic_brightness, MAX_BRIGHTNESS,
                read(Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS),
                showValue = { "${it * 100 / MAX_BRIGHTNESS}%" },
                step = BRIGHTNESS_STEP,
            ) { level ->
                // Automatic mode ignores a manual level, so dragging switches it off.
                write(Settings.System.SCREEN_BRIGHTNESS_MODE, MANUAL)
                write(Settings.System.SCREEN_BRIGHTNESS, level.coerceAtLeast(MIN_BRIGHTNESS))
            }
        } else {
            row(
                activity.getString(R.string.display_allow_title),
                activity.getString(R.string.display_allow_summary),
                icon = R.drawable.ic_brightness,
                trailing = R.drawable.ic_chevron_right,
            ) {
                open(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        .setData(Uri.fromParts("package", activity.packageName, null)),
                )
            }
        }

        label(R.string.display_theme)
        for (mode in ThemeMode.entries) {
            row(
                activity.getString(mode.labelRes),
                trailing = if (mode == prefs.themeMode) R.drawable.ic_check else null,
            ) {
                // The activity is recreated when the theme changes; close the panel first.
                dismiss()
                prefs.themeMode = mode
                ThemeManager.apply(activity)
            }
        }
        button(R.string.display_system_settings) { open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
    }

    private fun read(name: String, default: Int): Int = try {
        Settings.System.getInt(resolver, name)
    } catch (e: Settings.SettingNotFoundException) {
        default
    }

    private fun write(name: String, value: Int) {
        try {
            Settings.System.putInt(resolver, name, value)
        } catch (e: SecurityException) {
            render() // The grant was revoked meanwhile: show the button again.
        } catch (e: IllegalArgumentException) {
            // Some firmwares reject the brightness mode key; the slider still works.
        }
    }

    private fun open(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.settings_screen_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val MANUAL = Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        const val AUTOMATIC = Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        const val MAX_BRIGHTNESS = 255
        /** Never let the slider black the screen out completely. */
        const val MIN_BRIGHTNESS = 8
        /** About a tenth of the range per tap on minus or plus. */
        const val BRIGHTNESS_STEP = 25
    }
}
