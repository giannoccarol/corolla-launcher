package io.github.giannoccarol.corollalauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.apps.AppRepository
import io.github.giannoccarol.corollalauncher.assistant.KeyVault
import io.github.giannoccarol.corollalauncher.assistant.ai.AiProviders
import io.github.giannoccarol.corollalauncher.settings.Prefs
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
        seedAssistantFromBuild()
    }

    /** A key from local.properties is stored once, encrypted, then the vault owns it. */
    private fun seedAssistantFromBuild() {
        val key = BuildConfig.ASSISTANT_API_KEY
        if (key.isBlank()) return
        val prefs = Prefs(this)
        val provider = AiProviders.byId(BuildConfig.ASSISTANT_PROVIDER.ifBlank { AiProviders.DEFAULT_ID })
        if (prefs.aiProviderId == null) prefs.aiProviderId = provider.id
        BuildConfig.ASSISTANT_MODEL.takeIf { it.isNotBlank() && prefs.aiModel(provider.id) == null }
            ?.let { prefs.setAiModel(provider.id, it) }
        KeyVault(this).seedFromBuild(provider.id, key)
    }
}
