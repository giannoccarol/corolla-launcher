package io.github.giannoccarol.corollalauncher.apps

import io.github.giannoccarol.corollalauncher.ui.AlbumColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class IconTintTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun hue(color: Int) =
        AlbumColor.hsv((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF).first

    /** WCAG relative luminance contrast. */
    private fun contrast(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val v = ((color shr shift) and 0xFF) / 255.0
                return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun spotifyGreenGivesAGreenHue() {
        // A green disc on a black background, as in Spotify's icon.
        val pixels = IntArray(144) { if (it % 12 in 3..8 && it / 12 in 3..8) argb(30, 215, 96) else argb(0, 0, 0) }
        val hue = IconTint.hueOf(pixels)
        assertNotNull(hue)
        assertTrue(hue!! in 120f..160f)
    }

    @Test
    fun greyIconsHaveNoHue() {
        assertNull(IconTint.hueOf(IntArray(144) { argb(90, 90, 90) }))
    }

    @Test
    fun everyHueIsReadableInBothThemes() {
        for (h in 0 until 360 step 5) {
            for (dark in listOf(false, true)) {
                val colors = IconTint.colors(h.toFloat(), dark)
                assertTrue("hue $h dark=$dark", contrast(colors.tile, colors.glyph) >= 4.2)
            }
        }
    }

    @Test
    fun tilesFollowTheTheme() {
        val light = IconTint.colors(140f, dark = false)
        val dark = IconTint.colors(140f, dark = true)
        assertTrue(contrast(light.tile, argb(0, 0, 0)) > contrast(dark.tile, argb(0, 0, 0)))
    }

    @Test
    fun neverPurple() {
        for (h in 0 until 360 step 5) {
            for (dark in listOf(false, true)) {
                val colors = IconTint.colors(h.toFloat(), dark)
                for (color in listOf(colors.tile, colors.glyph)) {
                    assertTrue("hue $h gave ${hue(color)}", hue(color) !in 241.0..319.0)
                }
            }
        }
        assertEquals(225.0, hue(IconTint.colors(270f, dark = false).glyph), 3.0)
    }
}
