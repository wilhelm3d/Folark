package org.arkikeskus.launcher.feature.home

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.arkikeskus.launcher.data.StatusNotification
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.NotificationBadge
import androidx.compose.ui.Alignment

/**
 * Category Card Interface Briefing Card Notification Widget:
 * Features a clean rounded M3 card with subtle background tint, active notification
 * summary header ("Notifications"), grouped app notification icons row, latest
 * notification title & body snippet, and unread count badge.
 */
@Composable
fun SamsungNotificationWidget(
    modifier: Modifier = Modifier,
    viewModel: NotificationsWidgetViewModel = hiltViewModel(),
    screenType: ScreenType = LocalScreenType.current,
) {
    LaunchedEffect(screenType) { viewModel.setScreenType(screenType) }
    CompositionLocalProvider(LocalScreenType provides screenType) {
        val context = LocalContext.current
    val hasAccess by viewModel.hasAccess.collectAsStateWithLifecycle()
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    val countStyle by viewModel.countStyle.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val noIndication = remember { MutableInteractionSource() }
    val totalUnread = slots.sumOf { it.notification.count }
    val latestSlot = slots.firstOrNull()

    WidgetSurface(
        modifier = modifier.clickable(interactionSource = noIndication, indication = null) {
            if (!hasAccess) {
                openNotificationListenerSettings(context)
            } else if (slots.isNotEmpty()) {
                viewModel.open(slots.first())
            } else {
                NotificationShade.expand(context)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header: "Notifications" summary + unread count badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_notification_generic),
                        contentDescription = null,
                        tint = widgetContentColor(),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.notifications_widget_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = widgetContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (totalUnread > 0) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$totalUnread",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            if (!hasAccess) {
                Text(
                    text = stringResource(R.string.notifications_widget_allow_access),
                    style = MaterialTheme.typography.bodyMedium,
                    color = widgetContentColor().copy(alpha = 0.85f),
                )
            } else if (slots.isEmpty()) {
                Text(
                    text = stringResource(R.string.widget_search_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = widgetContentColor().copy(alpha = 0.6f),
                )
            } else {
                // Grouped app notification icons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    slots.take(5).forEach { slot ->
                        SamsungNotificationSlot(slot, 36.dp, countStyle, noIndication) {
                            viewModel.open(slot)
                        }
                    }
                    if (slots.size > 5) {
                        Text(
                            text = "+${slots.size - 5}",
                            style = MaterialTheme.typography.labelMedium,
                            color = widgetContentColor().copy(alpha = 0.8f),
                            modifier = Modifier.clickable(interactionSource = noIndication, indication = null) {
                                NotificationShade.expand(context)
                            },
                        )
                    }
                }

                // Latest notification title & body snippet
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val appName = latestSlot?.app?.label ?: latestSlot?.notification?.packageName ?: ""
                    val snippetText = if (appName.isNotEmpty()) {
                        "$appName: ${latestSlot?.notification?.count ?: 1} active notification(s)"
                    } else {
                        stringResource(R.string.notifications_widget_name)
                    }
                    Text(
                        text = appName.ifEmpty { stringResource(R.string.notifications_widget_name) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = widgetContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = snippetText,
                        style = MaterialTheme.typography.bodySmall,
                        color = widgetContentColor().copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
}

@Composable
private fun SamsungNotificationSlot(
    slot: NotificationsWidgetViewModel.Slot,
    iconSize: Dp,
    countStyle: String,
    interaction: MutableInteractionSource,
    onOpen: () -> Unit,
) {
    val badgeCount = if (countStyle == LauncherSettings.COUNT_NONE) 0 else slot.notification.count
    val showCount = countStyle == LauncherSettings.COUNT_NUMBER
    Box(
        modifier = Modifier.clickable(interactionSource = interaction, indication = null, onClick = onOpen),
    ) {
        val app = slot.app
        if (app != null && slot.showsAppIcon) {
            AppIcon(
                appItem = app,
                labelColor = Color.White,
                showLabel = false,
                iconSize = iconSize,
                badgeCount = badgeCount,
                badgeShowCount = showCount,
                badgeScale = 0.8f,
            )
        } else {
            val bitmap = rememberSamsungNotifSmallIcon(slot.notification, iconSize)
            Box(contentAlignment = Alignment.TopEnd) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = slot.notification.packageName,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(iconSize),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_notification_generic),
                        contentDescription = slot.notification.packageName,
                        tint = Color.White,
                        modifier = Modifier.size(iconSize),
                    )
                }
                NotificationBadge(count = badgeCount, showCount = showCount, scale = 0.8f)
            }
        }
    }
}

@Composable
private fun rememberSamsungNotifSmallIcon(n: StatusNotification, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    return remember(n.key, n.postTime, px) {
        runCatching { n.icon.loadDrawable(context)?.toBitmap(width = px, height = px)?.asImageBitmap() }.getOrNull()
    }
}
