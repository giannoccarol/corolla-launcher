package io.github.giannoccarol.corollalauncher.home

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

/**
 * The quick settings tiles. Old Android versions, which most head units run, let an app switch
 * Wi-Fi and Bluetooth directly; newer ones only allow opening the system panel, so each toggle
 * falls back to that.
 */
class QuickToggles(private val context: Context) {

    private val wifi =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun wifiEnabled(): Boolean = try {
        wifi?.isWifiEnabled == true
    } catch (e: SecurityException) {
        false
    }

    // Bluetooth state needs no runtime permission on any supported version.
    @SuppressLint("MissingPermission")
    fun bluetoothEnabled(): Boolean = try {
        adapter()?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    /** Flips Wi-Fi where allowed (Android 9 and below), else opens the Wi-Fi panel. */
    fun toggleWifi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && wifi != null) {
            try {
                @Suppress("DEPRECATION")
                wifi.isWifiEnabled = !wifi.isWifiEnabled
                return
            } catch (e: SecurityException) {
                // Fall through to the system screen.
            }
        }
        open(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI
            else Settings.ACTION_WIFI_SETTINGS,
            Settings.ACTION_WIFI_SETTINGS,
        )
    }

    /**
     * Flips Bluetooth where allowed (Android 11 and below, install-time BLUETOOTH_ADMIN), else
     * opens the Bluetooth settings.
     */
    @SuppressLint("MissingPermission")
    fun toggleBluetooth() {
        val adapter = adapter()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && adapter != null) {
            try {
                @Suppress("DEPRECATION")
                if (adapter.isEnabled) adapter.disable() else adapter.enable()
                return
            } catch (e: SecurityException) {
                // Fall through to the system screen.
            }
        }
        open(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    fun openDisplaySettings() = open(Settings.ACTION_DISPLAY_SETTINGS)

    /** The system or head unit equalizer when one is registered, else the sound settings. */
    fun openAudio() {
        val equalizer = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!start(equalizer)) open(Settings.ACTION_SOUND_SETTINGS)
    }

    /** Returns false when the device has none of the given screens. */
    private fun open(vararg actions: String): Boolean =
        actions.any { start(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun adapter() =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
}
