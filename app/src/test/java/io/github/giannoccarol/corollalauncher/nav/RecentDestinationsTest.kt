package io.github.giannoccarol.corollalauncher.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentDestinationsTest {

    @Test
    fun newestComesFirst() {
        assertEquals(listOf("Bari", "Lecce"), RecentDestinations.push(listOf("Lecce"), "Bari"))
    }

    @Test
    fun repeatsMoveToTheFrontIgnoringCaseAndSpaces() {
        val result = RecentDestinations.push(listOf("Lecce", "Bari"), "  bari ")
        assertEquals(listOf("bari", "Lecce"), result)
    }

    @Test
    fun listIsCapped() {
        val result = RecentDestinations.push(listOf("a", "b", "c"), "d", max = 3)
        assertEquals(listOf("d", "a", "b"), result)
    }

    @Test
    fun blankInputChangesNothing() {
        assertEquals(listOf("Lecce"), RecentDestinations.push(listOf("Lecce"), "   "))
    }

    @Test
    fun innerSpacesAreCollapsed() {
        assertEquals(listOf("Via Roma 1"), RecentDestinations.push(emptyList(), "Via   Roma  1"))
    }
}
