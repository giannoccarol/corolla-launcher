package io.github.giannoccarol.corollalauncher.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedGaugeTest {

    @Test
    fun barLightsInProportion() {
        assertEquals(Gauge.Lit(0, 0f), Gauge.litSegments(0f, 180f, 36))
        assertEquals(18, Gauge.litSegments(90f, 180f, 36).full)
        val partial = Gauge.litSegments(92.5f, 180f, 36)
        assertEquals(18, partial.full)
        assertEquals(0.5f, partial.partial, 0.001f)
        assertEquals(Gauge.Lit(36, 0f), Gauge.litSegments(250f, 180f, 36))
        assertEquals(Gauge.Lit(0, 0f), Gauge.litSegments(-5f, 180f, 36))
    }

    @Test
    fun segmentsFromTheLimitUpAreWarnings() {
        // 36 segments over 180 km/h: 5 km/h each, so 130 km/h starts at segment 26.
        assertFalse(Gauge.isWarning(25, 36, 180f, 130f))
        assertTrue(Gauge.isWarning(26, 36, 180f, 130f))
    }

    @Test
    fun segmentsRiseFromLowToHigh() {
        assertEquals(6f, Gauge.segmentHeight(0, 36, 6f, 20f), 0.001f)
        assertEquals(20f, Gauge.segmentHeight(35, 36, 6f, 20f), 0.001f)
        assertTrue(Gauge.segmentHeight(10, 36, 6f, 20f) < Gauge.segmentHeight(11, 36, 6f, 20f))
    }

    @Test
    fun digitsAreRightAlignedWithBlanks() {
        assertArrayEquals(
            intArrayOf(0, SevenSegment.mask(4), SevenSegment.mask(5)),
            SevenSegment.masksFor(45, 3),
        )
        assertArrayEquals(intArrayOf(0, 0, SevenSegment.mask(0)), SevenSegment.masksFor(0, 3))
        assertArrayEquals(
            intArrayOf(SevenSegment.mask(1), SevenSegment.mask(3), SevenSegment.mask(2)),
            SevenSegment.masksFor(132, 3),
        )
    }

    @Test
    fun noSpeedShowsDashesAndOverflowClamps() {
        assertArrayEquals(IntArray(3) { SevenSegment.DASH }, SevenSegment.masksFor(null, 3))
        assertArrayEquals(IntArray(3) { SevenSegment.mask(9) }, SevenSegment.masksFor(1200, 3))
    }

    @Test
    fun digitMasksUseTheRightSegments() {
        assertEquals(0b1111111, SevenSegment.mask(8))
        assertEquals(2, Integer.bitCount(SevenSegment.mask(1)))
        assertEquals(6, Integer.bitCount(SevenSegment.mask(0)))
        assertEquals(0, SevenSegment.mask(0) and SevenSegment.DASH)
    }
}
