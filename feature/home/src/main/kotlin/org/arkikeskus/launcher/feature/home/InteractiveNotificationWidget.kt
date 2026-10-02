package org.arkikeskus.launcher.feature.home

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import org.arkikeskus.launcher.ui.LauncherIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.NotificationBadgeRepository
import org.arkikeskus.launcher.data.StatusNotification
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalScreenType
import javax.inject.Inject

@HiltViewModel
class InteractiveNotificationWidgetViewModel @Inject constructor(
    private val badgeRepository: NotificationBadgeRepository,
    appRepository: AppRepository,
) : ViewModel() {

    val allNotifications = badgeRepository.allNotifications
    val pinnedKeys = badgeRepository.pinnedKeys

    val appsByPackage = appRepository.apps.map { apps ->
        apps.associateBy { it.packageName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun togglePin(key: String) {
        badgeRepository.togglePin(key)
    }

    fun cancelNotification(key: String) {
        badgeRepository.cancelNotification(key)
    }

    fun clearAllUnpinned() {
        badgeRepository.cancelAllNotifications(badgeRepository.pinnedKeys.value.toList())
    }

    fun invokeAction(pendingIntent: PendingIntent?, packageName: String? = null, context: Context? = null) {
        if (pendingIntent != null) {
            try {
                pendingIntent.send()
            } catch (e: Exception) {
                e.printStackTrace()
                launchFallback(packageName, context)
            }
        } else {
            launchFallback(packageName, context)
        }
    }
    
    private fun launchFallback(packageName: String?, context: Context?) {
        if (packageName == null || context == null) return
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun InteractiveNotificationWidget(
    modifier: Modifier = Modifier,
    viewModel: InteractiveNotificationWidgetViewModel = hiltViewModel()
) {
    val notifications by viewModel.allNotifications.collectAsStateWithLifecycle()
    val pinnedKeys by viewModel.pinnedKeys.collectAsStateWithLifecycle()
    val appsByPackage by viewModel.appsByPackage.collectAsStateWithLifecycle()

    val grouped = remember(notifications) {
        notifications.groupBy { it.packageName }
    }

    Surface(
        modifier = modifier.border(
            width = 0.5.dp,
            color = Color.White.copy(alpha = 0.3f),
            shape = RoundedCornerShape(24.dp)
        ),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.1f),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (notifications.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearAllUnpinned() }) {
                        Text("Clear All", color = Color.White)
                    }
                }
            }

            if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notifications", color = Color.White.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    grouped.forEach { (packageName, notifs) ->
                        val app = appsByPackage[packageName]
                        item(key = "header_$packageName") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                if (app != null) {
                                    AppIcon(
                                        appItem = app,
                                        labelColor = Color.White,
                                        showLabel = false,
                                        iconSize = 24.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = packageName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        
                        items(notifs, key = { it.key }) { notif ->
                            val context = LocalContext.current
                            InteractiveNotificationItem(
                                notification = notif,
                                isPinned = pinnedKeys.contains(notif.key),
                                onCancel = { viewModel.cancelNotification(notif.key) },
                                onTogglePin = { viewModel.togglePin(notif.key) },
                                onActionClick = { viewModel.invokeAction(it, notif.packageName, context) },
                                onContentClick = { viewModel.invokeAction(notif.contentIntent, notif.packageName, context) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveNotificationItem(
    notification: StatusNotification,
    isPinned: Boolean,
    onCancel: () -> Unit,
    onTogglePin: () -> Unit,
    onActionClick: (PendingIntent?) -> Unit,
    onContentClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onCancel()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onTogglePin()
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                }
            )
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> LauncherIcons.Pin
                SwipeToDismissBoxValue.EndToStart -> LauncherIcons.Delete
                else -> LauncherIcons.Delete
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = if (direction == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable { onContentClick() }
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val iconBitmap = rememberNotifSmallIcon(notification, 24.dp)
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_notification_generic),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    notification.title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    notification.text?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isPinned) {
                    Icon(
                        painter = painterResource(LauncherIcons.Pin),
                        contentDescription = "Pinned",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (notification.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    notification.actions.forEach { action ->
                        Button(
                            onClick = { onActionClick(action.actionIntent) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(action.title.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberNotifSmallIcon(n: StatusNotification, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    return remember(n.key, n.postTime, px) {
        runCatching { n.icon.loadDrawable(context)?.toBitmap(width = px, height = px)?.asImageBitmap() }.getOrNull()
    }
}
