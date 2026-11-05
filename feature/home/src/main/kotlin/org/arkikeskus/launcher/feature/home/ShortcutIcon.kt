package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.AppLabel

// How a shortcut (or app pair) icon is drawn on the workspace (split out of Workspace.kt).

/** The icon + label of a pinned deep shortcut (the gesture, menu and offset live at the call site). */
@Composable
internal fun ShortcutIconContent(
    shortcut: PlacedShortcut,
    showLabel: Boolean,
    labelColor: Color = Color.White,
    iconSize: Dp = 52.dp,
) {
    val pairApps = shortcut.pairApps
    if (pairApps != null) {
        // An app pair: both app icons side by side on one tile, under the pair's name.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(iconSize * 0.3f)),
            ) {
                Box(Modifier.align(Alignment.CenterStart).padding(start = iconSize * 0.05f)) {
                    AppIcon(
                        appItem = pairApps.first,
                        labelColor = labelColor,
                        showLabel = false,
                        iconSize = iconSize * 0.44f,
                        aquamorphicTouchEnabled = false,
                    )
                }
                Box(Modifier.align(Alignment.CenterEnd).padding(end = iconSize * 0.05f)) {
                    AppIcon(
                        appItem = pairApps.second,
                        labelColor = labelColor,
                        showLabel = false,
                        iconSize = iconSize * 0.44f,
                        aquamorphicTouchEnabled = false,
                    )
                }
                // P8: a peek scene wears a small peek mark (a big pane, a strip at its edge).
                if (shortcut.appPair?.let(::isPeekScene) == true) PeekMark(iconSize)
                // A three-app layout: the floating app, smaller, on top in the middle of the two —
                // on a dark disc so it reads as sitting above them.
                shortcut.pairThirdApp?.let { third ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(iconSize * 0.4f)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            appItem = third,
                            labelColor = labelColor,
                            showLabel = false,
                            iconSize = iconSize * 0.32f,
                            aquamorphicTouchEnabled = false,
                        )
                    }
                }
            }
            if (showLabel) AppLabel(shortcut.label, labelColor, reserveLines = true)
        }
        return
    }
    val floatingApp = shortcut.floatingApp
    if (floatingApp != null) {
        // A floating shortcut wears its app's own icon, plus a corner badge telling it apart from
        // the plain app icon it may sit next to.
        Box {
            AppIcon(
                appItem = floatingApp,
                labelColor = labelColor,
                showLabel = showLabel,
                iconSize = iconSize,
                reserveLabelLines = true,
                // The cell around a shortcut already carries the press feedback.
                aquamorphicTouchEnabled = false,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = iconSize * 0.38f, y = (-2).dp)
                    .size(iconSize * 0.38f)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(LauncherIcons.OpenInNew),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(iconSize * 0.24f),
                )
            }
        }
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val icon = shortcut.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = shortcut.label, modifier = Modifier.size(iconSize))
        } else {
            Box(Modifier.size(iconSize))
        }
        if (showLabel) AppLabel(shortcut.label, labelColor, reserveLines = true)
    }
}

/** A scene that opens as peek view: two apps in peek layout, or three with a narrow strip. */
internal fun isPeekScene(pair: org.arkikeskus.launcher.model.AppPair): Boolean {
    if (!pair.hasThirdApp) return pair.effectiveLayout == org.arkikeskus.launcher.model.AppPair.LAYOUT_PEEK ||
        pair.sizeList.singleOrNull()?.let { it < 150 || it > 850 } == true
    val sizes = pair.sizeList
    return sizes.size == 2 && (sizes[0] < 150 || sizes[1] > 850)
}

/** The peek mark on a scene's tile (P8), at its top end. */
@Composable
private fun PeekMark(iconSize: Dp) {
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(iconSize), contentAlignment = Alignment.TopEnd) {
        androidx.compose.foundation.Canvas(
            Modifier
                .offset(x = iconSize * 0.08f, y = -(iconSize * 0.06f))
                .size(width = iconSize * 0.42f, height = iconSize * 0.28f)
                .background(color, RoundedCornerShape(iconSize * 0.08f))
                .padding(iconSize * 0.04f),
        ) {
            val w = size.width
            val h = size.height
            val strip = w * 0.16f
            drawRoundRect(Color.White, size = androidx.compose.ui.geometry.Size(strip, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(strip / 2))
            drawRoundRect(
                Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(strip + w * 0.08f, 0f),
                size = androidx.compose.ui.geometry.Size(w - strip - w * 0.08f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.14f),
            )
        }
    }
}
