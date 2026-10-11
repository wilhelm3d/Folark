package org.arkikeskus.launcher.feature.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalTonalWidgets = staticCompositionLocalOf { false }

/** How solid the widget cards are (Settings ▸ Widget background): 0 = see-through, 1 = as themed. */
val LocalWidgetBackgroundAlpha = staticCompositionLocalOf { 1f }

/** Below this the card no longer carries dark text, so widget text turns white (it sits on the wallpaper). */
internal const val WIDGET_LIGHT_TEXT_BELOW = 0.5f

@Composable
internal fun widgetContentColor(): Color =
    if (LocalTonalWidgets.current && LocalWidgetBackgroundAlpha.current >= WIDGET_LIGHT_TEXT_BELOW) {
        MaterialTheme.colorScheme.onSurface
    } else Color.White

@Composable
internal fun widgetSurfaceColor(): Color =
    (if (LocalTonalWidgets.current) MaterialTheme.colorScheme.surfaceContainer else Color.Black.copy(alpha = 0.15f))
        .let { it.copy(alpha = it.alpha * LocalWidgetBackgroundAlpha.current) }

@Composable
internal fun WidgetSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = modifier.border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.2f * LocalWidgetBackgroundAlpha.current),
            shape = RoundedCornerShape(24.dp),
        ),
        shape = RoundedCornerShape(24.dp),
        color = widgetSurfaceColor(),
        contentColor = widgetContentColor(),
    ) {
        Box(content = content)
    }
}
