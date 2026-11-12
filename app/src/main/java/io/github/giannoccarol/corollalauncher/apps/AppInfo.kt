package io.github.giannoccarol.corollalauncher.apps

import android.content.ComponentName
import android.graphics.drawable.Drawable

/**
 * One launchable activity and its label. [icon] is the finished icon, or, when [themed], an
 * alpha mask that [AppIcons.bind] draws on a tile in the current theme's colours.
 */
data class AppInfo(
    val component: ComponentName,
    val label: String,
    val icon: Drawable,
    val themed: Boolean = false,
) {
    /** Stable identifier used for pins and launch counts. */
    val key: String get() = component.flattenToString()
}
