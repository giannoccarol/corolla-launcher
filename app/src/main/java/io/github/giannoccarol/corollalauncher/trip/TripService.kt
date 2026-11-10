package io.github.giannoccarol.corollalauncher.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.home.HomeActivity

/**
 * Keeps GPS updates flowing while another app (usually the navigator) is in front. Android
 * throttles location for background apps to a few fixes per hour, which would make the trip
 * computer useless; a foreground service is the supported way around that. Its notification
 * is silent and low priority.
 */
class TripService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!TripTracker.hasPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                },
            )
        } catch (e: RuntimeException) {
            // Refused by the system (for example started from the background on Android 12+).
            stopSelf()
            return START_NOT_STICKY
        }
        TripTracker.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        TripTracker.stop()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_LOW: shown without sound or vibration.
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.trip_channel_name), NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
        }
        val openHome = PendingIntent.getActivity(
            this, 0, Intent(this, HomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gps)
            .setContentTitle(getString(R.string.trip_notification_title))
            .setContentText(getString(R.string.trip_notification_text))
            .setContentIntent(openHome)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "trip"
        private const val NOTIFICATION_ID = 1

        /** Starts the service when location is allowed. Call from a visible activity. */
        fun start(context: Context) {
            if (!TripTracker.hasPermission(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, TripService::class.java))
            } catch (e: RuntimeException) {
                // Not allowed right now; the next visit to the home screen tries again.
            }
        }
    }
}
