package org.arkikeskus.launcher.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.arkikeskus.launcher.feature.appdrawer.AppDrawerScreen
import org.arkikeskus.launcher.feature.home.HomeScreen
import org.arkikeskus.launcher.feature.home.taskbar.TaskbarSpace
import org.arkikeskus.launcher.model.FoldAction
import org.arkikeskus.launcher.ui.component.LocalBadgeAvatars
import org.arkikeskus.launcher.ui.component.LocalBatterySaver
import org.arkikeskus.launcher.ui.component.LocalHomeCovered
import org.arkikeskus.launcher.ui.component.LocalIconNudges
import org.arkikeskus.launcher.ui.component.LocalIconRings
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.LocalTaskbarInset
import org.arkikeskus.launcher.ui.component.WindowBlurBehind

/**
 * Hosts the launcher surface: the home screen with the app drawer as a **finger-following** slide-up
 * overlay. A swipe up on home drives the drawer's progress (0 = closed, 1 = open, see [DrawerSlide])
 * so the drawer tracks the finger; releasing flings/settles to the nearest end. A pull-down at the
 * top of the drawer (or BACK / HOME) closes it. [onOpenSettings] is a long-press on home / the
 * launcher's own drawer icon.
 *
 * This is also the launcher's drag layer: it owns the shared [HomeDragController] and draws the single
 * floating icon above every surface, so an icon can be dragged from the dock, the home grid, **or the
 * app drawer** onto any of them. Dragging an app out of the drawer slides the drawer closed (so home
 * is visible to drop onto) while keeping the drawer composed, so the in-flight gesture isn't cancelled.
 *
 * Its parts are in DrawerSlide.kt (the drawer's position and gestures) and LauncherShellParts.kt
 * (what the icons show, the taskbar's room, and the background, live pill and dragged icon layers).
 */
@Composable
fun LauncherShell(
    homeSignals: Flow<Boolean>,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    shellCommands: Flow<FoldAction> = emptyFlow(),
) {
    val viewModel = hiltViewModel<LauncherShellViewModel>()
    val screenType = LocalScreenType.current
    LaunchedEffect(screenType) {
        viewModel.setScreenType(screenType)
    }
    val iconStyle by viewModel.iconStyle.collectAsStateWithLifecycle()
    // Read only where it is shown (the pill layer, the icon rings): a download ticking along used to
    // recompose this whole shell every second.
    val liveState = viewModel.live.collectAsStateWithLifecycle()
    // Read only when an app's long-press menu shows them (see rememberPopupNotifications).
    val statusNotifications = viewModel.notifications.collectAsStateWithLifecycle()
    val avatars by viewModel.avatars.collectAsStateWithLifecycle()
    val shellSettings by viewModel.settings.collectAsStateWithLifecycle()
    val drawerHeightFraction by viewModel.drawerHeightFraction.collectAsStateWithLifecycle()
    val drawerWidthFraction by viewModel.drawerWidthFraction.collectAsStateWithLifecycle()
    val drawerAlignment by viewModel.drawerAlignment.collectAsStateWithLifecycle()
    val slide = rememberDrawerSlide()
    var shellOrigin by remember { mutableStateOf(Offset.Zero) }

    // The shared drag state. Created here so both the home screen and the app drawer feed the same
    // controller and the floating icon below can travel between them.
    val dragController = rememberHomeDragController()

    // The taskbar steps aside while the drawer is open (or being pulled up), and comes back after.
    LaunchedEffect(slide.isOpen) { TaskbarSpace.setDrawerOpen(slide.isOpen) }
    // While an app is being dragged out of the drawer, keep the drawer composed even after it has
    // slid shut — otherwise removing it from composition would cancel the in-progress drag gesture.
    // Keyed on [moving] (not [isDragging]): a still long-press only lifts (menu), and must NOT fade
    // the drawer — otherwise it flashes out and back when the menu opens.
    val draggingFromDrawer by remember {
        derivedStateOf { dragController.moving && dragController.source == DragSource.Drawer }
    }

    LaunchedEffect(homeSignals) {
        homeSignals.collect { slide.animateTo(0f) }
    }

    // Fold/unfold actions from the activity ("open the drawer / search after unfolding").
    LaunchedEffect(shellCommands) {
        shellCommands.collect { command ->
            when (command) {
                FoldAction.DRAWER, FoldAction.SEARCH -> slide.animateTo(1f)
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
    val homeCovered by remember { derivedStateOf { slide.progress > 0.97f } }
    WindowBlurBehind(maxRadiusDp = drawerBlurDp) {
        val progress = if (draggingFromDrawer) 0f else slide.progress
        if (drawerBlurDp <= 0f) 0f else (homeBlurDp + (drawerBlurDp - homeBlurDp) * progress) / drawerBlurDp
    }

    // Each app's notifications for its long-press menu and sender photos for the dots, nudges and
    // rings on the icons: provided to home, dock and drawer alike.
    val popupNotifications = rememberPopupNotifications(
        enabled = shellSettings.live.popupNotifications,
        notifications = statusNotifications,
        dismiss = viewModel::dismissNotification,
    )
    val iconNudges = rememberIconNudges(
        nudges = viewModel.nudges,
        enabled = shellSettings.icons.nudge && !batterySaver,
        effect = shellSettings.icons.nudgeEffect,
        softness = shellSettings.icons.nudgeSoftness,
    )
    val iconRings = rememberIconRings(shellSettings.icons.ring, liveState)
    CompositionLocalProvider(
        LocalAppNotifications provides popupNotifications,
        LocalBadgeAvatars provides avatars,
        LocalIconNudges provides iconNudges,
        LocalIconRings provides iconRings,
        LocalTaskbarInset provides rememberTaskbarInset(screenType),
    ) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { slide.heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .onGloballyPositioned { shellOrigin = it.positionInRoot() },
    ) {
        HomeBackground(shellSettings, batterySaver, homeCovered)
        CompositionLocalProvider(LocalHomeCovered provides homeCovered) {
        HomeScreen(
            screenType = screenType,
            onOpenDrawer = { slide.animateTo(1f) },
            onDrawerDrag = { slide.drag(it) },
            onDrawerSettle = { slide.settle(it) },
            onOpenSettings = onOpenSettings,
            homeSignals = homeSignals,
            dragController = dragController,
            modifier = Modifier
                .fillMaxSize()
                // Under a glass drawer the home screen recedes (fades and shrinks slightly), so its
                // icons and labels don't show through the translucent panel and clash with the grid.
                .graphicsLayer {
                    if (glassDrawer && !draggingFromDrawer) {
                        val p = slide.progress
                        alpha = 1f - p
                        val scale = 1f - 0.05f * p
                        scaleX = scale
                        scaleY = scale
                    }
                },
        )
        }

        LiveActivityLayer(liveState, shellSettings, batterySaver, homeCovered)

        // Always compose AppDrawerScreen at fillMaxSize to keep it pre-composed, pre-measured, and pre-laid out.
        // When closed, it is translated completely off-screen (translationY = size.height).
        // Compose's pointer input system respects the graphicsLayer transform, so it will not block pointer events for the HomeScreen.
        AppDrawerScreen(
            onClose = { slide.animateTo(0f) },
            onDrawerDrag = { slide.drag(it) },
            onDrawerSettle = { slide.settle(it) },
            onOpenSettings = {
                slide.animateTo(0f)
                onOpenSettings()
            },
            dragController = dragController,
            homeSignals = homeSignals,
            drawerOpen = slide.isOpen || draggingFromDrawer,
            drawerHeightFraction = drawerHeightFraction,
            drawerWidthFraction = drawerWidthFraction,
            drawerAlignment = drawerAlignment,
            drawerProgress = { slide.progress },
            onDragOutStart = { slide.snapClosed() },
            modifier = Modifier.fillMaxSize(),
        )

        DraggedIconLayer(dragController, { shellOrigin }, iconStyle)

        // "… removed from home · Undo" after an app was removed or hidden anywhere (AppEdits).
        UndoBar(Modifier.align(Alignment.BottomCenter))
    }
    }

    PredictiveBackHandler(enabled = slide.isOpen) { backEvents ->
        try {
            // Scrub the drawer closed live as the system back gesture progresses (0 → 1), so it
            // tracks the finger instead of only animating on release (progress: 1 open → 0 closed).
            backEvents.collect { ev -> slide.scrubTo(1f - ev.progress) }
            // Commit: the user completed the swipe.
            slide.animateTo(0f)
        } catch (e: CancellationException) {
            // Cancelled: the user dragged back but then let go to cancel the back gesture.
            slide.animateTo(1f)
        }
    }
}
