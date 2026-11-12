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
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import io.github.giannoccarol.corollalauncher.R
import io.github.giannoccarol.corollalauncher.theme.ThemeManager
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * App icons in the chosen style:
 *
 *  - themed (the default): one glyph per app on a tonal tile, like Android 13's themed
 *    icons, in the app's own hue taken from its icon ([IconTint]); grey apps stay neutral.
 *    The glyph is the app's own monochrome layer when it has one (Android 13+), else a bundled
 *    logo or symbol ([IconGlyphs]), else the silhouette of its adaptive foreground, else its
 *    initial. Glyphs are kept as alpha masks and coloured when bound, so they follow the light
 *    and dark theme without a reload.
 *  - mono: the same glyphs, all in the launcher's accent colours.
 *  - original: the apps' own icons, in one shape ([IconShaper]).
 *  - an installed icon pack: its drawables, and the pack's background, mask and overlay for
 *    apps it does not cover.
 */
object AppIcons {

    const val STYLE_THEMED = "themed"
    const val STYLE_ORIGINAL = "original"
    const val STYLE_MONO = "mono"

    /** Icons are sampled at this size to find the app's colour. */
    private const val HUE_SAMPLE = 12

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
            .takeIf { it != STYLE_THEMED && it != STYLE_ORIGINAL && it != STYLE_MONO }
            ?.let { IconPack.load(context, it) }

        /** Glyphs drawn on tiles by [bind], rather than finished icons. */
        private val themed: Boolean = pack == null && style != STYLE_ORIGINAL

        /** Themed glyphs in each app's own colour rather than the accent. */
        private val colored: Boolean = themed && style != STYLE_MONO

        fun load(info: ResolveInfo, component: ComponentName, label: String): AppInfo {
            pack?.let { return AppInfo(component, label, packIcon(it, info, component)) }
            val original = info.loadIcon(pm)
            if (!themed) return AppInfo(component, label, IconShaper.shape(context, original, size))
            val glyph = BitmapDrawable(context.resources, glyph(original, component.packageName, label))
            return AppInfo(component, label, glyph, themed = true, colored = colored, hue = if (colored) hueOf(original) else null)
        }

        private fun glyph(original: Drawable, packageName: String, label: String): Bitmap {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                (original as? AdaptiveIconDrawable)?.monochrome?.let { layer ->
                    return mask { drawLayer(it, layer) }
                }
            }
            IconGlyphs.match(packageName, label)
                ?.let(GlyphResources::drawable)
                ?.let { ContextCompat.getDrawable(context, it) }
                ?.let { glyph -> return mask { drawBox(it, glyph) } }
            silhouette(original)?.let { return it }
            return letter(label)
        }

        /** The app's colour, from a thumbnail of its full icon. */
        private fun hueOf(icon: Drawable): Float? {
            val thumb = Bitmap.createBitmap(HUE_SAMPLE, HUE_SAMPLE, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, HUE_SAMPLE, HUE_SAMPLE)
            icon.draw(Canvas(thumb))
            val pixels = IntArray(HUE_SAMPLE * HUE_SAMPLE)
            thumb.getPixels(pixels, 0, HUE_SAMPLE, 0, 0, HUE_SAMPLE, HUE_SAMPLE)
            thumb.recycle()
            return IconTint.hueOf(pixels)
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
        val hue = app.hue
        val (tileColor, glyphColor) = when {
            !app.colored -> color(context, R.color.icon_tile) to color(context, R.color.icon_glyph)
            hue == null -> color(context, R.color.icon_tile_neutral) to color(context, R.color.icon_glyph_neutral)
            else -> IconTint.colors(hue, ThemeManager.isDark(context)).let { it.tile to it.glyph }
        }
        val size = max(1, app.icon.intrinsicWidth)
        val tile = GradientDrawable().apply {
            cornerRadius = size * IconShaper.CORNER_FRACTION
            setColor(tileColor)
            val hairline = max(1, (context.resources.displayMetrics.density * 0.75f).roundToInt())
            setStroke(hairline, ContextCompat.getColor(context, R.color.card_border))
            setSize(size, size)
        }
        val glyph = (app.icon.constantState?.newDrawable(context.resources) ?: app.icon).mutate()
        glyph.setTint(glyphColor)
        view.setImageDrawable(LayerDrawable(arrayOf(tile, glyph)))
    }

    private fun color(context: Context, @ColorRes id: Int) = ContextCompat.getColor(context, id)
}
