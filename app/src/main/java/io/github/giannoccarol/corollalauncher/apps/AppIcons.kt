package io.github.giannoccarol.corollalauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.widget.ImageView
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * App icons in the chosen style:
 *
 *  - themed (the default): one monochrome glyph per app on a tonal tile, like Android 13's
 *    themed icons. The glyph is the app's own monochrome layer when it has one (Android 13+),
 *    else a bundled logo or symbol ([IconGlyphs]), else the silhouette of its adaptive
 *    foreground, else its initial. Glyphs are kept as alpha masks and coloured when bound, so
 *    they follow the light and dark theme without a reload.
 *  - original: the apps' own icons, in one shape ([IconShaper]).
 *  - an installed icon pack: its drawables, and the pack's background, mask and overlay for
 *    apps it does not cover.
 */
object AppIcons {

    const val STYLE_THEMED = "themed"
    const val STYLE_ORIGINAL = "original"

    /** Bundled glyphs are drawn in this fraction of the tile, centred. */
    private const val GLYPH_BOX = 0.58f

    /** Adaptive layers are 108dp wide with a 72dp visible area. */
    private const val LAYER_SCALE = 1.5f

    private const val LETTER_SIZE = 0.46f

    /** Loads icons for one pass over the installed apps. Background thread only. */
    class Loader(private val context: Context, style: String) {

        private val size = context.resources.getDimensionPixelSize(R.dimen.app_icon_bitmap)
        private val pm = context.packageManager
        private val pack: IconPack? = style
            .takeIf { it != STYLE_THEMED && it != STYLE_ORIGINAL }
            ?.let { IconPack.load(context, it) }

        /** True when [icon] returns alpha masks to be drawn with [bind]. */
        val themed: Boolean = pack == null && style != STYLE_ORIGINAL

        fun icon(info: ResolveInfo, component: ComponentName, label: String): Drawable {
            pack?.let { return packIcon(it, info, component) }
            if (!themed) return IconShaper.shape(context, info.loadIcon(pm), size)
            return BitmapDrawable(context.resources, glyph(info, component.packageName, label))
        }

        private fun glyph(info: ResolveInfo, packageName: String, label: String): Bitmap {
            var original: Drawable? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                original = info.loadIcon(pm)
                (original as? AdaptiveIconDrawable)?.monochrome?.let { layer ->
                    return mask { drawLayer(it, layer) }
                }
            }
            IconGlyphs.match(packageName, label)
                ?.let(GlyphResources::drawable)
                ?.let { ContextCompat.getDrawable(context, it) }
                ?.let { glyph -> return mask { drawBox(it, glyph) } }
            silhouette(original ?: info.loadIcon(pm))?.let { return it }
            return letter(label)
        }

        private fun silhouette(icon: Drawable): Bitmap? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || icon !is AdaptiveIconDrawable) return null
            val foreground = icon.foreground ?: return null
            val bitmap = mask { drawLayer(it, foreground) }
            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            val alpha = IntArray(pixels.size) { pixels[it] ushr 24 }
            if (IconSilhouette.looksLikeLogo(alpha, size)) return bitmap
            bitmap.recycle()
            return null
        }

        private fun letter(label: String): Bitmap {
            val initial = label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '•'
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size * LETTER_SIZE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
            return mask { canvas ->
                val baseline = size / 2f - (paint.ascent() + paint.descent()) / 2f
                canvas.drawText(initial.toString(), size / 2f, baseline, paint)
            }
        }

        private fun packIcon(pack: IconPack, info: ResolveInfo, component: ComponentName): Drawable {
            pack.iconFor(component)?.let { return rasterize(it) }
            val shaped = IconShaper.shape(context, info.loadIcon(pm), size)
            val filter = pack.filter
            if (!filter.canFitUncovered) return shaped

            // Background, then the app's icon scaled down and cut by the mask, then the overlay.
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            filter.backFor(component.packageName)?.let(pack::drawable)?.let { draw(canvas, it, 0, size) }
            val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val inner = (size * filter.scale).roundToInt()
            draw(Canvas(icon), shaped, (size - inner) / 2, inner)
            filter.mask?.let(pack::drawable)?.let { mask ->
                val cut = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
                val maskBitmap = rasterizeBitmap(mask)
                Canvas(icon).drawBitmap(maskBitmap, 0f, 0f, cut)
                maskBitmap.recycle()
            }
            canvas.drawBitmap(icon, 0f, 0f, null)
            icon.recycle()
            filter.upon?.let(pack::drawable)?.let { draw(canvas, it, 0, size) }
            return BitmapDrawable(context.resources, out)
        }

        /** Pack drawables are often 192px or more: keep them at the size we draw them. */
        private fun rasterize(drawable: Drawable): Drawable =
            BitmapDrawable(context.resources, rasterizeBitmap(drawable))

        private fun rasterizeBitmap(drawable: Drawable): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap), drawable, 0, size)
            return bitmap
        }

        private fun draw(canvas: Canvas, drawable: Drawable, offset: Int, extent: Int) {
            drawable.setBounds(offset, offset, offset + extent, offset + extent)
            drawable.draw(canvas)
        }

        /** An alpha-only bitmap: a quarter of the memory, and tinted at draw time. */
        private fun mask(paint: (Canvas) -> Unit): Bitmap =
            Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8).also { paint(Canvas(it)) }

        private fun drawLayer(canvas: Canvas, layer: Drawable) {
            val extent = (size * LAYER_SCALE).roundToInt()
            draw(canvas, layer, (size - extent) / 2, extent)
        }

        private fun drawBox(canvas: Canvas, glyph: Drawable) {
            val extent = (size * GLYPH_BOX).roundToInt()
            draw(canvas, glyph, (size - extent) / 2, extent)
        }
    }

    /** Shows [app]'s icon in [view], on its tile in the current theme's colours when themed. */
    fun bind(view: ImageView, app: AppInfo) {
        if (!app.themed) {
            view.setImageDrawable(app.icon)
            return
        }
        val context = view.context
        val size = max(1, app.icon.intrinsicWidth)
        val tile = GradientDrawable().apply {
            cornerRadius = size * IconShaper.CORNER_FRACTION
            setColor(ContextCompat.getColor(context, R.color.icon_tile))
            val hairline = max(1, (context.resources.displayMetrics.density * 0.75f).roundToInt())
            setStroke(hairline, ContextCompat.getColor(context, R.color.card_border))
            setSize(size, size)
        }
        val glyph = (app.icon.constantState?.newDrawable(context.resources) ?: app.icon).mutate()
        glyph.setTint(ContextCompat.getColor(context, R.color.icon_glyph))
        view.setImageDrawable(LayerDrawable(arrayOf(tile, glyph)))
    }
}
