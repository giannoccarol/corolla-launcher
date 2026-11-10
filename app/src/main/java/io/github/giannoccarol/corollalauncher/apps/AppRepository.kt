package io.github.giannoccarol.corollalauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.settings.Prefs
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * The single source of truth for installed, launchable apps. Icons are loaded once on a
 * background thread and kept in memory; listeners are always called on the main thread.
 */
object AppRepository {

    const val DOCK_SIZE = 6

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    /** Launchable apps sorted by label. Empty until the first load finishes. */
    @Volatile
    var apps: List<AppInfo> = emptyList()
        private set

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun reload(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            val loaded = load(appContext)
            mainHandler.post {
                apps = loaded
                listeners.forEach { it() }
            }
        }
    }

    /** Starts [app] and records the launch. Returns false when the app could not be started. */
    fun launch(context: Context, app: AppInfo): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            Prefs(context).incrementLaunch(app.key)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** The apps currently shown in the dock; see [DockSelector] for the rules. */
    fun dockApps(context: Context): List<AppInfo> {
        val prefs = Prefs(context)
        val byKey = apps.associateBy { it.key }
        return DockSelector
            .select(apps.map { it.key }, prefs.pinnedApps, prefs.launchCounts(), DOCK_SIZE)
            .mapNotNull { byKey[it] }
    }

    private fun load(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.app_icon_bitmap)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                AppInfo(
                    component = ComponentName(info.activityInfo.packageName, info.activityInfo.name),
                    label = info.loadLabel(packageManager).toString(),
                    icon = IconShaper.shape(context, info.loadIcon(packageManager), iconSize),
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }
}
