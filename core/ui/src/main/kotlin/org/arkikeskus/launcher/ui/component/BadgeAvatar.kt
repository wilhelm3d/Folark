package org.arkikeskus.launcher.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/**
 * The photo of whoever wrote last, per app ([org.arkikeskus.launcher.model.AppItem.badgeKey]), for
 * chat apps that attach one (Settings ▸ Notifications ▸ Sender photo on dots). Empty when off.
 */
val LocalBadgeAvatars = compositionLocalOf { emptyMap<String, ImageBitmap>() }

/** Room Folark's taskbar takes at the bottom while it stays on home (beyond the navigation bar). */
val LocalTaskbarInset = compositionLocalOf { 0.dp }

/**
 * A notification dot that shows the sender's photo, with the message count on its corner when
 * there is more than one and counts are on.
 */
@Composable
fun AvatarBadge(avatar: ImageBitmap, count: Int, showCount: Boolean, scale: Float = 1f, modifier: Modifier = Modifier) {
    val s = scale.coerceIn(0.5f, 2.5f)
    Box(modifier, contentAlignment = Alignment.BottomEnd) {
        Image(
            avatar,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size((22 * s).dp)
                .clip(CircleShape)
                .border((1.5f * s).dp, Color.White, CircleShape),
        )
        if (showCount && count > 1) {
            NotificationBadge(
                count = count,
                showCount = true,
                scale = s * 0.75f,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
