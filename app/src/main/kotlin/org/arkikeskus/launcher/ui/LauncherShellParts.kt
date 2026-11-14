package org.arkikeskus.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.Flow
import org.arkikeskus.launcher.data.NotificationNudge
import org.arkikeskus.launcher.data.PendingIntents
import org.arkikeskus.launcher.data.StatusNotification
import org.arkikeskus.launcher.feature.home.rememberHingeFold
import org.arkikeskus.launcher.feature.home.taskbar.TaskbarSpace
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.LiveActivity
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.catching
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.IconNudgeEvent
import org.arkikeskus.launcher.ui.component.IconNudges
import org.arkikeskus.launcher.ui.component.IconRing
import org.arkikeskus.launcher.ui.component.IconRings
import org.arkikeskus.launcher.ui.component.LiveCard
import org.arkikeskus.launcher.ui.component.LivePill
import org.arkikeskus.launcher.ui.component.LocalIconPack
import org.arkikeskus.launcher.ui.component.LocalIconShape
import org.arkikeskus.launcher.ui.component.LocalThemedIcons
import org.arkikeskus.launcher.ui.component.rememberKeyboardShown
import kotlin.math.roundToInt

// The launcher shell's parts (split out of LauncherShell.kt): what the icons get to show (their
// notifications, nudges and rings), the room kept for the taskbar, and the layers drawn around home
// (the background picture, the live pill, the icon being dragged).

/**
 * Each app's notifications for its long-press menu (Settings ▸ Notifications), when [enabled]. A
 * function, read by the menu as it shows: an ongoing download's notification changing every second
 * used to recompose the whole shell each time.
 */
@Composable
internal fun rememberPopupNotifications(
    enabled: Boolean,
    notifications: State<List<StatusNotification>>,
    dismiss: (key: String) -> Unit,
): () -> List<PopupNotification> {
    val context = LocalContext.current
    return remember(enabled) {
        {
            if (!enabled) {
                emptyList()
            } else {
                notifications.value.mapNotNull { n ->
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
                            n.contentIntent?.let { PendingIntents.sendFromForeground(context, it) }
                            if (n.autoCancel) dismiss(n.key)
                        },
                        dismiss = { dismiss(n.key) },
                    )
                }
            }
        }
    }
}

/**
 * A new notification nudges its app's icon (Settings ▸ Icons & badges): the newest per app, each
 * with its own id so a second message replays the effect. Null while off or in battery saver.
 */
@Composable
internal fun rememberIconNudges(
    nudges: Flow<NotificationNudge>,
    enabled: Boolean,
    effect: String,
    softness: Float,
): IconNudges? {
    val events = remember { mutableStateMapOf<String, IconNudgeEvent>() }
    LaunchedEffect(Unit) {
        var id = 0L
        nudges.collect { n -> events[n.badgeKey] = IconNudgeEvent(n.badgeKey, n.color, n.time, ++id) }
    }
    return remember(enabled, effect, softness) { if (enabled) IconNudges(effect, softness, events) else null }
}

/**
 * Rings around icons (Settings ▸ Icons & badges, [mode]): a download's progress, a running timer,
 * and with "progress & unread" the apps with new notifications. Only what has something to show.
 */
@Composable
internal fun rememberIconRings(mode: String, live: State<List<LiveActivity>>): IconRings? {
    val rings = remember(mode) { if (mode == LauncherSettings.RING_OFF) null else IconRings(mode) }
    LaunchedEffect(rings) {
        rings ?: return@LaunchedEffect
        snapshotFlow { live.value }.collect { activities ->
            rings.update(
                activities.filter { it.progress != null || it.indeterminate || it.chronometerBase != 0L }
                    .groupBy { "${it.packageName}/${it.userSerial}" }
                    .mapValues { (_, items) ->
                        // A determinate progress first (it says the most), else a timer, else busy.
                        val a = items.firstOrNull { it.progress != null && !it.indeterminate }
                            ?: items.firstOrNull { it.chronometerBase != 0L } ?: items.first()
                        IconRing(
                            progress = a.progress,
                            indeterminate = a.indeterminate,
                            chronometerBase = if (a.progress == null) a.chronometerBase else 0L,
                            color = a.color,
                        )
                    },
            )
        }
    }
    return rings
}

/**
 * Folark's taskbar staying on home (inner display): the room the dock and the drawer keep clear of
 * it. While the keyboard is up the bar steps aside (it would cover the keyboard), so no room is kept
 * for it either; home and the drawer make room for the keyboard instead.
 */
@Composable
internal fun rememberTaskbarInset(screenType: ScreenType): Dp {
    val density = LocalDensity.current
    val taskbarPx by TaskbarSpace.bottomPx.collectAsStateWithLifecycle()
    val navigationPx = WindowInsets.navigationBars.getBottom(density)
    val keyboardUp by rememberKeyboardShown()
    LaunchedEffect(keyboardUp, screenType) {
        if (screenType == ScreenType.INNER) TaskbarSpace.setKeyboardOnHome(keyboardUp)
    }
    return if (screenType == ScreenType.INNER && !keyboardUp) {
        with(density) { (taskbarPx - navigationPx).coerceAtLeast(0).toDp() }
    } else {
        0.dp
    }
}

/**
 * Behind the home screen: this display's own background picture or video (drawn over the system
 * wallpaper, which a launcher cannot swap per display without re-theming the whole system), then
 * the dim layer that keeps icons and labels readable on a busy wallpaper. With Settings ▸ Theme ▸
 * Wallpaper follows the hinge, the picture (or Android's own wallpaper) zooms in a little as the
 * phone folds and back out as it opens.
 */
@Composable
internal fun HomeBackground(settings: LauncherSettings, batterySaver: Boolean, homeCovered: Boolean) {
    val backgroundPath = settings.home.backgroundImage
    // Read in the picture's own layer only.
    val wallpaperFold = rememberHingeFold(enabled = settings.fold.wallpaperHinge && !batterySaver && !homeCovered)
    // Android's own wallpaper behind home (no picture of Folark's): zoomed the same way through
    // WallpaperManager's zoom, where this Android build lets a launcher call it.
    val shellView = LocalView.current
    if (settings.fold.wallpaperHinge && backgroundPath.isBlank()) {
        LaunchedEffect(shellView) {
            try {
                snapshotFlow { wallpaperFold.value }.collect { setSystemWallpaperZoom(shellView, it * WALLPAPER_ZOOM_OUT) }
            } finally {
                setSystemWallpaperZoom(shellView, 0f)
            }
        }
    }
    val hingeZoom = Modifier.graphicsLayer {
        val s = 1f + wallpaperFold.value * WALLPAPER_HINGE_ZOOM
        scaleX = s
        scaleY = s
    }
    if (backgroundPath.isNotBlank() && isVideoBackground(backgroundPath)) {
        // A looping silent video, playing only while home is in view (VideoBackground.kt).
        VideoBackground(
            path = backgroundPath,
            playing = !homeCovered && !batterySaver,
            awakeSeconds = settings.home.videoAwakeSeconds,
            modifier = Modifier.fillMaxSize().then(hingeZoom),
        )
    } else if (backgroundPath.isNotBlank()) {
        AsyncImage(
            model = java.io.File(backgroundPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(hingeZoom),
        )
    }
    if (settings.home.wallpaperDim > 0f) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = settings.home.wallpaperDim)),
        )
    }
}

/**
 * What is going on right now (a timer, navigation, a download): a live pill on top of home. While
 * Android's status bar shows, what it already has a chip for (calls, Live updates) isn't shown a
 * second time. [liveState] is read only here, so its changes recompose nothing else.
 */
@Composable
internal fun BoxScope.LiveActivityLayer(
    liveState: State<List<LiveActivity>>,
    settings: LauncherSettings,
    batterySaver: Boolean,
    homeCovered: Boolean,
) {
    val context = LocalContext.current
    val chipsShown = settings.live.skipSystemChips && !settings.statusBar.hideSystem
    val live = liveState.value.let { all -> if (chipsShown) all.filterNot { it.systemChip } else all }
    val cameraClear = rememberCameraClearance()
    if (!settings.live.activities || live.isEmpty() || homeCovered) return
    var liveIndex by remember { mutableIntStateOf(0) }
    var liveOpen by remember { mutableStateOf(false) }
    val shownLive = live[liveIndex % live.size]
    val canOpenUp = settings.live.expandOnLongPress
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
            // Below the status bar, and always below the front camera (with the status
            // bar hidden there is no bar to keep clear, but the camera hole is still there).
            .padding(top = cameraClear + 4.dp, start = 16.dp, end = 16.dp),
    ) { open ->
        if (open) {
            LiveCard(
                activity = shownLive,
                index = liveIndex % live.size,
                count = live.size,
                onSwitch = { liveIndex = it },
                onOpen = {
                    liveOpen = false
                    shownLive.contentIntent?.let { PendingIntents.sendFromForeground(context, it) }
                },
                onAction = { action -> PendingIntents.sendFromForeground(context, action.intent) },
            )
        } else {
            LivePill(
                // Several at once: a sideways swipe shows the next (or the one before).
                modifier = if (live.size < 2) Modifier else Modifier.pointerInput(live.size) {
                    var dragged = 0f
                    val step = 36.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = 0f },
                        onDragEnd = {
                            if (dragged <= -step) liveIndex++
                            else if (dragged >= step) liveIndex = (liveIndex - 1 + live.size) % live.size
                        },
                    ) { change, amount -> change.consume(); dragged += amount }
                },
                activity = shownLive,
                effect = settings.live.activityEffect,
                animate = !batterySaver,
                light = false,
                extra = live.size - 1,
                onClick = { shownLive.contentIntent?.let { PendingIntents.sendFromForeground(context, it) } },
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

/**
 * The single floating icon of an in-progress drag (home, dock or drawer), drawn above every surface
 * so it can travel between them, where the finger is in root coordinates ([shellOrigin] is where the
 * shell itself sits). It keeps the icon's real size and stays centred where it was grabbed, not
 * under the finger, so picking it up never makes it jump.
 */
@Composable
internal fun DraggedIconLayer(dragController: HomeDragController, shellOrigin: () -> Offset, iconStyle: IconStyle) {
    val dragged = dragController.draggedApp
    if (dragged == null || !dragController.moving) return
    val iconDp = dragController.iconSize
    val sizeDp = iconDp + 4.dp
    val halfPx = with(LocalDensity.current) { (sizeDp / 2).toPx() }
    // A short lift as the drag begins instead of popping straight to the lifted size.
    val lift = remember(dragged.key) { Animatable(1f) }
    LaunchedEffect(dragged.key) {
        lift.animateTo(DRAG_LIFT_SCALE, spring(dampingRatio = 0.7f, stiffness = 900f))
    }
    Box(
        modifier = Modifier
            .offset {
                val origin = shellOrigin()
                val p = dragController.rootPosition + dragController.grabOffset
                IntOffset(
                    (p.x - origin.x - halfPx).roundToInt(),
                    (p.y - origin.y - halfPx).roundToInt(),
                )
            }
            .size(sizeDp)
            .graphicsLayer {
                alpha = 0.92f
                scaleX = lift.value
                scaleY = lift.value
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
                iconSize = iconDp,
            )
        }
    }
}

/**
 * How far down from the top of the screen the live pill must start: below the status bar when it
 * shows, and below the front camera's hole (the outer screen's camera sits in the middle of the
 * top edge) whether the status bar shows or not.
 */
@Composable
private fun rememberCameraClearance(): Dp {
    val density = LocalDensity.current
    val view = LocalView.current
    val config = LocalConfiguration.current
    // Both read in composition, so a screen switch or the status bar coming and going updates it.
    val barTop = WindowInsets.statusBars.getTop(density)
    val cutoutTop = WindowInsets.displayCutout.getTop(density)
    val holeBottom = remember(cutoutTop, barTop, config.screenWidthDp, config.screenHeightDp, config.orientation) {
        // The camera hole itself (Android's safe inset can be a little shorter than the hole).
        val rects = catching { view.rootWindowInsets?.displayCutout?.boundingRects }.getOrNull().orEmpty()
        val limit = view.rootView.height.takeIf { it > 0 }?.div(4) ?: Int.MAX_VALUE
        rects.filter { it.top < limit && it.bottom < limit }.maxOfOrNull { it.bottom } ?: 0
    }
    return with(density) { maxOf(barTop, cutoutTop, holeBottom).toDp() }
}

/** How long an opened-up live card stays without a touch. */
private const val LIVE_CARD_IDLE_MS = 10_000L

/** How much the home picture grows with the phone folded to 90° (Settings ▸ Wallpaper follows the hinge). */
private const val WALLPAPER_HINGE_ZOOM = 0.14f

/** The same for Android's own wallpaper, as WallpaperManager's zoom (0 none, 1 the most). */
private const val WALLPAPER_ZOOM_OUT = 0.8f

/** WallpaperManager's zoom call, looked up once (it runs on every frame of a hinge move); null where hidden. */
private val wallpaperZoomMethod: java.lang.reflect.Method? by lazy {
    catching {
        android.app.WallpaperManager::class.java.getMethod("setWallpaperZoomOut", android.os.IBinder::class.java, Float::class.javaPrimitiveType)
    }.getOrNull()
}

/** Android's wallpaper zoom for the window of [view]; quietly nothing where Android won't allow it. */
private fun setSystemWallpaperZoom(view: android.view.View, zoom: Float) {
    val token = view.windowToken ?: return
    val method = wallpaperZoomMethod ?: return
    catching { method.invoke(android.app.WallpaperManager.getInstance(view.context), token, zoom.coerceIn(0f, 1f)) }
}

/** How much a dragged icon grows as it is lifted. */
private const val DRAG_LIFT_SCALE = 1.1f
