package io.github.giannoccarol.corollalauncher.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppMatcherTest {

    private val labels = listOf("Spotify", "YouTube Music", "Music", "Radio FM", "Google Maps", "Waze", "Télépéage")

    @Test
    fun exactMatchIgnoresCaseAndSpaces() {
        assertEquals(4, AppMatcher.find(labels, "google maps"))
        assertEquals(3, AppMatcher.find(labels, "radiofm"))
    }

    @Test
    fun prefixMatch() {
        assertEquals(0, AppMatcher.find(labels, "spot"))
    }

    @Test
    fun shortestContainingLabelWins() {
        assertEquals(2, AppMatcher.find(labels, "music"))
    }

    @Test
    fun queryContainingALabel() {
        assertEquals(5, AppMatcher.find(labels, "apri waze per favore"))
    }

    @Test
    fun accentsAreIgnored() {
        assertEquals(6, AppMatcher.find(labels, "telepeage"))
    }

    @Test
    fun noMatchGivesNull() {
        assertNull(AppMatcher.find(labels, "Netflix"))
        assertNull(AppMatcher.find(labels, "  "))
    }
}
