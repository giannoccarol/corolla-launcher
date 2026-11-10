package io.github.giannoccarol.corollalauncher.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeManagerTest {

    @Test
    fun nightCoversEveningAndEarlyMorning() {
        assertTrue(ThemeManager.isNightHour(18))
        assertTrue(ThemeManager.isNightHour(23))
        assertTrue(ThemeManager.isNightHour(0))
        assertTrue(ThemeManager.isNightHour(6))
    }

    @Test
    fun dayStartsAtSevenAndEndsBeforeSix() {
        assertFalse(ThemeManager.isNightHour(7))
        assertFalse(ThemeManager.isNightHour(12))
        assertFalse(ThemeManager.isNightHour(17))
    }

    @Test
    fun unknownIdFallsBackToDark() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromId(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromId("nonsense"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromId("light"))
    }
}
