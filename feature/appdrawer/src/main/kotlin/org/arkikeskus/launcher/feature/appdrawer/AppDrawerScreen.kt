package org.arkikeskus.launcher.feature.appdrawer

import org.arkikeskus.launcher.ui.R as UiR
import org.arkikeskus.launcher.ui.RemoveChoices
import org.arkikeskus.launcher.model.AzPosition
import org.arkikeskus.launcher.model.DrawerAlignment
import org.arkikeskus.launcher.model.DrawerHandle
import org.arkikeskus.launcher.model.DrawerLayout
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SearchEngine
import org.arkikeskus.launcher.model.SearchPosition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.roundToInt
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.SearchResult
import org.arkikeskus.launcher.ui.AppActionPopup
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.AppActions
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
import org.arkikeskus.launcher.ui.drawer.rememberDrawerColors
import org.arkikeskus.launcher.ui.expressive.LocalAccent
import org.arkikeskus.launcher.ui.expressive.ExpressiveTheme
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import org.arkikeskus.launcher.ui.rememberHomeDragController
import org.arkikeskus.launcher.data.createDrawerFolder
import org.arkikeskus.launcher.data.renameDrawerFolder
import org.arkikeskus.launcher.data.deleteDrawerFolder
import org.arkikeskus.launcher.data.addAppsToDrawerFolder
import org.arkikeskus.launcher.data.removeAppFromDrawerFolder
import org.arkikeskus.launcher.data.moveDrawerFolder
import org.arkikeskus.launcher.model.catching

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
    drawerAlignment: DrawerAlignment? = null,
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
        if (sidebar && it != DrawerAlignment.LEFT) DrawerAlignment.RIGHT else it
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
    // "Hidden apps" at the end of the list, opened (after the fingerprint when locked).
    var showHidden by remember { mutableStateOf(false) }
    val unlockTitle = stringResource(R.string.drawer_hidden_unlock)
    // The app whose "Add to folder" is open (the non-drag way into a drawer folder).
    var folderTarget by remember { mutableStateOf<AppItem?>(null) }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    // Drags that stay inside the drawer: an icon dropped on another app makes a folder, one dropped
    // on a folder goes in. Dropping it outside the panel is still the old drag-to-home gesture.
    val folderDrag = remember { DrawerFolderDrag() }
    DisposableEffect(folderDrag, viewModel) {
        folderDrag.onCombine = { app, other -> viewModel.createFolderByDrop(app, other) { openFolderId = it } }
        folderDrag.onAddToFolder = { app, id ->
            viewModel.addAppToDrawerFolder(app, id)
            // Switch to the tab that just received the app so the user sees it land there.
            viewModel.selectTab(id)
        }
        folderDrag.onOpenFolder = { id -> openFolderId = id }
        onDispose {
            folderDrag.onCombine = null
            folderDrag.onAddToFolder = null
            folderDrag.onOpenFolder = null
        }
    }
    // Who is in which folder, for "that app is already in there" when it is dropped on its own folder.
    LaunchedEffect(folderDrag, uiState.folders) {
        folderDrag.folderMembers = uiState.folders.associate { f -> f.id to f.apps.map { it.key }.toSet() }
    }
    // The "+ New folder" tile in the drawer's folder area: create, then open its editor at once.
    var showNewFolderDialog by remember { mutableStateOf(false) }
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
    // Dragging an app to the top or bottom edge of the drawer scrolls the list, so a folder above
    // the fold stays reachable without letting go. Zero stops the loop, so it never spins while idle.
    var edgeScroll by remember { mutableStateOf(0f) }
    LaunchedEffect(edgeScroll, uiState.drawerLayoutMode) {
        if (edgeScroll == 0f) return@LaunchedEffect
        while (true) {
            if (uiState.drawerLayoutMode == DrawerLayout.LIST) {
                listState.scrollBy(edgeScroll)
            } else {
                gridState.scrollBy(edgeScroll)
            }
            delay(16)
        }
    }
    LaunchedEffect(folderDrag) { folderDrag.onEdgeScroll = { edgeScroll = it } }
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
                        catching { context.startIntentSender(sender, null, 0, 0, 0) }
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
    FollowClipboard(uiState.clipboardRow)
    // Apps installed lately and not opened yet get a small dot (AppIcon).
    CompositionLocalProvider(org.arkikeskus.launcher.ui.component.LocalNewApps provides uiState.newApps) {
        AppDrawerContent(
            colors = drawerColors,
            style = drawerStyle,
            searchAtBottom = uiState.drawerSearchPosition == SearchPosition.BOTTOM,
            sidebar = sidebar,
            apps = uiState.apps,
            frequentApps = uiState.frequentApps,
            clipboardRow = uiState.clipboardRow,
            folders = uiState.folders,
            // With folders as tabs there are no tiles: the folders live in the tab row instead.
            folderTiles = if (uiState.folderTabsEnabled) emptyList() else uiState.folders,
            query = uiState.query,
            columns = uiState.columns,
            badges = badges,
            badgeShowCount = uiState.notificationDotCount,
            badgeScale = uiState.notificationDotScale,
            showSearch = uiState.showSearch,
            onQueryChange = viewModel::onQueryChange,
            onFolderClick = { openFolderId = it.id },
            onNewFolder = { showNewFolderDialog = true },
            onEditFolder = { openFolderId = it },
            onRenameFolder = viewModel::renameDrawerFolder,
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
            folderDrag = folderDrag,
            onDragOutStart = onDragOutStart,
            onDropOnHome = { app, page, cellX, cellY -> viewModel.addToHomeAt(app, page, cellX, cellY) },
            onDropOnDock = { app -> viewModel.addToDock(app) },
            onDropOnTab = viewModel::moveAppBetweenTabs,
            showLabels = uiState.showLabels,
            calc = uiState.calc,
            settingResults = uiState.settingResults,
            contactResults = uiState.contactResults,
            shortcutResults = uiState.shortcutResults,
            actionResults = uiState.actionResults,
            searchWeb = uiState.searchWeb,
            searchWebEngine = uiState.searchWebEngine,
            hiddenCount = uiState.hiddenApps.size,
            hiddenLock = uiState.hiddenLock,
            onHiddenApps = {
                if (uiState.hiddenLock) confirmOwner(context, unlockTitle) { showHidden = true } else showHidden = true
            },
            searchInlineShortcuts = uiState.searchInlineShortcuts,
            azHoldScroll = uiState.azHoldScroll,
            twoPane = uiState.twoPane,
            tabs = uiState.tabs,
            tabBar = uiState.folderTabsEnabled,
            selectedTab = uiState.selectedTab,
            onSelectTab = viewModel::selectTab,
            onMoveTab = viewModel::moveDrawerFolder,
            tabsReorderable = !uiState.tabsLocked,
            onDeleteTab = { id -> viewModel.deleteDrawerFolder(id); viewModel.selectTab(null) },
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
            drawerLetterTiles = uiState.drawerLetterTiles,
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
            // The everyday actions as icons on top; how it looks and where it is filed on a
            // Customize page; Add to home / dock as rows; the ways to get rid of it on a Remove page.
            quickActions = listOfNotNull(
                PopupAction(stringResource(UiR.string.popup_app_info), LauncherIcons.Info) { AppActions.openAppInfo(context, app) },
                if (uiState.splitLaunchEnabled) {
                    // The partner picker lives with the home screen (shared drag controller).
                    PopupAction(stringResource(UiR.string.popup_split), LauncherIcons.SwapHoriz) { dragController.splitRequest = app }
                } else {
                    null
                },
                if (uiState.floatingLaunchEnabled) {
                    PopupAction(stringResource(UiR.string.popup_floating), LauncherIcons.OpenInNew) {
                        if (viewModel.launchFloating(app)) onClose()
                    }
                } else {
                    null
                },
            ),
            customize = listOfNotNull(
                // The icon picker lives with the home screen (shared drag controller), like the split picker.
                PopupAction(stringResource(R.string.change_icon), LauncherIcons.SwapVert) {
                    dragController.iconPickRequest = app
                },
                // Organizing without a drag: the drawer's folders (and tabs) are the targets here.
                PopupAction(stringResource(R.string.folder_add_to), LauncherIcons.Folder) { folderTarget = app },
                if (uiState.aspectRatioMenu && AppActions.canOpenAspectRatio(context, app)) {
                    PopupAction(stringResource(UiR.string.app_aspect_ratio), LauncherIcons.SwapHoriz) {
                        AppActions.openAspectRatio(context, app)
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
            ),
            actions = listOfNotNull(
                // Added to home: the drawer closes and home shows where it went.
                if (!inHome) {
                    PopupAction(stringResource(R.string.add_to_home), LauncherIcons.Home) {
                        viewModel.addToHome(app)
                        onClose()
                    }
                } else {
                    null
                },
                // "Add to dock" only when a visible slot is free; a full dock hides it instead of
                // appearing to do nothing.
                if (!inDock && !dockFull) {
                    PopupAction(stringResource(R.string.add_to_dock), LauncherIcons.Add) { viewModel.addToDock(app) }
                } else {
                    null
                },
            ),
            remove = RemoveChoices(
                fromHome = if (inHome) ({ viewModel.removeFromHome(app) }) else null,
                fromDock = if (inDock) ({ viewModel.removeFromDock(app) }) else null,
                hide = { viewModel.hideApp(app) },
                uninstall = if (AppActions.canUninstall(context, app)) ({ AppActions.uninstall(context, app) }) else null,
            ),
            onDismiss = { menuTarget = null },
            onPinShortcut = { item -> viewModel.pinShortcut(item) },
            onRename = { newLabel -> viewModel.setCustomAppLabel(app.key, newLabel) },
        )
    }

    if (showHidden) {
        HiddenAppsDialog(
            apps = uiState.hiddenApps,
            onOpen = { app ->
                showHidden = false
                if (viewModel.onAppClick(app)) onClose()
            },
            onUnhide = { app -> viewModel.unhideApp(app) },
            onDismiss = { showHidden = false },
        )
        // The last one shown again: nothing left to list.
        if (uiState.hiddenApps.isEmpty()) showHidden = false
    }

    renameTarget?.let { app ->
        RenameDialog(
            initialName = app.label,
            onConfirm = { viewModel.setCustomAppLabel(app.key, it) },
            onReset = { viewModel.setCustomAppLabel(app.key, "") },
            onDismiss = { renameTarget = null },
        )
    }

    // "Add to folder" in the drawer: the target is a drawer folder, which is also a tab when folders
    // are shown as tabs. A folder made here opens at once, so its name can be typed over.
    folderTarget?.let { app ->
        AddToFolderDialog(
            app = app,
            folders = uiState.folders,
            onPick = { id -> viewModel.addAppToDrawerFolder(app, id) },
            onCreate = { viewModel.createFolderWithApp(app) { openFolderId = it } },
            onDismiss = { folderTarget = null },
        )
    }

    // Close the open folder sheet if its folder was deleted (or all members removed and it's gone).
    LaunchedEffect(openFolderId, uiState.folders) {
        if (openFolderId != null && uiState.folders.none { it.id == openFolderId }) openFolderId = null
    }
    // A folder opening or closing never brings the keyboard up: the search field lets go and the
    // keyboard goes, both when the folder opens and when it closes (focus would otherwise return
    // to the search field as the folder's sheet window goes, and with it the keyboard).
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(openFolderId) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    val openFolder = openFolderId?.let { id -> uiState.folders.firstOrNull { it.id == id } }
    val editorApps by viewModel.editorApps.collectAsStateWithLifecycle()
    if (openFolder != null) {
        // The card belongs to the drawer, so it wears the drawer's own palette and font rather than
        // the app theme's — otherwise a custom drawer accent stops at the panel edge.
        DrawerPanelTheme(colors = drawerColors, style = drawerStyle) {
        FolderPopover(
            folder = openFolder,
            allApps = editorApps,
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

    if (showNewFolderDialog) {
        NewFolderDialog(
            onCreate = { name ->
                showNewFolderDialog = false
                viewModel.createDrawerFolder(name) { openFolderId = it }
            },
            onDismiss = { showNewFolderDialog = false },
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
    /** The folders drawn as tiles in the list. Empty when folders are shown as tabs instead. */
    folderTiles: List<DrawerFolderUi> = folders,
    /** Settings ▸ App drawer ▸ Clipboard row (shown above the most used apps). */
    clipboardRow: Boolean = false,
    query: String,
    columns: Int,
    badges: Map<String, Int>,
    badgeShowCount: Boolean,
    badgeScale: Float,
    showSearch: Boolean,
    onQueryChange: (String) -> Unit,
    onFolderClick: (DrawerFolderUi) -> Unit,
    onNewFolder: () -> Unit = {},
    onEditFolder: (Long) -> Unit = {},
    onRenameFolder: (Long, String) -> Unit = { _, _ -> },
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem, Rect) -> Unit,
    onClose: () -> Unit = {},
    onDrawerDrag: (Float) -> Unit,
    onDrawerSettle: (Float) -> Unit,
    dragController: HomeDragController,
    folderDrag: DrawerFolderDrag,
    onDragOutStart: () -> Unit,
    onDropOnHome: (AppItem, Int, Int, Int) -> Unit,
    onDropOnDock: (AppItem) -> Unit,
    onDropOnTab: (AppItem, Long?) -> Unit = { _, _ -> },
    showLabels: Boolean,
    calc: SearchResult.Calculation?,
    settingResults: List<SearchResult.Setting>,
    contactResults: List<SearchResult.Contact>,
    shortcutResults: List<SearchResult.Shortcut> = emptyList(),
    actionResults: List<SearchResult.Action> = emptyList(),
    searchWeb: Boolean = false,
    searchWebEngine: SearchEngine = SearchEngine.SYSTEM,
    hiddenCount: Int = 0,
    hiddenLock: Boolean = false,
    onHiddenApps: () -> Unit = {},
    searchInlineShortcuts: Boolean = false,
    azHoldScroll: Boolean = false,
    twoPane: Boolean = false,
    // Drawer folders shown as tabs (id to name); empty = the feature is off or there are none.
    tabs: List<Pair<Long, String>> = emptyList(),
    /** Whether the tab bar is shown at all — it stays up with no tabs, so the first one can be made. */
    tabBar: Boolean = tabs.isNotEmpty(),
    selectedTab: Long? = null,
    onSelectTab: (Long?) -> Unit = {},
    /** Moves a tab chip to another place in the row/rail (the same move Settings offers). */
    onMoveTab: (Int, Int) -> Unit = { _, _ -> },
    /** Settings ▸ App drawer ▸ Lock the tab order: chips stay put. */
    tabsReorderable: Boolean = true,
    onDeleteTab: (Long) -> Unit = {},
    locked: Boolean,
    gridState: LazyGridState,
    listState: LazyListState,
    drawerLayoutMode: DrawerLayout = DrawerLayout.GRID,
    drawerIndexBarEnabled: Boolean = true,
    azIndexPosition: AzPosition = AzPosition.VERTICAL_RIGHT,
    azIndexScale: Float = 1f,
    drawerCornerRadius: Int = LauncherSettings.DRAWER_CORNER_AUTO,
    drawerHandleStyle: DrawerHandle = DrawerHandle.BAR,
    drawerBottomSpace: Int = 0,
    drawerLetterHeaders: Boolean = false,
    drawerLetterTiles: Boolean = false,
    drawerLargeLetterPopup: Boolean = true,
    drawerSwipeTabs: Boolean = true,
    drawerFlickClose: Boolean = false,
    drawerOpen: Boolean = true,
    drawerHeightFraction: Float = 1.0f,
    drawerWidthFraction: Float = 1.0f,
    drawerAlignment: DrawerAlignment = DrawerAlignment.CENTER,
    drawerScrimOpacity: Float = 0.5f,
    /** How far open the drawer is, 0..1 — a lambda so it is read while drawing, not composing. */
    drawerProgress: () -> Float = { 1.0f },
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val privateSpace = LocalPrivateSpace.current
    // While an app is dragged out of the drawer, the folder tabs become the drop-target chips over
    // home (the drawer itself slides away): what the chips are and where a drop on one lands.
    androidx.compose.runtime.DisposableEffect(tabs) {
        dragController.folderTargets = tabs
        dragController.folderDrop = onDropOnTab
        onDispose {
            dragController.folderTargets = emptyList()
            dragController.folderDrop = null
        }
    }
    val pullConnection = rememberDrawerPullConnection(onDrawerDrag, onDrawerSettle, drawerOpen, drawerFlickClose, onClose)

    val sheetAlignment = when (drawerAlignment) {
        DrawerAlignment.LEFT -> Alignment.BottomStart
        DrawerAlignment.RIGHT -> Alignment.BottomEnd
        else -> Alignment.BottomCenter
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = sheetAlignment,
    ) {
        if (drawerScrimOpacity > 0f) DrawerScrim(drawerScrimOpacity, drawerProgress, onClose)

        val panelShape = drawerPanelShape(
            sidebar, drawerCornerRadius, style.corner, drawerHeightFraction, drawerWidthFraction, drawerAlignment,
        )
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
            // Keyboard navigation (D-pad / hardware keyboard): the panel takes focus when the
            // drawer opens, and the arrows then walk the app list (the focus indicator draws on
            // the icons; the lazy lists scroll to whatever is focused). Touch is unaffected.
            val kbGrab = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current
            LaunchedEffect(drawerOpen) {
                if (drawerOpen) kbGrab.requestFocus() else focusManager.clearFocus(force = true)
            }
            Column(
                modifier = Modifier
                    .focusRequester(kbGrab)
                    .focusable()
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
            DragHandle(width = style.handleWidth, arrow = drawerHandleStyle == DrawerHandle.ARROW, progress = drawerProgress)
            if (showSearch && !searchAtBottom) {
                DrawerSearchField(query = query, onQueryChange = onQueryChange, colors = colors, style = style)
            }
            if (tabBar && query.isBlank() && !(twoPane && LocalScreenType.current == ScreenType.INNER)) {
                DrawerTabBar(
                    tabs = tabs, selectedTab = selectedTab, onSelectTab = onSelectTab,
                    folderDrag = folderDrag, onRenameFolder = onRenameFolder,
                    onMoveTab = onMoveTab, tabsReorderable = tabsReorderable, onNewFolder = onNewFolder,
                    folders = folders, onEditFolder = onEditFolder, onDeleteTab = onDeleteTab,
                )
            }
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
                top = if (azIndexPosition == AzPosition.HORIZONTAL_TOP) indexStrip else 0.dp,
                bottom = if (azIndexPosition == AzPosition.HORIZONTAL_BOTTOM) indexStrip else 0.dp,
            )
            // Letter headings: the apps in runs under their first letter (one run when off or searching).
            val letterRuns = remember(apps, drawerLetterHeaders, searching) { runsByLetter(apps, drawerLetterHeaders && !searching) }
            // What the list shows, written once for both layouts (DrawerItems.kt).
            val appDeps = remember(
                badges, badgeShowCount, badgeScale, showLabels, drawerIconSize, onAppClick, onAppLongClick, dragController,
                folderDrag, selectedTab, onDragOutStart, onDropOnHome, onDropOnDock, onDropOnTab, haptics, locked, onClose,
            ) {
                DrawerAppDeps(
                    badges = badges, badgeShowCount = badgeShowCount, badgeScale = badgeScale, showLabels = showLabels,
                    iconSize = drawerIconSize, onAppClick = onAppClick, onAppLongClick = onAppLongClick,
                    dragController = dragController, folderDrag = folderDrag, sourceTab = selectedTab,
                    onDragOutStart = onDragOutStart, onDropOnHome = onDropOnHome, onDropOnDock = onDropOnDock,
                    onDropOnTab = onDropOnTab, haptics = haptics, locked = locked, onClose = onClose,
                )
            }
            val drawerItems: DrawerItems.() -> Unit = {
                if (searching) {
                    searchResults(
                        query, calc, actionResults, apps, settingResults, contactResults, shortcutResults,
                        searchWeb, searchWebEngine, searchInlineShortcuts,
                    )
                } else {
                    browse(
                        clipboardRow, frequentApps, columns, folderTiles, letterRuns,
                        drawerLetterTiles && drawerLayoutMode != DrawerLayout.LIST, onFolderClick, privateSpace, colors,
                        hiddenCount, hiddenLock, onHiddenApps,
                    )
                }
            }
            val currentSelectedTab by rememberUpdatedState(selectedTab)
            val currentSelectTab by rememberUpdatedState(onSelectTab)
            // Settings ▸ App drawer ▸ two-pane (unfolded): the tabs as a list on the left.
            val railTabs = twoPane && LocalScreenType.current == ScreenType.INNER && tabBar && !searching
            Row(Modifier.weight(1f).fillMaxWidth()) {
            if (railTabs) DrawerTabRail(
                    tabs = tabs, selected = selectedTab, onSelect = onSelectTab,
                    drag = folderDrag, onRename = onRenameFolder,
                    onNewFolder = onNewFolder,
                    onMove = onMoveTab, reorderable = tabsReorderable,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // The apps' area: a drag that stays over it is a folder drag (drop onto another
                    // app or onto a folder tile). Carrying the app up over the header — the one
                    // strip that never holds a drop target — is how it goes out to home.
                    .onGloballyPositioned { folderDrag.gridBounds = it.boundsInRoot() }
                    .swipeToChangeTab(
                        enabled = drawerSwipeTabs, tabs = tabs, searching = searching,
                        azIndexPosition = azIndexPosition, indexStrip = indexStrip, dragController = dragController,
                        selectedTab = { currentSelectedTab }, onSelectTab = { currentSelectTab(it) },
                    ),
            ) {
                if (drawerLayoutMode == DrawerLayout.LIST) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize().padding(indexStripPadding),
                    ) { DrawerListItems(this, appDeps).drawerItems() }
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
                    ) { DrawerGridItems(this, appDeps).drawerItems() }
                }

                if (drawerIndexBarEnabled && !searching) {
                    if (railTabs && selectedTab != null) {
                        OpenTabSettingsMenu(folders, selectedTab, onEditFolder, onDeleteTab, Modifier.align(Alignment.TopEnd).padding(8.dp))
                    }
                    DrawerIndexOverlay(
                        position = azIndexPosition,
                        scale = azIndexScale,
                        apps = apps,
                        letterRuns = letterRuns,
                        // Only the tiles drawn above the list push the apps down; with folders
                        // as tabs there are none, so counting folders here would overshoot.
                        itemsAbove = (if (clipboardRow) 1 else 0) + (if (frequentApps.isNotEmpty()) 1 else 0) + folderTiles.size,
                        listLayout = drawerLayoutMode == DrawerLayout.LIST,
                        listState = listState,
                        gridState = gridState,
                        largeLetterPopup = drawerLargeLetterPopup,
                        holdScroll = azHoldScroll,
                    )
                }
            }
            }
            if (showSearch && searchAtBottom) {
                DrawerSearchField(query = query, onQueryChange = onQueryChange, colors = colors, style = style)
            }
            }
        }
        }

        DrawerDragGhost(folderDrag)
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
