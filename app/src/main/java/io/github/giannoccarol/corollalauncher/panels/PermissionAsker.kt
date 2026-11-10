package io.github.giannoccarol.corollalauncher.panels

/**
 * Runtime permissions are requested through launchers registered by the activity, so panels
 * ask the activity. Each request comes from a tap in the panel, never on its own.
 */
interface PermissionAsker {
    fun askForLocation()
    fun askForNearbyDevices()
}
