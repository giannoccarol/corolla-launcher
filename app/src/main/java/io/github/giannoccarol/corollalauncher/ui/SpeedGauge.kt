package io.github.giannoccarol.corollalauncher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The speed readout, in the spirit of a late-90s Japanese roadster's digital dash: a bar graph
 * of segments that rise from left to right and light up with the speed, over italic
 * seven-segment digits whose unlit segments stay faintly visible, like an LCD.
 *
 * Drawn on a canvas, only when the value changes; the bar glides to the new speed, the digits
 * change at once, as on a real LCD.
 */
class SpeedGauge @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.accent) }
    private val warn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.gauge_warn) }
    private val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.gauge_ghost) }
    private val digit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.text_primary) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = sp(11f)
    }
    private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = sp(14f)
    }

    private val segment = RectF()
    private val digitPaths = Array(DIGITS) { Array(7) { Path() } }
    private var layoutWidth = 0

    /** Speed shown by the digits; null shows dashes. */
    private var speed: Int? = null

    /** Speed shown by the bar, animated towards [speed]. */
    private var barSpeed = 0f
    private var animator: ValueAnimator? = null

    var unitText: String = "km/h"
        set(value) {
            field = value
            invalidate()
        }

    fun setSpeed(kmh: Int?) {
        if (kmh == speed) return
        speed = kmh
        contentDescription = kmh?.let { "$it $unitText" } ?: unitText
        val target = (kmh ?: 0).toFloat()
        animator?.cancel()
        if (Motion.enabled(context) && isAttachedToWindow) {
            animator = ValueAnimator.ofFloat(barSpeed, target).apply {
                duration = BAR_MS
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                addUpdateListener {
                    barSpeed = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            barSpeed = target
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutWidth = w
        buildDigits()
    }

    override fun onDraw(canvas: Canvas) {
        drawBar(canvas)
        drawDigits(canvas)
    }

    // region Bar graph

    private fun drawBar(canvas: Canvas) {
        val gap = 2 * density
        val width = width - paddingLeft - paddingRight
        val top = paddingTop.toFloat()
        val barHeight = BAR_HEIGHT_DP * density
        val segmentWidth = (width - gap * (SEGMENTS - 1)) / SEGMENTS
        val lighted = Gauge.litSegments(barSpeed, MAX_SPEED, SEGMENTS)
        for (i in 0 until SEGMENTS) {
            // Segments rise from left to right, as on the roadster's tach.
            val height = Gauge.segmentHeight(i, SEGMENTS, barHeight * 0.32f, barHeight)
            val left = paddingLeft + i * (segmentWidth + gap)
            segment.set(left, top + barHeight - height, left + segmentWidth, top + barHeight)
            val paint = when {
                i < lighted.full -> if (Gauge.isWarning(i, SEGMENTS, MAX_SPEED, WARN_SPEED)) warn else lit
                else -> ghost
            }
            canvas.drawRoundRect(segment, density, density, paint)
            // The segment being reached fades in, so the bar glides instead of stepping.
            if (i == lighted.full && lighted.partial > 0f) {
                val partial = if (Gauge.isWarning(i, SEGMENTS, MAX_SPEED, WARN_SPEED)) warn else lit
                val alpha = partial.alpha
                partial.alpha = (alpha * lighted.partial).roundToInt()
                canvas.drawRoundRect(segment, density, density, partial)
                partial.alpha = alpha
            }
        }
        // Scale under the bar.
        val labelY = top + barHeight + LABEL_GAP_DP * density + label.textSize * 0.8f
        for (value in SCALE) {
            val x = paddingLeft + width * (value / MAX_SPEED)
            val text = value.toInt().toString()
            val textWidth = label.measureText(text)
            val clamped = x.coerceIn(paddingLeft.toFloat(), paddingLeft + width - textWidth)
            canvas.drawText(text, if (value == 0f) clamped else clamped - textWidth / 2f, labelY, label)
        }
    }

    // endregion

    // region Seven-segment digits

    private fun digitsTop(): Float =
        paddingTop + BAR_HEIGHT_DP * density + LABEL_GAP_DP * density + label.textSize + DIGITS_GAP_DP * density

    private fun buildDigits() {
        val height = (height - paddingBottom - digitsTop()).coerceAtLeast(1f)
        val width = height * DIGIT_ASPECT
        val spacing = height * 0.16f
        val skew = Matrix().apply { setSkew(-SKEW, 0f, 0f, digitsTop() + height) }
        for (index in 0 until DIGITS) {
            val left = paddingLeft + index * (width + spacing)
            val shapes = SevenSegment.shapes(left, digitsTop(), width, height)
            for (s in 0 until 7) {
                digitPaths[index][s].apply {
                    reset()
                    addPath(shapes[s])
                    transform(skew)
                }
            }
        }
    }

    private fun drawDigits(canvas: Canvas) {
        val masks = SevenSegment.masksFor(speed, DIGITS)
        for (index in 0 until DIGITS) {
            for (s in 0 until 7) {
                val on = masks[index] and (1 shl s) != 0
                canvas.drawPath(digitPaths[index][s], if (on) digit else ghost)
            }
        }
        // The unit, on the digits' baseline.
        val height = height - paddingBottom - digitsTop()
        // The italic slant pushes the top of the last digit right by SKEW x height.
        val digitsRight = paddingLeft + DIGITS * height * (DIGIT_ASPECT + 0.16f) + SKEW * height
        canvas.drawText(unitText, digitsRight + 6 * density, height + digitsTop(), unit)
    }

    // endregion

    companion object {
        const val SEGMENTS = 36
        const val MAX_SPEED = 180f
        const val WARN_SPEED = 130f
        const val DIGITS = 3
        private const val BAR_MS = 450L
        private const val BAR_HEIGHT_DP = 20f
        private const val LABEL_GAP_DP = 3f
        private const val DIGITS_GAP_DP = 4f
        private const val DIGIT_ASPECT = 0.52f
        private const val SKEW = 0.1f
        private val SCALE = floatArrayOf(0f, 60f, 120f, 180f)
    }
}

/** Bar graph arithmetic. Pure, unit tested. */
object Gauge {

    data class Lit(val full: Int, val partial: Float)

    /** How many segments are fully lit for [speed], and how lit the next one is (0..1). */
    fun litSegments(speed: Float, max: Float, segments: Int): Lit {
        val exact = (speed.coerceIn(0f, max) / max) * segments
        val full = exact.toInt().coerceAtMost(segments)
        return Lit(full, if (full >= segments) 0f else exact - full)
    }

    /** Segment [index] covers speeds at or above [warnAt]. */
    fun isWarning(index: Int, segments: Int, max: Float, warnAt: Float): Boolean =
        (index * max / segments) >= warnAt

    /** Segments grow from [low] to [high], slowly at first. */
    fun segmentHeight(index: Int, segments: Int, low: Float, high: Float): Float =
        low + (high - low) * (index.toFloat() / (segments - 1)).pow(1.4f)
}

/** Seven-segment digits. Segment bits: a=0 top, b=1 top right, c=2 bottom right, d=3 bottom,
 * e=4 bottom left, f=5 top left, g=6 middle. Pure apart from [shapes], unit tested. */
object SevenSegment {

    private val DIGIT_MASKS = intArrayOf(
        0b0111111, // 0: a b c d e f
        0b0000110, // 1: b c
        0b1011011, // 2: a b d e g
        0b1001111, // 3: a b c d g
        0b1100110, // 4: b c f g
        0b1101101, // 5: a c d f g
        0b1111101, // 6: a c d e f g
        0b0000111, // 7: a b c
        0b1111111, // 8
        0b1101111, // 9: a b c d f g
    )
    const val DASH = 0b1000000

    fun mask(digit: Int): Int = DIGIT_MASKS[digit]

    /** Right-aligned digits of [value], blank to the left; dashes when [value] is null. */
    fun masksFor(value: Int?, digits: Int): IntArray {
        if (value == null) return IntArray(digits) { DASH }
        val text = value.coerceIn(0, "9".repeat(digits).toInt()).toString()
        return IntArray(digits) { index ->
            val offset = index - (digits - text.length)
            if (offset < 0) 0 else mask(text[offset] - '0')
        }
    }

    /** The seven segment outlines of one digit cell, with bevelled ends like an LCD. */
    fun shapes(left: Float, top: Float, width: Float, height: Float): Array<Path> {
        val t = height * 0.12f
        val half = t / 2f
        val gap = t * 0.18f
        val right = left + width
        val bottom = top + height
        val middle = top + height / 2f
        fun horizontal(y: Float) = Path().apply {
            moveTo(left + half + gap, y)
            lineTo(left + t + gap, y - half)
            lineTo(right - t - gap, y - half)
            lineTo(right - half - gap, y)
            lineTo(right - t - gap, y + half)
            lineTo(left + t + gap, y + half)
            close()
        }
        fun vertical(x: Float, from: Float, to: Float) = Path().apply {
            moveTo(x, from + half + gap)
            lineTo(x + half, from + t + gap)
            lineTo(x + half, to - t - gap)
            lineTo(x, to - half - gap)
            lineTo(x - half, to - t - gap)
            lineTo(x - half, from + t + gap)
            close()
        }
        return arrayOf(
            horizontal(top + half),
            vertical(right - half, top, middle),
            vertical(right - half, middle, bottom),
            horizontal(bottom - half),
            vertical(left + half, middle, bottom),
            vertical(left + half, top, middle),
            horizontal(middle),
        )
    }
}
