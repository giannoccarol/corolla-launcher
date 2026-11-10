package io.github.giannoccarol.corollalauncher.trip

/** Turns a GPS bearing into one of eight compass points. Pure logic, unit tested. */
object Compass {

    /** Index into N, NE, E, SE, S, SW, W, NW for a bearing in degrees (any range). */
    fun pointIndex(bearingDegrees: Float): Int {
        val normalized = ((bearingDegrees % 360f) + 360f) % 360f
        return ((normalized + 22.5f) / 45f).toInt() % 8
    }
}
