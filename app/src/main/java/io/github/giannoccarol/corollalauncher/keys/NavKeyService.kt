package io.github.giannoccarol.corollalauncher.keys

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Lets the dock's Back, Home and Recents buttons act like the system navigation keys, which
 * are hidden while the launcher is full screen.
 *
 * Android only lets an accessibility service trigger those actions, so this is one, and it is
 * as small as a service can be: it subscribes to no events, cannot read window content, and
 * does nothing except [performGlobalAction] on request. The user has to enable it in the
 * system accessibility settings; until then [perform] returns false.
 */
class NavKeyService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        // Set while the system has the service bound, cleared in onUnbind/onDestroy.
        @Volatile
        private var instance: NavKeyService? = null

        val isEnabled: Boolean get() = instance != null

        /** Runs one of the `GLOBAL_ACTION_*` constants. False when the service is not enabled. */
        fun perform(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
    }
}
