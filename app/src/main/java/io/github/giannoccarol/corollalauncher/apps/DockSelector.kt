package io.github.giannoccarol.corollalauncher.apps

/**
 * Decides which apps fill the dock. Pure logic, no Android types, so it is unit tested.
 *
 * Order of precedence:
 * 1. apps the user pinned, in pin order;
 * 2. the most launched apps;
 * 3. on a fresh install, apps that look like car essentials (maps, music, radio, phone);
 * 4. whatever is left, in the order given.
 */
object DockSelector {

    /** Substrings matched against the component name to seed the dock before any usage exists. */
    val DEFAULT_HINTS = listOf(
        "maps", "waze", "spotify", "music", "radio", "dialer", "phone", "bluetooth", "youtube",
    )

    /**
     * @param keys every available app key, already in display order
     * @param pinned keys pinned by the user; ones no longer installed are ignored
     * @param counts launch count per key
     * @param size number of dock slots
     */
    fun select(
        keys: List<String>,
        pinned: List<String>,
        counts: Map<String, Int>,
        size: Int,
    ): List<String> {
        if (size <= 0) return emptyList()
        val available = keys.toSet()
        val result = LinkedHashSet<String>()

        fun add(key: String) {
            if (result.size < size) result += key
        }

        pinned.filter { it in available }.forEach(::add)
        keys.filter { (counts[it] ?: 0) > 0 }
            .sortedByDescending { counts[it] ?: 0 }
            .forEach(::add)
        for (hint in DEFAULT_HINTS) {
            keys.firstOrNull { it !in result && it.lowercase().contains(hint) }?.let(::add)
        }
        keys.forEach(::add)
        return result.toList()
    }
}
