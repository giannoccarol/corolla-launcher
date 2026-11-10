package io.github.giannoccarol.corollalauncher.assistant

import java.text.Normalizer
import java.util.Locale

/**
 * Finds the app the driver named, among the installed apps' labels. Spoken names are loose
 * ("apri spotify", "YouTube music", "la radio"), so matching ignores case, accents and
 * punctuation and falls back from exact to prefix to containment. Pure logic, unit tested.
 */
object AppMatcher {

    /** Index into [labels] of the best match for [query], or null. */
    fun find(labels: List<String>, query: String): Int? {
        val wanted = normalize(query)
        if (wanted.isEmpty()) return null
        val names = labels.map(::normalize)
        names.indexOfFirst { it == wanted }.takeIf { it >= 0 }?.let { return it }
        names.indexOfFirst { it.isNotEmpty() && it.startsWith(wanted) }.takeIf { it >= 0 }?.let { return it }
        // Shortest label containing the query: "music" picks "Music" over "YouTube Music".
        names.withIndex()
            .filter { (_, name) -> name.isNotEmpty() && name.contains(wanted) }
            .minByOrNull { it.value.length }
            ?.let { return it.index }
        // The query contains a whole label: "open the spotify app" finds "Spotify".
        return names.withIndex()
            .filter { (_, name) -> name.length >= MIN_LABEL && wanted.contains(name) }
            .maxByOrNull { it.value.length }
            ?.index
    }

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "")

    private const val MIN_LABEL = 3
}
