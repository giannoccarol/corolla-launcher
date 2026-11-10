package io.github.giannoccarol.corollalauncher.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class DockSelectorTest {

    private val keys = listOf(
        "com.android.calculator/.Main",
        "com.android.settings/.Main",
        "com.google.android.apps.maps/.Main",
        "com.headunit.radio/.Main",
        "com.spotify.music/.Main",
        "org.example.notes/.Main",
    )

    @Test
    fun pinnedAppsComeFirstInPinOrder() {
        val pinned = listOf("org.example.notes/.Main", "com.android.settings/.Main")
        val result = DockSelector.select(keys, pinned, emptyMap(), size = 3)
        assertEquals(pinned, result.take(2))
    }

    @Test
    fun uninstalledPinsAreIgnored() {
        val result = DockSelector.select(keys, listOf("gone.app/.Main"), emptyMap(), size = 6)
        assertEquals(keys.toSet(), result.toSet())
    }

    @Test
    fun mostLaunchedAppsFollowPins() {
        val counts = mapOf(
            "com.android.calculator/.Main" to 2,
            "org.example.notes/.Main" to 9,
        )
        val result = DockSelector.select(
            keys, listOf("com.android.settings/.Main"), counts, size = 3,
        )
        assertEquals(
            listOf(
                "com.android.settings/.Main",
                "org.example.notes/.Main",
                "com.android.calculator/.Main",
            ),
            result,
        )
    }

    @Test
    fun freshInstallPrefersCarEssentials() {
        val result = DockSelector.select(keys, emptyList(), emptyMap(), size = 3)
        assertEquals(
            listOf(
                "com.google.android.apps.maps/.Main",
                "com.spotify.music/.Main",
                "com.headunit.radio/.Main",
            ),
            result,
        )
    }

    @Test
    fun neverReturnsMoreThanSizeOrDuplicates() {
        val counts = keys.associateWith { 1 }
        val result = DockSelector.select(keys, keys, counts, size = 4)
        assertEquals(4, result.size)
        assertEquals(result.distinct(), result)
    }

    @Test
    fun zeroSizeGivesEmptyDock() {
        assertEquals(emptyList<String>(), DockSelector.select(keys, keys, emptyMap(), size = 0))
    }
}
