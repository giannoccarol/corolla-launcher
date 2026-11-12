package io.github.giannoccarol.corollalauncher.ui

import kotlin.math.max
import kotlin.math.min

/**
 * Picks the colour of an album cover for the media card's tint, the way a premium player does:
 * the most saturated, mid-bright colour that covers a fair share of the cover, then calmed down
 * so it tints rather than shouts. Works on a handful of pixels (an 8x8 thumbnail is plenty).
 *
 * Pure arithmetic, no Android types, so it is unit tested.
 */
object AlbumColor {

    /** ARGB pixels in, an opaque RGB colour out; null for a cover with no usable colour. */
    fun pick(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        // Hue buckets weighted by how colourful each pixel is.
        val weights = DoubleArray(BUCKETS)
        val sumR = DoubleArray(BUCKETS)
        val sumG = DoubleArray(BUCKETS)
        val sumB = DoubleArray(BUCKETS)
        for (pixel in pixels) {
            if ((pixel ushr 24) < MIN_ALPHA) continue
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val (hue, saturation, value) = hsv(r, g, b)
            if (value < MIN_VALUE || saturation < MIN_SATURATION) continue
            val bucket = ((hue / 360.0) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            // Prefer colourful, mid-bright pixels over washed-out or near-black ones.
            val weight = saturation * (1.0 - kotlin.math.abs(value - 0.65))
            weights[bucket] += weight
            sumR[bucket] += r * weight
            sumG[bucket] += g * weight
            sumB[bucket] += b * weight
        }
        val best = weights.indices.maxByOrNull { weights[it] } ?: return null
        if (weights[best] < MIN_WEIGHT * pixels.size) return null
        val r = (sumR[best] / weights[best]).toInt()
        val g = (sumG[best] / weights[best]).toInt()
        val b = (sumB[best] / weights[best]).toInt()
        return calm(r, g, b)
    }

    /** Keeps the hue, settles saturation and brightness into a range that reads as a tint. */
    private fun calm(r: Int, g: Int, b: Int): Int {
        val (hue, saturation, value) = hsv(r, g, b)
        return rgb(withoutPurple(hue), saturation.coerceIn(0.35, 0.75), value.coerceIn(0.45, 0.85))
    }

    /**
     * The owner wants no purple anywhere, including colours taken from covers and app icons:
     * violet hues move to blue, magenta ones to pink.
     */
    fun withoutPurple(hue: Double): Double = when {
        hue < PURPLE_FROM || hue > PURPLE_TO -> hue
        hue < (PURPLE_FROM + PURPLE_TO) / 2 -> BLUE
        else -> PINK
    }

    /** Hue in degrees, saturation and value in 0..1. */
    fun hsv(r: Int, g: Int, b: Int): Triple<Double, Double, Double> {
        val rf = r / 255.0
        val gf = g / 255.0
        val bf = b / 255.0
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC
        val hue = when {
            delta == 0.0 -> 0.0
            maxC == rf -> 60 * (((gf - bf) / delta).mod(6.0))
            maxC == gf -> 60 * (((bf - rf) / delta) + 2)
            else -> 60 * (((rf - gf) / delta) + 4)
        }
        val saturation = if (maxC == 0.0) 0.0 else delta / maxC
        return Triple(hue, saturation, maxC)
    }

    fun rgb(hue: Double, saturation: Double, value: Double): Int {
        val c = value * saturation
        val x = c * (1 - kotlin.math.abs((hue / 60.0).mod(2.0) - 1))
        val m = value - c
        val (r, g, b) = when ((hue / 60).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0.0)
            1 -> Triple(x, c, 0.0)
            2 -> Triple(0.0, c, x)
            3 -> Triple(0.0, x, c)
            4 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        fun channel(v: Double) = ((v + m) * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /** [color] with the alpha replaced. */
    fun withAlpha(color: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (color and 0x00FFFFFF)

    private const val PURPLE_FROM = 240.0
    private const val PURPLE_TO = 320.0
    private const val BLUE = 225.0
    private const val PINK = 340.0
    private const val BUCKETS = 12
    private const val MIN_ALPHA = 128
    private const val MIN_VALUE = 0.18
    private const val MIN_SATURATION = 0.22
    private const val MIN_WEIGHT = 0.04
}
