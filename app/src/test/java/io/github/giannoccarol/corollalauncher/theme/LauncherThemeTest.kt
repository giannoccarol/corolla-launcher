package io.github.giannoccarol.corollalauncher.theme

import io.github.giannoccarol.corollalauncher.R
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherThemeTest {

    @Test
    fun automaticFontFollowsTheTheme() {
        assertEquals(FontChoice.SYSTEM, LauncherTheme.resolveFont(ThemeStyle.CLASSIC, FontChoice.AUTO))
        assertEquals(FontChoice.MANROPE, LauncherTheme.resolveFont(ThemeStyle.AMBRA, FontChoice.AUTO))
    }

    @Test
    fun aChosenFontWinsOverTheTheme() {
        assertEquals(FontChoice.NUNITO, LauncherTheme.resolveFont(ThemeStyle.AMBRA, FontChoice.NUNITO))
        assertEquals(FontChoice.MANROPE, LauncherTheme.resolveFont(ThemeStyle.CLASSIC, FontChoice.MANROPE))
        assertEquals(FontChoice.SYSTEM, LauncherTheme.resolveFont(ThemeStyle.AMBRA, FontChoice.SYSTEM))
    }

    @Test
    fun eachFontHasItsOverlay() {
        assertEquals(R.style.ThemeOverlay_Launcher_Font_System, LauncherTheme.fontOverlay(FontChoice.SYSTEM))
        assertEquals(R.style.ThemeOverlay_Launcher_Font_Manrope, LauncherTheme.fontOverlay(FontChoice.MANROPE))
        assertEquals(R.style.ThemeOverlay_Launcher_Font_Nunito, LauncherTheme.fontOverlay(FontChoice.NUNITO))
    }

    @Test
    fun unknownIdsFallBack() {
        assertEquals(ThemeStyle.CLASSIC, ThemeStyle.fromId(null))
        assertEquals(ThemeStyle.CLASSIC, ThemeStyle.fromId("neon"))
        assertEquals(ThemeStyle.AMBRA, ThemeStyle.fromId("ambra"))
        assertEquals(FontChoice.AUTO, FontChoice.fromId("comic"))
    }
}
