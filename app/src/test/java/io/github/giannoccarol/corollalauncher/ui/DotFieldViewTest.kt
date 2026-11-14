package io.github.giannoccarol.corollalauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DotFieldViewTest {

    @Test
    fun fullInTheMiddleGoneAtTheEdges() {
        assertEquals(1f, DotFieldView.falloff(0.5f, 0.5f), 0.0001f)
        assertEquals(0f, DotFieldView.falloff(0f, 0.5f), 0.0001f)
        assertEquals(0f, DotFieldView.falloff(1f, 1f), 0.0001f)
    }

    @Test
    fun fadesSmoothlyOutwards() {
        val near = DotFieldView.falloff(0.6f, 0.5f)
        val far = DotFieldView.falloff(0.8f, 0.5f)
        assertTrue(near > far)
        assertTrue(far > 0f)
    }
}
