package org.arkikeskus.launcher.feature.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.data.root.RootTasks
import org.arkikeskus.launcher.model.AppItem

// Open apps on the bar as pictures of the app, as Recents shows them (Settings ▸ Taskbar ▸ Open apps
// as previews; extension functions on [AppTaskbar]). The pictures are Android's own task snapshots,
// read with root in one call for all open apps; until one is there the app keeps its icon.

/** How long a picture is used before it is read again (the app may look different by now). */
private const val PREVIEW_STALE_MS = 20_000L

/** A preview tile is a little wider than an icon, so it reads as a window, not an icon. */
internal const val PREVIEW_WIDTH = 1.3f

/** The picture of [app]'s open window, if one was read. */
internal fun AppTaskbar.previewFor(app: AppItem): Bitmap? =
    openTaskOf[app.packageName]?.let { previewCache.get(it.first) }

/** Reads the pictures of the open apps on the bar that have none (or an old one), then shows them. */
internal fun AppTaskbar.loadPreviews(s: TaskbarSetup) {
    if (!s.openPreviews || RootShell.lastKnown != true) return
    if (previewJob?.isActive == true) return
    val now = SystemClock.elapsedRealtime()
    val wanted = recents.mapNotNull { openTaskOf[it.packageName] }
        .filter { (id, _) -> now - (previewTakenAt[id] ?: 0L) > PREVIEW_STALE_MS }
        .toMap()
    if (wanted.isEmpty()) return
    // Asked once per round, found or not: no root call again on every rebuild of the bar.
    wanted.keys.forEach { previewTakenAt[it] = now }
    val edge = dp(s.iconSizeDp) * 3
    previewJob = scope.launch {
        val got = withContext(Dispatchers.IO) {
            RootTasks.snapshots(context, wanted).mapNotNull { (id, bytes) -> decodePreview(bytes, edge)?.let { id to it } }
        }
        if (got.isEmpty()) return@launch
        got.forEach { (id, bitmap) -> previewCache.put(id, bitmap) }
        // Shown in place on the bar that is up now (no rebuild, no flicker).
        recents.forEach { app ->
            val picture = previewFor(app) ?: return@forEach
            (iconViews[app.badgeKey] as? ImageView)?.let { showPreview(it, app, picture, s) }
        }
    }
}

/** [view] (an app's icon on the bar) shows [picture] with the app's icon in its corner. */
internal fun AppTaskbar.showPreview(view: ImageView, app: AppItem, picture: Bitmap, s: TaskbarSetup) {
    val current = view.drawable
    if (current is PreviewTileDrawable && current.picture === picture) return
    val icon = (current as? PreviewTileDrawable)?.icon ?: current ?: iconCache[app.key]
    val radius = dpf(10f)
    view.setImageDrawable(PreviewTileDrawable(picture, icon, radius))
    view.scaleType = ImageView.ScaleType.FIT_XY
    val inset = dp(3)
    view.setPadding(inset, inset, inset, inset)
    view.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(v: View, outline: Outline) {
            outline.setRoundRect(v.paddingLeft, v.paddingTop, v.width - v.paddingRight, v.height - v.paddingBottom, radius)
            outline.alpha = 0.6f
        }
    }
    view.elevation = dpf(4f)
    val size = dp(s.iconSizeDp)
    val wide = (size * PREVIEW_WIDTH).toInt()
    view.layoutParams?.let { lp ->
        if (lp.width != wide) {
            lp.width = wide
            view.layoutParams = lp
        }
    }
}

/** The picture, scaled down to about [edge] pixels on its long side as it is read. */
private fun decodePreview(bytes: ByteArray, edge: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val long = maxOf(bounds.outWidth, bounds.outHeight)
    if (long <= 0) return@runCatching null
    var sample = 1
    while (long / (sample * 2) >= edge) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/**
 * The window picture filling the tile (cropped from its top, where an app's own header is), with
 * rounded corners, and [icon] small in the bottom-right corner on a soft dark disc.
 */
internal class PreviewTileDrawable(val picture: Bitmap, var icon: Drawable?, private val radius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(70, 255, 255, 255)
    }
    private val matrix = Matrix()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        rect.set(b)
        val scale = maxOf(b.width() / picture.width.toFloat(), b.height() / picture.height.toFloat())
        matrix.setScale(scale, scale)
        matrix.postTranslate(b.left + (b.width() - picture.width * scale) / 2f, b.top.toFloat())
        paint.shader.setLocalMatrix(matrix)
        canvas.drawRoundRect(rect, radius, radius, paint)
        frame.strokeWidth = maxOf(1f, b.width() / 60f)
        canvas.drawRoundRect(rect, radius, radius, frame)
        val i = icon ?: return
        val side = (minOf(b.width(), b.height()) * 0.46f).toInt()
        val right = b.right - side / 6
        val bottom = b.bottom - side / 6
        canvas.drawCircle(right - side / 2f, bottom - side / 2f, side * 0.56f, disc)
        val saved = i.copyBounds()
        i.setBounds(right - side, bottom - side, right, bottom)
        i.draw(canvas)
        i.bounds = saved
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
}
