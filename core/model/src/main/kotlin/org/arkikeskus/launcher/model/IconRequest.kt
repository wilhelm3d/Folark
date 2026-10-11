package org.arkikeskus.launcher.model

/**
 * Coil model for rendering an app icon. [themed] and [dark] are part of the model — and therefore the
 * cache key — so toggling themed icons or switching the dark/light theme re-renders the icon
 * immediately and caches each variant separately.
 *
 * [themed] only changes apps whose adaptive icon ships a monochrome layer (Android 13+); every other
 * app falls back to its normal icon, exactly like Pixel Launcher.
 *
 * [iconPack] is the package name of a selected third-party icon pack (empty = none). When set it takes
 * priority over [themed]: mapped apps use the pack's drawable, unmapped apps are masked to the pack's
 * style. It's part of the model/cache key so switching packs re-renders every icon.
 *
 * [epoch] is the app's [IconEpochs] token. It's part of the model/cache key so a package update
 * re-renders that app's icon immediately — including icons already on screen, whose AsyncImage only
 * re-fetches when the model changes.
 *
 * [customIcon] is the user's own choice for this one app — "packPackage|drawableName", empty for
 * none. It wins over everything else, and is part of the model/cache key like the rest.
 *
 * [sizePx] is the edge the icon is rasterized at (see [iconRasterSize]; 0 = the fetcher's default).
 * In the key too, so a folder's thumbnail and a large home icon each cache a bitmap of their own
 * size instead of sharing one that is too big for the first and too soft for the second.
 *
 * [shape] is the icon shape (LauncherSettings.ICON_SHAPE_*; empty = the system's), in the key too.
 */
data class IconRequest(
    val app: AppItem,
    val themed: Boolean = false,
    val dark: Boolean = false,
    val iconPack: String = "",
    val epoch: Int = 0,
    val customIcon: String = "",
    val sizePx: Int = 0,
    val shape: String = "",
)

/** The few sizes icons are rasterized at — steps, so near-equal sizes share one cached bitmap. */
private val ICON_RASTER_SIZES = intArrayOf(48, 72, 96, 128, 168, 224, 288)

/** The rasterization edge for an icon shown [displayPx] pixels wide: the next step up (never
 *  upscaled until the largest step). */
fun iconRasterSize(displayPx: Int): Int =
    ICON_RASTER_SIZES.firstOrNull { it >= displayPx } ?: ICON_RASTER_SIZES.last()
