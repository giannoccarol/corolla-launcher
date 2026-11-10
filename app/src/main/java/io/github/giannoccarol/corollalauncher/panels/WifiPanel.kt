package io.github.giannoccarol.corollalauncher.panels

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.home.QuickToggles
import io.github.giannoccarol.corollalauncher.trip.TripTracker
import io.github.giannoccarol.corollalauncher.ui.TextPrompt

/**
 * Wi-Fi switch, current network and nearby networks. On Android 9 and below the panel switches
 * Wi-Fi and joins networks itself; newer versions reserve both to the system, so there the
 * panel shows the networks and hands over to the system Wi-Fi panel to join one.
 */
@Suppress("DEPRECATION") // The old Wi-Fi APIs are the ones that work on head unit Android versions.
class WifiPanel(
    activity: Activity,
    private val asker: PermissionAsker,
) : Sheet(activity, R.string.qs_wifi) {

    private val wifi = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val toggles = QuickToggles(activity)
    private val canManage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = render()
    }

    override fun build() {
        val enabled = wifi.isWifiEnabled
        val current = currentSsid()
        val summary = when {
            !enabled -> activity.getString(R.string.state_off)
            current != null -> activity.getString(R.string.wifi_connected_to, current)
            else -> activity.getString(R.string.wifi_not_connected)
        }
        switchRow(R.string.qs_wifi, summary, enabled) {
            toggles.toggleWifi()
            render()
        }
        if (!enabled) return

        label(R.string.wifi_networks)
        if (!TripTracker.hasPermission(activity)) {
            row(
                activity.getString(R.string.wifi_location_needed),
                activity.getString(R.string.wifi_location_needed_summary),
                icon = R.drawable.ic_gps,
                trailing = R.drawable.ic_chevron_right,
            ) { asker.askForLocation() }
        } else {
            val networks = nearbyNetworks()
            if (networks.isEmpty()) {
                row(activity.getString(R.string.wifi_searching), onClick = null)
            }
            for (network in networks) {
                val connected = network.SSID == current
                val secured = isSecured(network)
                row(
                    network.SSID,
                    activity.getString(
                        when {
                            connected -> R.string.wifi_connected
                            secured -> R.string.wifi_secured
                            else -> R.string.wifi_open
                        },
                    ),
                    icon = R.drawable.ic_wifi,
                    trailing = if (connected) R.drawable.ic_check else if (secured) R.drawable.ic_lock else null,
                ) { if (!connected) join(network, secured) }
            }
        }
        button(R.string.wifi_system_settings) { toggles.openWifiSettings() }
    }

    override fun onShown() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // Throttled by Android (a few scans per two minutes); cached results show meanwhile.
        if (TripTracker.hasPermission(activity)) {
            try {
                wifi.startScan()
            } catch (e: SecurityException) {
                // Some firmwares restrict scanning further; the cached results still show.
            }
        }
    }

    override fun onDismissed() {
        activity.unregisterReceiver(receiver)
    }

    /** One entry per network name, strongest signal first. */
    @SuppressLint("MissingPermission") // Location is checked by the caller.
    private fun nearbyNetworks(): List<ScanResult> = try {
        wifi.scanResults
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (_, sameName) -> sameName.maxBy { it.level } }
            .sortedByDescending { it.level }
            .take(MAX_NETWORKS)
    } catch (e: SecurityException) {
        emptyList()
    }

    private fun currentSsid(): String? {
        val ssid = wifi.connectionInfo?.ssid?.removeSurrounding("\"") ?: return null
        return ssid.takeUnless { it.isBlank() || it == UNKNOWN_SSID }
    }

    private fun isSecured(network: ScanResult): Boolean =
        SECURED_HINTS.any { network.capabilities.contains(it) }

    private fun join(network: ScanResult, secured: Boolean) {
        if (!canManage) {
            toggles.openWifiSettings()
            return
        }
        val saved = savedNetworkId(network.SSID)
        when {
            saved != null -> connect(saved, network.SSID)
            !secured -> addAndConnect(network.SSID, password = null)
            else -> TextPrompt.show(
                activity, activity.getString(R.string.wifi_password_title, network.SSID),
                R.string.wifi_password_hint, initial = null, positiveRes = R.string.wifi_connect,
                password = true,
            ) { password -> if (password.isNotEmpty()) addAndConnect(network.SSID, password) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun savedNetworkId(ssid: String): Int? = try {
        wifi.configuredNetworks?.firstOrNull { it.SSID == "\"$ssid\"" }?.networkId
    } catch (e: SecurityException) {
        null
    }

    private fun addAndConnect(ssid: String, password: String?) {
        val config = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            if (password == null) {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            } else {
                preSharedKey = "\"$password\""
            }
        }
        val id = wifi.addNetwork(config)
        if (id == -1) {
            Toast.makeText(activity, R.string.wifi_join_failed, Toast.LENGTH_LONG).show()
        } else {
            connect(id, ssid)
        }
    }

    private fun connect(networkId: Int, ssid: String) {
        wifi.disconnect()
        wifi.enableNetwork(networkId, true)
        wifi.reconnect()
        Toast.makeText(activity, activity.getString(R.string.wifi_connecting, ssid), Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val MAX_NETWORKS = 12
        const val UNKNOWN_SSID = "<unknown ssid>"
        val SECURED_HINTS = listOf("WEP", "WPA", "PSK", "SAE", "EAP")
    }
}
