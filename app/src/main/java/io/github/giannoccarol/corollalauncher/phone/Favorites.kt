package io.github.giannoccarol.corollalauncher.phone

import java.util.Locale

/** A contact on the home screen's Favourites card: one tap calls [number]. */
data class Favorite(val name: String, val number: String)

/**
 * Storage format and display helpers for the Favourites card. The card has [SLOTS] fixed
 * places, so a favourite stays where the driver put it. Pure logic, unit tested.
 */
object Favorites {

    const val SLOTS = 4

    /** One line for the name, one for the number. Null clears the slot. */
    fun encode(favorite: Favorite?): String? =
        favorite?.let { "${it.name.replace('\n', ' ').trim()}\n${it.number.trim()}" }

    fun decode(value: String?): Favorite? {
        val parts = value?.split('\n') ?: return null
        if (parts.size != 2) return null
        val name = parts[0].trim()
        val number = dialable(parts[1])
        return if (name.isEmpty() || number.isEmpty()) null else Favorite(name, number)
    }

    /** What a dialer accepts: digits, a leading +, and * # for service codes. */
    fun dialable(number: String): String {
        val kept = number.trim().filter { it.isDigit() || it == '*' || it == '#' || it == '+' }
        // A + only makes sense at the start.
        return if (kept.startsWith("+")) "+" + kept.drop(1).replace("+", "") else kept.replace("+", "")
    }

    /** "Mario Rossi" gives "MR", "Mamma" gives "M", "+39 333..." gives "#". */
    fun initials(name: String): String {
        val words = name.split(Regex("\\s+")).filter { word -> word.any { it.isLetter() } }
        if (words.isEmpty()) return "#"
        val letters = words.take(2).map { word -> word.first { it.isLetter() } }
        return letters.joinToString("").uppercase(Locale.getDefault())
    }

    /** A stable hue per name, so each person keeps the colour of their avatar. */
    fun hue(name: String): Float =
        Math.floorMod(name.trim().lowercase(Locale.ROOT).hashCode(), 360).toFloat()
}
