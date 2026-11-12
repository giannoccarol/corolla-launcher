package io.github.giannoccarol.corollalauncher.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Gives adaptive icons one consistent rounded-square shape, whatever mask the device vendor
 * configured. Legacy (non-adaptive) icons are returned untouched: they carry their own shape.
 */
object IconShaper {

    /** Corner radius as a fraction of the icon size. */
    const val CORNER_FRACTION = 0.24f

    /** Adaptive layers are 108dp wide with a 72dp visible area, hence 1.5x. */
    private const val LAYER_SCALE = 1.5f

    fun shape(context: Context, icon: Drawable, sizePx: Int): Drawable {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || icon !is AdaptiveIconDrawable) {
            return icon
        }

        val layerSize = (sizePx * LAYER_SCALE).toInt()
        val offset = (sizePx - layerSize) / 2
        val layers = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val layerCanvas = Canvas(layers)
        for (layer in listOfNotNull(icon.background, icon.foreground)) {
            layer.setBounds(offset, offset, offset + layerSize, offset + layerSize)
            layer.draw(layerCanvas)
        }

        // Draw through a shader instead of clipping, so the rounded corners are anti-aliased.
        val shaped = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(layers, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = sizePx * CORNER_FRACTION
        Canvas(shaped).drawRoundRect(
            RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, paint,
        )
        layers.recycle()
        return BitmapDrawable(context.resources, shaped)
    }
}
