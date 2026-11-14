package io.github.giannoccarol.corollalauncher.ui

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.WindowManager
import io.github.giannoccarol.corollalauncher.settings.Prefs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One size knob for the whole interface. Head units report all sorts of resolutions and
 * densities, and a screen read from the driver's seat needs everything larger than a tablet
 * at the same density. So instead of trusting the firmware's density, the launcher picks its
 * own: the short side of the screen becomes [UiSize.screenHeightDp] dp, and every dp and sp
 * (text, touch targets, icons, bars) grows or shrinks with it.
 */
object UiScale {

    /** How many dp the short side of the screen is laid out as. Fewer dp: bigger everything. */
    enum class UiSize(val id: String, val screenHeightDp: Int) {
        NORMAL("normal", 600),
        LARGE("large", 520),
        EXTRA_LARGE("extra_large", 460);

        companion object {
            val DEFAULT = LARGE
            fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: DEFAULT
        }
    }

    /** [base] with the density of the chosen size. Use it in attachBaseContext. */
    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        val oldDpi = config.densityDpi
        val newDpi = densityDpi(base, Prefs(base).uiSize)
        if (oldDpi == newDpi || oldDpi <= 0) return base
        // Keep the screen sizes in dp consistent with the new density.
        fun rescale(dp: Int) = if (dp <= 0) dp else (dp * oldDpi / newDpi.toFloat()).roundToInt()
        config.densityDpi = newDpi
        config.screenWidthDp = rescale(config.screenWidthDp)
        config.screenHeightDp = rescale(config.screenHeightDp)
        config.smallestScreenWidthDp = rescale(config.smallestScreenWidthDp)
        return base.createConfigurationContext(config)
    }

    /** The density that lays the short side of the screen out as [size]'s dp. */
    fun densityDpi(context: Context, size: UiSize): Int = densityFor(shortSidePx(context), size.screenHeightDp)

    /**
     * The panel's real short side, system bars included: the launcher hides them, and the
     * value must not change when they briefly come back.
     */
    @Suppress("DEPRECATION") // Display.getRealMetrics is the only way that works back to API 24.
    fun shortSidePx(context: Context): Int {
        val metrics = DisplayMetrics()
        val windows = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windows.defaultDisplay.getRealMetrics(metrics)
        return min(metrics.widthPixels, metrics.heightPixels)
    }

    /** Pure, unit tested: px / dp x 160, never below the 120 dpi of the smallest bucket. */
    fun densityFor(shortSidePx: Int, shortSideDp: Int): Int =
        (shortSidePx * BASELINE_DPI / shortSideDp.toFloat()).roundToInt().coerceAtLeast(MIN_DPI)

    private const val BASELINE_DPI = 160
    private const val MIN_DPI = 120
}
