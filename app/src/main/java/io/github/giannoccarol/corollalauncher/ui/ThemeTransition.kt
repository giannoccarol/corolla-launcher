package io.github.giannoccarol.corollalauncher.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.ImageView
import android.view.animation.PathInterpolator

/**
 * Crossfades a theme change instead of letting the screen jump: just before the theme is
 * applied, the current screen is drawn into a bitmap; the recreated activity starts with that
 * picture on top and fades it away, so dark melts into light.
 *
 * One screen-sized bitmap lives for well under a second.
 */
object ThemeTransition {

    private const val FADE_MS = 420L
    private const val MAX_AGE_MS = 2_000L

    private var snapshot: Bitmap? = null
    private var takenAt = 0L

    /** Call right before applying a theme that will recreate [activity]. */
    fun capture(activity: Activity) {
        if (!Motion.enabled(activity)) return
        val root = activity.window.decorView
        if (root.width == 0 || root.height == 0) return
        clear()
        snapshot = try {
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        } catch (e: OutOfMemoryError) {
            null
        }
        takenAt = SystemClock.uptimeMillis()
    }

    /** Call once the recreated activity has its content. Does nothing without a fresh snapshot. */
    fun reveal(activity: Activity) {
        val picture = snapshot ?: return
        snapshot = null
        if (SystemClock.uptimeMillis() - takenAt > MAX_AGE_MS) {
            picture.recycle()
            return
        }
        val decor = activity.window.decorView as ViewGroup
        val overlay = ImageView(activity).apply {
            setImageBitmap(picture)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        decor.addView(overlay, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        overlay.animate().alpha(0f).setStartDelay(START_DELAY_MS).setDuration(FADE_MS)
            .setInterpolator(PathInterpolator(0.4f, 0f, 0.2f, 1f)).withLayer()
            .withEndAction {
                decor.removeView(overlay)
                picture.recycle()
            }
            .start()
    }

    private fun clear() {
        snapshot?.recycle()
        snapshot = null
    }

    /** Lets the new screen lay out under the picture before the fade starts. */
    private const val START_DELAY_MS = 60L
}
