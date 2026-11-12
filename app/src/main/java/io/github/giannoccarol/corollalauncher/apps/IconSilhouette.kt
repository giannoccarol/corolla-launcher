package io.github.giannoccarol.corollalauncher.apps

/**
 * Decides whether the foreground layer of an adaptive icon, reduced to its alpha, is a logo
 * that reads as a themed glyph. A white chat bubble on a green background is; a full-bleed
 * picture or a big disc filling the tile is not, and gets a letter instead. Pure, unit tested.
 */
object IconSilhouette {

    private const val OPAQUE = 128
    private const val MIN_COVERAGE = 0.03f
    private const val MAX_COVERAGE = 0.45f

    /** Width of the border band, as a fraction of the size; logos stay clear of it. */
    private const val EDGE = 0.08f
    private const val MAX_EDGE_COVERAGE = 0.02f

    /** [alpha] holds size x size values, row by row, 0..255. */
    fun looksLikeLogo(alpha: IntArray, size: Int): Boolean {
        if (size <= 0 || alpha.size < size * size) return false
        val edge = (size * EDGE).toInt().coerceAtLeast(1)
        var opaque = 0
        var edgeOpaque = 0
        var edgeTotal = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val inEdge = x < edge || y < edge || x >= size - edge || y >= size - edge
                val on = alpha[y * size + x] > OPAQUE
                if (on) opaque++
                if (inEdge) {
                    edgeTotal++
                    if (on) edgeOpaque++
                }
            }
        }
        val coverage = opaque.toFloat() / (size * size)
        return coverage in MIN_COVERAGE..MAX_COVERAGE &&
            edgeOpaque.toFloat() / edgeTotal <= MAX_EDGE_COVERAGE
    }
}
