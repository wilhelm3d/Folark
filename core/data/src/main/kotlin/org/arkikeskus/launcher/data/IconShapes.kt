package org.arkikeskus.launcher.data

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
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
        // A square with small corners.
        LauncherSettings.ICON_SHAPE_SQUARE to
            "M6,0 L94,0 C97.3,0 100,2.7 100,6 L100,94 C100,97.3 97.3,100 94,100 L6,100 C2.7,100 0,97.3 0,94 L0,6 C0,2.7 2.7,0 6,0 Z",
        // Round at the top, flat with small corners at the bottom.
        LauncherSettings.ICON_SHAPE_ARCH to
            "M0,50 C0,22.4 22.4,0 50,0 C77.6,0 100,22.4 100,50 L100,90 C100,95.5 95.5,100 90,100 L10,100 C4.5,100 0,95.5 0,90 Z",
        // A hexagon with rounded corners, point up.
        LauncherSettings.ICON_SHAPE_HEXAGON to
            "M37.5,6.3 Q50,0 62.5,6.3 L87.5,18.7 Q100,25 100,39 L100,61 Q100,75 87.5,81.3 L62.5,93.7 Q50,100 37.5,93.7 L12.5,81.3 Q0,75 0,61 L0,39 Q0,25 12.5,18.7 Z",
        // A diamond with rounded corners.
        LauncherSettings.ICON_SHAPE_DIAMOND to
            "M38.7,11.3 Q50,0 61.3,11.3 L88.7,38.7 Q100,50 88.7,61.3 L61.3,88.7 Q50,100 38.7,88.7 L11.3,61.3 Q0,50 11.3,38.7 Z",
        // Nine soft scallops around a circle (Material's "cookie").
        LauncherSettings.ICON_SHAPE_COOKIE to
            "M50,0 L51.7,0.2 L53.4,0.9 L55.1,1.9 L56.6,3.2 L58,4.7 L59.3,6.2 L60.6,7.6 L61.8,8.9 L63,9.9 L64.4,10.5 L65.8,10.9 L67.4,10.9 L69.1,10.8 L71,10.5 L73,10.2 L75,9.9 L77,9.9 L78.9,10.2 L80.7,10.8 L82.1,11.7 L83.3,13 L84.2,14.6 L84.8,16.4 L85.1,18.4 L85.2,20.4 L85.3,22.4 L85.3,24.3 L85.5,26.1 L85.8,27.6 L86.4,29 L87.3,30.2 L88.4,31.3 L89.9,32.2 L91.5,33.2 L93.2,34.3 L94.9,35.4 L96.5,36.7 L97.8,38.1 L98.7,39.6 L99.2,41.3 L99.3,43.1 L99,44.9 L98.2,46.6 L97.2,48.4 L96,50 L94.7,51.6 L93.5,53 L92.5,54.5 L91.8,55.9 L91.4,57.3 L91.3,58.8 L91.5,60.3 L92,62 L92.6,63.8 L93.2,65.7 L93.8,67.7 L94.2,69.7 L94.3,71.6 L94,73.4 L93.3,75 L92.2,76.4 L90.8,77.5 L89.1,78.4 L87.2,79.1 L85.2,79.6 L83.3,80 L81.4,80.3 L79.7,80.8 L78.2,81.4 L77,82.2 L76,83.3 L75.1,84.6 L74.4,86.2 L73.7,88 L73,89.8 L72.2,91.7 L71.2,93.5 L70,95 L68.7,96.2 L67.1,97 L65.4,97.4 L63.6,97.3 L61.7,96.9 L59.8,96.2 L58,95.3 L56.2,94.3 L54.6,93.4 L53,92.7 L51.5,92.2 L50,92 L48.5,92.2 L47,92.7 L45.4,93.4 L43.8,94.3 L42,95.3 L40.2,96.2 L38.3,96.9 L36.4,97.3 L34.6,97.4 L32.9,97 L31.3,96.2 L30,95 L28.8,93.5 L27.8,91.7 L27,89.8 L26.3,88 L25.6,86.2 L24.9,84.6 L24,83.3 L23,82.2 L21.8,81.4 L20.3,80.8 L18.6,80.3 L16.7,80 L14.8,79.6 L12.8,79.1 L10.9,78.4 L9.2,77.5 L7.8,76.4 L6.7,75 L6,73.4 L5.7,71.6 L5.8,69.7 L6.2,67.7 L6.8,65.7 L7.4,63.8 L8,62 L8.5,60.3 L8.7,58.8 L8.6,57.3 L8.2,55.9 L7.5,54.5 L6.5,53 L5.3,51.6 L4,50 L2.8,48.4 L1.8,46.6 L1,44.9 L0.7,43.1 L0.8,41.3 L1.3,39.6 L2.2,38.1 L3.5,36.7 L5.1,35.4 L6.8,34.3 L8.5,33.2 L10.1,32.2 L11.6,31.3 L12.7,30.2 L13.6,29 L14.2,27.6 L14.5,26.1 L14.7,24.3 L14.7,22.4 L14.8,20.4 L14.9,18.4 L15.2,16.4 L15.8,14.6 L16.7,13 L17.9,11.7 L19.3,10.8 L21.1,10.2 L23,9.9 L25,9.9 L27,10.2 L29,10.5 L30.9,10.8 L32.6,10.9 L34.2,10.9 L35.6,10.5 L37,9.9 L38.2,8.9 L39.4,7.6 L40.7,6.2 L42,4.7 L43.4,3.2 L44.9,1.9 L46.6,0.9 L48.3,0.2 Z",
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

    /**
     * [drawable] cut to [shape]; unchanged for the system shape. An icon that is not adaptive (an
     * old app's plain picture) stays as it is, unless [plateOthers]: then it sits on a plate in a
     * pale tone of its own colour, cut to the same shape, so every icon on screen matches.
     */
    fun apply(drawable: Drawable, shape: String, plateOthers: Boolean = false): Drawable {
        if (shape.isBlank() || shape == LauncherSettings.ICON_SHAPE_SYSTEM) return drawable
        val adaptive = drawable as? AdaptiveIconDrawable
            ?: (if (plateOthers) onPlate(drawable) else null)
            ?: return drawable
        val data = PATHS[shape] ?: return drawable
        val mask = runCatching { PathParser.createPathFromPathData(data) }.getOrNull() ?: return drawable
        return ShapedIconDrawable(adaptive, mask)
    }

    /** A plain icon as an adaptive one: a pale plate of its own colour, the picture inset on it. */
    private fun onPlate(drawable: Drawable): AdaptiveIconDrawable? = runCatching {
        val color = IconColors.dominant(drawable)
        val plate = if (color == null) Color.WHITE else IconColors.tone(color, 0.10f, 0.98f)
        AdaptiveIconDrawable(ColorDrawable(plate), InsetDrawable(drawable, PLATE_INSET))
    }.getOrNull()

    private fun systemMask(): String? = runCatching {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier("config_icon_mask", "string", "android")
        if (id != 0) resources.getString(id) else null
    }.getOrNull()

    private const val MASK_SIZE = 100f
    /** Inset of a plain picture in its plate's layer (layers overhang the mask by a quarter): the
     *  picture then fills about 80% of the shaped icon. */
    private const val PLATE_INSET = 0.24f
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
