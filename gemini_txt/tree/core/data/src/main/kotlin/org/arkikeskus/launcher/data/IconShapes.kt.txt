package org.arkikeskus.launcher.data

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser
import org.arkikeskus.launcher.model.LauncherSettings

/**
 * App icon shapes (Settings ▸ Appearance ▸ Icon shape). The outlines are Motorola's icon-shape
 * overlays (round, rounded square, teardrop, pebble) plus a squircle, in a 100×100 box like
 * Android's own `config_icon_mask`. Only adaptive icons can take a shape; others stay as they are.
 */
object IconShapes {
    private val PATHS = mapOf(
        LauncherSettings.ICON_SHAPE_CIRCLE to
            "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0Z",
        LauncherSettings.ICON_SHAPE_SQUIRCLE to
            "M50,0 C10,0 0,10 0,50 C0,90 10,100 50,100 C90,100 100,90 100,50 C100,10 90,0 50,0 Z",
        LauncherSettings.ICON_SHAPE_ROUNDED to
            "M50,0L88,0 C94.4,0 100,5.4 100 12 L100,88 C100,94.6 94.6 100 88 100 L12,100 C5.4,100 0,94.6 0,88 L0 12 C0 5.4 5.4 0 12 0 L50,0 Z",
        LauncherSettings.ICON_SHAPE_TEARDROP to
            "M50,0 C77.6,0 100,22.4 100,50 L100,88 C100,94.6 94.6,100 88,100 L50,100 C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0 Z",
        LauncherSettings.ICON_SHAPE_PEBBLE to
            "M55,0 C25,0 0,25 0,50 0,78 28,100 55,100 85,100 100,85 100,58 100,30 86,0 55,0 Z",
    )

    /** The outline of [shape] (100×100), or null when unknown. "System" is the phone's own mask. */
    fun pathData(shape: String): String? =
        if (shape == LauncherSettings.ICON_SHAPE_SYSTEM || shape.isBlank()) systemMask() else PATHS[shape]

    /** The outline of [shape] scaled to a [size]×[size] square, for drawing it (settings tiles). */
    fun path(shape: String, size: Float): Path? {
        val data = pathData(shape) ?: return null
        val path = runCatching { PathParser.createPathFromPathData(data) }.getOrNull() ?: return null
        path.transform(Matrix().apply { setScale(size / MASK_SIZE, size / MASK_SIZE) })
        return path
    }

    /** [drawable] cut to [shape]; unchanged for the system shape or an icon that is not adaptive. */
    fun apply(drawable: Drawable, shape: String): Drawable {
        if (shape.isBlank() || shape == LauncherSettings.ICON_SHAPE_SYSTEM) return drawable
        val adaptive = drawable as? AdaptiveIconDrawable ?: return drawable
        val data = PATHS[shape] ?: return drawable
        val mask = runCatching { PathParser.createPathFromPathData(data) }.getOrNull() ?: return drawable
        return ShapedIconDrawable(adaptive, mask)
    }

    private fun systemMask(): String? = runCatching {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier("config_icon_mask", "string", "android")
        if (id != 0) resources.getString(id) else null
    }.getOrNull()

    private const val MASK_SIZE = 100f
}

/**
 * An adaptive icon's two layers drawn into [mask] with smooth edges (a shader-filled path; a clip
 * would leave jagged edges on a software canvas). The layers overhang the icon by a quarter on each
 * side, as Android draws them (108dp layers behind a 72dp mask).
 */
private class ShapedIconDrawable(
    private val icon: AdaptiveIconDrawable,
    private val mask: Path,
) : Drawable() {
    private val outline = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var picture: Bitmap? = null

    override fun onBoundsChange(bounds: Rect) {
        outline.reset()
        mask.transform(
            Matrix().apply {
                setScale(bounds.width() / 100f, bounds.height() / 100f)
                postTranslate(bounds.left.toFloat(), bounds.top.toFloat())
            },
            outline,
        )
        val dx = (bounds.width() * LAYER_OVERHANG).toInt()
        val dy = (bounds.height() * LAYER_OVERHANG).toInt()
        val layer = Rect(bounds.left - dx, bounds.top - dy, bounds.right + dx, bounds.bottom + dy)
        icon.background?.bounds = layer
        icon.foreground?.bounds = layer
        picture = null
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val bitmap = picture ?: Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888).also { bitmap ->
            val layers = Canvas(bitmap)
            layers.translate(-b.left.toFloat(), -b.top.toFloat())
            icon.background?.draw(layers)
            icon.foreground?.draw(layers)
            picture = bitmap
            paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(b.left.toFloat(), b.top.toFloat()) })
            }
        }
        if (bitmap.isRecycled) return
        canvas.drawPath(outline, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = icon.intrinsicWidth

    override fun getIntrinsicHeight(): Int = icon.intrinsicHeight

    private companion object {
        /** Extra layer size per side: (108 − 72) / 2 / 72. */
        const val LAYER_OVERHANG = 0.25f
    }
}
