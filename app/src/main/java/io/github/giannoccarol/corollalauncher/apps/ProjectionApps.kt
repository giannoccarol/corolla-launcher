package io.github.giannoccarol.corollalauncher.apps

import java.util.Locale

/**
 * Finds the head unit's Android Auto / CarPlay app among the installed ones. Aftermarket units
 * ship it under many names (Phone Link, ZLink, AutoLink, Carlinkit, EasyConnection...), so it is
 * recognised by package and label hints. Pure logic, unit tested.
 */
object ProjectionApps {

    data class Candidate(val packageName: String, val label: String)

    /** Fragments of package names used by head unit projection apps. */
    private val PACKAGE_HINTS = listOf(
        "zlink", "carlinkit", "autolink", "carplay", "phonelink", "easyconn", "carbit",
        "tlink", "androidauto", "projection", "mirror",
    )

    /** Words in the app's label, compared without spaces or case. */
    private val LABEL_HINTS = listOf(
        "androidauto", "carplay", "phonelink", "zlink", "autolink", "carlink", "easyconnection",
    )

    /**
     * Google's Android Auto *phone* app: on a head unit it shows the phone-screen mode, not the
     * projection receiver, so it never counts.
     */
    private const val PHONE_SIDE_ANDROID_AUTO = "com.google.android.projection.gearhead"

    /** Index of the best match in [apps], or null. Label matches beat package-only matches. */
    fun find(apps: List<Candidate>): Int? {
        var best: Int? = null
        var bestScore = 0
        apps.forEachIndexed { index, app ->
            if (app.packageName == PHONE_SIDE_ANDROID_AUTO) return@forEachIndexed
            val pkg = app.packageName.lowercase(Locale.ROOT)
            val label = app.label.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
            var score = 0
            if (LABEL_HINTS.any { label.contains(it) }) score += 2
            if (PACKAGE_HINTS.any { pkg.contains(it) }) score += 1
            // "mirror" and "projection" are vague: only with a matching label.
            if (score == 1 && (pkg.contains("mirror") || pkg.contains("projection"))) score = 0
            if (score > bestScore) {
                best = index
                bestScore = score
            }
        }
        return best
    }
}
