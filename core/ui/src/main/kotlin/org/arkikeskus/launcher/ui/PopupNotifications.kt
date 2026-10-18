package org.arkikeskus.launcher.ui

import android.graphics.drawable.Icon
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.ui.component.squareBitmap
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

/** One of an app's notifications, as the long-press menu shows it. */
class PopupNotification(
    val key: String,
    val packageName: String,
    val userSerial: Long,
    val title: String,
    val text: String,
    val postTime: Long,
    val icon: Icon?,
    /** Opens it like a tap in the notification shade. */
    val open: () -> Unit,
    /** Dismisses it like a swipe in the shade. */
    val dismiss: () -> Unit,
)

/**
 * Every app's current notifications, newest first (Settings ▸ Notifications ▸ Notifications in
 * the long-press menu); empty when that is off or there is no notification access.
 */
val LocalAppNotifications = compositionLocalOf { emptyList<PopupNotification>() }

/** Most an app's menu lists before its own shortcuts. */
internal const val MAX_POPUP_NOTIFICATIONS = 3

@Composable
internal fun PopupNotificationRow(notification: PopupNotification, onOpened: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalExpressivePalette.current
    val icon: ImageBitmap? = remember(notification.key, notification.postTime) {
        runCatching { notification.icon?.loadDrawable(context)?.squareBitmap(40)?.asImageBitmap() }.getOrNull()
    }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                notification.open()
                onOpened()
            }
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(Modifier.padding(top = 2.dp).size(20.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(icon, contentDescription = null, colorFilter = ColorFilter.tint(LocalAccent.current), modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notification.title,
                    color = palette.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    " · " + DateUtils.getRelativeTimeSpanString(
                        notification.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ),
                    color = palette.dim,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            if (notification.text.isNotBlank()) {
                Text(
                    notification.text,
                    color = palette.dim,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).clickable { notification.dismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(LauncherIcons.Close),
                contentDescription = stringResource(R.string.popup_notification_dismiss),
                tint = palette.dim,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
