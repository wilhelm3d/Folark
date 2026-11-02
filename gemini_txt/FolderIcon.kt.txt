package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.AppLabel
import org.arkikeskus.launcher.ui.component.LiquidGlassContainer
import org.arkikeskus.launcher.ui.component.NotificationBadge
import org.arkikeskus.launcher.ui.component.badgeAlignment

/**
 * How home folders look (Settings ▸ Grid ▸ Folders): the closed tile's [shape], colour [theme] and
 * [preview], and the opened folder's grid [openColumns] and [openSize]. The values are the
 * `LauncherSettings.FOLDER_*` constants.
 */
data class FolderStyle(
    val shape: String = LauncherSettings.FOLDER_SHAPE_SQUIRCLE,
    val theme: String = LauncherSettings.FOLDER_THEME_GLASS,
    val preview: String = LauncherSettings.FOLDER_PREVIEW_GRID_2,
    val openColumns: Int = 4,
    val openSize: String = LauncherSettings.FOLDER_OPEN_MEDIUM,
)

/** The folder style of the display in use; provided by HomeScreen from the settings. */
val LocalFolderStyle = compositionLocalOf { FolderStyle() }

/** The solid background of a folder [theme], or null for Liquid Glass (which is not one colour). */
@Composable
internal fun folderThemeColor(theme: String): Color? = when (theme) {
    LauncherSettings.FOLDER_THEME_DARK -> Color(0xE6131418)
    LauncherSettings.FOLDER_THEME_LIGHT -> Color(0xEBF5F5F7)
    LauncherSettings.FOLDER_THEME_ACCENT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
    else -> null
}

/** The text colour that reads on a folder [theme]'s background. */
@Composable
internal fun folderContentColor(theme: String): Color = when (theme) {
    LauncherSettings.FOLDER_THEME_DARK -> Color.White
    LauncherSettings.FOLDER_THEME_LIGHT -> Color(0xFF1B1B1F)
    LauncherSettings.FOLDER_THEME_ACCENT -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSurface
}

/** A folder background in the chosen [theme]: Liquid Glass, or a solid themed colour. */
@Composable
internal fun FolderSurface(
    theme: String,
    shape: Shape,
    glassBlurRadius: Float,
    glassDarkTint: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val color = folderThemeColor(theme)
    if (color == null) {
        LiquidGlassContainer(
            blurRadiusDp = glassBlurRadius,
            darkTintAlpha = glassDarkTint,
            shape = shape,
            modifier = modifier,
        ) { content() }
    } else {
        Box(modifier = modifier.clip(shape).background(color)) { content() }
    }
}

/**
 * A home-screen folder: a tile previewing its apps (a 2×2 or 3×3 grid, or the first app as a cover),
 * an optional label, and a notification badge (aggregated from its contents). Shape, theme and
 * preview come from [LocalFolderStyle].
 */
@Composable
fun FolderIcon(
    name: String,
    apps: List<AppItem>,
    showLabel: Boolean,
    badgeCount: Int,
    badgeShowCount: Boolean,
    badgeScale: Float = 1f,
    labelColor: Color = Color.White,
    size: Dp = 52.dp,
    glassBlurRadius: Float = 25f,
    glassDarkTint: Float = 0.45f,
    modifier: Modifier = Modifier,
) {
    val style = LocalFolderStyle.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = badgeAlignment()) {
            // EVERYTHING inside scales with the tile — padding and gap included. A fixed padding/gap
            // with linearly scaled icons inverted the margin below ~45dp tiles (6–7 columns on a
            // narrow screen), squeezing the preview asymmetrically out of its card.
            val scale = size / 52.dp
            val circle = style.shape == LauncherSettings.FOLDER_SHAPE_CIRCLE
            val tileShape: Shape = when (style.shape) {
                LauncherSettings.FOLDER_SHAPE_CIRCLE -> CircleShape
                LauncherSettings.FOLDER_SHAPE_SQUARE -> RoundedCornerShape(6.dp * scale)
                else -> RoundedCornerShape(15.dp * scale)
            }
            // A circle cuts the corners off, so its preview sits further in.
            val inset = (if (circle) 10.dp else 6.dp) * scale
            val gap = 2.dp * scale
            FolderSurface(
                theme = style.theme,
                shape = tileShape,
                glassBlurRadius = glassBlurRadius,
                glassDarkTint = glassDarkTint,
                modifier = Modifier.size(size),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inset),
                    contentAlignment = Alignment.Center,
                ) {
                    if (style.preview == LauncherSettings.FOLDER_PREVIEW_COVER) {
                        PreviewSlot(apps.firstOrNull(), size - inset * 2)
                    } else {
                        val perSide = if (style.preview == LauncherSettings.FOLDER_PREVIEW_GRID_3) 3 else 2
                        val mini = (size - inset * 2 - gap * (perSide - 1)) / perSide
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            for (row in 0 until perSide) {
                                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                    for (column in 0 until perSide) {
                                        PreviewSlot(apps.getOrNull(row * perSide + column), mini)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            NotificationBadge(count = badgeCount, showCount = badgeShowCount, scale = badgeScale)
        }
        // Home-only tile in a fixed centred cell: reserve the full label block (see AppLabel).
        if (showLabel) AppLabel(name, labelColor, reserveLines = true)
    }
}

@Composable
private fun PreviewSlot(app: AppItem?, size: Dp) {
    if (app == null) {
        Spacer(Modifier.size(size))
    } else {
        // Purely a picture: the tile around it owns the touch feedback and the gestures.
        AppIcon(
            appItem = app,
            labelColor = Color.Transparent,
            showLabel = false,
            iconSize = size,
            aquamorphicTouchEnabled = false,
        )
    }
}
