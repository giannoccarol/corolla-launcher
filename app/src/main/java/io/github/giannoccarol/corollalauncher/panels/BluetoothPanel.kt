package io.github.giannoccarol.corollalauncher.panels

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.home.QuickToggles

/**
 * Bluetooth switch and the paired phones. Pairing and connecting stay with the system (or the
 * head unit's own Bluetooth app): Android gives third-party apps no public way to do either.
 */
class BluetoothPanel(
    activity: Activity,
    private val asker: PermissionAsker,
) : Sheet(activity, R.string.qs_bluetooth) {

    private val adapter =
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val toggles = QuickToggles(activity)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = render()
    }

    override fun build() {
        val enabled = toggles.bluetoothEnabled()
        switchRow(
            R.string.qs_bluetooth,
            activity.getString(if (enabled) R.string.state_on else R.string.state_off),
            enabled,
        ) {
            toggles.toggleBluetooth()
            render()
        }
        if (enabled && adapter != null) {
            label(R.string.bt_paired)
            if (!canListDevices()) {
                row(
                    activity.getString(R.string.bt_permission_needed),
                    icon = R.drawable.ic_bluetooth,
                    trailing = R.drawable.ic_chevron_right,
                ) { asker.askForNearbyDevices() }
            } else {
                val devices = pairedDevices()
                if (devices.isEmpty()) row(activity.getString(R.string.bt_none_paired), onClick = null)
                for ((name, kind) in devices) {
                    row(name, kind, icon = R.drawable.ic_bluetooth) { toggles.openBluetoothSettings() }
                }
            }
        }
        button(R.string.bt_system_settings) { toggles.openBluetoothSettings() }
    }

    override fun onShown() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        // Exported: these come from the Bluetooth process. Both actions are protected.
        ContextCompat.registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onDismissed() {
        activity.unregisterReceiver(receiver)
    }

    private fun canListDevices(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Name and a short description ("Phone", "Audio") of each bonded device. */
    @SuppressLint("MissingPermission") // Checked in canListDevices().
    private fun pairedDevices(): List<Pair<String, String?>> = try {
        adapter?.bondedDevices.orEmpty()
            .map { device ->
                val name = device.name?.takeIf { it.isNotBlank() } ?: device.address
                name to kindOf(device)
            }
            .sortedBy { it.first.lowercase() }
    } catch (e: SecurityException) {
        emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun kindOf(device: BluetoothDevice): String? = when (device.bluetoothClass?.majorDeviceClass) {
        android.bluetooth.BluetoothClass.Device.Major.PHONE -> activity.getString(R.string.bt_kind_phone)
        android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> activity.getString(R.string.bt_kind_audio)
        else -> null
    }
}
