package io.github.giannoccarol.corollalauncher.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconSilhouetteTest {

    private val size = 100

    private fun image(opaque: (x: Int, y: Int) -> Boolean) =
        IntArray(size * size) { if (opaque(it % size, it / size)) 255 else 0 }

    @Test
    fun aCentredLogoIsAccepted() {
        // A disc of radius 25 in the middle: about 20% of the tile.
        assertTrue(IconSilhouette.looksLikeLogo(image { x, y -> (x - 50) * (x - 50) + (y - 50) * (y - 50) < 625 }, size))
    }

    @Test
    fun fullBleedArtIsRejected() {
        assertFalse(IconSilhouette.looksLikeLogo(image { _, _ -> true }, size))
    }

    @Test
    fun aBigDiscIsRejected() {
        assertFalse(IconSilhouette.looksLikeLogo(image { x, y -> (x - 50) * (x - 50) + (y - 50) * (y - 50) < 1900 }, size))
    }

    @Test
    fun shapesTouchingTheEdgeAreRejected() {
        // Small overall, but it runs into the border: a cropped picture, not a logo.
        assertFalse(IconSilhouette.looksLikeLogo(image { x, _ -> x < 12 }, size))
    }

    @Test
    fun emptyOrTinyIsRejected() {
        assertFalse(IconSilhouette.looksLikeLogo(image { _, _ -> false }, size))
        assertFalse(IconSilhouette.looksLikeLogo(image { x, y -> x == 50 && y == 50 }, size))
        assertFalse(IconSilhouette.looksLikeLogo(IntArray(3), size))
    }
}
