package io.github.giannoccarol.corollalauncher.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoritesTest {

    @Test
    fun roundTrips() {
        val favorite = Favorite("Mario Rossi", "+39 333 123 4567")
        assertEquals(Favorite("Mario Rossi", "+393331234567"), Favorites.decode(Favorites.encode(favorite)))
    }

    @Test
    fun emptyOrBrokenSlotsAreEmpty() {
        assertNull(Favorites.encode(null))
        assertNull(Favorites.decode(null))
        assertNull(Favorites.decode("only a name"))
        assertNull(Favorites.decode("\n333"))
        assertNull(Favorites.decode("Name\nno digits"))
    }

    @Test
    fun namesStayOnOneLine() {
        assertEquals(Favorite("Ufficio Milano", "021234"), Favorites.decode(Favorites.encode(Favorite("Ufficio\nMilano", "021234"))))
    }

    @Test
    fun numbersAreCleanedForTheDialer() {
        assertEquals("+393331234567", Favorites.dialable(" +39 (333) 123-4567 "))
        assertEquals("0039333", Favorites.dialable("0039 333"))
        assertEquals("*21#", Favorites.dialable("*21#"))
        assertEquals("+3912", Favorites.dialable("+39+12"))
    }

    @Test
    fun initials() {
        assertEquals("MR", Favorites.initials("Mario Rossi"))
        assertEquals("M", Favorites.initials("mamma"))
        assertEquals("AB", Favorites.initials("Anna Bianchi Verdi"))
        assertEquals("#", Favorites.initials("+39 333"))
        assertEquals("É", Favorites.initials("Élise"))
    }

    @Test
    fun hueIsStableAndInRange() {
        assertEquals(Favorites.hue("Mario"), Favorites.hue(" mario "), 0f)
        for (name in listOf("a", "Mario Rossi", "Zoë", "")) {
            val hue = Favorites.hue(name)
            assert(hue >= 0f && hue < 360f)
        }
    }
}
