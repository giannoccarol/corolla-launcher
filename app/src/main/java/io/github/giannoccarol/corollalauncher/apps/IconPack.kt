package io.github.giannoccarol.corollalauncher.apps

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser

/**
 * An icon pack installed from the Play Store, in the appfilter.xml format most launchers read.
 * Loaded on the app repository's background thread.
 */
class IconPack private constructor(
    val packageName: String,
    private val resources: Resources,
    val filter: IconPackFilter,
) {

    /** The pack's own drawable for [component], or null when the pack does not cover it. */
    fun iconFor(component: ComponentName): Drawable? =
        filter.drawableFor(component.packageName, component.className)?.let(::drawable)

    @SuppressLint("DiscouragedApi") // Icon packs name their drawables in XML: lookup by name is the format.
    fun drawable(name: String): Drawable? {
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return try {
            ResourcesCompat.getDrawable(resources, id, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    data class Installed(val packageName: String, val label: String)

    companion object {
        private const val TAG = "IconPack"

        /** Intent actions icon packs declare so launchers can find them. */
        private val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
        )

        fun installed(context: Context): List<Installed> {
            val pm = context.packageManager
            return ACTIONS
                .flatMap { action -> pm.queryIntentActivities(Intent(action), 0) }
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != context.packageName }
                .map { Installed(it.packageName, it.loadLabel(pm).toString()) }
                .sortedBy { it.label.lowercase() }
        }

        /** Null when the pack is gone or its appfilter.xml cannot be read. */
        fun load(context: Context, packageName: String): IconPack? = try {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            readFilter(resources, packageName)?.let { IconPack(packageName, resources, it) }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: Exception) {
            // A broken pack must never take the home screen down with it.
            Log.w(TAG, "Unreadable icon pack $packageName", e)
            null
        }

        @SuppressLint("DiscouragedApi")
        private fun readFilter(resources: Resources, packageName: String): IconPackFilter? {
            val builder = IconPackFilter.Builder()
            val id = resources.getIdentifier("appfilter", "xml", packageName)
            if (id != 0) {
                resources.getXml(id).use { parse(it, builder) }
            } else {
                // Packs that ship appfilter.xml as a raw asset instead of a compiled resource.
                val stream = try {
                    resources.assets.open("appfilter.xml")
                } catch (e: java.io.IOException) {
                    return null
                }
                stream.use {
                    val parser = Xml.newPullParser()
                    parser.setInput(it, null)
                    parse(parser, builder)
                }
            }
            return builder.build()
        }

        private fun parse(parser: XmlPullParser, builder: IconPackFilter.Builder) {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    builder.tag(parser.name) { parser.getAttributeValue(null, it) }
                }
                event = parser.next()
            }
        }
    }
}
