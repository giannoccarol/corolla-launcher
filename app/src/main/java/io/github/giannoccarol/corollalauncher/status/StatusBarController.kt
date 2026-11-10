package io.github.giannoccarol.corollalauncher.status

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.widget.ImageView
import android.widget.TextClock
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import java.util.Locale

/**
 * Drives the launcher's own status bar (`view_status_bar.xml`): time, date, and whether GPS,
 * Bluetooth and the network are on. It replaces the system status bar, which is hidden while
 * the launcher is in front.
 *
 * Icons stay in place and dim when the thing they stand for is off, so the bar never jumps.
 */
class StatusBarController(view: View) {

    private val context: Context = view.context
    private val gps: ImageView = view.findViewById(R.id.statusGps)
    private val bluetooth: ImageView = view.findViewById(R.id.statusBluetooth)
    private val network: ImageView = view.findViewById(R.id.statusNetwork)

    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val location = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = render()
    }

    /** Network callbacks arrive on a binder thread; rendering must happen on the main one. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = postRender()
        override fun onLost(network: Network) = postRender()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            postRender()
    }

    init {
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM")
        view.findViewById<TextClock>(R.id.statusDate).apply {
            format12Hour = datePattern
            format24Hour = datePattern
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        // Exported on purpose: the Bluetooth broadcast comes from the Bluetooth process, not
        // from the system uid, and both actions are protected so no other app can send them.
        ContextCompat.registerReceiver(
            context, radioReceiver, filter, ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback)
        } catch (e: RuntimeException) {
            // Too many callbacks or a stripped connectivity service: the icon just stays static.
        }
        render()
    }

    fun stop() {
        if (!started) return
        started = false
        context.unregisterReceiver(radioReceiver)
        handler.removeCallbacksAndMessages(null)
        try {
            connectivity.unregisterNetworkCallback(networkCallback)
        } catch (e: RuntimeException) {
            // It was never registered.
        }
    }

    private fun postRender() {
        handler.post { if (started) render() }
    }

    private fun render() {
        setOn(gps, gpsOn())
        setOn(bluetooth, bluetoothOn())

        val capabilities = activeCapabilities()
        val cellularOnly = capabilities != null &&
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        network.setImageResource(if (cellularOnly) R.drawable.ic_cell else R.drawable.ic_wifi)
        setOn(network, capabilities != null)
    }

    private fun setOn(icon: ImageView, on: Boolean) {
        icon.alpha = if (on) 1f else OFF_ALPHA
    }

    private fun gpsOn(): Boolean = try {
        location.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (e: RuntimeException) {
        false
    }

    // On Android 12+ reading the adapter state needs no permission; below that the manifest
    // declares the install-time BLUETOOTH permission.
    @SuppressLint("MissingPermission")
    private fun bluetoothOn(): Boolean = try {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    private fun activeCapabilities(): NetworkCapabilities? = try {
        connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    } catch (e: SecurityException) {
        null
    }

    private companion object {
        const val OFF_ALPHA = 0.28f
    }
}
