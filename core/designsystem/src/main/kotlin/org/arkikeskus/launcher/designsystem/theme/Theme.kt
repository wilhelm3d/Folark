package org.arkikeskus.launcher.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Typography
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

/**
 * The launcher theme. Defaults to the branded Arkikeskus color scheme; Material You dynamic color
 * is opt-in via [dynamicColor] (off by default so the brand identity holds out of the box).
 */
@Composable
fun LauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    /** Settings ▸ Appearance ▸ Typography: the font every text uses unless it picks its own. Null keeps the system font. */
    fontFamily: FontFamily? = null,
    /** Bold for every text that doesn't pick its own weight. */
    boldText: Boolean = false,
    /** Multiplies the text size on top of the system's font size. */
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && amoledDark) {
                base.copy(
                    background = Color.Black,
                    surface = Color.Black,
                )
            } else base
        }
        darkTheme -> {
            if (amoledDark) {
                DarkColorScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                )
            } else {
                DarkColorScheme
            }
        }
        else -> LightColorScheme
    }
    val launcherColors = if (darkTheme) DarkLauncherColors else LightLauncherColors

    val typography = remember(fontFamily, boldText) {
        LauncherTypography.withFont(fontFamily, if (boldText) FontWeight.Bold else null)
    }
    val density = LocalDensity.current
    val scaledDensity = remember(density, fontScale) {
        if (fontScale == 1f) density else Density(density.density, density.fontScale * fontScale)
    }
    CompositionLocalProvider(
        LocalLauncherColors provides launcherColors,
        LocalDensity provides scaledDensity,
    ) {
        // MaterialTheme also hands typography.bodyLarge down as the default text style, so plain
        // Text calls (app labels, menus, settings rows) pick the font up as well.
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

/** Every text style with [family] and [weight] in place of its own (null keeps the style's). */
private fun Typography.withFont(family: FontFamily?, weight: FontWeight?): Typography {
    if (family == null && weight == null) return this
    fun TextStyle.withFont() = copy(fontFamily = family ?: fontFamily, fontWeight = weight ?: fontWeight)
    return copy(
        displayLarge = displayLarge.withFont(),
        displayMedium = displayMedium.withFont(),
        displaySmall = displaySmall.withFont(),
        headlineLarge = headlineLarge.withFont(),
        headlineMedium = headlineMedium.withFont(),
        headlineSmall = headlineSmall.withFont(),
        titleLarge = titleLarge.withFont(),
        titleMedium = titleMedium.withFont(),
        titleSmall = titleSmall.withFont(),
        bodyLarge = bodyLarge.withFont(),
        bodyMedium = bodyMedium.withFont(),
        bodySmall = bodySmall.withFont(),
        labelLarge = labelLarge.withFont(),
        labelMedium = labelMedium.withFont(),
        labelSmall = labelSmall.withFont(),
    )
}
