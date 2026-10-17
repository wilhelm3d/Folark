package org.arkikeskus.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.model.IconRequest

/**
 * Coil fetcher that resolves an [IconRequest]'s launcher icon via [LauncherAppsSource]. The icon is
 * **rasterized to a Bitmap once** here rather than handed to Coil as a live Drawable: launcher icons
 * are usually AdaptiveIconDrawables that re-composite their layers on every draw, which makes a
 * scrolling grid stutter. A cached Bitmap is just a cheap blit per frame. The [IconRequest] carries
 * the themed/dark flags so the source can pick the Material You monochrome icon when requested.
 */
class AppIconFetcher(
    private val data: IconRequest,
    private val source: LauncherAppsSource,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.Default) {
        val loaded = source.loadIcon(data.app, data.themed, data.dark, data.iconPack, data.customIcon) ?: return@withContext null
        // Settings ▸ Icon shape: adaptive icons cut to the chosen outline (the system's otherwise).
        val drawable = IconShapes.apply(loaded, data.shape)
        // The size the icon is shown at (stepped, see iconRasterSize); the default for callers
        // that do not say.
        val size = if (data.sizePx > 0) data.sizePx else ICON_PX
        
        // VRAM Optimization: If the drawable is completely opaque, we can save 50% of the memory
        // by using RGB_565 instead of ARGB_8888.
        val config = if (drawable.opacity == android.graphics.PixelFormat.OPAQUE) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }
        val tempBitmap = Bitmap.createBitmap(size, size, config)
        val canvas = Canvas(tempBitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        // Hardware bitmaps might fail to allocate on low-end devices or during heavy memory pressure.
        val hardwareBitmap = runCatching { tempBitmap.copy(Bitmap.Config.HARDWARE, false) }.getOrNull() ?: tempBitmap
        // The drawing copy is done with once the icon lives in graphics memory; freeing it now
        // instead of at the next garbage collection keeps a full drawer scroll from piling them up.
        if (hardwareBitmap !== tempBitmap) tempBitmap.recycle()
        ImageFetchResult(
            image = hardwareBitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val source: LauncherAppsSource) : Fetcher.Factory<IconRequest> {
        override fun create(data: IconRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconFetcher(data, source)
    }

    private companion object {
        /** Rasterization size (px); crisp for icons up to ~56dp at xxxhdpi. */
        const val ICON_PX = 168
    }
}
