package org.arkikeskus.launcher.feature.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LiquidGlassContainer
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.DarkExpressivePalette
import org.arkikeskus.launcher.ui.expressive.ExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LightExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** A few installed apps, for the icons in the previews (provided by the settings screen). */
internal val LocalPreviewApps = staticCompositionLocalOf<List<AppItem>> { emptyList() }

/**
 * Values a slider is being dragged to, before they are saved: the previews follow the finger
 * instead of waiting for the setting to be written (provided by the Appearance page).
 */
@Stable
internal class PreviewOverrides {
    var glassBlur by mutableStateOf<Float?>(null)
    var glassTint by mutableStateOf<Float?>(null)
    var homeDim by mutableStateOf<Float?>(null)
    var homeBlur by mutableStateOf<Float?>(null)
    var stackCorner by mutableStateOf<Float?>(null)
    var widgetOpacity by mutableStateOf<Float?>(null)
}

internal val LocalPreviewOverrides = staticCompositionLocalOf<PreviewOverrides?> { null }

/** [this] settings with the values being dragged right now. */
internal fun LauncherSettings.withPreview(o: PreviewOverrides?): LauncherSettings = if (o == null) this else copy(
    glassBlurRadius = o.glassBlur ?: glassBlurRadius,
    glassDarkTint = o.glassTint ?: glassDarkTint,
    homeWallpaperDim = o.homeDim ?: homeWallpaperDim,
    homeWallpaperBlur = o.homeBlur ?: homeWallpaperBlur,
    stackCornerRadius = o.stackCorner?.roundToInt() ?: stackCornerRadius,
    widgetBackgroundOpacity = o.widgetOpacity ?: widgetBackgroundOpacity,
)

// ------------------------------------------------------------------ the mini home screen

/**
 * A small home screen drawn with the current look: backdrop (picture, dim, blur), a widget card
 * (corners, background), app icons (icon pack, themed icons, labels), a folder (shape, theme,
 * glass), the dock (opacity) and a long-press menu (menu style). It follows every change at once.
 */
@Composable
internal fun HomeThemePreview(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val s = settings.withPreview(LocalPreviewOverrides.current)
    val apps = LocalPreviewApps.current
    val labelColor = Color(s.appLabelColor)
    PreviewFrame(height = 250.dp, modifier = modifier) {
        Backdrop(path = s.homeBackgroundImage, blur = s.homeWallpaperBlur, dim = s.homeWallpaperDim)
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text("12:30", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
            Text(previewDate(), color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            WidgetCard(s, Modifier.fillMaxWidth(0.62f).height(46.dp))
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                apps.take(3).forEach { app ->
                    AppIcon(
                        appItem = app,
                        labelColor = labelColor,
                        iconSize = 40.dp,
                        showLabel = s.showHomeLabels,
                        maxLabelLines = 1,
                        aquamorphicTouchEnabled = false,
                        modifier = Modifier.width(62.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
                    FolderTile(s, apps.drop(3).take(4), 40.dp)
                    if (s.showHomeLabels) {
                        Text(
                            stringResource(R.string.settings_preview_folder),
                            color = labelColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black.copy(alpha = s.dockBackgroundOpacity.coerceIn(0f, 1f)))
                    .padding(vertical = 8.dp),
            ) {
                apps.drop(7).take(4).ifEmpty { apps.take(4) }.forEach { app ->
                    AppIcon(
                        appItem = app,
                        labelColor = Color.Transparent,
                        iconSize = 34.dp,
                        showLabel = false,
                        aquamorphicTouchEnabled = false,
                    )
                }
            }
        }
        MenuCard(
            theme = s.popupTheme,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 14.dp)
                .width(132.dp),
        )
    }
}

// ------------------------------------------------------------------ previews beside the options

/** The folder glass: frost and tint over a busy backdrop, exactly as folders draw them. */
@Composable
internal fun GlassBlurPreview(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val s = settings.withPreview(LocalPreviewOverrides.current)
    PreviewFrame(height = 104.dp, modifier = modifier.padding(vertical = 6.dp)) {
        Backdrop(path = "", blur = 0f, dim = 0f)
        LiquidGlassContainer(
            blurRadiusDp = s.glassBlurRadius,
            darkTintAlpha = s.glassDarkTint,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            ) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Accent),
                    contentAlignment = Alignment.Center,
                ) { Text("Fx", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_preview_glass), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(
                            R.string.settings_preview_glass_values,
                            (s.glassBlurRadius * 2).roundToInt(),
                            (s.glassDarkTint * 100).roundToInt(),
                        ),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.5.sp,
                    )
                }
            }
        }
    }
}

/** The home backdrop: picture, dim and blur, with a clock on top. */
@Composable
internal fun BackdropPreview(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val s = settings.withPreview(LocalPreviewOverrides.current)
    PreviewFrame(height = 96.dp, modifier = modifier.padding(vertical = 6.dp)) {
        Backdrop(path = s.homeBackgroundImage, blur = s.homeWallpaperBlur, dim = s.homeWallpaperDim)
        Text(
            "12:30",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** A widget card with the chosen corners and background. */
@Composable
internal fun CornerPreview(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val s = settings.withPreview(LocalPreviewOverrides.current)
    PreviewFrame(height = 92.dp, modifier = modifier.padding(vertical = 6.dp)) {
        Backdrop(path = "", blur = 0f, dim = 0.15f)
        WidgetCard(s, Modifier.align(Alignment.Center).width(170.dp).height(58.dp), solid = true)
    }
}

/** The long-press menu in the chosen style. */
@Composable
internal fun PopupThemePreview(theme: String, modifier: Modifier = Modifier) {
    PreviewFrame(height = 116.dp, modifier = modifier.padding(vertical = 6.dp)) {
        Backdrop(path = "", blur = 0f, dim = 0.1f)
        MenuCard(theme = theme, modifier = Modifier.align(Alignment.Center).width(180.dp))
    }
}

/** The folder tile in the chosen shape and theme. */
@Composable
internal fun FolderStylePreview(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val s = settings.withPreview(LocalPreviewOverrides.current)
    PreviewFrame(height = 96.dp, modifier = modifier.padding(vertical = 6.dp)) {
        Backdrop(path = "", blur = 0f, dim = 0f)
        Box(Modifier.align(Alignment.Center)) { FolderTile(s, LocalPreviewApps.current.take(4), 60.dp) }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun PreviewFrame(height: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val palette = LocalExpressivePalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, palette.faint.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
        content = content,
    )
}

/** The home picture (or the wallpaper, or a fallback gradient), blurred and dimmed like home. */
@Composable
private fun BoxScope.Backdrop(path: String, blur: Float, dim: Float) {
    val picture = rememberBackdrop(path)
    val blurModifier = if (blur > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(blur.dp) else Modifier
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().then(blurModifier),
        )
    } else {
        Box(
            Modifier
                .matchParentSize()
                .then(blurModifier)
                .background(Brush.linearGradient(listOf(Color(0xFF1B2A4E), Color(0xFF3B1D3F), Color(0xFF0D0E12))))
                .background(
                    Brush.radialGradient(
                        listOf(Accent.copy(alpha = 0.75f), Color.Transparent),
                        center = Offset(80f, 60f),
                        radius = 260f,
                    ),
                )
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF38BDF8).copy(alpha = 0.6f), Color.Transparent),
                        center = Offset(560f, 260f),
                        radius = 300f,
                    ),
                ),
        )
    }
    if (dim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim.coerceIn(0f, 1f))))
}

@Composable
private fun rememberBackdrop(path: String): ImageBitmap? {
    if (path.isBlank()) return null
    val context = LocalContext.current
    var picture by remember(path) { mutableStateOf(backdropCache[path]) }
    LaunchedEffect(path) {
        if (picture == null) {
            picture = withContext(Dispatchers.IO) { loadPicture(context, path) }?.also { backdropCache[path] = it }
        }
    }
    return picture
}

/** Decoded pictures by path (a settings session shows one or two). */
private val backdropCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

private fun loadPicture(context: Context, path: String): ImageBitmap? = runCatching {
    val file = File(path)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= PREVIEW_PICTURE_PX && bounds.outHeight / (sample * 2) >= PREVIEW_PICTURE_PX) sample *= 2
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

private const val PREVIEW_PICTURE_PX = 600

@Composable
private fun previewDate(): String = remember {
    runCatching { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }.getOrDefault("")
}

/** A widget card: the stack corner radius, and the widget background (tonal or dark, its opacity). */
@Composable
private fun WidgetCard(s: LauncherSettings, modifier: Modifier, solid: Boolean = false) {
    val shape = RoundedCornerShape(s.stackCornerRadius.coerceAtLeast(0).dp)
    val base = if (s.widgetTonalBackground) MaterialTheme.colorScheme.surfaceContainer else Color.Black.copy(alpha = 0.15f)
    // A transparent widget background would hide the corners: the preview shows them faintly.
    val opacity = if (solid) s.widgetBackgroundOpacity.coerceAtLeast(0.6f) else s.widgetBackgroundOpacity
    Box(
        modifier = modifier
            .clip(shape)
            .background(base.copy(alpha = base.alpha * opacity))
            .border(1.dp, Color.White.copy(alpha = 0.2f * opacity.coerceAtLeast(0.4f)), shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(Icons.Rounded.Widgets, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Column {
                Text(stringResource(R.string.settings_preview_widget), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "%d dp".format(s.stackCornerRadius),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** A folder tile as home draws it: shape, theme (glass with frost and tint, or a solid colour). */
@Composable
private fun FolderTile(s: LauncherSettings, apps: List<AppItem>, size: Dp) {
    val scale = size / 52.dp
    val circle = s.folderShape == LauncherSettings.FOLDER_SHAPE_CIRCLE
    val shape: Shape = when (s.folderShape) {
        LauncherSettings.FOLDER_SHAPE_CIRCLE -> CircleShape
        LauncherSettings.FOLDER_SHAPE_SQUARE -> RoundedCornerShape(6.dp * scale)
        else -> RoundedCornerShape(15.dp * scale)
    }
    val inset = (if (circle) 10.dp else 6.dp) * scale
    val gap = 2.dp * scale
    val mini = (size - inset * 2 - gap) / 2
    val inside: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize().padding(inset),
        ) {
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until 2) {
                        val app = apps.getOrNull(row * 2 + column)
                        if (app != null) {
                            AppIcon(appItem = app, labelColor = Color.Transparent, iconSize = mini, showLabel = false, aquamorphicTouchEnabled = false)
                        } else {
                            Spacer(Modifier.size(mini))
                        }
                    }
                }
            }
        }
    }
    val solid = when (s.folderTheme) {
        LauncherSettings.FOLDER_THEME_DARK -> Color(0xE6131418)
        LauncherSettings.FOLDER_THEME_LIGHT -> Color(0xEBF5F5F7)
        LauncherSettings.FOLDER_THEME_ACCENT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
        else -> null
    }
    if (solid == null) {
        LiquidGlassContainer(
            blurRadiusDp = s.glassBlurRadius,
            darkTintAlpha = s.glassDarkTint,
            shape = shape,
            modifier = Modifier.size(size),
        ) { inside() }
    } else {
        Box(Modifier.size(size).clip(shape).background(solid)) { inside() }
    }
}

/** A long-press menu with two rows, in the colours of menu style [theme]. */
@Composable
private fun MenuCard(theme: String, modifier: Modifier = Modifier) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val (palette: ExpressivePalette, accent: Color) = when (theme) {
        LauncherSettings.POPUP_THEME_MATERIAL -> ExpressivePalette(
            bg = scheme.surface,
            surfaceHi = scheme.surfaceContainerHigh,
            text = scheme.onSurface,
            dim = scheme.onSurfaceVariant,
            faint = scheme.outline,
            trackOff = scheme.outlineVariant,
            thumbOff = scheme.outline,
            btn = scheme.onSurface.copy(alpha = 0.07f),
            shadow = 0.dp,
        ) to scheme.primary
        LauncherSettings.POPUP_THEME_DARK -> DarkExpressivePalette to Accent
        LauncherSettings.POPUP_THEME_LIGHT -> LightExpressivePalette to Accent
        LauncherSettings.POPUP_THEME_GLASS -> DarkExpressivePalette.copy(surfaceHi = Color.Black.copy(alpha = 0.38f)) to Accent
        else -> (if (dark) DarkExpressivePalette else LightExpressivePalette) to Accent
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surfaceHi)
            .padding(6.dp),
    ) {
        listOf(
            Icons.Rounded.Info to R.string.settings_preview_menu_info,
            Icons.Rounded.Widgets to R.string.settings_preview_menu_widgets,
        ).forEach { (icon, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.btn)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(stringResource(label), color = palette.text, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
