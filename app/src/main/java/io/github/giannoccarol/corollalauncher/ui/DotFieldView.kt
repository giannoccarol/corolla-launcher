package io.github.giannoccarol.corollalauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import kotlin.math.sqrt

/**
 * A field of fine dots that fades out towards the edges, the "floor" under the car in the
 * Amber theme. Decoration only: drawn once into a bitmap when the size changes, then blitted.
 */
class DotFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val baseColor = ContextCompat.getColor(context, R.color.text_primary)
    private var cache: Bitmap? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cache?.recycle()
        cache = null
        if (w <= 0 || h <= 0) return
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val step = SPACING_DP * density
        val radius = DOT_DP * density
        var y = step / 2
        while (y < h) {
            var x = step / 2
            while (x < w) {
                val strength = falloff(x / w, y / h)
                if (strength > 0f) {
                    paint.color = baseColor
                    paint.alpha = (MAX_ALPHA * strength).toInt()
                    canvas.drawCircle(x, y, radius, paint)
                }
                x += step
            }
            y += step
        }
        cache = bitmap
    }

    override fun onDraw(canvas: Canvas) {
        cache?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    override fun onDetachedFromWindow() {
        cache?.recycle()
        cache = null
        super.onDetachedFromWindow()
    }

    companion object {
        private const val SPACING_DP = 11f
        private const val DOT_DP = 1.1f
        private const val MAX_ALPHA = 46

        /**
         * Dot strength (0..1) at a position given as fractions of the size: full inside an
         * ellipse around the centre, gone at its edge. Pure, unit tested.
         */
        fun falloff(fx: Float, fy: Float): Float {
            val dx = (fx - 0.5f) / 0.5f
            val dy = (fy - 0.5f) / 0.5f
            val distance = sqrt(dx * dx + dy * dy)
            return ((1f - distance) / (1f - INNER)).coerceIn(0f, 1f)
        }

        private const val INNER = 0.3f
    }
}
