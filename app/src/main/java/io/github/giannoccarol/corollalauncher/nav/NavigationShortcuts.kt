package io.github.giannoccarol.corollalauncher.nav

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Hands destinations to whatever navigation app is installed. */
object NavigationShortcuts {

    /** Opens the default maps app on a search for [query]. Works with any app that handles `geo:`. */
    fun search(context: Context, query: String): Boolean =
        start(context, Uri.parse("geo:0,0?q=" + Uri.encode(query)))

    /**
     * Starts turn-by-turn navigation to [address] when Google Maps is installed, and falls back
     * to a plain search in any other maps app.
     */
    fun navigateTo(context: Context, address: String): Boolean =
        start(context, Uri.parse("google.navigation:q=" + Uri.encode(address))) ||
            search(context, address)

    private fun start(context: Context, uri: Uri): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
