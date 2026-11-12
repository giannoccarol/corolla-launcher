package io.github.giannoccarol.corollalauncher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPackFilterTest {

    private fun filter(vararg tags: Pair<String, Map<String, String>>): IconPackFilter {
        val builder = IconPackFilter.Builder()
        for ((name, attributes) in tags) builder.tag(name) { attributes[it] }
        return builder.build()
    }

    @Test
    fun parsesComponents() {
        assertEquals(
            "com.app" to "com.app.Main",
            IconPackFilter.parseComponent("ComponentInfo{com.app/com.app.Main}"),
        )
        assertEquals("com.app" to "com.app.ui.Home", IconPackFilter.parseComponent("ComponentInfo{com.app/.ui.Home}"))
        assertNull(IconPackFilter.parseComponent("ComponentInfo{com.app}"))
        assertNull(IconPackFilter.parseComponent("ComponentInfo{/Main}"))
        assertNull(IconPackFilter.parseComponent(":LAUNCHER_ACTION_APP_DRAWER"))
    }

    @Test
    fun mapsActivitiesAndFallsBackToThePackage() {
        val filter = filter(
            "item" to mapOf("component" to "ComponentInfo{com.spotify.music/com.spotify.music.MainActivity}", "drawable" to "spotify"),
            "item" to mapOf("component" to "ComponentInfo{com.waze/.FreeMapAppActivity}", "drawable" to "waze"),
            "item" to mapOf("component" to "broken", "drawable" to "nothing"),
        )
        assertEquals("spotify", filter.drawableFor("com.spotify.music", "com.spotify.music.MainActivity"))
        assertEquals("waze", filter.drawableFor("com.waze", "com.waze.FreeMapAppActivity"))
        // The app renamed its activity since the pack was made.
        assertEquals("spotify", filter.drawableFor("com.spotify.music", "com.spotify.music.NewMain"))
        assertNull(filter.drawableFor("com.other", "com.other.Main"))
    }

    @Test
    fun firstMappingWins() {
        val filter = filter(
            "item" to mapOf("component" to "ComponentInfo{com.a/com.a.Main}", "drawable" to "first"),
            "item" to mapOf("component" to "ComponentInfo{com.a/com.a.Main}", "drawable" to "second"),
        )
        assertEquals("first", filter.drawableFor("com.a", "com.a.Main"))
    }

    @Test
    fun readsTheFitInRules() {
        val filter = filter(
            "iconback" to mapOf("img1" to "back1", "img3" to "back3"),
            "iconmask" to mapOf("img1" to "mask"),
            "iconupon" to mapOf("img1" to "upon"),
            "scale" to mapOf("factor" to "0.8"),
        )
        assertTrue(filter.canFitUncovered)
        assertEquals(listOf("back1", "back3"), filter.backs)
        assertEquals("mask", filter.mask)
        assertEquals("upon", filter.upon)
        assertEquals(0.8f, filter.scale, 0.0001f)
        // The same app always gets the same background.
        assertEquals(filter.backFor("com.app"), filter.backFor("com.app"))
    }

    @Test
    fun withoutFitInRulesUncoveredAppsStayAsTheyAre() {
        val filter = filter("scale" to mapOf("factor" to "nonsense"))
        assertFalse(filter.canFitUncovered)
        assertNull(filter.backFor("com.app"))
        assertEquals(1f, filter.scale, 0.0001f)
    }
}
