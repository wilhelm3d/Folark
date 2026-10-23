package org.arkikeskus.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import org.arkikeskus.launcher.ui.component.LiveCard
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.feature.appdrawer.AppDrawerScreen
import org.arkikeskus.launcher.feature.home.HomeScreen
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalHomeCovered
import org.arkikeskus.launcher.ui.component.LocalBatterySaver
import org.arkikeskus.launcher.ui.component.WindowBlurBehind
import org.arkikeskus.launcher.ui.component.LocalIconPack
import org.arkikeskus.launcher.ui.component.LocalIconShape
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.LocalThemedIcons
import kotlin.math.roundToInt
import org.arkikeskus.launcher.ui.component.rememberKeyboardShown
import androidx.compose.foundation.layout.ime
import org.arkikeskus.launcher.ui.component.LocalTaskbarInset
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.feature.home.TaskbarSpace
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import org.arkikeskus.launcher.ui.component.LocalIconNudges
import org.arkikeskus.launcher.ui.component.IconNudges
import org.arkikeskus.launcher.ui.component.IconNudgeEvent
import androidx.compose.runtime.mutableStateMapOf
import org.arkikeskus.launcher.ui.component.LocalBadgeAvatars
import org.arkikeskus.launcher.ui.component.LivePill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import android.os.Build
import android.content.Context
import android.app.PendingIntent
import android.app.ActivityOptions

/**
 * Hosts the launcher surface: the home screen with the app drawer as a **finger-following** slide-up
 * overlay. A swipe up on home drives [progress] (0 = closed, 1 = open) so the drawer tracks the
 * finger; releasing flings/settles to the nearest end. A pull-down at the top of the drawer (or BACK
 * / HOME) closes it. [onOpenSettings] is a long-press on home / the launcher's own drawer icon.
 *
 * This is also the launcher's drag layer: it owns the shared [HomeDragController] and draws the single
 * floating icon above every surface, so an icon can be dragged from the dock, the home grid, **or the
 * app drawer** onto any of them. Dragging an app out of the drawer slides the drawer closed (so home
 * is visible to drop onto) while keeping the drawer composed, so the in-flight gesture isn't cancelled.
 */
@Composable
fun LauncherShell(
    homeSignals: Flow<Boolean>,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    shellCommands: Flow<String> = emptyFlow(),
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewModel = hiltViewModel<LauncherShellViewModel>()
    val screenType = LocalScreenType.current
    LaunchedEffect(screenType) {
        viewModel.setScreenType(screenType)
    }
    val iconStyle by viewModel.iconStyle.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val live by viewModel.live.collectAsStateWithLifecycle()
    val statusNotifications by viewModel.notifications.collectAsStateWithLifecycle()
    val avatars by viewModel.avatars.collectAsStateWithLifecycle()
    val shellSettings by viewModel.settings.collectAsStateWithLifecycle()
    val drawerHeightFraction by viewModel.drawerHeightFraction.collectAsStateWithLifecycle()
    val drawerWidthFraction by viewModel.drawerWidthFraction.collectAsStateWithLifecycle()
    val drawerAlignment by viewModel.drawerAlignment.collectAsStateWithLifecycle()
    // Bounds keep a high-initial-velocity settle (the fling-carried spring below) from overshooting
    // past the ends — an overshoot past 1 would briefly translate the drawer above the screen top.
    val progress = remember { Animatable(0f).apply { updateBounds(0f, 1f) } }
    var shellHeightPx by remember { mutableFloatStateOf(1f) }
    var shellOrigin by remember { mutableStateOf(Offset.Zero) }
    val settleSpring = remember { spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow) }

    // The shared drag state. Created here so both the home screen and the app drawer feed the same
    // controller and the floating icon below can travel between them.
    val dragController = rememberHomeDragController()
    var isDraggingDrawer by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val currentProgress by remember {
        derivedStateOf { if (isDraggingDrawer) dragProgress else progress.value }
    }

    val drawerOpen by remember { derivedStateOf { currentProgress > 0.001f } }
    // While an app is being dragged out of the drawer, keep the drawer composed even after it has
    // slid shut — otherwise removing it from composition would cancel the in-progress drag gesture.
    // Keyed on [moving] (not [isDragging]): a still long-press only lifts (menu), and must NOT fade
    // the drawer — otherwise it flashes out and back when the menu opens.
    val draggingFromDrawer by remember {
        derivedStateOf { dragController.moving && dragController.source == DragSource.Drawer }
    }
    // Smoothly cross-fade the drawer out as a drag-out begins (instead of a hard cut), then reset.
    val dragOutAlpha = remember { Animatable(1f) }
    LaunchedEffect(draggingFromDrawer) {
        if (draggingFromDrawer) dragOutAlpha.animateTo(0f, tween(durationMillis = 180))
        else dragOutAlpha.snapTo(1f)
    }

    // The single in-flight drawer-progress coroutine (a drag snapTo or a settle animateTo). Each new
    // drag/settle cancels the previous one, so a fast finger flick can't pile up competing snapTo/
    // animateTo jobs that make the drawer lag behind the finger.
    var dragJob by remember { mutableStateOf<Job?>(null) }

    fun animateProgress(target: Float) {
        isDraggingDrawer = false
        dragJob?.cancel()
        dragJob = scope.launch { progress.animateTo(target, settleSpring) }
    }

    fun dragDrawer(dyPx: Float) {
        if (!isDraggingDrawer) {
            isDraggingDrawer = true
            dragProgress = progress.value
        }
        val effectiveDrawerHeightPx = shellHeightPx.coerceAtLeast(1f)
        // Swipe up (negative dy) increases progress; clamp to [0, 1].
        val target = (dragProgress - dyPx / effectiveDrawerHeightPx).coerceIn(0f, 1f)
        dragProgress = target
        dragJob?.cancel()
        dragJob = scope.launch { progress.snapTo(target) }
    }

    fun settleDrawer(velocityPxPerSec: Float) {
        val currentVal = if (isDraggingDrawer) dragProgress else progress.value
        isDraggingDrawer = false
        val effectiveDrawerHeightPx = shellHeightPx.coerceAtLeast(1f)
        // Launcher3-style: a fling (release speed past the threshold) snaps to the direction of the
        // fling regardless of distance; otherwise settle by how far it was dragged. Velocity is px/s.
        val flingThreshold = 600f
        val target = when {
            velocityPxPerSec < -flingThreshold -> 1f // fast up → open
            velocityPxPerSec > flingThreshold -> 0f  // fast down → close
            else -> if (currentVal > 0.4f) 1f else 0f
        }
        dragJob?.cancel()
        dragJob = scope.launch {
            progress.snapTo(currentVal)
            // Carry the finger's release velocity into the spring (converted to progress units/s;
            // downward px velocity = negative progress velocity). Starting the spring from rest made a
            // fling-to-close visibly hesitate before accelerating — the "drawer lags coming down" feel.
            progress.animateTo(target, settleSpring, initialVelocity = -velocityPxPerSec / effectiveDrawerHeightPx)
        }
    }

    LaunchedEffect(homeSignals) {
        homeSignals.collect { animateProgress(0f) }
    }

    // Fold/unfold actions from the activity ("open the drawer / search after unfolding").
    LaunchedEffect(shellCommands) {
        shellCommands.collect { command ->
            when (command) {
                LauncherSettings.ACTION_DRAWER, LauncherSettings.ACTION_SEARCH -> animateProgress(1f)
                else -> Unit
            }
        }
    }

    // Liquid Glass backdrop: blur the wallpaper behind the launcher window as the drawer opens.
    // While an icon is dragged out of the drawer the home screen must be sharp again.
    val glassDrawer = shellSettings.drawer.glassEnabled
    // The home screen can carry a resting blur of its own (Settings ▸ Appearance); the drawer then
    // deepens it from there to its own radius as it opens.
    val batterySaver = LocalBatterySaver.current
    val homeBlurDp = if (batterySaver) 0f else shellSettings.home.wallpaperBlur
    val drawerBlurDp = if (batterySaver) 0f else maxOf(shellSettings.drawer.backgroundBlur * 2f, homeBlurDp)
    // The drawer (almost) fully open: what is underneath can stop animating.
    val homeCovered by remember { derivedStateOf { currentProgress > 0.97f } }
    WindowBlurBehind(maxRadiusDp = drawerBlurDp) {
        val progress = if (draggingFromDrawer) 0f else currentProgress
        if (drawerBlurDp <= 0f) 0f else (homeBlurDp + (drawerBlurDp - homeBlurDp) * progress) / drawerBlurDp
    }

    // Each app's notifications for its long-press menu (Settings ▸ Notifications), sender photos
    // for the dots: provided to home, dock and drawer alike.
    val popupNotifications = remember(statusNotifications, shellSettings.live.popupNotifications) {
        if (!shellSettings.live.popupNotifications) {
            emptyList()
        } else {
            statusNotifications.mapNotNull { n ->
                val title = n.title?.takeIf { it.isNotBlank() } ?: n.text?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PopupNotification(
                    key = n.key,
                    packageName = n.packageName,
                    userSerial = n.userSerial,
                    title = title,
                    text = if (n.title.isNullOrBlank()) "" else n.text.orEmpty(),
                    postTime = n.postTime,
                    icon = n.icon,
                    open = {
                        n.contentIntent?.let { openNotificationIntent(context, it) }
                        if (n.autoCancel) viewModel.dismissNotification(n.key)
                    },
                    dismiss = { viewModel.dismissNotification(n.key) },
                )
            }
        }
    }
    // A new notification nudges its app's icon (newest per app; each with its own id so a second
    // message replays the effect). Not in battery saver.
    val nudgeEvents = remember { mutableStateMapOf<String, IconNudgeEvent>() }
    LaunchedEffect(Unit) {
        var id = 0L
        viewModel.nudges.collect { n -> nudgeEvents[n.badgeKey] = IconNudgeEvent(n.badgeKey, n.color, n.time, ++id) }
    }
    val iconNudges = remember(shellSettings.icons.nudge, shellSettings.icons.nudgeEffect, shellSettings.icons.nudgeSoftness, batterySaver) {
        if (shellSettings.icons.nudge && !batterySaver) {
            IconNudges(shellSettings.icons.nudgeEffect, shellSettings.icons.nudgeSoftness, nudgeEvents)
        } else {
            null
        }
    }
    // Folark's taskbar staying on home (inner display): the dock and the drawer keep clear of it.
    val taskbarPx by TaskbarSpace.bottomPx.collectAsStateWithLifecycle()
    val navigationPx = WindowInsets.navigationBars.getBottom(density)
    // While the keyboard is up the bar steps aside (it would cover the keyboard), so no room is
    // kept for it either; the drawer and home make room for the keyboard instead.
    val keyboardUp by rememberKeyboardShown()
    LaunchedEffect(keyboardUp, screenType) {
        if (screenType == ScreenType.INNER) TaskbarSpace.setKeyboardOnHome(keyboardUp)
    }
    val taskbarInset = if (screenType == ScreenType.INNER && !keyboardUp) {
        with(density) { (taskbarPx - navigationPx).coerceAtLeast(0).toDp() }
    } else {
        0.dp
    }
    CompositionLocalProvider(
        LocalAppNotifications provides popupNotifications,
        LocalBadgeAvatars provides avatars,
        LocalIconNudges provides iconNudges,
        LocalTaskbarInset provides taskbarInset,
    ) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { shellHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .onGloballyPositioned { shellOrigin = it.positionInRoot() },
    ) {
        // Behind the home screen: this display's own background picture (drawn over the system
        // wallpaper, which a launcher cannot swap per display without re-theming the whole system),
        // then the dim layer that keeps icons and labels readable on a busy wallpaper.
        val backgroundPath = shellSettings.home.backgroundImage
        if (backgroundPath.isNotBlank() && isVideoBackground(backgroundPath)) {
            // A looping silent video, playing only while home is in view (VideoBackground.kt).
            VideoBackground(
                path = backgroundPath,
                playing = !homeCovered && !LocalBatterySaver.current,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (backgroundPath.isNotBlank()) {
            AsyncImage(
                model = java.io.File(backgroundPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (shellSettings.home.wallpaperDim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = shellSettings.home.wallpaperDim)),
            )
        }
        CompositionLocalProvider(LocalHomeCovered provides homeCovered) {
        HomeScreen(
            screenType = screenType,
            onOpenDrawer = { animateProgress(1f) },
            onDrawerDrag = { dragDrawer(it) },
            onDrawerSettle = { settleDrawer(it) },
            onOpenSettings = onOpenSettings,
            homeSignals = homeSignals,
            dragController = dragController,
            modifier = Modifier
                .fillMaxSize()
                // Under a glass drawer the home screen recedes (fades and shrinks slightly), so its
                // icons and labels don't show through the translucent panel and clash with the grid.
                .graphicsLayer {
                    if (glassDrawer && !draggingFromDrawer) {
                        val p = currentProgress
                        alpha = 1f - p
                        val scale = 1f - 0.05f * p
                        scaleX = scale
                        scaleY = scale
                    }
                },
        )
        }

        // What is going on right now (a timer, navigation, a download): a live pill on top of home.
        if (shellSettings.live.activities && live.isNotEmpty() && !homeCovered) {
            var liveIndex by remember { mutableIntStateOf(0) }
            var liveOpen by remember { mutableStateOf(false) }
            val shownLive = live[liveIndex % live.size]
            val canOpenUp = shellSettings.live.expandOnLongPress
            // Opened up, it closes again on its own after a while without a touch.
            LaunchedEffect(liveOpen, liveIndex) {
                if (liveOpen) {
                    kotlinx.coroutines.delay(LIVE_CARD_IDLE_MS)
                    liveOpen = false
                }
            }
            if (liveOpen) {
                // A tap anywhere else closes the card.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { liveOpen = false },
                )
            }
            AnimatedContent(
                targetState = liveOpen && canOpenUp,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(260), initialScale = 0.86f, transformOrigin = TransformOrigin(0.5f, 0f)))
                        .togetherWith(fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 0f)))
                        .using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.TopCenter,
                label = "live",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 4.dp, start = 16.dp, end = 16.dp),
            ) { open ->
                if (open) {
                    LiveCard(
                        activity = shownLive,
                        index = liveIndex % live.size,
                        count = live.size,
                        onSwitch = { liveIndex = it },
                        onOpen = {
                            liveOpen = false
                            shownLive.contentIntent?.let { openNotificationIntent(context, it) }
                        },
                        onAction = { action -> openNotificationIntent(context, action.intent) },
                    )
                } else {
                    LivePill(
                        activity = shownLive,
                        effect = shellSettings.live.activityEffect,
                        animate = !batterySaver,
                        light = false,
                        extra = live.size - 1,
                        onClick = { shownLive.contentIntent?.let { openNotificationIntent(context, it) } },
                        // A long press opens it up; with that off, several at once: it shows the next.
                        onLongPress = when {
                            canOpenUp -> ({ liveOpen = true })
                            live.size > 1 -> ({ liveIndex++ })
                            else -> null
                        },
                    )
                }
            }
        }

        // Always compose AppDrawerScreen at fillMaxSize to keep it pre-composed, pre-measured, and pre-laid out.
        // When closed, it is translated completely off-screen (translationY = size.height).
        // Compose's pointer input system respects the graphicsLayer transform, so it will not block pointer events for the HomeScreen.
        AppDrawerScreen(
            onClose = { animateProgress(0f) },
            onDrawerDrag = { dragDrawer(it) },
            onDrawerSettle = { settleDrawer(it) },
            onOpenSettings = {
                animateProgress(0f)
                onOpenSettings()
            },
            dragController = dragController,
            homeSignals = homeSignals,
            drawerOpen = drawerOpen || draggingFromDrawer,
            drawerHeightFraction = drawerHeightFraction,
            drawerWidthFraction = drawerWidthFraction,
            drawerAlignment = drawerAlignment,
            drawerProgress = { currentProgress },
            onDragOutStart = {
                isDraggingDrawer = false
                scope.launch { progress.snapTo(0f) }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Single floating icon for any in-progress drag (home, dock or drawer), drawn above every
        // surface so it can travel between them. Positioned by the finger's root coords.
        val dragged = dragController.draggedApp
        if (dragged != null && dragController.moving) {
            val sizeDp = 56.dp
            val halfPx = with(density) { (sizeDp / 2).toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dragController.rootPosition.x - shellOrigin.x - halfPx).roundToInt(),
                            (dragController.rootPosition.y - shellOrigin.y - halfPx).roundToInt(),
                        )
                    }
                    .size(sizeDp)
                    .graphicsLayer {
                        alpha = 0.92f
                        scaleX = 1.1f
                        scaleY = 1.1f
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Match the surfaces' icon style (the floating icon is a sibling of Home/Drawer, outside
                // their CompositionLocal scopes, so without this it reverts to the plain system icon).
                CompositionLocalProvider(
                    LocalIconPack provides iconStyle.iconPack,
                    LocalIconShape provides iconStyle.shape,
                    LocalThemedIcons provides iconStyle.themed,
                ) {
                    AppIcon(
                        appItem = dragged,
                        labelColor = Color.White,
                        showLabel = false,
                        iconSize = 52.dp,
                    )
                }
            }
        }
    }
    }

    PredictiveBackHandler(enabled = drawerOpen) { progress ->
        try {
            progress.collect { _ -> }
            // Commit: the user completed the swipe.
            animateProgress(0f)
        } catch (e: CancellationException) {
            // Cancelled: the user dragged back but then let go to cancel the back gesture.
            animateProgress(1f)
        }
    }
}

/**
 * Fires a notification's tap action like the notification shade would. Android 14+ no longer lends
 * the sender's right to open an activity; home is in front on the tap, so the grant is ours to give.
 */
/** How long an opened-up live card stays without a touch. */
private const val LIVE_CARD_IDLE_MS = 10_000L

private fun openNotificationIntent(context: Context, intent: PendingIntent) {
    runCatching {
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("DEPRECATION")
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        intent.send(context, 0, null, null, null, null, options.toBundle())
    }
}
