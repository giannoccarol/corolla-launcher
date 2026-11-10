package io.github.giannoccarol.corollalauncher.nav

import java.util.Locale

/** Keeps the list of recent destinations tidy. Pure logic, unit tested. */
object RecentDestinations {

    const val MAX = 5

    /**
     * Puts [destination] first, drops an earlier entry that differs only in case or spacing,
     * and keeps at most [max] entries. Blank input leaves the list unchanged.
     */
    fun push(list: List<String>, destination: String, max: Int = MAX): List<String> {
        val cleaned = destination.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return list
        val key = cleaned.lowercase(Locale.ROOT)
        return (listOf(cleaned) + list.filter { it.lowercase(Locale.ROOT) != key }).take(max)
    }
}
