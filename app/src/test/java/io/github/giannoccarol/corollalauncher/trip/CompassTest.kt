package io.github.giannoccarol.corollalauncher.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassTest {

    @Test
    fun cardinalAndIntercardinalPoints() {
        assertEquals(0, Compass.pointIndex(0f))
        assertEquals(1, Compass.pointIndex(45f))
        assertEquals(2, Compass.pointIndex(90f))
        assertEquals(4, Compass.pointIndex(180f))
        assertEquals(6, Compass.pointIndex(270f))
        assertEquals(7, Compass.pointIndex(315f))
    }

    @Test
    fun boundariesRoundToTheNearestPoint() {
        assertEquals(0, Compass.pointIndex(22.4f))
        assertEquals(1, Compass.pointIndex(22.5f))
        assertEquals(0, Compass.pointIndex(337.5f))
        assertEquals(7, Compass.pointIndex(337.4f))
    }

    @Test
    fun anyRangeIsNormalized() {
        assertEquals(0, Compass.pointIndex(360f))
        assertEquals(2, Compass.pointIndex(450f))
        assertEquals(6, Compass.pointIndex(-90f))
    }
}
