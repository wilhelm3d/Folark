package org.arkikeskus.launcher.feature.home.widgets

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R
import org.arkikeskus.launcher.feature.home.workspace.*
import org.arkikeskus.launcher.feature.home.taskbar.*

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
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
import androidx.compose.ui.res.stringResource
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
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.SplitCoordinator
import org.arkikeskus.launcher.data.StatusNotification
import org.arkikeskus.launcher.model.ScreenType
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalScreenType
import javax.inject.Inject

@HiltViewModel
class InteractiveNotificationWidgetViewModel @Inject constructor(
    private val badgeRepository: NotificationBadgeRepository,
    private val appRepository: AppRepository,
    private val splitCoordinator: SplitCoordinator,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** A long press offers "Open in split screen" (Settings ▸ Recents ▸ Split screen). */
    val splitOn = settingsRepository.settings(ScreenType.INNER).map { it.recents.notificationSplit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Opens [notification] beside the app used last, in split screen; when that can't be done it
     * opens on its own, as a tap would.
     */
    fun openInSplit(notification: StatusNotification, context: Context) {
        val intent = notification.contentIntent ?: return invokeAction(null, notification.packageName, context)
        viewModelScope.launch {
            val userId = runCatching {
                context.getSystemService(android.os.UserManager::class.java)?.getUserForSerialNumber(notification.userSerial)?.hashCode()
            }.getOrNull() ?: (android.os.Process.myUid() / 100_000)
            if (!splitCoordinator.openBeside(intent, notification.packageName, userId)) {
                android.widget.Toast.makeText(context, R.string.notification_open_split_failed, android.widget.Toast.LENGTH_SHORT).show()
                invokeAction(intent, notification.packageName, context)
            }
        }
    }

    val allNotifications = badgeRepository.allNotifications
    val pinnedKeys = badgeRepository.pinnedKeys

    val appsByPackage = appRepository.apps.map { apps ->
        apps.associateBy { it.packageName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun togglePin(key: String) {
        badgeRepository.togglePin(key)
    }

    /** Hides one of an app's notification channels from every Folark surface (Android's shade untouched). */
    fun hideChannel(packageName: String, channelId: String) = viewModelScope.launch {
        settingsRepository.setChannelHidden(packageName, channelId)
    }

    fun cancelNotification(key: String) {
        badgeRepository.cancelNotification(key)
    }

    fun clearAllUnpinned() {
        badgeRepository.cancelAllNotifications(badgeRepository.pinnedKeys.value.toList())
    }

    fun invokeAction(pendingIntent: PendingIntent?, packageName: String? = null, context: Context? = null) {
        if (pendingIntent != null) {
            // Sent the way a tap in the shade sends it: Android 14+ otherwise blocks the app from
            // opening its screen, since the launcher's right to start an activity isn't passed on.
            val sent = if (context != null) {
                sendNotificationIntent(context, pendingIntent)
            } else {
                runCatching { pendingIntent.send() }.isSuccess
            }
            if (!sent) launchFallback(packageName, context)
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
            android.util.Log.w("NotificationWidget", "can't open $packageName", e)
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
    val splitOn by viewModel.splitOn.collectAsStateWithLifecycle()

    val grouped = remember(notifications) {
        notifications.groupBy { it.packageName }
    }

    // Seamless: no border and no background, only the notifications over the wallpaper.
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
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
                    text = stringResource(R.string.notifications_widget_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (notifications.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearAllUnpinned() }) {
                        Text(stringResource(R.string.notifications_clear_all), color = Color.White)
                    }
                }
            }

            if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.notifications_empty), color = Color.White.copy(alpha = 0.6f))
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
                                onContentClick = { viewModel.invokeAction(notif.contentIntent, notif.packageName, context) },
                                onOpenInSplit = if (splitOn && notif.contentIntent != null) {
                                    { viewModel.openInSplit(notif, context) }
                                } else {
                                    null
                                },
                                onHideChannel = notif.channelId?.let { id ->
                                    { viewModel.hideChannel(notif.packageName, id) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun InteractiveNotificationItem(
    notification: StatusNotification,
    isPinned: Boolean,
    onCancel: () -> Unit,
    onTogglePin: () -> Unit,
    onActionClick: (PendingIntent?) -> Unit,
    onContentClick: () -> Unit,
    /** A long press offers to open it in split screen (null: no long press). */
    onOpenInSplit: (() -> Unit)? = null,
    /** Hides this notification's channel from Folark's surfaces (null: the post had no channel). */
    onHideChannel: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    // The veto callback is deprecated in favour of dynamic anchors; it is still the simplest way to
    // let a swipe dismiss only past the threshold here.
    @Suppress("DEPRECATION")
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
                .combinedClickable(
                    onClick = { onContentClick() },
                    onLongClick = onOpenInSplit?.let { { menu = true } },
                )
                .padding(12.dp)
        ) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notification_open_split)) },
                    onClick = {
                        menu = false
                        onOpenInSplit?.invoke()
                    },
                )
            }
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
                // A small mute at the row's end: this notification's channel stops showing on any
                // of Folark's surfaces (the app's other notifications keep coming).
                if (onHideChannel != null) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = stringResource(org.arkikeskus.launcher.feature.home.R.string.notif_hide_channel),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onHideChannel),
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
                    
                    // Render system-provided Smart Replies (Android 10+)
                    val context = LocalContext.current
                    notification.actions.forEach { action ->
                        action.remoteInputs?.forEach { remoteInput ->
                            // Use app-provided choices, or default to a standard reply if empty
                            val choices = remoteInput.choices ?: arrayOf("Yes", "No", "Thanks!")
                            choices.forEach { choice ->
                                OutlinedButton(
                                    onClick = { 
                                        val intent = Intent().apply {
                                            android.app.RemoteInput.addResultsToIntent(
                                                arrayOf(remoteInput),
                                                this,
                                                android.os.Bundle().apply { putCharSequence(remoteInput.resultKey, choice) }
                                            )
                                        }
                                        org.arkikeskus.launcher.data.PendingIntents.sendFillIn(context, action.actionIntent, intent)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(choice.toString(), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberNotifSmallIcon(n: StatusNotification, size: Dp): ImageBitmap? {
    val px = with(LocalDensity.current) { size.roundToPx() }
    return rememberNotificationIcon(n.icon, n.key + "@" + n.postTime, px)
}
