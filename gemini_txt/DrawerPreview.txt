package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.liquidGlass
import org.arkikeskus.launcher.ui.drawer.drawerStyleSpec
import org.arkikeskus.launcher.ui.drawer.rememberDrawerColors

/**
 * A miniature of the app drawer as the edited profile renders it — theme, accent, style corners,
 * search position and Liquid Glass — over a stand-in wallpaper, so colour choices can be judged
 * without leaving the settings (or folding the phone to see the other display).
 */
@Composable
fun DrawerPreview(s: LauncherSettings, modifier: Modifier = Modifier) {
    val colors = rememberDrawerColors(
        theme = s.drawer.themeTint,
        accentArgb = s.drawer.accentColor,
        customArgb = s.drawer.customColor,
        glass = s.drawer.glassEnabled,
        panelOpacity = s.drawer.panelOpacity,
    )
    val style = drawerStyleSpec(s.drawer.style)
    val corner = (if (s.drawer.cornerRadius >= 0) s.drawer.cornerRadius.dp else style.corner) * 0.6f
    val searchAtBottom = s.drawer.searchPosition == LauncherSettings.SEARCH_BOTTOM

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3B2A6B), Color(0xFFB8457A), Color(0xFFF2A65A)),
                ),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Dimming behind the drawer, as the scrim setting applies it.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (s.drawer.scrimOpacity * 0.7f).coerceIn(0f, 0.8f))),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .fillMaxHeight(0.86f)
                .liquidGlass(
                    shape = RoundedCornerShape(topStart = corner, topEnd = corner),
                    tint = colors.container,
                    highlight = if (colors.glass) 1f else 0f,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(width = style.handleWidth * 0.6f, height = 3.dp)
                        .background(colors.contentDim.copy(alpha = 0.5f), CircleShape),
                )
                if (s.drawer.showSearch && !searchAtBottom) PreviewSearch(colors.field, colors.accent)
                repeat(2) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        repeat(4) { column ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .background(
                                            // One accent-coloured tile per row hints at where the accent shows up.
                                            if (column == row) colors.accent else colors.content.copy(alpha = 0.28f),
                                            RoundedCornerShape(9.dp),
                                        ),
                                )
                                if (s.drawer.showLabels) {
                                    Box(
                                        Modifier
                                            .size(width = 22.dp, height = 3.dp)
                                            .background(colors.contentDim, CircleShape),
                                    )
                                }
                            }
                        }
                    }
                }
                if (s.drawer.showSearch && searchAtBottom) PreviewSearch(colors.field, colors.accent)
            }
        }
    }
}

@Composable
private fun PreviewSearch(field: Color, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .background(field, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(accent, CircleShape))
    }
}
