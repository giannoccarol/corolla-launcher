package io.github.giannoccarol.corollalauncher.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.res.ResourcesCompat

/** The theme's typefaces for text drawn on a canvas, where layouts' fontFamily cannot reach. */
object ThemeFonts {

    /**
     * The typeface behind a theme attribute such as `R.attr.fontLight`: a bundled font
     * resource, or a system family name ("sans-serif-light"). Falls back to [fallback].
     */
    fun typeface(context: Context, @AttrRes attr: Int, fallback: String): Typeface {
        val value = TypedValue()
        if (context.theme.resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                try {
                    ResourcesCompat.getFont(context, value.resourceId)?.let { return it }
                } catch (e: android.content.res.Resources.NotFoundException) {
                    // Not a font resource: fall through to the family name.
                }
            }
            value.string?.toString()?.takeIf { it.isNotBlank() && !it.startsWith("res/") }
                ?.let { return Typeface.create(it, Typeface.NORMAL) }
        }
        return Typeface.create(fallback, Typeface.NORMAL)
    }
}
