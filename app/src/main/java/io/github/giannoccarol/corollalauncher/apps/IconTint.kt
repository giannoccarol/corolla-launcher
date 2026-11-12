package io.github.giannoccarol.corollalauncher.apps

import io.github.giannoccarol.corollalauncher.ui.AlbumColor

/**
 * The colours of a themed icon tinted with its app's own colour: a pale tile and a deep glyph
 * in the light theme, a dark tile and a pastel glyph in the dark theme, both of the app's hue.
 * Yellows and greens look brighter than blues at the same value, so they are drawn darker;
 * every hue keeps a glyph-to-tile contrast of about 4.5:1 or more. Pure, unit tested.
 */
object IconTint {

    data class Colors(val tile: Int, val glyph: Int)

    /** The app's hue from its icon's pixels, never purple; null for a grey or black icon. */
    fun hueOf(pixels: IntArray): Float? =
        AlbumColor.pick(pixels)?.let { color ->
            AlbumColor.hsv((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF).first.toFloat()
        }

    fun colors(hue: Float, dark: Boolean): Colors {
        val h = AlbumColor.withoutPurple(hue.toDouble())
        val bright = h in BRIGHT_FROM..BRIGHT_TO
        return if (dark) {
            Colors(
                tile = AlbumColor.rgb(h, 0.55, if (bright) 0.22 else 0.26),
                glyph = AlbumColor.rgb(h, if (bright) 0.32 else 0.40, 1.0),
            )
        } else {
            Colors(
                tile = AlbumColor.rgb(h, 0.17, 0.97),
                glyph = AlbumColor.rgb(
                    h, 0.90,
                    when {
                        h in YELLOW_FROM..YELLOW_TO -> 0.40
                        bright -> 0.48
                        else -> 0.66
                    },
                ),
            )
        }
    }

    /** Oranges to cyans: the hues that look lighter than they are. */
    private const val BRIGHT_FROM = 25.0
    private const val BRIGHT_TO = 205.0

    /** Yellows and yellow-greens, the lightest of all: darker still on a pale tile. */
    private const val YELLOW_FROM = 45.0
    private const val YELLOW_TO = 100.0
}
