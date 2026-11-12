package io.github.giannoccarol.corollalauncher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconGlyphsTest {

    @Test
    fun wellKnownAppsGetTheirLogo() {
        assertEquals("spotify", IconGlyphs.match("com.spotify.music", "Spotify"))
        assertEquals("googlemaps", IconGlyphs.match("com.google.android.apps.maps", "Maps"))
        assertEquals("waze", IconGlyphs.match("com.waze", "Waze"))
    }

    @Test
    fun brandNamesInLabelsCatchVariants() {
        assertEquals("youtubemusic", IconGlyphs.match("com.vendor.ytm", "YouTube Music"))
        assertEquals("youtube", IconGlyphs.match("com.vendor.yt", "YouTube Vanced"))
        assertEquals("androidauto", IconGlyphs.match("com.vendor.player", "Android Auto"))
    }

    @Test
    fun headUnitAppsAreRecognisedByLabelInEveryLanguage() {
        assertEquals("radio", IconGlyphs.match("com.syu.radio2", "Radio"))
        assertEquals("music", IconGlyphs.match("com.syu.ms", "Musica"))
        assertEquals("settings", IconGlyphs.match("com.syu.cs", "Einstellungen"))
        assertEquals("gallery", IconGlyphs.match("com.syu.gallery", "Galería"))
        assertEquals("equalizer", IconGlyphs.match("com.nwd.eq", "Égaliseur"))
        assertEquals("bluetooth", IconGlyphs.match("com.syu.bt", "Bluetooth"))
    }

    @Test
    fun earlierRulesWin() {
        assertEquals("phone", IconGlyphs.match("com.syu.bt", "BT Phone"))
        assertEquals("music", IconGlyphs.match("com.syu.btav", "Bluetooth Music"))
        assertEquals("car", IconGlyphs.match("com.syu.carinfo", "Car settings"))
        assertEquals("video", IconGlyphs.match("com.vendor.videoplayer", "Player"))
        assertEquals("assistant", IconGlyphs.match("com.android.soundrecorder", "Sound Recorder"))
    }

    @Test
    fun packageNamesAreTheFallback() {
        assertEquals("clock", IconGlyphs.match("com.android.deskclock", "我的应用"))
        assertEquals("search", IconGlyphs.match("com.android.quicksearchbox", "Search"))
        assertEquals("bluetooth", IconGlyphs.match("com.syu.bt", ""))
    }

    @Test
    fun shortPackageHintsMustBeAWholeSegment() {
        // "car" inside "carousel" and "tv" inside "tvshowtime" are not hints.
        assertNull(IconGlyphs.match("com.vendor.carousel", "Carousel"))
        assertEquals("car", IconGlyphs.match("com.vendor.car", "Something"))
    }

    @Test
    fun projectionReceiversAreCars() {
        assertEquals("car", IconGlyphs.match("com.zjinnova.zlink", "ZLink"))
    }

    @Test
    fun unknownAppsGetNothing() {
        assertNull(IconGlyphs.match("com.example.notes", "Notes"))
    }

    @Test
    fun everyGlyphHasADrawable() {
        val missing = IconGlyphs.ALL - GlyphResources.names
        assertTrue("No drawable for $missing", missing.isEmpty())
        for (glyph in IconGlyphs.ALL) assertNotNull(GlyphResources.drawable(glyph))
    }
}
