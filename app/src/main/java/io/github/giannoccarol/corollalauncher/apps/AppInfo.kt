package io.github.giannoccarol.corollalauncher.apps

import android.content.ComponentName
import android.graphics.drawable.Drawable

/** One launchable activity, with the real icon and label the system reports for it. */
data class AppInfo(
    val component: ComponentName,
    val label: String,
    val icon: Drawable,
) {
    /** Stable identifier used for pins and launch counts. */
    val key: String get() = component.flattenToString()
}
