package org.arkikeskus.launcher.feature.home

import org.arkikeskus.launcher.feature.home.widgets.BadgeCounts
import org.arkikeskus.launcher.feature.home.widgets.WidgetDragController
import org.arkikeskus.launcher.feature.home.workspace.Workspace
import org.arkikeskus.launcher.feature.home.workspace.WorkspaceActions
import org.arkikeskus.launcher.feature.home.workspace.WorkspaceSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.catching
import org.arkikeskus.launcher.ui.AppActions
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.RenameDialog
import org.arkikeskus.launcher.ui.component.LocalBatterySaver
import org.arkikeskus.launcher.ui.component.LocalHomeCovered
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.rememberHomeDragController
import kotlin.math.roundToInt

/**
 * Home screen: a paged [Workspace] of app shortcuts + the dock. All home gestures (icon drag, swipe
 * up/down, long-press settings, page swipe) live inside [Workspace] so they don't fight each other.
 *
 * This function owns what decides which popup or dialog is open, and wires the pieces together:
 * the widget machinery is in HomeWidgets.kt, the frame around the pages (status bar, dock, look
 * settings) in HomeChrome.kt, the drag targets in HomeDragTargets.kt, the popups in HomeDialogs.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    homeSignals: Flow<Boolean> = emptyFlow(),
    onDrawerDrag: (Float) -> Unit = {},
    onDrawerSettle: (Float) -> Unit = {},
    dragController: HomeDragController = rememberHomeDragController(),
    viewModel: HomeViewModel = hiltViewModel(),
    screenType: ScreenType = LocalScreenType.current,
) {
    LaunchedEffect(screenType) { viewModel.setScreenType(screenType) }
    CompositionLocalProvider(LocalScreenType provides screenType) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    // True briefly after a heads-up notification, while the system transiently shows its own status bar
    // over ours; used to blank the themed bar so they don't overlap.
    // Read only where the themed bar fades (in its layer), so a heads-up doesn't recompose home.
    val headsUpActive = viewModel.headsUpActive.collectAsStateWithLifecycle()
    // First-run intro (fresh installs only) — covers the whole home surface until finished/skipped.
    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height

    // Optionally hide the system status bar while the launcher is foreground (immersive home).
    HideSystemStatusBar(settings.statusBar.hideSystem)

    // READ_PHONE_STATE granted outside onboarding (Settings ▸ Luvat, system app info) has no callback
    // into SignalMonitor — re-check on every resume so the 4G/5G generation label appears when the
    // user comes back home. No-op when the grant state hasn't changed.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPhonePermission()
        onPauseOrDispose { }
    }

    // The single Pixel-style long-press menu, anchored to the long-pressed icon.
    var menuTarget by remember { mutableStateOf<AppMenuTarget?>(null) }
    var renameTarget by remember { mutableStateOf<AppItem?>(null) }
    // The app whose "Add to folder" is open: the non-drag way into a home folder.
    var folderTarget by remember { mutableStateOf<AppItem?>(null) }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    // Empty-area long-press options popup (anchor + whether it flips above the press point).
    var homeOptions by remember { mutableStateOf<Pair<IntOffset, Boolean>?>(null) }
    // Page the workspace should show after a page was added or removed from the empty-area menu.
    val pageRequests = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    val defaultFolderName = stringResource(R.string.folder_default_name)

    // Edit home (several apps at once), and "show me where it went" after Add to home.
    val homeEdit = remember { HomeEditState() }
    LaunchedEffect(screenType) {
        org.arkikeskus.launcher.ui.AppEdits.reveal.collect { r ->
            if (r.screen != screenType.name) return@collect
            pageRequests.tryEmit(r.page)
            homeEdit.reveal(r.key)
        }
    }
    // Leaving home (an app opens, the screen goes off) ends edit mode; so does Back.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { homeEdit.stop() }
    androidx.activity.compose.BackHandler(enabled = homeEdit.editing) { homeEdit.stop() }
    // Uninstall and App info as drop targets (HomeDragController): they need a Context.
    DisposableEffect(dragController, context) {
        dragController.onUninstall = { app -> AppActions.uninstall(context, app) }
        dragController.onAppInfo = { app -> AppActions.openAppInfo(context, app) }
        onDispose {
            dragController.onUninstall = null
            dragController.onAppInfo = null
        }
    }

    val widgetDrag = remember(dragController) { WidgetDragController(dragController) }
    val widgetScope = rememberCoroutineScope()
    // Getting widgets onto home: host views, the bind → configure → save pipeline, the picker's state.
    val widgets = rememberHomeWidgets(viewModel, uiState.entries, widgetScope)

    // Shared drag state spanning the workspace, dock and drawer (Launcher3-style drag layer/controller).
    // Created by LauncherShell and passed in so the app drawer can drag onto home; falls back to a
    // local one when HomeScreen is used standalone (previews/tests).
    LaunchedEffect(uiState.dockApps.size, settings.dock.enabled, settings.dock.columns) {
        dragController.dockItemCount = uiState.dockApps.size
        dragController.dockHasSpace = settings.dock.enabled && uiState.dockApps.size < settings.dock.columns
    }
    // The split drop target only exists while the setting is on (see HomeDragController.isOverSplit).
    LaunchedEffect(settings.launch.splitEnabled) {
        dragController.splitEnabled = settings.launch.splitEnabled
    }
    // A drag that starts moving dismisses the menu (so the menu and drop bar never coexist).
    LaunchedEffect(dragController.moving) {
        if (dragController.moving) menuTarget = null
    }
    // Pressing HOME (onNewIntent → homeSignals) returns to a clean home: dismiss every open popup /
    // overlay — on every signal, also when coming home from an app. Without this the long-press app
    // menu and the empty-area options popup lingered until the user pressed Back — a Compose Popup
    // doesn't react to HOME on its own. (Workspace separately uses homeSignals to scroll back to
    // page 0, but only on an alreadyOnHome press; a SharedFlow feeds both collectors.)
    LaunchedEffect(homeSignals) {
        homeSignals.collect {
            menuTarget = null
            homeOptions = null
            renameTarget = null
            openFolderId = null
            widgets.showPicker = false
            widgets.stackTarget = null
            widgetDrag.cancel()
        }
    }

    // Notification badges (empty when disabled), and whether to render the count or a plain dot.
    // The counts are their own state and are only read where a badge is drawn (see BadgeCounts),
    // so a notification does not recompose the home screen from the top.
    val badgeState = viewModel.badges.collectAsStateWithLifecycle()
    val showNotificationDots = rememberUpdatedState(settings.dots.enabled)
    val badges = remember(badgeState) { BadgeCounts(badgeState) { showNotificationDots.value } }
    val badgeShowCount = settings.dots.showCount
    val badgeScale = settings.dots.scale

    fun executeAction(action: String) =
        runHomeGesture(context, action, settings.recents.enabled, onOpenDrawer, widgets.message)

    // Half folded (and the setting on): where the hinge is decides the layout, see HalfFold.kt.
    val halfFoldHinge = if (settings.fold.halfOpenedModeEnabled) rememberHalfFoldHinge() else null
    val tabletopHinge = halfFoldHinge?.takeIf { it.acrossScreen }
    val bookFold = halfFoldHinge != null && !halfFoldHinge.acrossScreen

    val widgetLooks by viewModel.widgetLooks.collectAsStateWithLifecycle()
    ProvideHomeLocals(settings, widgetLooks) {
        // The motion sensor only runs while the launcher is the app in front: home stays composed
        // behind every other app, and used to keep the sensor awake there. Its on/off lives in its
        // own small composable, so a resume, a pause or the drawer covering home recomposes only that.
        val parallaxOffset = remember { mutableStateOf<State<Offset>?>(null) }
        HomeParallax(enabledSetting = settings.home.parallaxWallpaper, out = parallaxOffset)
        // Half folded: home leans with the hinge (HingeTilt.kt), read in the layer below.
        val hingeFold = rememberHingeFold(
            enabled = settings.fold.hingeTilt && halfFoldHinge != null && !LocalBatterySaver.current && !LocalHomeCovered.current,
        )
    Box(
        modifier = modifier
            .fillMaxSize()
            // Read in the layer, not in composition: the sensor moves this layer and nothing else.
            .parallaxAndHingeLayer(parallaxOffset, hingeFold, tabletop = tabletopHinge != null)
            // Root-level, Initial-pass swipe detector: a flick up/down anywhere on home (over icons,
            // folders, shortcuts or the dock — not just empty space) drives the drawer/notifications.
            .pixelHomeSwipe(
                // All home gestures pause while the first-run intro covers the screen — this
                // detector runs in the Initial pass on the ROOT, so it would otherwise catch a
                // vertical swipe before the intro overlay could swallow it.
                // Edit home pauses them too: there an icon moves as soon as it is dragged, so a
                // swipe that starts on one must not open the drawer or the shade instead.
                swipeUpAction = if (showOnboarding || homeEdit.editing) LauncherSettings.GESTURE_NONE else settings.home.swipeUpAction,
                swipeDownAction = if (showOnboarding || homeEdit.editing) LauncherSettings.GESTURE_NONE else settings.home.swipeDownAction,
                dragController = dragController,
                onDrawerDrag = onDrawerDrag,
                onDrawerSettle = onDrawerSettle,
                onAction = ::executeAction,
                // Left-edge action: a right-drag on the leftmost page launches the configured app.
                leftEdgeEnabled = settings.home.leftSwipeAppKey.isNotBlank() && !showOnboarding && !homeEdit.editing,
                atLeftEdge = { dragController.currentPage == 0 },
                onLeftEdgeAction = viewModel::onLeftSwipe,
                // Right-edge action: a left-drag on the rightmost page launches the configured app.
                rightEdgeEnabled = settings.home.rightSwipeAppKey.isNotBlank() && !showOnboarding && !homeEdit.editing,
                // Book layout: the pager rests on the left page of the last pair.
                atRightEdge = {
                    dragController.currentPage + (if (dragController.isDualPage) 1 else 0) >= (uiState.pageCount - 1).coerceAtLeast(0)
                },
                onRightEdgeAction = viewModel::onRightSwipe,
                // Swipe up on an app icon (Settings ▸ Gestures): instead of opening the drawer.
                iconSwipeEnabled = settings.home.iconSwipeUpAction != LauncherSettings.ICON_SWIPE_NONE &&
                    !settings.home.desktopLocked && !showOnboarding && !homeEdit.editing,
                onIconSwipe = { app, at ->
                    when (settings.home.iconSwipeUpAction) {
                        LauncherSettings.ICON_SWIPE_MENU -> {
                            menuTarget = AppMenuTarget(
                                app, IntOffset(at.x.roundToInt(), at.y.roundToInt()), DragSource.Home,
                                at.y > windowHeightPx / 2,
                            )
                        }
                        LauncherSettings.ICON_SWIPE_FLOATING -> viewModel.launchFloating(app)
                        LauncherSettings.ICON_SWIPE_SPLIT -> {
                            dragController.splitRequest = app
                        }
                        else -> Unit
                    }
                },
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeStatusBar(settings, headsUpActive)
            // Book half-fold: one page per half, nothing on the crease.
            val isDualPage = screenType == ScreenType.INNER && (settings.home.innerDualPage || bookFold)
            // Unfolded just now (outer screen → inner): the pages open out from the hinge.
            val unfold = rememberUnfoldProgress(screenType, settings.home.unfoldAnimation)
            val workspaceActions = WorkspaceActions(
                onAddWidget = widgets.addWidget,
                onAppClick = viewModel::launch,
                onAppMenu = { app, anchor, above -> menuTarget = AppMenuTarget(app, anchor, DragSource.Home, above) },
                onMove = viewModel::moveItem,
                onMoveFolder = viewModel::moveFolder,
                onMoveToDock = { app, index -> viewModel.moveToDock(app, index) },
                onRemoveFromHome = { viewModel.removeFromHome(it) },
                onOpenFolder = { openFolderId = it.id },
                onFolderTap = { folder ->
                    val first = folder.apps.firstOrNull()
                    if (settings.folders.tapFirstApp && first != null) {
                        viewModel.launch(first)
                    } else {
                        openFolderId = folder.id
                    }
                },
                onFolderAppTap = { viewModel.launch(it) },
                onLaunchShortcut = { viewModel.launchShortcut(it) },
                onRemoveShortcut = { viewModel.removeShortcut(it) },
                onCreateFolder = { target, dropped -> viewModel.createFolder(target, dropped, defaultFolderName) },
                onAddToFolder = { app, folderId -> viewModel.addToFolder(app, folderId) },
                onCreateWidgetStack = { targetRowId, droppedRowId -> viewModel.createWidgetStack(targetRowId, droppedRowId) },
                onAddToWidgetStack = { stackId, widgetRowId ->
                    if (widgetRowId < 0L) {
                        widgets.stackTarget = stackId
                        widgets.showPicker = true
                    } else {
                        viewModel.addToWidgetStack(stackId, widgetRowId)
                    }
                },
                onRemoveFromWidgetStack = { stackId, itemRowId ->
                    viewModel.removeFromWidgetStack(stackId, itemRowId) { freedId ->
                        catching { widgets.host?.deleteAppWidgetId(freedId) }
                    }
                },
                onEmptyAreaMenu = { anchor, above -> homeOptions = anchor to above },
                onEmptyAreaDoubleTap = {
                    val action = settings.home.doubleTapAction
                    if (action != LauncherSettings.GESTURE_NONE) {
                        executeAction(action)
                    }
                },
                onRemoveWidget = { rowId, appWidgetId ->
                    // A built-in widget has no host id to free (appWidgetId == null). A widget stack
                    // has none of its own either, but the widgets inside it do — the repository
                    // reports those once the rows are gone.
                    appWidgetId?.let { widgets.host?.deleteAppWidgetId(it) }
                    viewModel.removeWidget(rowId) { freed ->
                        freed.filter { it != appWidgetId }.forEach { id ->
                            catching { widgets.host?.deleteAppWidgetId(id) }
                        }
                    }
                },
                onRestorePendingWidget = { widgets.restore(it) },
                // A pending placeholder has no allocated host id yet, so just drop its row.
                onRemovePendingWidget = { viewModel.removeWidget(it) },
                onSetWidgetBounds = viewModel::setWidgetBounds,
                onReconfigureWidget = widgets.reconfigure,
                onWidgetLookChange = { rowId, look -> viewModel.setWidgetLook(rowId, look) },
            )
            CompositionLocalProvider(LocalHomeEdit provides homeEdit) {
            Workspace(
                unfoldProgress = { unfold.value },
                pageCount = uiState.pageCount,
                entries = uiState.entries,
                badges = badges,
                settings = WorkspaceSettings(
                    columns = settings.home.columns,
                    rows = settings.home.rows,
                    showLabels = settings.home.showLabels,
                    labelColor = Color(settings.icons.labelColor),
                    badgeShowCount = badgeShowCount,
                    badgeScale = badgeScale,
                    glassBlurRadius = settings.drawer.glassBlurRadius,
                    glassDarkTint = settings.drawer.glassDarkTint,
                    showPageIndicator = settings.home.showPageIndicator,
                    locked = settings.home.desktopLocked,
                    isDualPage = isDualPage,
                    pageBounceEnabled = settings.home.pageBounceEnabled,
                    pageScrollEffect = settings.home.pageScrollEffect,
                    allowNewPage = settings.home.newPageOnDrag,
                    minusOneScreen = settings.home.minusOneScreen,
                    animationSpeed = settings.motion.animationSpeed,
                ),
                actions = workspaceActions,
                homeSignals = homeSignals,
                homePage = if (uiState.loaded) uiState.homePage else null,
                pageRequests = pageRequests,
                dragController = dragController,
                widgetDragController = widgetDrag,
                widgetViews = widgets.views,
                widgetScrollableById = widgets.scrollable,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Status-bar inset is consumed by the StatusBar above when it's shown; otherwise the
                    // workspace takes it itself.
                    .then(if (settings.statusBar.enabled) Modifier else Modifier.statusBarsPadding())
                    // No horizontal padding: the grid spans the full width so a "full width" widget
                    // (spanX = columns at cellX 0) reaches both screen edges with no side gap. Icons
                    // shift outward by a negligible ~8dp.
                    .padding(vertical = 12.dp),
            )
            }

            // The dock whenever it's enabled (see HomeDock).
            val dockContent: @Composable ColumnScope.() -> Unit = {
                HomeDock(
                    apps = uiState.dockApps,
                    settings = settings,
                    screenType = screenType,
                    badges = badges,
                    badgeShowCount = badgeShowCount,
                    badgeScale = badgeScale,
                    dragController = dragController,
                    windowHeightPx = windowHeightPx,
                    onAppClick = viewModel::launch,
                    onReorder = viewModel::reorderDock,
                    onMoveToHome = { app, page, cellX, cellY -> viewModel.moveToHome(app, page, cellX, cellY) },
                    onRemoveFromDock = { viewModel.removeFromDock(it) },
                    onAppMenu = { app, anchor -> menuTarget = AppMenuTarget(app, anchor, DragSource.Dock, anchor.y > windowHeightPx / 2) },
                )
            }
            if (tabletopHinge != null) {
                // Laptop / tabletop: the pages fill the top half (the workspace takes the rest of the
                // column), the bottom half below the hinge holds the dock in its middle.
                val bottomHalf = with(LocalDensity.current) {
                    (windowHeightPx - tabletopHinge.bounds.top).coerceAtLeast(0).toDp()
                }
                Column(
                    modifier = Modifier.fillMaxWidth().height(bottomHalf),
                    verticalArrangement = Arrangement.Center,
                    content = dockContent,
                )
            } else {
                dockContent()
            }
        }

        // Drop targets at the top while an icon is dragged.
        HomeDragTargets(dragController, settings)
        // Edit home: the bar at the bottom and its two dialogs.
        HomeEditLayer(homeEdit, uiState, viewModel, defaultFolderName)

        // Not-default reminder card (never under the full first-run intro).
        DefaultLauncherReminder(showOnboarding)

        // The floating dragged icon is drawn by LauncherShell (above the workspace, dock and the app
        // drawer), so it can travel across all three surfaces.

        // First-run intro on top of everything (fresh installs only, once).
        if (showOnboarding) {
            OnboardingFlow(
                onFinish = { viewModel.finishOnboarding() },
                // The restore already marked the intro as done; close it and reload the home
                // screen so the restored layout and widgets are set up from scratch.
                onRestored = {
                    viewModel.finishOnboarding()
                    context.hostActivity()?.recreate()
                },
                onContactsGranted = { viewModel.onContactsPermissionGranted() },
                onPhoneGranted = { viewModel.onPhonePermissionGranted() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // An app waiting for its split-screen partner, or a split zone dropped on.
    HomeSplitRequests(dragController, viewModel)

    // "Change icon" was chosen for an app (from a menu on home, the dock or the drawer).
    HomeIconPicker(dragController)

    val menu = menuTarget
    if (menu != null) {
        HomeAppMenu(
            menu = menu,
            settings = settings,
            dragController = dragController,
            viewModel = viewModel,
            onAddToFolder = { folderTarget = it },
            onDismiss = { menuTarget = null },
        )
    }

    homeOptions?.let { (anchor, above) ->
        HomeOptionsMenu(
            anchor = anchor,
            above = above,
            settings = settings,
            uiState = uiState,
            dragController = dragController,
            viewModel = viewModel,
            homeEdit = homeEdit,
            widgets = widgets,
            scope = widgetScope,
            pageRequests = pageRequests,
            onOpenSettings = onOpenSettings,
            onDismiss = { homeOptions = null },
        )
    }

    HomeWidgetPicker(widgets, widgetDrag, viewModel, settings, screenType)

    renameTarget?.let { app ->
        RenameDialog(
            initialName = app.label,
            onConfirm = { viewModel.setCustomAppLabel(app.key, it) },
            onReset = { viewModel.setCustomAppLabel(app.key, "") },
            onDismiss = { renameTarget = null },
        )
    }

    // "Add to folder" in a home app's menu: home folders only. Folders that already hold the app
    // are left out (it would be a no-op), and a new folder needs a second app from this screen.
    folderTarget?.let { app ->
        HomeAddToFolderDialog(
            app = app,
            folders = uiState.entries.filterIsInstance<PlacedFolder>()
                .filter { folder -> folder.apps.none { it.key == app.key } },
            companions = uiState.entries.filterIsInstance<PlacedApp>()
                .map { it.app }
                .filter { it.key != app.key },
            onPick = { id -> viewModel.addToFolder(app, id) },
            onNew = { other -> viewModel.createFolder(app, other, defaultFolderName) },
            onDismiss = { folderTarget = null },
        )
    }

    val openFolder = openFolderId?.let { id ->
        uiState.entries.filterIsInstance<PlacedFolder>().firstOrNull { it.id == id }
    }
    // Close the sheet if the folder dissolved (dropped to one app) while it was open.
    LaunchedEffect(openFolderId, openFolder == null) {
        if (openFolderId != null && openFolder == null) openFolderId = null
    }
    if (openFolder != null) {
        FolderDialog(
            folder = openFolder,
            badges = badges,
            badgeShowCount = badgeShowCount,
            badgeScale = badgeScale,
            glassBlurRadius = settings.drawer.glassBlurRadius,
            glassDarkTint = settings.drawer.glassDarkTint,
            openAnimation = settings.folders.openAnimation,
            animationSpeed = settings.motion.animationSpeed,
            onRename = { viewModel.renameFolder(openFolder.id, it) },
            onAppClick = { viewModel.launch(it) },
            onRemoveFromFolder = { viewModel.removeFromFolder(it, openFolder.id) },
            onDismiss = { openFolderId = null },
        )
    }
    }
    }
}
