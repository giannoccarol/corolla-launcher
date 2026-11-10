package io.github.giannoccarol.corollalauncher.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.settings.Prefs

/**
 * Every animation in the app goes through here, so the rules live in one place:
 *
 * - views move only through alpha, translation and scale, which the GPU composites without a
 *   layout pass or a redraw of their content; colour changes touch one drawable at a time;
 * - nothing runs longer than about a third of a second, and the only repeating motion is the
 *   assistant's breathing halo, which stops with the state that started it;
 * - everything is skipped when the user turned animations off, in the app or system-wide.
 *
 * Curves follow Material motion: things that arrive decelerate ([enterEase]), things that react
 * to a finger respond at once and settle ([standardEase]).
 */
object Motion {

    private const val PRESS_SCALE = 0.95f
    private const val PRESS_IN_MS = 90L
    private const val PRESS_OUT_MS = 260L
    private const val ENTER_MS = 380L
    private const val ENTER_STAGGER_MS = 60L
    private const val POP_MS = 300L
    private const val POP_STAGGER_MS = 35L
    private const val FADE_MS = 260L
    private const val COLOR_MS = 320L
    private const val BREATH_MS = 900L
    private const val ENTER_OFFSET_DP = 16f

    /** Material "emphasized decelerate": arrives fast, lands softly. */
    private val enterEase = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    /** Material "standard": for state changes and the press release. */
    private val standardEase = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** A finger going down: immediate, slightly accelerating into the press. */
    private val pressEase = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    /** Slow in and out, for the breathing halo. */
    private val breathEase = PathInterpolator(0.4f, 0f, 0.6f, 1f)

    private val argb = ArgbEvaluator()

    fun enabled(context: Context): Boolean {
        if (!Prefs(context).animations) return false
        // Respects "Animator duration scale: off" in developer options.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }

    /** Makes the views sink slightly while pressed, then settle back. Does not consume the touch. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(vararg views: View) {
        for (view in views) {
            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> if (enabled(v.context)) {
                        v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).alpha(PRESSED_ALPHA)
                            .setStartDelay(0).setDuration(PRESS_IN_MS).setInterpolator(pressEase).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                            .setStartDelay(0).setDuration(PRESS_OUT_MS).setInterpolator(standardEase).start()
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
                .setInterpolator(enterEase).withLayer().start()
        }
    }

    /** Small items (dock icons) grow in one after the other. */
    fun popIn(views: List<View>) {
        if (views.isEmpty() || !enabled(views[0].context)) return
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.scaleX = POP_FROM
            view.scaleY = POP_FROM
            view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(index * POP_STAGGER_MS).setDuration(POP_MS)
                .setInterpolator(enterEase).start()
        }
    }

    /** A single soft bounce, for a change the driver caused (the cards on the Home tab). */
    fun pop(view: View) {
        if (!enabled(view.context)) return
        view.scaleX = BOUNCE_FROM
        view.scaleY = BOUNCE_FROM
        view.animate().scaleX(1f).scaleY(1f)
            .setStartDelay(0).setDuration(POP_MS).setInterpolator(enterEase).start()
    }

    /** Fades new content in, used when the track changes. */
    fun fadeIn(view: View) {
        if (!enabled(view.context)) return
        view.alpha = 0f
        view.translationY = view.resources.displayMetrics.density * FADE_OFFSET_DP
        view.animate().alpha(1f).translationY(0f)
            .setStartDelay(0).setDuration(FADE_MS).setInterpolator(enterEase).withLayer().start()
    }

    /** Moves a view's opacity to [alpha]: status icons dimming, labels appearing. */
    fun fadeTo(view: View, alpha: Float) {
        if (view.alpha == alpha) return
        if (!enabled(view.context) || !view.isAttachedToWindow) {
            view.animate().cancel()
            view.alpha = alpha
            return
        }
        view.animate().alpha(alpha).setStartDelay(0).setDuration(FADE_MS).setInterpolator(standardEase).start()
    }

    /**
     * Crossfades a drawable's fill colour. Used for tiles switching on and off and for the
     * album colour of the media card; only that one drawable redraws.
     */
    fun colorTo(view: View, from: Int, to: Int, apply: (Int) -> Unit) {
        transition(view, animate = from != to) { t -> apply(argb.evaluate(t, from, to) as Int) }
    }

    /**
     * Runs [apply] with a fraction from 0 to 1 over a short state change, or once with 1 when
     * not animating. A new transition on the same view replaces the running one.
     */
    fun transition(view: View, animate: Boolean = true, apply: (Float) -> Unit) {
        (view.getTag(R.id.motion_color_animator) as? ValueAnimator)?.cancel()
        if (!animate || !enabled(view.context)) {
            apply(1f)
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COLOR_MS
            interpolator = standardEase
            addUpdateListener { apply(it.animatedValue as Float) }
        }
        view.setTag(R.id.motion_color_animator, animator)
        animator.start()
    }

    /** A slow, repeating swell while the assistant is thinking. Stop it with [stopBreathing]. */
    fun breathe(view: View) {
        if (!enabled(view.context)) return
        stopBreathing(view)
        val animator = ValueAnimator.ofFloat(1f, BREATH_SCALE).apply {
            duration = BREATH_MS
            interpolator = breathEase
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val scale = it.animatedValue as Float
                view.scaleX = scale
                view.scaleY = scale
            }
        }
        view.setTag(R.id.motion_breath_animator, animator)
        animator.start()
    }

    fun stopBreathing(view: View) {
        (view.getTag(R.id.motion_breath_animator) as? ValueAnimator)?.cancel()
        view.setTag(R.id.motion_breath_animator, null)
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

    private const val PRESSED_ALPHA = 0.82f
    private const val POP_FROM = 0.82f
    private const val BOUNCE_FROM = 0.97f
    private const val BREATH_SCALE = 1.1f
    private const val FADE_OFFSET_DP = 6f
}
