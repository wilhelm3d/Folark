package org.arkikeskus.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import org.arkikeskus.launcher.model.catching

/**
 * An app's own colour, read from its icon: the pixels averaged, weighted towards the colourful
 * ones (a white logo plate or a black outline doesn't win over the brand colour). Used for icons
 * themed in each app's colour and for the taskbar's launch glow, so both agree.
 */
object IconColors {
    /** Below this saturation an icon counts as grey (no brand colour to use). */
    const val GREY_SATURATION = 0.12f

    /** The icon's main colour (opaque), or null when it can't be read. */
    fun dominant(icon: Drawable): Int? = catching {
        val side = SAMPLE_EDGE
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val adaptive = icon as? AdaptiveIconDrawable
        if (adaptive != null) {
            // Both layers: many brands put their colour in the background layer.
            adaptive.background?.let { drawInto(it, canvas, side) }
            adaptive.foreground?.let { drawInto(it, canvas, side) }
        } else {
            drawInto(icon, canvas, side)
        }
        val hsv = FloatArray(3)
        var r = 0f; var g = 0f; var b = 0f; var weight = 0f
        for (y in 0 until side) for (x in 0 until side) {
            val p = bitmap.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            val w = 0.15f + hsv[1] * hsv[2]
            r += Color.red(p) * w; g += Color.green(p) * w; b += Color.blue(p) * w; weight += w
        }
        bitmap.recycle()
        if (weight == 0f) null else Color.rgb((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt())
    }.getOrNull()

    /** [color] at the given HSV saturation and value, its hue kept (grey stays grey). */
    fun tone(color: Int, saturation: Float, value: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val grey = hsv[1] < GREY_SATURATION
        hsv[1] = if (grey) 0f else saturation.coerceIn(0f, 1f)
        hsv[2] = value.coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    /** [color]'s saturation, 0..1. */
    fun saturation(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[1]
    }

    private fun drawInto(drawable: Drawable, canvas: Canvas, side: Int) {
        val d = drawable.constantState?.newDrawable()?.mutate() ?: drawable
        d.setBounds(0, 0, side, side)
        d.draw(canvas)
    }

    private const val SAMPLE_EDGE = 16
}
