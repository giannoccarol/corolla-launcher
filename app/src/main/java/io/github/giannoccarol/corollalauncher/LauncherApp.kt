package io.github.giannoccarol.corollalauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.apps.AppRepository
import io.github.giannoccarol.corollalauncher.theme.ThemeManager

/**
 * Process-wide setup: applies the saved theme before any activity is created and keeps the
 * app list fresh for as long as the launcher process lives.
 */
class LauncherApp : Application() {

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppRepository.reload(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        AppRepository.reload(this)
    }
}
