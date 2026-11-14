package org.arkikeskus.launcher.feature.home

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.arkikeskus.launcher.feature.home.widgets.LocalTonalWidgets
import org.arkikeskus.launcher.feature.home.widgets.WidgetChoice
import org.arkikeskus.launcher.feature.home.widgets.WidgetDragController
import org.arkikeskus.launcher.feature.home.widgets.WidgetPickerScreen
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.catching
import org.arkikeskus.launcher.ui.AppActionPopup
import org.arkikeskus.launcher.ui.AppActions
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.IconMenuItem
import org.arkikeskus.launcher.ui.IconMenuPopup
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.PopupAction
import org.arkikeskus.launcher.ui.RemoveChoices
import org.arkikeskus.launcher.ui.R as UiR

// The popups and dialogs home opens over itself (split out of HomeScreen.kt). HomeScreen keeps the
// state that decides which is open; each of these draws one and reports back through its callbacks.

/** The long-pressed app and where its menu should anchor (which surface it came from). */
internal data class AppMenuTarget(
    val app: AppItem,
    val anchor: IntOffset,
    val source: DragSource,
    val preferAbove: Boolean,
)

/**
 * Dropped on a split zone pill: Folark's split picker beside it (or, for Three, two more apps).
 * Without a way to make the split (no grant, root or module), the partner picker instead. And the
 * partner picker for an app waiting for its split-screen partner (dropped on the split target, or
 * chosen from a long-press menu on home, the dock or the drawer).
 */
@Composable
internal fun HomeSplitRequests(dragController: HomeDragController, viewModel: HomeViewModel) {
    dragController.splitSideRequest?.let { (app, zone) ->
        LaunchedEffect(app, zone) {
            dragController.splitSideRequest = null
            if (!viewModel.openSplitPicker(app, zone)) dragController.splitRequest = app
        }
    }
    dragController.splitRequest?.let { first ->
        val allApps by viewModel.allApps.collectAsStateWithLifecycle()
        SplitPartnerDialog(
            first = first,
            apps = allApps.filter { it.key != first.key },
            onPick = { second, addToHome ->
                dragController.splitRequest = null
                if (addToHome) viewModel.addAppPairShortcut(first, second)
                viewModel.launchSplit(first, second)
            },
            onDismiss = { dragController.splitRequest = null },
        )
    }
}

/** "Change icon" was chosen for an app (from a menu on home, the dock or the drawer). */
@Composable
internal fun HomeIconPicker(dragController: HomeDragController, viewModel: IconPickerViewModel = hiltViewModel()) {
    dragController.iconPickRequest?.let { app ->
        val iconPacks by viewModel.iconPacks.collectAsStateWithLifecycle()
        val customIcons by viewModel.customIcons.collectAsStateWithLifecycle()
        val iconShapes by viewModel.iconShapes.collectAsStateWithLifecycle()
        IconPickerDialog(
            app = app,
            shape = iconShapes[app.key],
            onShape = { viewModel.setIconShape(app, it) },
            packs = iconPacks.map { it.packageName to it.label },
            hasCustomIcon = app.key in customIcons,
            loadNames = viewModel::iconPackDrawableNames,
            loadIcon = viewModel::iconPackBitmap,
            onPick = { pack, drawable ->
                viewModel.setCustomIcon(app, pack, drawable)
                dragController.iconPickRequest = null
            },
            onReset = {
                viewModel.clearCustomIcon(app)
                dragController.iconPickRequest = null
            },
            onDismiss = { dragController.iconPickRequest = null },
        )
    }
}

/** The long-press menu on an app: the shared [AppActionPopup] (core/ui) filled in for home. */
@Composable
internal fun HomeAppMenu(
    menu: AppMenuTarget,
    settings: LauncherSettings,
    dragController: HomeDragController,
    viewModel: HomeViewModel,
    onAddToFolder: (AppItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AppActionPopup(
        app = menu.app,
        anchor = menu.anchor,
        // Flip the popup above the icon when it's in the lower half (e.g. dock or a bottom-row
        // icon), so it never runs off the bottom of the screen. Decided where the anchor is built.
        preferAbove = menu.preferAbove,
        // The everyday actions as icons on top; how it looks on a Customize page; the ways to
        // get rid of it together on a Remove page (each with undo, see AppEdits).
        quickActions = listOfNotNull(
            PopupAction(stringResource(UiR.string.popup_app_info), LauncherIcons.Info) { AppActions.openAppInfo(context, menu.app) },
            if (settings.launch.splitEnabled) {
                PopupAction(stringResource(UiR.string.popup_split), LauncherIcons.SwapHoriz) { dragController.splitRequest = menu.app }
            } else {
                null
            },
            if (settings.launch.floatingEnabled) {
                PopupAction(stringResource(UiR.string.popup_floating), LauncherIcons.OpenInNew) { viewModel.launchFloating(menu.app) }
            } else {
                null
            },
        ),
        customize = listOfNotNull(
            PopupAction(stringResource(R.string.change_icon), LauncherIcons.SwapVert) {
                dragController.iconPickRequest = menu.app
            },
            // A folder belongs to a screen: on home this only offers home folders. A dock item
            // is not on home, so filing it would do nothing — hence the source check.
            if (menu.source != DragSource.Dock) {
                PopupAction(stringResource(R.string.home_add_to_folder), LauncherIcons.Folder) { onAddToFolder(menu.app) }
            } else {
                null
            },
            if (settings.launch.aspectRatioMenu && AppActions.canOpenAspectRatio(context, menu.app)) {
                PopupAction(stringResource(UiR.string.app_aspect_ratio), LauncherIcons.SwapHoriz) {
                    AppActions.openAspectRatio(context, menu.app)
                }
            } else {
                null
            },
            if (settings.launch.floatingEnabled) {
                PopupAction(stringResource(R.string.add_floating_shortcut), LauncherIcons.Add) {
                    viewModel.addFloatingShortcut(menu.app)
                }
            } else {
                null
            },
        ),
        actions = emptyList(),
        remove = RemoveChoices(
            fromHome = if (menu.source != DragSource.Dock) ({ viewModel.removeFromHome(menu.app) }) else null,
            fromDock = if (menu.source == DragSource.Dock) ({ viewModel.removeFromDock(menu.app) }) else null,
            hide = { viewModel.hideApp(menu.app) },
            uninstall = if (AppActions.canUninstall(context, menu.app)) ({ AppActions.uninstall(context, menu.app) }) else null,
        ),
        onDismiss = onDismiss,
        onPinShortcut = { item -> viewModel.pinShortcut(item) },
        onRename = { newLabel -> viewModel.setCustomAppLabel(menu.app.key, newLabel) },
    )
}

/**
 * The menu of a long press on empty space: edit home, widgets, settings, wallpaper, and the pages
 * (set as home, remove when empty). [scope] outlives the menu, so removing a page finishes after
 * the menu has closed.
 */
@Composable
internal fun HomeOptionsMenu(
    anchor: IntOffset,
    above: Boolean,
    settings: LauncherSettings,
    uiState: HomeUiState,
    dragController: HomeDragController,
    viewModel: HomeViewModel,
    homeEdit: HomeEditState,
    widgets: HomeWidgets,
    scope: CoroutineScope,
    pageRequests: MutableSharedFlow<Int>,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    IconMenuPopup(
        anchor = anchor,
        preferAbove = above,
        items = buildList {
            // Edit home: select several apps, remove them, file them, move them, add more.
            if (!settings.home.desktopLocked) {
                add(IconMenuItem(LauncherIcons.Edit, stringResource(R.string.home_options_edit)) { homeEdit.start() })
            }
            if (!settings.home.desktopLocked) {
                add(IconMenuItem(R.drawable.ic_widgets, stringResource(R.string.home_options_widgets)) {
                    widgets.stackTarget = null
                    widgets.showPicker = true
                })
            }
            add(IconMenuItem(R.drawable.ic_home_settings, stringResource(R.string.home_options_settings)) {
                onOpenSettings()
            })
            add(IconMenuItem(R.drawable.ic_wallpaper, stringResource(R.string.home_options_wallpaper)) {
                catching {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            })
            // Pages: the menu was opened on the page the pager is showing.
            val page = dragController.currentPage.coerceIn(0, uiState.pageCount - 1)
            if (page != uiState.homePage) {
                add(IconMenuItem(R.drawable.ic_home_page, stringResource(R.string.home_options_set_home)) {
                    viewModel.setHomePage(page)
                })
            }
            if (!settings.home.desktopLocked && uiState.pageCount > 1 && viewModel.isPageEmpty(page)) {
                add(IconMenuItem(LauncherIcons.Delete, stringResource(R.string.home_options_page_remove)) {
                    scope.launch {
                        when (val target = viewModel.removeEmptyPage(page)) {
                            null -> widgets.message(R.string.home_options_page_not_empty)
                            PAGE_FAILED -> widgets.message(R.string.home_options_page_failed)
                            else -> pageRequests.tryEmit(target)
                        }
                    }
                })
            }
        },
        onDismiss = onDismiss,
    )
}

/** The widget picker, fading and scaling in over home. */
@Composable
internal fun HomeWidgetPicker(
    widgets: HomeWidgets,
    widgetDrag: WidgetDragController,
    viewModel: HomeViewModel,
    settings: LauncherSettings,
    screenType: ScreenType,
) {
    AnimatedVisibility(
        visible = widgets.showPicker,
        enter = fadeIn(animationSpec = tween(220)) + scaleIn(animationSpec = tween(220), initialScale = 0.94f),
        exit = fadeOut(animationSpec = tween(180)) + scaleOut(animationSpec = tween(180), targetScale = 0.94f),
    ) {
        CompositionLocalProvider(LocalTonalWidgets provides settings.widgets.tonalBackground) {
            WidgetPickerScreen(
                dragController = widgetDrag,
                columns = settings.home.columns,
                rows = settings.home.rows,
                screenType = screenType,
                settings = settings,
                onPick = { choice, sx, sy ->
                    val targetStack = widgets.stackTarget
                    if (targetStack != null) {
                        // The same bind → configure → save pipeline as a normal add (system bind
                        // dialog, the widget's own setup screen, process death), ending in the stack.
                        when (choice) {
                            is WidgetChoice.App -> widgets.startBind(choice.provider, null, null, targetStack)
                            is WidgetChoice.Builtin -> viewModel.addBuiltinToStack(targetStack, choice.type)
                        }
                        widgets.stackTarget = null
                        widgets.showPicker = false
                    } else {
                        val target = widgetDrag.findSpace?.invoke(sx, sy)
                        if (target == null) widgets.message(R.string.widget_no_space) else widgets.addWidget(choice, target)
                    }
                },
                onDismiss = { widgetDrag.cancel(); widgets.showPicker = false; widgets.stackTarget = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
