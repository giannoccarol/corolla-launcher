package io.github.giannoccarol.corollalauncher.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.settings.Prefs

/**
 * Every animation in the app goes through here, so the rules live in one place:
 *
 * - only alpha, translation and scale are animated. Those are composited by the GPU and never
 *   trigger a layout or redraw of the view's content, which is what keeps them cheap on a weak
 *   head unit;
 * - nothing runs longer than about a quarter of a second or loops;
 * - everything is skipped when the user turned animations off, in the app or system-wide.
 */
object Motion {

    private const val PRESS_SCALE = 0.93f
    private const val PRESS_IN_MS = 80L
    private const val PRESS_OUT_MS = 180L
    private const val ENTER_MS = 260L
    private const val ENTER_STAGGER_MS = 50L
    private const val POP_STAGGER_MS = 30L
    private const val FADE_MS = 200L
    private const val ENTER_OFFSET_DP = 14f

    private val decelerate = DecelerateInterpolator(1.6f)
    private val overshoot = OvershootInterpolator(1.4f)

    fun enabled(context: Context): Boolean {
        if (!Prefs(context).animations) return false
        // Respects "Animator duration scale: off" in developer options.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }

    /** Makes the views shrink slightly while pressed. Does not consume the touch. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(vararg views: View) {
        for (view in views) {
            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> if (enabled(v.context)) {
                        v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
                            .setStartDelay(0).setDuration(PRESS_IN_MS)
                            .setInterpolator(decelerate).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f)
                            .setStartDelay(0).setDuration(PRESS_OUT_MS)
                            .setInterpolator(overshoot).start()
                }
                false
            }
        }
    }

    /** Large sections rise and fade in one after the other. */
    fun enter(vararg views: View) {
        if (views.isEmpty() || !enabled(views[0].context)) return
        val offset = views[0].resources.displayMetrics.density * ENTER_OFFSET_DP
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = offset
            view.animate().alpha(1f).translationY(0f)
                .setStartDelay(index * ENTER_STAGGER_MS).setDuration(ENTER_MS)
                .setInterpolator(decelerate).withLayer().start()
        }
    }

    /** Small items (dock icons) pop in one after the other. */
    fun popIn(views: List<View>) {
        if (views.isEmpty() || !enabled(views[0].context)) return
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.scaleX = 0.7f
            view.scaleY = 0.7f
            view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(index * POP_STAGGER_MS).setDuration(FADE_MS)
                .setInterpolator(overshoot).start()
        }
    }

    /** A single bounce, used when a button's meaning changes (play becomes pause). */
    fun pop(view: View) {
        if (!enabled(view.context)) return
        view.scaleX = 0.86f
        view.scaleY = 0.86f
        view.animate().scaleX(1f).scaleY(1f)
            .setStartDelay(0).setDuration(PRESS_OUT_MS).setInterpolator(overshoot).start()
    }

    /** Fades new content in, used when the track changes. */
    fun fadeIn(view: View) {
        if (!enabled(view.context)) return
        view.alpha = 0f
        view.animate().alpha(1f)
            .setStartDelay(0).setDuration(FADE_MS).setInterpolator(decelerate).withLayer().start()
    }

    /** Opens a screen of this app with the shared rise-and-fade transition. */
    fun open(activity: Activity, intent: Intent) {
        activity.startActivity(intent)
        transition(activity, R.anim.screen_enter, R.anim.screen_hold)
    }

    /** Call right after `finish()` to play the matching closing transition. */
    fun close(activity: Activity) {
        transition(activity, R.anim.screen_hold, R.anim.screen_exit)
    }

    private fun transition(activity: Activity, enterAnim: Int, exitAnim: Int) {
        val animate = enabled(activity)
        // Deprecated in API 34 in favour of overrideActivityTransition, which does not exist on
        // the old Android versions head units run. This call works on every version.
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(if (animate) enterAnim else 0, if (animate) exitAnim else 0)
    }
}
