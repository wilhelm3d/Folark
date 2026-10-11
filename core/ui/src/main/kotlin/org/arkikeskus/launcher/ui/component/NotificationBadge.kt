package org.arkikeskus.launcher.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.LauncherSettings

/** Red dot used for notification badges; the white ring keeps it readable on any background. */
private val NotificationDotColor = Color(0xFFE53935)

/**
 * How notification dots look under this subtree (Settings ▸ Notifications): shape, colour and the
 * corner of the icon they sit on. The values are the `LauncherSettings.DOT_*` constants.
 */
data class BadgeStyle(
    val shape: String = LauncherSettings.DOT_SHAPE_CIRCLE,
    val color: String = LauncherSettings.DOT_COLOR_RED,
    val position: String = LauncherSettings.DOT_POSITION_TOP_END,
)

val LocalBadgeStyle = compositionLocalOf { BadgeStyle() }

/** The corner a badge sits on — the `contentAlignment` of the Box that holds an icon and its badge. */
@Composable
fun badgeAlignment(): Alignment = when (LocalBadgeStyle.current.position) {
    LauncherSettings.DOT_POSITION_TOP_START -> Alignment.TopStart
    LauncherSettings.DOT_POSITION_BOTTOM_END -> Alignment.BottomEnd
    LauncherSettings.DOT_POSITION_BOTTOM_START -> Alignment.BottomStart
    else -> Alignment.TopEnd
}

/**
 * A notification badge: a numeric count (Nova-style) when [showCount] is true, otherwise a plain dot
 * (Pixel-style). [scale] multiplies the size (1.0 = default). Shared by [AppIcon] and the folder icon;
 * shape and colour come from [LocalBadgeStyle].
 */
@Composable
fun NotificationBadge(count: Int, showCount: Boolean, scale: Float = 1f, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val s = scale.coerceIn(0.5f, 2.5f)
    val style = LocalBadgeStyle.current
    val shape: Shape = when (style.shape) {
        LauncherSettings.DOT_SHAPE_SQUIRCLE -> RoundedCornerShape(percent = 34)
        LauncherSettings.DOT_SHAPE_SQUARE -> RoundedCornerShape((2.5f * s).dp)
        else -> CircleShape
    }
    val fill: Color
    val ring: Color
    val textColor: Color
    when (style.color) {
        LauncherSettings.DOT_COLOR_ACCENT -> {
            fill = MaterialTheme.colorScheme.primary
            ring = Color.White
            textColor = MaterialTheme.colorScheme.onPrimary
        }
        LauncherSettings.DOT_COLOR_WHITE -> {
            fill = Color.White
            ring = Color.Black.copy(alpha = 0.28f)
            textColor = Color(0xFF1B1B1F)
        }
        else -> {
            fill = NotificationDotColor
            ring = Color.White
            textColor = Color.White
        }
    }
    if (showCount) {
        Box(
            modifier = modifier
                .defaultMinSize(minWidth = (16 * s).dp, minHeight = (16 * s).dp)
                .border((1.5f * s).dp, ring, shape)
                .background(fill, shape)
                .padding(horizontal = (4 * s).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                color = textColor,
                fontSize = (9 * s).sp,
                lineHeight = (10 * s).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    } else {
        Box(
            modifier = modifier
                .size((11 * s).dp)
                .border((1.5f * s).dp, ring, shape)
                .background(fill, shape),
        )
    }
}
