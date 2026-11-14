package io.github.giannoccarol.corollalauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {

    @Test
    fun aDesignSizedScreenKeepsItsDensity() {
        assertEquals(160, UiScale.densityFor(600, 600))
    }

    @Test
    fun fewerDpMeansEverythingLarger() {
        // 1024x600 laid out as 520 dp tall: 600 / 520 x 160.
        assertEquals(185, UiScale.densityFor(600, 520))
        // A 1280x720 panel shown at the same physical size as a 1024x600 one.
        assertEquals(192, UiScale.densityFor(720, 600))
        assertEquals(250, UiScale.densityFor(720, 460))
    }

    @Test
    fun neverBelowTheSmallestBucket() {
        assertEquals(120, UiScale.densityFor(300, 600))
    }

    @Test
    fun unknownSizesFallBackToTheDefault() {
        assertEquals(UiScale.UiSize.DEFAULT, UiScale.UiSize.fromId(null))
        assertEquals(UiScale.UiSize.DEFAULT, UiScale.UiSize.fromId("huge"))
        assertEquals(UiScale.UiSize.NORMAL, UiScale.UiSize.fromId("normal"))
    }
}
