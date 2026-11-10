package io.github.giannoccarol.corollalauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumColorTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun hue(color: Int) = AlbumColor.hsv((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF).first

    @Test
    fun dominantColourfulHueWins() {
        // Mostly black and grey, with a red-orange area: the tint is red-orange, not grey.
        val pixels = IntArray(64) { i ->
            when {
                i < 30 -> argb(10, 10, 10)
                i < 44 -> argb(128, 128, 128)
                else -> argb(220, 70, 30)
            }
        }
        val color = AlbumColor.pick(pixels)
        assertNotNull(color)
        assertTrue(hue(color!!) in 5.0..30.0)
    }

    @Test
    fun greyOrBlackCoversGiveNoTint() {
        assertNull(AlbumColor.pick(IntArray(64) { argb(30, 30, 30) }))
        assertNull(AlbumColor.pick(IntArray(64) { argb(160, 160, 160) }))
        assertNull(AlbumColor.pick(IntArray(0)))
    }

    @Test
    fun neonColoursAreCalmedDown() {
        val color = AlbumColor.pick(IntArray(64) { argb(0, 255, 0) })!!
        val (_, saturation, value) = AlbumColor.hsv((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)
        assertTrue(saturation <= 0.76)
        assertTrue(value <= 0.86)
    }

    @Test
    fun hsvRoundTrip() {
        val original = argb(40, 120, 200)
        val (h, s, v) = AlbumColor.hsv(40, 120, 200)
        val back = AlbumColor.rgb(h, s, v)
        for (shift in listOf(16, 8, 0)) {
            val delta = ((original shr shift) and 0xFF) - ((back shr shift) and 0xFF)
            assertTrue(kotlin.math.abs(delta) <= 1)
        }
    }

    @Test
    fun alphaIsReplaced() {
        assertEquals(0x00123456, AlbumColor.withAlpha(0xFF123456.toInt(), 0f))
        assertEquals(0xFF123456.toInt(), AlbumColor.withAlpha(0x00123456, 1f))
    }
}
