package org.arkikeskus.launcher.feature.appdrawer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import org.arkikeskus.launcher.ui.component.rememberKeyboardShown
import org.arkikeskus.launcher.ui.component.keyboardInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.roundToInt
import kotlin.math.abs
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.SearchResult
import org.arkikeskus.launcher.ui.AppActionPopup
import org.arkikeskus.launcher.ui.AppActions
import org.arkikeskus.launcher.ui.AppShortcuts
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.PopupAction
import org.arkikeskus.launcher.ui.RenameDialog
import org.arkikeskus.launcher.ui.component.LocalTaskbarInset
import org.arkikeskus.launcher.ui.component.LocalIconSizeScale
import org.arkikeskus.launcher.ui.component.iconSizeForCell
import org.arkikeskus.launcher.ui.component.liquidGlass
import org.arkikeskus.launcher.ui.component.LocalAppLabelLines
import org.arkikeskus.launcher.ui.component.LocalAppLabelScale
import org.arkikeskus.launcher.ui.component.LocalIconPack
import org.arkikeskus.launcher.ui.component.LocalIconShape
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.LocalThemedIcons
import org.arkikeskus.launcher.ui.drawer.DrawerColors
import org.arkikeskus.launcher.ui.drawer.DrawerStyleSpec
import org.arkikeskus.launcher.ui.drawer.drawerStyleSpec
import org.arkikeskus.launcher.ui.drawer.readableOn
import org.arkikeskus.launcher.ui.drawer.rememberDrawerColors
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.ExpressiveActionRow
import org.arkikeskus.launcher.ui.expressive.ExpressiveSectionTitle
import org.arkikeskus.launcher.ui.expressive.ExpressiveTheme
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import org.arkikeskus.launcher.ui.rememberHomeDragController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerScreen(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onDrawerDrag: (Float) -> Unit = {},
    onDrawerSettle: (Float) -> Unit = {},
    dragController: HomeDragController = rememberHomeDragController(),
    onDragOutStart: () -> Unit = {},
    homeSignals: Flow<Boolean> = emptyFlow(),
    drawerOpen: Boolean = true,
    drawerHeightFraction: Float? = null,
    drawerWidthFraction: Float? = null,
    drawerAlignment: String? = null,
    /** How far open the drawer is, 0..1 — a lambda so it is read while drawing, not composing. */
    drawerProgress: () -> Float = { 1.0f },
    viewModel: AppDrawerViewModel = hiltViewModel(),
    screenType: ScreenType = LocalScreenType.current,
) {
    LaunchedEffect(screenType) { viewModel.setScreenType(screenType) }
    CompositionLocalProvider(LocalScreenType provides screenType) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Inner display only: the drawer docks to one side as a full-height panel instead of covering
    // the unfolded screen edge to edge.
    val sidebar = uiState.innerSidebarAppDrawer && screenType == ScreenType.INNER
    // While the keyboard is up (searching), a shorter drawer grows to the full height so the
    // results keep room above the keyboard; it shrinks back when the keyboard goes.
    val keyboardUp by rememberKeyboardShown()
    val chosenHeight = if (sidebar) 1f else (drawerHeightFraction ?: uiState.drawerHeightFraction).coerceIn(0.4f, 1.0f)
    val effectiveHeightFraction by animateFloatAsState(
        targetValue = if (keyboardUp) 1f else chosenHeight,
        animationSpec = tween(KEYBOARD_GROW_MS),
        label = "drawerHeightWithKeyboard",
    )
    val effectiveWidthFraction = (drawerWidthFraction ?: uiState.drawerWidthFraction).coerceIn(0.4f, 1.0f)
        .let { if (sidebar) it.coerceAtMost(SIDEBAR_MAX_WIDTH_FRACTION) else it }
    val effectiveAlignment = (drawerAlignment ?: uiState.drawerAlignment).let {
        // A sidebar has to sit on a side; "centre" means the default trailing edge.
        if (sidebar && it != LauncherSettings.DRAWER_ALIGNMENT_LEFT) LauncherSettings.DRAWER_ALIGNMENT_RIGHT else it
    }
    val drawerColors = rememberDrawerColors(
        theme = uiState.drawerThemeTint,
        accentArgb = uiState.drawerAccentColor,
        customArgb = uiState.drawerCustomColor,
        glass = uiState.drawerGlassEnabled,
        panelOpacity = uiState.drawerPanelOpacity,
    )
    val drawerStyle = remember(uiState.drawerStyle) { drawerStyleSpec(uiState.drawerStyle) }
    // The app-drawer UI scale of this display profile (Settings ▸ Grid & DPI).
    val systemDensity = LocalDensity.current
    val drawerDensity = remember(systemDensity, uiState.dpiAppDrawer) {
        Density(
            density = systemDensity.density * uiState.dpiAppDrawer,
            fontScale = systemDensity.fontScale * uiState.dpiAppDrawer,
        )
    }

    val context = LocalContext.current
    var menuTarget by remember { mutableStateOf<Pair<AppItem, Rect>?>(null) }
    var renameTarget by remember { mutableStateOf<AppItem?>(null) }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val focusManager = LocalFocusManager.current
    // The keyboard was closed (back, its own close key): the search field lets go too, so no
    // cursor lingers and a tap on the field brings the keyboard back.
    var hadKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardUp) {
        if (hadKeyboard && !keyboardUp) focusManager.clearFocus()
        hadKeyboard = keyboardUp
    }
    // The drawer stays composed while closed (translated off-screen), so its grid scroll persists
    // across opens. When "open at top" is on, reset it to the top as the drawer closes (invisible),
    // so the next open shows the "most used" row / A–Z start instead of the last scroll position.
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    LaunchedEffect(drawerOpen, uiState.drawerOpensAtTop) {
        // Check the offset too: a short scroll leaves the first row visible (index still 0) but
        // offset > 0, and skipping the reset then would reopen the drawer slightly scrolled.
        val gridScrolled = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        if (!drawerOpen && uiState.drawerOpensAtTop && gridScrolled) {
            gridState.scrollToItem(0)
        }
        val listScrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        if (!drawerOpen && uiState.drawerOpensAtTop && listScrolled) {
            listState.scrollToItem(0)
        }
    }

    // HOME pressed → dismiss the popup, drop search focus (which hides the soft keyboard) and reset the
    // query, so the keyboard never lingers after the drawer slides away and the drawer reopens fresh.
    LaunchedEffect(homeSignals) {
        homeSignals.collect {
            menuTarget = null
            focusManager.clearFocus()
            viewModel.onQueryChange("")
        }
    }

    // Turning the drawer search OFF must clear any active query — the search field (and its clear
    // button) disappear, so an old filter would otherwise strand the grid with no way to reset it.
    LaunchedEffect(uiState.showSearch) {
        if (!uiState.showSearch && uiState.query.isNotBlank()) viewModel.onQueryChange("")
    }

    val badges = if (uiState.showNotificationDots) uiState.badges else emptyMap()

    // Private Space (Android 15+): a section at the end of the "All" list (PrivateSpace.kt).
    val privateSpaceUi by viewModel.privateSpace.collectAsStateWithLifecycle()
    val latestOnClose by rememberUpdatedState(onClose)
    val privateSpaceDrawer = remember(privateSpaceUi) {
        privateSpaceUi?.let { ui ->
            PrivateSpaceDrawer(
                ui = ui,
                onToggleLock = viewModel::togglePrivateSpace,
                onInstall = {
                    viewModel.privateSpaceInstallSender()?.let { sender ->
                        runCatching { context.startIntentSender(sender, null, 0, 0, 0) }
                    }
                },
                onOpen = { app -> if (viewModel.launchPrivate(app)) latestOnClose() },
            )
        }
    }

    // All app icons in the drawer (grid, folder sheet, popup) honour the themed-icons setting and the
    // user's app-label text-size multiplier.
    CompositionLocalProvider(
        LocalThemedIcons provides uiState.useThemedIcons,
        LocalIconPack provides uiState.iconPackPackage,
        LocalIconShape provides uiState.iconShape,
        LocalPrivateSpace provides privateSpaceDrawer,
        LocalAppLabelScale provides uiState.appLabelTextScale,
        LocalAppLabelLines provides if (uiState.twoLineLabels) 2 else 1,
        LocalIconSizeScale provides uiState.iconSizeScale,
    ) {
    ExpressiveTheme {
    CompositionLocalProvider(LocalDensity provides drawerDensity) {
    AppDrawerContent(
        colors = drawerColors,
        style = drawerStyle,
        searchAtBottom = uiState.drawerSearchPosition == LauncherSettings.SEARCH_BOTTOM,
        sidebar = sidebar,
        apps = uiState.apps,
        frequentApps = uiState.frequentApps,
        folders = uiState.folders,
        query = uiState.query,
        columns = uiState.columns,
        badges = badges,
        badgeShowCount = uiState.notificationDotCount,
        badgeScale = uiState.notificationDotScale,
        showSearch = uiState.showSearch,
        onQueryChange = viewModel::onQueryChange,
        onFolderClick = { openFolderId = it.id },
        onAppClick = { app ->
            if (app.packageName == context.packageName) {
                onOpenSettings()
            } else if (viewModel.onAppClick(app)) {
                // Keep the drawer open if the launch failed, so the user isn't dumped back to home
                // with no explanation.
                onClose()
            }
        },
        onAppLongClick = { app, bounds -> menuTarget = app to bounds },
        onClose = onClose,
        onDrawerDrag = onDrawerDrag,
        onDrawerSettle = onDrawerSettle,
        dragController = dragController,
        onDragOutStart = onDragOutStart,
        onDropOnHome = { app, page, cellX, cellY -> viewModel.addToHomeAt(app, page, cellX, cellY) },
        onDropOnDock = { app -> viewModel.addToDock(app) },
        showLabels = uiState.showLabels,
        calc = uiState.calc,
        settingResults = uiState.settingResults,
        contactResults = uiState.contactResults,
        shortcutResults = uiState.shortcutResults,
        actionResults = uiState.actionResults,
        searchWeb = uiState.searchWeb,
        tabs = uiState.tabs,
        selectedTab = uiState.selectedTab,
        onSelectTab = viewModel::selectTab,
        locked = uiState.desktopLocked,
        gridState = gridState,
        listState = listState,
        drawerLayoutMode = uiState.drawerLayoutMode,
        drawerIndexBarEnabled = uiState.drawerIndexBarEnabled,
        azIndexPosition = uiState.azIndexPosition,
        azIndexScale = uiState.azIndexScale,
        drawerCornerRadius = uiState.drawerCornerRadius,
        drawerHandleStyle = uiState.drawerHandleStyle,
        drawerBottomSpace = uiState.drawerBottomSpace,
        drawerLetterHeaders = uiState.drawerLetterHeaders,
        drawerLargeLetterPopup = uiState.drawerLargeLetterPopup,
        drawerSwipeTabs = uiState.drawerSwipeTabs,
        drawerFlickClose = uiState.drawerFlickClose,
        drawerOpen = drawerOpen,
        drawerHeightFraction = effectiveHeightFraction,
        drawerWidthFraction = effectiveWidthFraction,
        drawerAlignment = effectiveAlignment,
        drawerScrimOpacity = uiState.drawerScrimOpacity,
        drawerProgress = drawerProgress,
        modifier = modifier,
    )
    }

    val menu = menuTarget
    if (menu != null) {
        val (app, bounds) = menu
        val inHome = app.key in uiState.homeKeys
        val inDock = app.key in uiState.dockKeys
        // A full visible dock can't show another favorite — offering "Add to dock" would appear to
        // do nothing (the new favorite lands in the invisible hidden tail). Hide the add action then.
        val dockFull = uiState.dockKeys.size >= uiState.dockColumns
        // Anchor to the icon's edge (not its centre) so the popup clears it with a small gap: below
        // the cell for top-half icons, above it for bottom-half ones.
        val preferAbove = bounds.center.y > windowHeightPx / 2
        val anchorY = (if (preferAbove) bounds.top else bounds.bottom).roundToInt()
        val anchor = IntOffset(bounds.center.x.roundToInt(), anchorY)
        AppActionPopup(
            app = app,
            anchor = anchor,
            preferAbove = preferAbove,
            actions = listOfNotNull(
                PopupAction(stringResource(R.string.app_info), LauncherIcons.Info) { AppActions.openAppInfo(context, app) },
                PopupAction(stringResource(R.string.rename), LauncherIcons.Edit) { renameTarget = app },
                // The icon picker lives with the home screen (shared drag controller), like the split picker.
                PopupAction(stringResource(R.string.change_icon), LauncherIcons.SwapVert) {
                    dragController.iconPickRequest = app
                },
                if (uiState.floatingLaunchEnabled) {
                    PopupAction(stringResource(R.string.open_floating), LauncherIcons.OpenInNew) {
                        if (viewModel.launchFloating(app)) onClose()
                    }
                } else {
                    null
                },
                if (uiState.floatingLaunchEnabled) {
                    PopupAction(stringResource(R.string.add_floating_shortcut), LauncherIcons.Add) {
                        viewModel.addFloatingShortcut(app)
                    }
                } else {
                    null
                },
                if (uiState.splitLaunchEnabled) {
                    // The partner picker lives with the home screen (shared drag controller).
                    PopupAction(stringResource(R.string.open_split), LauncherIcons.SwapHoriz) {
                        dragController.splitRequest = app
                    }
                } else {
                    null
                },
                PopupAction(
                    stringResource(if (inHome) R.string.remove_from_home else R.string.add_to_home),
                    if (inHome) LauncherIcons.Close else LauncherIcons.Add,
                ) {
                    if (inHome) viewModel.removeFromHome(app) else viewModel.addToHome(app)
                },
                // Offer "Remove from dock" for a member, "Add to dock" only when a visible slot is
                // free; a full dock hides the add action instead of appearing to do nothing.
                if (inDock) {
                    PopupAction(stringResource(R.string.remove_from_dock), LauncherIcons.Close) {
                        viewModel.removeFromDock(app)
                    }
                } else if (!dockFull) {
                    PopupAction(stringResource(R.string.add_to_dock), LauncherIcons.Add) {
                        viewModel.addToDock(app)
                    }
                } else {
                    null
                },
                PopupAction(stringResource(R.string.hide_app), LauncherIcons.VisibilityOff) { viewModel.hideApp(app) },
                PopupAction(stringResource(R.string.uninstall), LauncherIcons.Delete) { AppActions.uninstall(context, app) },
            ),
            onDismiss = { menuTarget = null },
            onPinShortcut = { item -> viewModel.pinShortcut(item) },
            onRename = { newLabel -> viewModel.setCustomAppLabel(app.key, newLabel) },
        )
    }

    renameTarget?.let { app ->
        RenameDialog(
            initialName = app.label,
            onConfirm = { viewModel.setCustomAppLabel(app.key, it) },
            onReset = { viewModel.setCustomAppLabel(app.key, "") },
            onDismiss = { renameTarget = null },
        )
    }

    // Close the open folder sheet if its folder was deleted (or all members removed and it's gone).
    LaunchedEffect(openFolderId, uiState.folders) {
        if (openFolderId != null && uiState.folders.none { it.id == openFolderId }) openFolderId = null
    }
    val openFolder = openFolderId?.let { id -> uiState.folders.firstOrNull { it.id == id } }
    if (openFolder != null) {
        DrawerFolderSheet(
            folder = openFolder,
            candidateApps = uiState.apps,
            badges = badges,
            badgeShowCount = uiState.notificationDotCount,
            badgeScale = uiState.notificationDotScale,
            onRename = { viewModel.renameDrawerFolder(openFolder.id, it) },
            onAppClick = { app ->
                if (viewModel.onAppClick(app)) {
                    openFolderId = null
                    onClose()
                }
            },
            onRemoveApp = { viewModel.removeAppFromDrawerFolder(openFolder.id, it.key) },
            onAddApps = { keys -> viewModel.addAppsToDrawerFolder(openFolder.id, keys) },
            onDelete = {
                viewModel.deleteDrawerFolder(openFolder.id)
                openFolderId = null
            },
            onDismiss = { openFolderId = null },
        )
    }
    }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawerContent(
    colors: DrawerColors,
    style: DrawerStyleSpec,
    searchAtBottom: Boolean,
    sidebar: Boolean,
    apps: List<AppItem>,
    frequentApps: List<AppItem>,
    folders: List<DrawerFolderUi>,
    query: String,
    columns: Int,
    badges: Map<String, Int>,
    badgeShowCount: Boolean,
    badgeScale: Float,
    showSearch: Boolean,
    onQueryChange: (String) -> Unit,
    onFolderClick: (DrawerFolderUi) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem, Rect) -> Unit,
    onClose: () -> Unit = {},
    onDrawerDrag: (Float) -> Unit,
    onDrawerSettle: (Float) -> Unit,
    dragController: HomeDragController,
    onDragOutStart: () -> Unit,
    onDropOnHome: (AppItem, Int, Int, Int) -> Unit,
    onDropOnDock: (AppItem) -> Unit,
    showLabels: Boolean,
    calc: SearchResult.Calculation?,
    settingResults: List<SearchResult.Setting>,
    contactResults: List<SearchResult.Contact>,
    shortcutResults: List<SearchResult.Shortcut> = emptyList(),
    actionResults: List<SearchResult.Action> = emptyList(),
    searchWeb: Boolean = false,
    // Drawer folders shown as tabs (id to name); empty = the feature is off or there are none.
    tabs: List<Pair<Long, String>> = emptyList(),
    selectedTab: Long? = null,
    onSelectTab: (Long?) -> Unit = {},
    locked: Boolean,
    gridState: LazyGridState,
    listState: LazyListState,
    drawerLayoutMode: String = LauncherSettings.DRAWER_LAYOUT_GRID,
    drawerIndexBarEnabled: Boolean = true,
    azIndexPosition: String = LauncherSettings.AZ_POSITION_VERTICAL_RIGHT,
    azIndexScale: Float = 1f,
    drawerCornerRadius: Int = LauncherSettings.DRAWER_CORNER_AUTO,
    drawerHandleStyle: String = LauncherSettings.DRAWER_HANDLE_BAR,
    drawerBottomSpace: Int = 0,
    drawerLetterHeaders: Boolean = false,
    drawerLargeLetterPopup: Boolean = true,
    drawerSwipeTabs: Boolean = true,
    drawerFlickClose: Boolean = false,
    drawerOpen: Boolean = true,
    drawerHeightFraction: Float = 1.0f,
    drawerWidthFraction: Float = 1.0f,
    drawerAlignment: String = LauncherSettings.DRAWER_ALIGNMENT_CENTER,
    drawerScrimOpacity: Float = 0.5f,
    /** How far open the drawer is, 0..1 — a lambda so it is read while drawing, not composing. */
    drawerProgress: () -> Float = { 1.0f },
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val privateSpace = LocalPrivateSpace.current
    val flickClosePx = with(LocalDensity.current) { 2400.dp.toPx() }
    val pullConnection = remember(onDrawerDrag, onDrawerSettle, drawerOpen, drawerFlickClose, onClose) {
        object : NestedScrollConnection {
            private var pulling = false
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pulling && available.y < 0f) {
                    onDrawerDrag(available.y)
                    return available
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    pulling = true
                    onDrawerDrag(available.y)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Settings ▸ App drawer ▸ flick down to close: a fast downward flick closes the
                // drawer wherever the list is scrolled to, instead of flinging the list.
                if (!pulling && drawerFlickClose && available.y > flickClosePx) {
                    onClose()
                    return available
                }
                if (pulling) {
                    pulling = false
                    onDrawerSettle(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pulling) {
                    pulling = false
                    onDrawerSettle(consumed.y + available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val sheetAlignment = when (drawerAlignment.lowercase()) {
        LauncherSettings.DRAWER_ALIGNMENT_LEFT -> Alignment.BottomStart
        LauncherSettings.DRAWER_ALIGNMENT_RIGHT -> Alignment.BottomEnd
        else -> Alignment.BottomCenter
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = sheetAlignment,
    ) {
        if (drawerScrimOpacity > 0f) {
            // Present only while the drawer is at least partly open (it would catch taps otherwise);
            // its strength follows the slide in the draw phase.
            val progress by rememberUpdatedState(drawerProgress)
            val scrimShown by remember { derivedStateOf { progress() > 0f } }
            if (scrimShown) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (progress() * drawerScrimOpacity * 0.7f).coerceIn(0f, 0.8f) }
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures { onClose() }
                        }
                )
            }
        }

        // Settings ▸ App drawer ▸ corner radius; "Auto" keeps the drawer style's own corners (and a
        // square full-screen drawer).
        val customCorner = drawerCornerRadius >= 0
        val panelCorner = if (customCorner) drawerCornerRadius.dp else style.corner
        val panelShape = when {
            !sidebar && !customCorner && drawerHeightFraction >= 1.0f && drawerWidthFraction >= 1.0f -> RoundedCornerShape(0.dp)
            !sidebar -> RoundedCornerShape(topStart = panelCorner, topEnd = panelCorner)
            drawerAlignment == LauncherSettings.DRAWER_ALIGNMENT_LEFT ->
                RoundedCornerShape(topEnd = panelCorner, bottomEnd = panelCorner)
            else -> RoundedCornerShape(topStart = panelCorner, bottomStart = panelCorner)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(drawerWidthFraction.coerceIn(0.4f, 1.0f))
                .fillMaxHeight(drawerHeightFraction.coerceIn(0.4f, 1.0f))
                .graphicsLayer {
                    translationY = (1f - drawerProgress()) * size.height
                }
                .liquidGlass(
                    shape = panelShape,
                    tint = colors.container,
                    highlight = if (colors.glass) 1f else 0f,
                )
                // Swallow touches on the panel itself so they never reach the tap-to-close scrim.
                .pointerInput(Unit) {},
        ) {
        DrawerPanelTheme(colors = colors, style = style) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // A drawer that stops short of the top must not reserve the status bar's height
                    // above its handle (that was the empty band at the top of the panel).
                    .windowInsetsPadding(
                        // The keyboard's room only while Android really shows it (keyboardInsets).
                        if (sidebar || drawerHeightFraction >= 1.0f) {
                            WindowInsets.systemBars.union(WindowInsets.displayCutout).union(keyboardInsets())
                        } else {
                            WindowInsets.systemBars.union(WindowInsets.displayCutout)
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                                .union(keyboardInsets())
                        },
                    )
                    // Settings ▸ App drawer ▸ space below the drawer (a taskbar the system does not report),
                    // or at least the room Folark's own taskbar takes when it stays on screen.
                    .padding(bottom = maxOf(drawerBottomSpace.dp, LocalTaskbarInset.current))
                    .nestedScroll(pullConnection),
            ) {
            DragHandle(width = style.handleWidth, arrow = drawerHandleStyle == LauncherSettings.DRAWER_HANDLE_ARROW, progress = drawerProgress)
            if (showSearch && !searchAtBottom) {
                DrawerSearchField(query = query, onQueryChange = onQueryChange, colors = colors, style = style)
            }
            if (tabs.isNotEmpty() && query.isBlank()) {
                DrawerTabRow(tabs = tabs, selected = selectedTab, onSelect = onSelectTab)
            }
            val context = LocalContext.current
            val searching = query.isNotBlank()
            var gridWidthPx by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            val preferredIconSize = 56.dp * LocalIconSizeScale.current
            val drawerIconSize = if (gridWidthPx > 0 && columns > 0) {
                iconSizeForCell(with(density) { (gridWidthPx.toFloat() / columns).toDp() }, preferredIconSize)
            } else {
                preferredIconSize
            }

            // Room kept free for a horizontal A-Z bar, so it never lies over the apps.
            val indexStrip = if (drawerIndexBarEnabled && !searching) 26.dp * azIndexScale else 0.dp
            val indexStripPadding = PaddingValues(
                top = if (azIndexPosition == LauncherSettings.AZ_POSITION_HORIZONTAL_TOP) indexStrip else 0.dp,
                bottom = if (azIndexPosition == LauncherSettings.AZ_POSITION_HORIZONTAL_BOTTOM) indexStrip else 0.dp,
            )
            // Letter headings: the full list as runs of apps that share a first letter (a single run
            // without a letter when headings are off or a search is showing).
            val letterRuns: List<Pair<Char?, List<AppItem>>> = remember(apps, drawerLetterHeaders, searching) {
                if (!drawerLetterHeaders || searching) {
                    listOf<Pair<Char?, List<AppItem>>>(null to apps)
                } else {
                    buildList<Pair<Char?, List<AppItem>>> {
                        var start = 0
                        while (start < apps.size) {
                            val letter = drawerLetterOf(apps[start])
                            var end = start + 1
                            while (end < apps.size && drawerLetterOf(apps[end]) == letter) end++
                            add(letter to apps.subList(start, end))
                            start = end
                        }
                    }
                }
            }
            // The letter under the finger on the A-Z bar, shown large in the middle of the list.
            var activeLetter by remember { mutableStateOf<Char?>(null) }
            val currentSelectedTab by rememberUpdatedState(selectedTab)
            val currentSelectTab by rememberUpdatedState(onSelectTab)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Settings ▸ App drawer ▸ swipe sideways to change tab. Watched on the way down
                    // to the icons; a drag that starts on a horizontal A-Z bar, or while an icon is
                    // being dragged out, is left alone.
                    .pointerInput(drawerSwipeTabs, tabs, searching, azIndexPosition, indexStrip) {
                        if (!drawerSwipeTabs || tabs.isEmpty() || searching) return@pointerInput
                        val slop = viewConfiguration.touchSlop
                        val stripPx = indexStrip.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val onBar = (azIndexPosition == LauncherSettings.AZ_POSITION_HORIZONTAL_BOTTOM && down.position.y > size.height - stripPx) ||
                                (azIndexPosition == LauncherSettings.AZ_POSITION_HORIZONTAL_TOP && down.position.y < stripPx)
                            var total = Offset.Zero
                            var claimed = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                total += change.positionChange()
                                if (onBar || dragController.moving) { claimed = false; break }
                                if (!claimed && abs(total.x) > slop * 2f && abs(total.x) > abs(total.y) * 1.5f) claimed = true
                                if (claimed) change.consume()
                            }
                            if (claimed && abs(total.x) > slop * 3f) {
                                val ids: List<Long?> = listOf<Long?>(null) + tabs.map { it.first }
                                val at = ids.indexOf(currentSelectedTab).coerceAtLeast(0)
                                val next = (at + if (total.x < 0f) 1 else -1).coerceIn(0, ids.lastIndex)
                                if (next != at) currentSelectTab(ids[next])
                            }
                        }
                    },
            ) {
                if (drawerLayoutMode == LauncherSettings.DRAWER_LAYOUT_LIST) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize().padding(indexStripPadding),
                    ) {
                        if (!searching) {
                            if (frequentApps.isNotEmpty()) {
                                item(contentType = "frequent") {
                                    FrequentAppsCard(
                                        apps = frequentApps,
                                        columns = columns,
                                        badges = badges,
                                        badgeShowCount = badgeShowCount,
                                        badgeScale = badgeScale,
                                        showLabels = showLabels,
                                        iconSize = drawerIconSize,
                                        onAppClick = onAppClick,
                                        onAppLongClick = onAppLongClick,
                                    )
                                }
                            }
                            items(items = folders, key = { "folder-${it.id}" }, contentType = { "folder" }) { folder ->
                                DrawerFolderTile(
                                    folder = folder,
                                    showLabel = showLabels,
                                    tileSize = drawerIconSize,
                                    badgeCount = folder.apps.sumOf { badges[it.badgeKey] ?: 0 },
                                    badgeShowCount = badgeShowCount,
                                    badgeScale = badgeScale,
                                    onClick = { onFolderClick(folder) },
                                )
                            }
                            letterRuns.forEachIndexed { runIndex, (letter, runApps) ->
                            if (letter != null) {
                                item(key = "letter-$runIndex", contentType = "letter") { DrawerLetterHeader(letter) }
                            }
                            items(items = runApps, key = { it.key }, contentType = { "app" }) { app ->
                                AppListRow(
                                    app = app,
                                    badgeCount = badges[app.badgeKey] ?: 0,
                                    badgeShowCount = badgeShowCount,
                                    badgeScale = badgeScale,
                                    showLabels = showLabels,
                                    onAppClick = onAppClick,
                                    onAppLongClick = onAppLongClick,
                                    dragController = dragController,
                                    onDragOutStart = onDragOutStart,
                                    onDropOnHome = onDropOnHome,
                                    onDropOnDock = onDropOnDock,
                                    haptics = haptics,
                                    locked = locked,
                                )
                            }
                            }
                            if (selectedTab == null) privateSpace?.let { privateSpaceRows(it, colors) }
                        } else {
                            calc?.let { c ->
                                item(contentType = "calc") {
                                    val copiedMsg = stringResource(R.string.search_calc_copied)
                                    CalcResultCard(c) {
                                        val clip = context.getSystemService(ClipboardManager::class.java)
                                        clip?.setPrimaryClip(ClipData.newPlainText("result", c.result))
                                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            if (actionResults.isNotEmpty()) {
                                item(contentType = "header") {
                                    ExpressiveSectionTitle(stringResource(R.string.search_section_actions))
                                }
                                items(items = actionResults, key = { it.id }, contentType = { "action" }) { action ->
                                    ExpressiveActionRow(label = action.label, description = action.detail, trailingIcon = LauncherIcons.OpenInNew) {
                                        if (startSearchAction(context, action)) onClose()
                                    }
                                }
                            }
                            if (apps.isNotEmpty()) {
                                item(contentType = "header") {
                                    ExpressiveSectionTitle(stringResource(R.string.search_section_apps))
                                }
                                items(items = apps, key = { it.key }, contentType = { "app" }) { app ->
                                    AppListRow(
                                        app = app,
                                        badgeCount = badges[app.badgeKey] ?: 0,
                                        badgeShowCount = badgeShowCount,
                                        badgeScale = badgeScale,
                                        showLabels = showLabels,
                                        onAppClick = onAppClick,
                                        onAppLongClick = onAppLongClick,
                                        dragController = dragController,
                                        onDragOutStart = onDragOutStart,
                                        onDropOnHome = onDropOnHome,
                                        onDropOnDock = onDropOnDock,
                                        haptics = haptics,
                                        locked = locked,
                                    )
                                }
                            }
                            if (settingResults.isNotEmpty()) {
                                item(contentType = "header") {
                                    ExpressiveSectionTitle(stringResource(R.string.search_section_settings))
                                }
                                items(items = settingResults, key = { it.id }, contentType = { "setting" }) { setting ->
                                    ExpressiveActionRow(label = setting.title, description = "") {
                                        runCatching {
                                            context.startActivity(
                                                Intent(setting.action)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    }
                                }
                            }
                            if (contactResults.isNotEmpty()) {
                                item(contentType = "header") {
                                    ExpressiveSectionTitle(stringResource(R.string.search_section_contacts))
                                }
                                items(items = contactResults, key = { it.id }, contentType = { "contact" }) { contact ->
                                    ContactResultRow(contact, onOpened = onClose)
                                }
                            }
                            if (shortcutResults.isNotEmpty()) {
                                item(contentType = "header") {
                                    ExpressiveSectionTitle(stringResource(R.string.search_section_shortcuts))
                                }
                                items(items = shortcutResults, key = { it.id }, contentType = { "shortcut" }) { shortcut ->
                                    ExpressiveActionRow(label = shortcut.label, description = shortcut.appLabel) {
                                        AppShortcuts.startById(context, shortcut.packageName, shortcut.shortcutId, shortcut.userSerial)
                                        onClose()
                                    }
                                }
                            }
                            if (searchWeb) {
                                item(contentType = "web") {
                                    ExpressiveActionRow(
                                        label = stringResource(R.string.search_web, query.trim()),
                                        description = "",
                                        trailingIcon = LauncherIcons.Search,
                                    ) { launchWebSearch(context, query.trim()) }
                                }
                            }
                            if (calc == null && apps.isEmpty() && settingResults.isEmpty() && contactResults.isEmpty() &&
                                shortcutResults.isEmpty() && actionResults.isEmpty() && !searchWeb
                            ) {
                                item(contentType = "empty") {
                                    Text(
                                        text = stringResource(R.string.search_no_results),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(indexStripPadding)
                            .padding(horizontal = 12.dp)
                            .onSizeChanged { gridWidthPx = it.width },
                    ) {
                        if (!searching) {
                            if (frequentApps.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "frequent") {
                                    FrequentAppsCard(
                                        apps = frequentApps,
                                        columns = columns,
                                        badges = badges,
                                        badgeShowCount = badgeShowCount,
                                        badgeScale = badgeScale,
                                        showLabels = showLabels,
                                        iconSize = drawerIconSize,
                                        onAppClick = onAppClick,
                                        onAppLongClick = onAppLongClick,
                                    )
                                }
                            }
                            items(items = folders, key = { "folder-${it.id}" }, contentType = { "folder" }) { folder ->
                                DrawerFolderTile(
                                    folder = folder,
                                    showLabel = showLabels,
                                    tileSize = drawerIconSize,
                                    badgeCount = folder.apps.sumOf { badges[it.badgeKey] ?: 0 },
                                    badgeShowCount = badgeShowCount,
                                    badgeScale = badgeScale,
                                    onClick = { onFolderClick(folder) },
                                )
                            }
                            letterRuns.forEachIndexed { runIndex, (letter, runApps) ->
                                if (letter != null) {
                                    item(key = "letter-$runIndex", span = { GridItemSpan(maxLineSpan) }, contentType = "letter") {
                                        DrawerLetterHeader(letter)
                                    }
                                }
                                appCells(runApps, badges, badgeShowCount, badgeScale, showLabels, drawerIconSize, onAppClick,
                                    onAppLongClick, dragController, onDragOutStart, onDropOnHome, onDropOnDock, haptics, locked)
                            }
                            if (selectedTab == null) privateSpace?.let { privateSpaceCells(it, colors, showLabels, drawerIconSize) }
                        } else {
                            calc?.let { c ->
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "calc") {
                                    val copiedMsg = stringResource(R.string.search_calc_copied)
                                    CalcResultCard(c) {
                                        val clip = context.getSystemService(ClipboardManager::class.java)
                                        clip?.setPrimaryClip(ClipData.newPlainText("result", c.result))
                                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            if (actionResults.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                                    ExpressiveSectionTitle(stringResource(R.string.search_section_actions))
                                }
                                items(items = actionResults, key = { it.id }, span = { GridItemSpan(maxLineSpan) },
                                    contentType = { "action" }) { action ->
                                    ExpressiveActionRow(label = action.label, description = action.detail, trailingIcon = LauncherIcons.OpenInNew) {
                                        if (startSearchAction(context, action)) onClose()
                                    }
                                }
                            }
                            if (apps.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                                    ExpressiveSectionTitle(stringResource(R.string.search_section_apps))
                                }
                                appCells(apps, badges, badgeShowCount, badgeScale, showLabels, drawerIconSize, onAppClick,
                                    onAppLongClick, dragController, onDragOutStart, onDropOnHome, onDropOnDock, haptics, locked)
                            }
                            if (settingResults.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                                    ExpressiveSectionTitle(stringResource(R.string.search_section_settings))
                                }
                                items(items = settingResults, key = { it.id }, span = { GridItemSpan(maxLineSpan) },
                                    contentType = { "setting" }) { setting ->
                                    ExpressiveActionRow(label = setting.title, description = "") {
                                        runCatching {
                                            context.startActivity(
                                                Intent(setting.action)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    }
                                }
                            }
                            if (contactResults.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                                    ExpressiveSectionTitle(stringResource(R.string.search_section_contacts))
                                }
                                items(items = contactResults, key = { it.id }, span = { GridItemSpan(maxLineSpan) },
                                    contentType = { "contact" }) { contact ->
                                    ContactResultRow(contact, onOpened = onClose)
                                }
                            }
                            if (shortcutResults.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                                    ExpressiveSectionTitle(stringResource(R.string.search_section_shortcuts))
                                }
                                items(items = shortcutResults, key = { it.id }, span = { GridItemSpan(maxLineSpan) },
                                    contentType = { "shortcut" }) { shortcut ->
                                    ExpressiveActionRow(label = shortcut.label, description = shortcut.appLabel) {
                                        AppShortcuts.startById(context, shortcut.packageName, shortcut.shortcutId, shortcut.userSerial)
                                        onClose()
                                    }
                                }
                            }
                            if (searchWeb) {
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "web") {
                                    ExpressiveActionRow(
                                        label = stringResource(R.string.search_web, query.trim()),
                                        description = "",
                                        trailingIcon = LauncherIcons.Search,
                                    ) { launchWebSearch(context, query.trim()) }
                                }
                            }
                            if (calc == null && apps.isEmpty() && settingResults.isEmpty() && contactResults.isEmpty() &&
                                shortcutResults.isEmpty() && actionResults.isEmpty() && !searchWeb
                            ) {
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                                    Text(
                                        text = stringResource(R.string.search_no_results),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (drawerIndexBarEnabled && !searching) {
                    val coroutineScope = rememberCoroutineScope()
                    val alignModifier = when (azIndexPosition) {
                        LauncherSettings.AZ_POSITION_VERTICAL_LEFT -> Modifier.align(Alignment.CenterStart)
                        LauncherSettings.AZ_POSITION_HORIZONTAL_TOP -> Modifier.align(Alignment.TopCenter)
                        LauncherSettings.AZ_POSITION_HORIZONTAL_BOTTOM -> Modifier.align(Alignment.BottomCenter)
                        else -> Modifier.align(Alignment.CenterEnd)
                    }
                    DrawerIndexBar(
                        position = azIndexPosition,
                        scale = azIndexScale,
                        onLetterSelected = { char ->
                            val headerOffset = (if (frequentApps.isNotEmpty()) 1 else 0) + folders.size
                            val matchIdx = if (char == '#') {
                                0
                            } else {
                                apps.indexOfFirst {
                                    val firstChar = it.label.firstOrNull()?.uppercaseChar() ?: ' '
                                    firstChar >= char
                                    // No app from this letter on: go to the end, not back to the top.
                                }.let { if (it < 0) (apps.size - 1).coerceAtLeast(0) else it }
                            }
                            // With letter headings every run before the target adds one more item.
                            var headingsBefore = 0
                            if (letterRuns.firstOrNull()?.first != null) {
                                var seen = 0
                                for ((_, run) in letterRuns) {
                                    if (seen > matchIdx) break
                                    headingsBefore++
                                    seen += run.size
                                }
                                headingsBefore = (headingsBefore - 1).coerceAtLeast(0)
                            }
                            val targetIndex = (headerOffset + matchIdx + headingsBefore)
                                .coerceIn(0, (headerOffset + apps.size + letterRuns.size).coerceAtLeast(0))
                            coroutineScope.launch {
                                if (drawerLayoutMode == LauncherSettings.DRAWER_LAYOUT_LIST) {
                                    listState.scrollToItem(targetIndex)
                                } else {
                                    gridState.scrollToItem(targetIndex)
                                }
                            }
                        },
                        modifier = alignModifier,
                        onActiveLetter = if (drawerLargeLetterPopup) ({ letter: Char? -> activeLetter = letter }) else null,
                    )
                    activeLetter?.let { letter ->
                        Surface(
                            shape = CircleShape,
                            color = LocalAccent.current,
                            contentColor = readableOn(LocalAccent.current),
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(104.dp).align(Alignment.Center),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = letter.toString(), fontSize = 46.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (showSearch && searchAtBottom) {
                DrawerSearchField(query = query, onQueryChange = onQueryChange, colors = colors, style = style)
            }
            }
        }
        }
    }
}

/** Width cap of the inner-display sidebar drawer, as a fraction of the unfolded screen. */
private const val SIDEBAR_MAX_WIDTH_FRACTION = 0.5f

/**
 * Re-themes everything inside the drawer panel from this display's [DrawerColors]: the Material
 * colour roles the drawer's rows already read (surface / onSurface / …), the expressive palette of
 * the search-result cards, the accent and the style's font.
 */
@Composable
private fun DrawerPanelTheme(
    colors: DrawerColors,
    style: DrawerStyleSpec,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme
    val scheme = remember(base, colors) {
        base.copy(
            surface = colors.base,
            onSurface = colors.content,
            surfaceVariant = colors.field,
            onSurfaceVariant = colors.contentDim,
            primary = colors.accent,
            onPrimary = colors.onAccent,
        )
    }
    val palette = remember(colors) { colors.toExpressivePalette() }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalExpressivePalette provides palette,
            LocalAccent provides colors.accent,
            LocalContentColor provides colors.content,
        ) {
            ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = style.fontFamily ?: LocalTextStyle.current.fontFamily), content)
        }
    }
}

/** How long a short drawer takes to grow to full height when the keyboard comes up. */
private const val KEYBOARD_GROW_MS = 220
