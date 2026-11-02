package org.arkikeskus.launcher.ui.drawer

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.expressive.ExpressivePalette

/**
 * Everything the app drawer paints with, resolved from one display profile's settings: the
 * background theme, the accent colour and the Liquid Glass switch. Outer and inner display each
 * resolve their own instance, so the two drawers can look completely different.
 */
@Immutable
data class DrawerColors(
    /** Opaque background colour of the chosen theme. */
    val base: Color,
    /** What the panel is actually filled with: [base], made translucent when glass is on. */
    val container: Color,
    val content: Color,
    val contentDim: Color,
    /** Fill of raised elements on the panel: search field, cards, folder tiles. */
    val field: Color,
    val accent: Color,
    val onAccent: Color,
    val glass: Boolean,
    val isLight: Boolean,
) {
    /** The expressive palette (search results, "most used" card) recoloured for this drawer. */
    fun toExpressivePalette(): ExpressivePalette = ExpressivePalette(
        bg = base,
        surfaceHi = field,
        text = content,
        dim = contentDim,
        faint = contentDim.copy(alpha = 0.7f),
        trackOff = contentDim.copy(alpha = 0.3f),
        thumbOff = content,
        btn = content.copy(alpha = 0.08f),
        shadow = 0.dp,
    )
}

/** Shape and type choices of a drawer style (Settings ▸ App drawer ▸ Style). */
@Immutable
data class DrawerStyleSpec(
    val corner: Dp,
    val searchShape: Shape,
    /** Outlined, unfilled search field (the dot-matrix look) instead of a filled pill. */
    val searchOutlined: Boolean,
    val fontFamily: FontFamily?,
    val handleWidth: Dp,
)

fun drawerStyleSpec(style: String): DrawerStyleSpec = when (style) {
    LauncherSettings.DRAWER_STYLE_ONE_UI -> DrawerStyleSpec(
        corner = 36.dp, searchShape = RoundedCornerShape(50), searchOutlined = false,
        fontFamily = null, handleWidth = 44.dp,
    )
    LauncherSettings.DRAWER_STYLE_NOTHING_OS -> DrawerStyleSpec(
        corner = 14.dp, searchShape = RoundedCornerShape(10.dp), searchOutlined = true,
        fontFamily = FontFamily.Monospace, handleWidth = 28.dp,
    )
    LauncherSettings.DRAWER_STYLE_MOTO -> DrawerStyleSpec(
        corner = 24.dp, searchShape = RoundedCornerShape(16.dp), searchOutlined = false,
        fontFamily = null, handleWidth = 36.dp,
    )
    else -> DrawerStyleSpec(
        corner = 28.dp, searchShape = RoundedCornerShape(22.dp), searchOutlined = false,
        fontFamily = null, handleWidth = 36.dp,
    )
}

/** Black or white, whichever reads better on [background]. */
fun readableOn(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF14161B) else Color.White

@Composable
fun rememberDrawerColors(
    theme: String,
    accentArgb: Int,
    customArgb: Int,
    glass: Boolean,
    panelOpacity: Float,
): DrawerColors {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    // Material You reads the system (wallpaper) palette; below Android 12 it falls back to the
    // launcher scheme, which is the closest thing available there.
    // Keyed on the configuration: a wallpaper change reaches the app as a configuration change.
    val configuration = LocalConfiguration.current
    val dynamic = remember(theme, dark, configuration) {
        if (theme == LauncherSettings.DRAWER_THEME_MATERIAL_YOU &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            null
        }
    }
    return remember(theme, accentArgb, customArgb, glass, panelOpacity, dark, scheme, dynamic) {
        val userAccent = Color(accentArgb).copy(alpha = 1f)
        val base = when (theme) {
            LauncherSettings.DRAWER_THEME_DEFAULT_DARK -> Color(0xFF14161C)
            LauncherSettings.DRAWER_THEME_OBSIDIAN_BLACK -> Color.Black
            LauncherSettings.DRAWER_THEME_LIGHT -> Color(0xFFF5F6FA)
            LauncherSettings.DRAWER_THEME_ACCENT_TINT ->
                lerp(if (dark) Color(0xFF101216) else Color(0xFFF7F8FB), userAccent, if (dark) 0.24f else 0.16f)
            LauncherSettings.DRAWER_THEME_MONOCHROME -> if (dark) Color(0xFF1A1A1A) else Color(0xFFF1F1F1)
            LauncherSettings.DRAWER_THEME_CUSTOM -> Color(customArgb).copy(alpha = 1f)
            LauncherSettings.DRAWER_THEME_MATERIAL_YOU -> dynamic?.surfaceContainer ?: scheme.surface
            else -> scheme.surface
        }
        val isLight = base.luminance() > 0.45f
        val content = if (isLight) Color(0xFF14161B) else Color(0xFFF2F4F7)
        val accent = when (theme) {
            LauncherSettings.DRAWER_THEME_MONOCHROME -> content
            LauncherSettings.DRAWER_THEME_MATERIAL_YOU -> dynamic?.primary ?: scheme.primary
            else -> userAccent
        }
        val opacity = panelOpacity.coerceIn(
            LauncherSettings.MIN_DRAWER_PANEL_OPACITY,
            LauncherSettings.MAX_DRAWER_PANEL_OPACITY,
        )
        DrawerColors(
            base = base,
            container = if (glass) base.copy(alpha = opacity) else base,
            content = content,
            contentDim = content.copy(alpha = 0.64f),
            // On glass the raised elements are a veil of the content colour, so they stay glassy;
            // on a solid panel they are an opaque step away from the background.
            field = if (glass) content.copy(alpha = 0.12f) else content.copy(alpha = 0.08f).compositeOver(base),
            accent = accent,
            onAccent = readableOn(accent),
            glass = glass,
            isLight = isLight,
        )
    }
}
