package io.github.giannoccarol.corollalauncher.apps

import android.content.ComponentName
import android.graphics.drawable.Drawable

/**
 * One launchable activity and its label. [icon] is the finished icon, or, when [themed], an
 * alpha mask that [AppIcons.bind] draws on a tile in the current theme's colours: the app's
 * [hue] when [colored] (neutral grey when it has none), the accent otherwise.
 */
data class AppInfo(
    val component: ComponentName,
    val label: String,
    val icon: Drawable,
    val themed: Boolean = false,
    val colored: Boolean = false,
    val hue: Float? = null,
) {
    /** Stable identifier used for pins and launch counts. */
    val key: String get() = component.flattenToString()
}
