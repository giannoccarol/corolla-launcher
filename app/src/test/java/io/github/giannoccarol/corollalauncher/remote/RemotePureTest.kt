package io.github.giannoccarol.corollalauncher.remote

import io.github.giannoccarol.corollalauncher.phone.Favorite
import io.github.giannoccarol.corollalauncher.theme.FontChoice
import io.github.giannoccarol.corollalauncher.theme.ThemeMode
import io.github.giannoccarol.corollalauncher.theme.ThemeStyle
import io.github.giannoccarol.corollalauncher.ui.UiScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpParserTest {

    @Test
    fun parsesARequestHead() {
        val request = HttpParser.parseHead("GET /?t=abc&x=1 HTTP/1.1\r\nHost: 192.168.1.5\r\nContent-Length: 12\r\n\r\n")!!
        assertEquals("GET", request.method)
        assertEquals("/", request.path)
        assertEquals(mapOf("t" to "abc", "x" to "1"), request.query)
        assertEquals("192.168.1.5", request.headers["host"])
        assertEquals(12, request.contentLength)
    }

    @Test
    fun refusesWhatIsNotARequest() {
        assertNull(HttpParser.parseHead(""))
        assertNull(HttpParser.parseHead("hello"))
        assertNull(HttpParser.parseHead("DELETE / HTTP/1.1\r\n"))
        assertNull(HttpParser.parseHead("GET / SMTP\r\n"))
    }

    @Test
    fun decodesForms() {
        assertEquals(
            mapOf("name" to "Lorenzo Rossi", "home" to "Via Roma 1, Città", "empty" to ""),
            HttpParser.parseForm("name=Lorenzo+Rossi&home=Via%20Roma%201%2C%20Citt%C3%A0&empty="),
        )
        // A malformed escape drops that pair, not the whole form.
        assertEquals(mapOf("a" to "1"), HttpParser.parseForm("a=1&b=%zz"))
        assertEquals(mapOf("a" to "2"), HttpParser.parseForm("a=1&a=2"))
    }

    @Test
    fun escapesHtml() {
        assertEquals("&lt;b&gt; &amp; &quot;x&quot; &#39;y&#39;", HttpParser.escapeHtml("<b> & \"x\" 'y'"))
    }
}

class SetupTokenTest {

    @Test
    fun tokensAreLongAndDifferent() {
        val a = SetupToken.generate()
        val b = SetupToken.generate()
        assertEquals(32, a.length)
        assertTrue(a != b)
        assertTrue(a.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun matching() {
        assertTrue(SetupToken.matches("abc", "abc"))
        assertFalse(SetupToken.matches("abc", "abd"))
        assertFalse(SetupToken.matches("abc", null))
        assertFalse(SetupToken.matches("", ""))
    }
}

class LocalAddressTest {

    @Test
    fun prefersWifiThenHotspot() {
        val candidates = listOf(
            LocalAddress.Candidate("rmnet0", "10.64.1.2"),
            LocalAddress.Candidate("ap0", "192.168.43.1"),
            LocalAddress.Candidate("wlan0", "192.168.1.20"),
        )
        assertEquals("192.168.1.20", LocalAddress.choose(candidates))
        assertEquals("192.168.43.1", LocalAddress.choose(candidates.filter { it.interfaceName != "wlan0" }))
    }

    @Test
    fun onlyPrivateAddresses() {
        assertTrue(LocalAddress.usable("10.0.2.15"))
        assertTrue(LocalAddress.usable("172.20.10.3"))
        assertFalse(LocalAddress.usable("172.32.0.1"))
        assertFalse(LocalAddress.usable("8.8.8.8"))
        assertFalse(LocalAddress.usable("169.254.3.4"))
        assertFalse(LocalAddress.usable("not an address"))
        assertNull(LocalAddress.choose(listOf(LocalAddress.Candidate("wlan0", "8.8.8.8"))))
    }
}

class SetupFormTest {

    private val current = SetupValues(
        driverName = "Lorenzo", homeAddress = null, workAddress = "Via Uffici 2",
        favorites = listOf(Favorite("Mamma", "333"), null, null, null),
        themeStyle = ThemeStyle.CLASSIC, themeMode = ThemeMode.DARK, font = FontChoice.AUTO,
        uiSize = UiScale.UiSize.LARGE, iconStyle = "themed", providerId = "anthropic", model = null,
    )

    private fun parse(fields: Map<String, String>) =
        SetupForm.parse(fields, current, setOf("anthropic", "openai"), setOf("themed", "mono", "original"))

    @Test
    fun readsEveryField() {
        val result = parse(
            mapOf(
                "name" to "  Luca  ", "home" to "Via Roma 1", "work" to "",
                "fav0_name" to "Mamma", "fav0_number" to "+39 333 123", "fav1_name" to "Marco", "fav1_number" to "02 1234",
                "theme" to "ambra", "mode" to "light", "font" to "nunito", "size" to "extra_large",
                "icons" to "mono", "provider" to "openai", "model" to "gpt-5", "key" to " sk-123 ",
            ),
        )
        val v = result.values
        assertTrue(result.problems.isEmpty())
        assertEquals("Luca", v.driverName)
        assertEquals("Via Roma 1", v.homeAddress)
        assertNull(v.workAddress)
        assertEquals(Favorite("Mamma", "+39333123"), v.favorites[0])
        assertEquals(Favorite("Marco", "021234"), v.favorites[1])
        assertNull(v.favorites[2])
        assertEquals(ThemeStyle.AMBRA, v.themeStyle)
        assertEquals(ThemeMode.LIGHT, v.themeMode)
        assertEquals(FontChoice.NUNITO, v.font)
        assertEquals(UiScale.UiSize.EXTRA_LARGE, v.uiSize)
        assertEquals("mono", v.iconStyle)
        assertEquals("openai", v.providerId)
        assertEquals("gpt-5", v.model)
        assertEquals("sk-123", v.newApiKey)
        assertFalse(v.removeApiKey)
    }

    @Test
    fun invalidValuesKeepWhatIsSaved() {
        val result = parse(mapOf("theme" to "neon", "size" to "huge", "icons" to "com.evil", "provider" to "nobody"))
        assertEquals(ThemeStyle.CLASSIC, result.values.themeStyle)
        assertEquals(UiScale.UiSize.LARGE, result.values.uiSize)
        assertEquals("themed", result.values.iconStyle)
        assertEquals("anthropic", result.values.providerId)
        // Fields that were not sent at all are left alone.
        assertEquals("Lorenzo", result.values.driverName)
        assertEquals(Favorite("Mamma", "333"), result.values.favorites[0])
        assertEquals("Via Uffici 2", result.values.workAddress)
    }

    @Test
    fun aBadFavouriteNumberIsReportedAndTheOldOneKept() {
        val result = parse(mapOf("fav0_name" to "Mamma", "fav0_number" to "call me"))
        assertEquals(SetupForm.Problem.FAVORITE_NUMBER, result.problems["fav0_number"])
        assertEquals(Favorite("Mamma", "333"), result.values.favorites[0])
    }

    @Test
    fun aTypedModelWinsOverTheList() {
        assertEquals("my-model", parse(mapOf("model" to "gpt-5", "model_custom" to " my-model ")).values.model)
        assertEquals("gpt-5", parse(mapOf("model" to "gpt-5", "model_custom" to "")).values.model)
        // The list's "Default" row is an empty value: back to the provider's default.
        assertNull(parse(mapOf("model" to "")).values.model)
    }

    @Test
    fun anEmptyKeyKeepsTheSavedOne() {
        assertNull(parse(mapOf("key" to "   ")).values.newApiKey)
        assertTrue(parse(mapOf("remove_key" to "on")).values.removeApiKey)
    }

    @Test
    fun textIsCappedAndSingleLine() {
        val result = parse(mapOf("name" to "a\nb" + "x".repeat(500)))
        assertEquals(200, result.values.driverName!!.length)
        assertFalse(result.values.driverName!!.contains('\n'))
    }
}
