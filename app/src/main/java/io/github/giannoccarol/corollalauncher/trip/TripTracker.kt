package io.github.giannoccarol.corollalauncher.trip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-wide GPS reader for the speed readout and the trip computer. [TripService] keeps the
 * process in the foreground so fixes keep arriving while a navigation app is on screen.
 * Listeners are called on the main thread.
 */
object TripTracker {

    private const val MIN_TIME_MS = 1_000L

    private val stats = TripStats()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private var manager: LocationManager? = null

    /** Bearing of the latest fix that had one, in degrees. */
    var bearing: Float? = null
        private set

    /** True once at least one fix arrived since tracking started. */
    var hasFix: Boolean = false
        private set

    val running: Boolean get() = manager != null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location)

        // Required on Android 9 and below, where these are abstract.
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = notifyListeners()
        override fun onProviderDisabled(provider: String) = notifyListeners()
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Starts GPS updates. Returns false without the permission or without a GPS receiver. */
    @SuppressLint("MissingPermission") // Checked just above the request.
    fun start(context: Context): Boolean {
        if (manager != null) return true
        if (!hasPermission(context)) return false
        val locationManager =
            context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, MIN_TIME_MS, 0f, locationListener,
                Looper.getMainLooper(),
            )
            manager = locationManager
            notifyListeners()
            true
        } catch (e: IllegalArgumentException) {
            false // No GPS provider on this device.
        } catch (e: SecurityException) {
            false // Permission revoked in the meantime.
        }
    }

    fun stop() {
        manager?.removeUpdates(locationListener)
        manager = null
        notifyListeners()
    }

    /** True while the GPS provider is switched on in the system settings. */
    fun gpsEnabled(context: Context): Boolean = try {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (e: RuntimeException) {
        false
    }

    /** Current speed in km/h, or null when there is no recent fix. */
    fun speedKmh(): Int? {
        if (!hasFix) return null
        val speed = stats.speedMps ?: return null
        return (speed * 3.6f).toInt()
    }

    fun isMoving(): Boolean = stats.isMoving(SystemClock.elapsedRealtime())

    fun hasTrip(): Boolean = stats.hasTrip

    fun distanceKm(): Double = stats.distanceM / 1_000.0

    fun durationMinutes(): Long = stats.durationMs / 60_000L

    fun averageKmh(): Int? = stats.averageKmh?.toInt()

    private fun onFix(location: Location) {
        val timeMs = location.elapsedRealtimeNanos / 1_000_000L
        stats.add(
            location.latitude, location.longitude, timeMs,
            if (location.hasSpeed()) location.speed else null,
            if (location.hasAccuracy()) location.accuracy else null,
        )
        if (location.hasBearing() && stats.isMoving(timeMs)) bearing = location.bearing
        hasFix = true
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }
}
