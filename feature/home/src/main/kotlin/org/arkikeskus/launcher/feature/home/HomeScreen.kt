package org.arkikeskus.launcher.feature.home

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.Density
import org.arkikeskus.launcher.model.WidgetPlacement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.FoldState
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.AppActionPopup
import org.arkikeskus.launcher.ui.AppActions
import org.arkikeskus.launcher.ui.AppShortcuts
import org.arkikeskus.launcher.ui.DefaultLauncher
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.IconMenuItem
import org.arkikeskus.launcher.ui.IconMenuPopup
import org.arkikeskus.launcher.ui.LauncherIcons
import org.arkikeskus.launcher.ui.PopupAction
import org.arkikeskus.launcher.ui.RenameDialog
import org.arkikeskus.launcher.ui.rememberHomeDragController
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.LocalIconSizeScale
import org.arkikeskus.launcher.ui.component.LocalAppLabelLines
import org.arkikeskus.launcher.ui.component.LocalAppLabelScale
import org.arkikeskus.launcher.ui.component.rememberParallaxOffset
import org.arkikeskus.launcher.ui.component.LocalBatterySaver
import org.arkikeskus.launcher.ui.component.LocalHomeCovered
import org.arkikeskus.launcher.ui.component.LocalFoldState
import org.arkikeskus.launcher.ui.component.LocalIconPack
import org.arkikeskus.launcher.data.MotoLinks
import org.arkikeskus.launcher.ui.component.LocalIconShape
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.LocalThemedIcons
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Home screen: a paged [Workspace] of app shortcuts + the dock. All home gestures (icon drag, swipe
 * up/down, long-press settings, page swipe) live inside [Workspace] so they don't fight each other.
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

    // Optionally hide the system status bar while the launcher is foreground (immersive home). No root
    // needed; the bar returns for any other app. Re-applied on every ON_RESUME so returning from another
    // app re-hides it. A swipe from the top still transiently reveals it (Android safety behaviour).
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(settings.hideSystemStatusBar, lifecycleOwner, view) {
        val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()?.window
        fun apply() {
            val controller = window?.insetsController ?: return
            if (settings.hideSystemStatusBar) {
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(android.view.WindowInsets.Type.statusBars())
            } else {
                controller.show(android.view.WindowInsets.Type.statusBars())
            }
        }
        apply()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) apply()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // READ_PHONE_STATE granted outside onboarding (Settings ▸ Luvat, system app info) has no callback
    // into SignalMonitor — re-check on every resume so the 4G/5G generation label appears when the
    // user comes back home. No-op when the grant state hasn't changed.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPhonePermission()
        onPauseOrDispose { }
    }

    // First-run onboarding: when we are NOT the default home, surface the system "set default launcher"
    // dialog (RoleManager) so a fresh install — e.g. downloaded from GitHub — can become the launcher like
    // Lawnchair/Launcher3, without adb or hunting through settings. Re-checked on resume.
    var isDefaultLauncher by remember { mutableStateOf(DefaultLauncher.isDefault(context)) }
    val setDefaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { isDefaultLauncher = DefaultLauncher.isDefault(context) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) isDefaultLauncher = DefaultLauncher.isDefault(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // The single Pixel-style long-press menu, anchored to the long-pressed icon.
    var menuTarget by remember { mutableStateOf<AppMenuTarget?>(null) }
    var renameTarget by remember { mutableStateOf<AppItem?>(null) }
    var openFolderId by remember { mutableStateOf<Long?>(null) }
    // Empty-area long-press options popup (anchor + whether it flips above the press point).
    var homeOptions by remember { mutableStateOf<Pair<IntOffset, Boolean>?>(null) }
    // Page the workspace should show after a page was added or removed from the empty-area menu.
    val pageRequests = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
    val defaultFolderName = stringResource(R.string.folder_default_name)

    val widgetHost = LocalAppWidgetHost.current
    val widgetConfigLauncher = LocalWidgetConfigLauncher.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    // The widget stack the picker is choosing a widget FOR ("Add to stack"); null = a normal add.
    var stackToAddTo by remember { mutableStateOf<Long?>(null) }
    val widgetDrag = remember(dragController) { WidgetDragController(dragController) }
    val widgetScope = rememberCoroutineScope()
    var liveBindId by remember { mutableStateOf<Int?>(null) }
    fun widgetMessage(message: Int) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    // Pending widget across the bind/configure result steps. [restoreRowId] non-null → this is a
    // RESTORE (re-bind an existing placeholder row) rather than adding a brand-new widget.
    // Saveable: while the system bind dialog or the widget's own config activity is in front, the
    // launcher is a killable background process — a plain remember lost the bind on process death
    // and the user's widget silently never appeared.
    var pendingWidget by rememberSaveable(stateSaver = PendingWidgetBindSaver) {
        mutableStateOf<PendingWidgetBind?>(null)
    }

    // Persistent, page-independent host views: created once per placed widget id and kept alive so the
    // AppWidgetHost listener is never lost when a pager page scrolls off-screen. A lost listener drops a
    // collection widget's deferred row update (notifyAppWidgetViewDataChanged) — the empty-WhatsApp bug.
    // Mirrors Launcher3's LauncherWidgetHolder keeping every host view registered for the app's lifetime.
    val widgetViews = remember { mutableStateMapOf<Int, android.appwidget.AppWidgetHostView>() }
    val widgetScrollableById = remember { mutableStateMapOf<Int, Boolean>() }
    // Widgets inside a stack need a host view too — without one the stack shows empty tiles.
    val placedWidgetIds = remember(uiState.entries) {
        uiState.entries.flatMap { entry ->
            when (entry) {
                is PlacedWidget -> listOf(entry.appWidgetId)
                is PlacedWidgetStack -> entry.items.filterIsInstance<PlacedWidget>().map { it.appWidgetId }
                else -> emptyList()
            }
        }.toSet()
    }
    LaunchedEffect(placedWidgetIds, widgetHost) {
        val host = widgetHost ?: return@LaunchedEffect
        placedWidgetIds.forEach { id ->
            if (widgetViews[id] == null) {
                val info = appWidgetManager.getAppWidgetInfo(id)
                if (info != null) {
                    runCatching {
                        // Mark the widget as living on the HOME screen up front — some providers
                        // (WhatsApp's chat list) won't populate until the host category is set.
                        appWidgetManager.updateAppWidgetOptions(
                            id,
                            android.os.Bundle().apply {
                                putInt(
                                    AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                                )
                            },
                        )
                        val v = host.createView(context, id, info)
                        v.setPadding(0, 0, 0, 0)
                        v.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                            widgetScrollableById[id] = v.containsScrollableCollection()
                        }
                        widgetScrollableById[id] = v.containsScrollableCollection()
                        widgetViews[id] = v
                    }
                }
            }
        }
        (widgetViews.keys - placedWidgetIds).forEach {
            widgetViews.remove(it)
            widgetScrollableById.remove(it)
        }
    }

    // One-shot at startup: free any AppWidgetHost ids with no home_items row — e.g. the old bound ids
    // left over after a full backup restore replaced the layout — so the host doesn't leak widget ids.
    // The host ids are snapshotted BEFORE the (suspending) DB query: an id allocated while the query
    // runs is absent from the snapshot and can never be swept; an in-flight bind's id is excluded too.
    LaunchedEffect(widgetHost) {
        val host = widgetHost ?: return@LaunchedEffect
        val hostIds = runCatching { host.appWidgetIds }.getOrNull() ?: return@LaunchedEffect
        val dbIds = viewModel.boundWidgetIds()
        hostIds.forEach { id ->
            if (id !in dbIds && id != pendingWidget?.appWidgetId) runCatching { host.deleteAppWidgetId(id) }
        }
        // The reverse direction: DB rows bound to ids this host never allocated — what a Google Auto
        // Backup / device-transfer restore leaves behind (the Room DB carries the OLD device's ids).
        // Unbind them into tap-to-set-up placeholders instead of invisible dead zones on the grid.
        val validIds = hostIds.toMutableSet()
        pendingWidget?.appWidgetId?.let { validIds.add(it) }
        viewModel.unbindStaleWidgets(validIds)
    }

    fun finishWidget(bind: PendingWidgetBind) {
        widgetScope.launch {
            // The allocated id and the layout row must reach a consistent state even if the Activity
            // is destroyed while Room is writing. Process death is recovered by the saved bind state.
            withContext(NonCancellable) {
                val saved = runCatching {
                    if (bind.restoreRowId != null) {
                        viewModel.bindRestoredWidget(bind.restoreRowId, bind.appWidgetId)
                    } else if (bind.stackId != null) {
                        viewModel.addBoundWidgetToStack(
                            bind.stackId, bind.appWidgetId, bind.provider.provider.flattenToString(),
                        )
                    } else {
                        val target = bind.placement ?: return@runCatching false
                        viewModel.addWidgetAt(bind.appWidgetId, bind.provider.provider.flattenToString(), null, target)
                    }
                }.getOrDefault(false)
                if (!saved) {
                    runCatching { widgetHost?.deleteAppWidgetId(bind.appWidgetId) }
                    widgetMessage(R.string.widget_add_failed)
                }
                if (pendingWidget?.appWidgetId == bind.appWidgetId) pendingWidget = null
            }
        }
    }

    val reconfigureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* the widget reconfigures itself via RemoteViews; no DB change needed */ }

    fun configureOrFinish(bind: PendingWidgetBind) {
        // Launch the configuration activity through the SYSTEM (Activity-routed), not a raw
        // ACTION_APPWIDGET_CONFIGURE Intent, so the framework records the widget as configured.
        // Otherwise some providers (WhatsApp's auth-gated chat widget) stay "configuration pending" and
        // never populate. If there's no config activity, finish immediately.
        val launcher = widgetConfigLauncher
        if (bind.provider.configure != null && launcher != null) {
            pendingWidget = bind
            launcher(bind.appWidgetId) { ok ->
                val p = pendingWidget
                if (p != null) {
                    if (ok) finishWidget(p)
                    else { widgetHost?.deleteAppWidgetId(p.appWidgetId); pendingWidget = null }
                }
            }
        } else {
            finishWidget(bind)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val p = pendingWidget
        if (p != null) {
            if (result.resultCode == android.app.Activity.RESULT_OK) configureOrFinish(p)
            else { widgetHost?.deleteAppWidgetId(p.appWidgetId); pendingWidget = null }
        }
    }

    // Allocate an id and bind [provider] (silently if allowed, else via the system bind dialog), then
    // configure. [restoreRowId] != null re-binds an existing placeholder row instead of adding a new one;
    // [stackId] != null adds the new widget to that widget stack instead of a grid placement.
    fun startBind(
        provider: AppWidgetProviderInfo,
        restoreRowId: Long?,
        placement: WidgetPlacement? = null,
        stackId: Long? = null,
    ) {
        // Guards a double tap. Only a bind started by THIS process blocks: [pendingWidget] is saveable,
        // so one whose result never arrives would otherwise refuse every later add, silently, forever.
        if (pendingWidget != null && pendingWidget?.appWidgetId == liveBindId) return
        val host = widgetHost ?: return
        val id = runCatching { host.allocateAppWidgetId() }.getOrElse {
            widgetMessage(R.string.widget_add_failed)
            return
        }
        val bind = PendingWidgetBind(id, provider, restoreRowId, placement, stackId)
        liveBindId = id
        pendingWidget = bind
        val bound = runCatching { appWidgetManager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null) }.getOrDefault(false)
        if (bound) {
            configureOrFinish(bind)
        } else {
            pendingWidget = bind
            val intent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
            }
            runCatching { bindLauncher.launch(intent) }
                .onFailure { host.deleteAppWidgetId(id); pendingWidget = null }
        }
    }

    fun addWidget(choice: WidgetChoice, placement: WidgetPlacement) {
        showWidgetPicker = false
        stackToAddTo = null
        when (choice) {
            is WidgetChoice.App -> startBind(choice.provider, restoreRowId = null, placement = placement)
            is WidgetChoice.Builtin -> widgetScope.launch {
                val ok = runCatching { viewModel.addWidgetAt(null, null, choice.type, placement) }.getOrDefault(false)
                if (!ok) widgetMessage(R.string.widget_add_failed)
            }
        }
    }

    // Re-bind a restored placeholder: resolve its provider info, then run the same bind/configure flow
    // targeting its existing row. If the provider's app is gone (uninstalled after restore), no-op.
    fun startRestoreWidget(pending: PendingWidget) {
        val provider = appWidgetManager.installedProviders.firstOrNull { it.provider == pending.provider } ?: return
        startBind(provider, restoreRowId = pending.rowId)
    }

    // A config result that arrived after process death: [pendingWidget] survived (saveable) but the
    // result callback died with the old process — LauncherActivity parks the outcome here instead.
    val orphanConfigResult = LocalOrphanWidgetConfigResult.current
    LaunchedEffect(orphanConfigResult) {
        orphanConfigResult?.collect { ok ->
            if (ok == null) return@collect
            orphanConfigResult.value = null
            val p = pendingWidget
            if (p != null) {
                if (ok) {
                    finishWidget(p)
                } else {
                    widgetHost?.deleteAppWidgetId(p.appWidgetId)
                    pendingWidget = null
                }
            }
        }
    }

    // Shared drag state spanning the workspace, dock and drawer (Launcher3-style drag layer/controller).
    // Created by LauncherShell and passed in so the app drawer can drag onto home; falls back to a
    // local one when HomeScreen is used standalone (previews/tests).
    LaunchedEffect(uiState.dockApps.size, settings.dockEnabled, settings.dockColumns) {
        dragController.dockItemCount = uiState.dockApps.size
        dragController.dockHasSpace = settings.dockEnabled && uiState.dockApps.size < settings.dockColumns
    }
    // The split drop target only exists while the setting is on (see HomeDragController.isOverSplit).
    LaunchedEffect(settings.splitLaunchEnabled) {
        dragController.splitEnabled = settings.splitLaunchEnabled
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
            showWidgetPicker = false
            stackToAddTo = null
            widgetDrag.cancel()
        }
    }

    // Notification badges (empty when disabled), and whether to render the count or a plain dot.
    // The counts are their own state and are only read where a badge is drawn (see BadgeCounts),
    // so a notification does not recompose the home screen from the top.
    val badgeState = viewModel.badges.collectAsStateWithLifecycle()
    val showNotificationDots = rememberUpdatedState(settings.showNotificationDots)
    val badges = remember(badgeState) { BadgeCounts(badgeState) { showNotificationDots.value } }
    val badgeShowCount = settings.notificationDotCount
    val badgeScale = settings.notificationDotScale

    fun executeAction(action: String) {
        when (action) {
            LauncherSettings.GESTURE_NOTIFICATIONS -> NotificationShade.expand(context)
            LauncherSettings.GESTURE_DRAWER -> onOpenDrawer()
            LauncherSettings.GESTURE_SEARCH -> onOpenDrawer()
            LauncherSettings.GESTURE_LOCK -> {
                if (!LockAccessibilityService.lock()) {
                    widgetMessage(R.string.double_tap_lock_needs_service)
                }
            }
            LauncherSettings.GESTURE_NONE -> {}
            // Motorola gestures (MotoLinks): a note, Secure Vault, Ready For, Game Center, ...
            else -> if (!MotoLinks.runGesture(context, action)) widgetMessage(R.string.gesture_moto_unavailable)
        }
    }

    // Half folded (and the setting on): where the hinge is decides the layout, see HalfFold.kt.
    val halfFoldHinge = if (settings.halfOpenedModeEnabled) rememberHalfFoldHinge() else null
    val tabletopHinge = halfFoldHinge?.takeIf { it.acrossScreen }
    val bookFold = halfFoldHinge != null && !halfFoldHinge.acrossScreen

    val systemDensity = LocalDensity.current
    val dpiDensity = remember(systemDensity, settings.dpiWorkspace) {
        Density(
            density = systemDensity.density * settings.dpiWorkspace,
            fontScale = systemDensity.fontScale * settings.dpiWorkspace,
        )
    }

    val widgetLooks by viewModel.widgetLooks.collectAsStateWithLifecycle()
    // All app icons on home (workspace, dock, folders) honour the themed-icons setting and the
    // user's app-label text-size multiplier.
    CompositionLocalProvider(
        LocalDensity provides dpiDensity,
        LocalTonalWidgets provides settings.widgetTonalBackground,
        LocalWidgetBackgroundAlpha provides settings.widgetBackgroundOpacity,
        LocalThemedIcons provides settings.useThemedIcons,
        LocalIconPack provides settings.iconPackPackage,
        LocalIconShape provides settings.iconShape,
        LocalMotoWeatherTap provides settings.motoWeatherTap,
        LocalAppLabelScale provides settings.appLabelTextScale,
        LocalAppLabelLines provides if (settings.twoLineHomeLabels) 2 else 1,
        LocalIconSizeScale provides settings.iconSizeScale,
        LocalWidgetLooks provides widgetLooks,
        LocalStackStyle provides StackStyle(
            cornerRadius = settings.stackCornerRadius,
            indicator = settings.stackIndicator,
            showPill = settings.stackShowPill,
            autoRotateSeconds = settings.stackAutoRotateSeconds,
        ),
        LocalFolderStyle provides FolderStyle(
            shape = settings.folderShape,
            theme = settings.folderTheme,
            preview = settings.folderPreview,
            openColumns = settings.folderOpenColumns,
            openSize = settings.folderOpenSize,
        ),
        // Force the workspace left-to-right even in RTL locales (ar/he/fa/ur). The grid renders with
        // RTL-aware Modifier.offset {} but the drag/drop, drop-ring and popup-anchor math is all
        // absolute left-origin, so under RTL they disagree and a drop lands in the mirrored cell.
        // Pinning LTR keeps render + touch consistent; the drawer/settings keep their natural RTL.
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        // The motion sensor only runs while the launcher is the app in front: home stays composed
        // behind every other app, and used to keep the sensor awake there. Its on/off lives in its
        // own small composable, so a resume, a pause or the drawer covering home recomposes only that.
        val parallaxOffset = remember { mutableStateOf<State<Offset>?>(null) }
        HomeParallax(enabledSetting = settings.parallaxWallpaper, out = parallaxOffset)
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Read here, not in composition: the sensor moves this layer and nothing else.
                val shift = parallaxOffset.value?.value ?: Offset.Zero
                // A plain layer: moving it reuses what every icon and widget already drew.
                translationX = shift.x
                translationY = shift.y
            }
            // Root-level, Initial-pass swipe detector: a flick up/down anywhere on home (over icons,
            // folders, shortcuts or the dock — not just empty space) drives the drawer/notifications.
            .pixelHomeSwipe(
                // All home gestures pause while the first-run intro covers the screen — this
                // detector runs in the Initial pass on the ROOT, so it would otherwise catch a
                // vertical swipe before the intro overlay could swallow it.
                swipeUpAction = if (showOnboarding) LauncherSettings.GESTURE_NONE else settings.swipeUpAction,
                swipeDownAction = if (showOnboarding) LauncherSettings.GESTURE_NONE else settings.swipeDownAction,
                dragController = dragController,
                onDrawerDrag = onDrawerDrag,
                onDrawerSettle = onDrawerSettle,
                onAction = ::executeAction,
                // Left-edge action: a right-drag on the leftmost page launches the configured app.
                leftEdgeEnabled = settings.leftSwipeAppKey.isNotBlank() && !showOnboarding,
                atLeftEdge = { dragController.currentPage == 0 },
                onLeftEdgeAction = viewModel::onLeftSwipe,
                // Right-edge action: a left-drag on the rightmost page launches the configured app.
                rightEdgeEnabled = settings.rightSwipeAppKey.isNotBlank() && !showOnboarding,
                // Book layout: the pager rests on the left page of the last pair.
                atRightEdge = {
                    dragController.currentPage + (if (dragController.isDualPage) 1 else 0) >= (uiState.pageCount - 1).coerceAtLeast(0)
                },
                onRightEdgeAction = viewModel::onRightSwipe,
                // Swipe up on an app icon (Settings ▸ Gestures): instead of opening the drawer.
                iconSwipeEnabled = settings.iconSwipeUpAction != LauncherSettings.ICON_SWIPE_NONE &&
                    !settings.desktopLocked && !showOnboarding,
                onIconSwipe = { app, at ->
                    when (settings.iconSwipeUpAction) {
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
            if (settings.showStatusBar) {
                val density = LocalDensity.current
                // The status-bar zone height: use the display-cutout band (≈ system status bar height,
                // and non-zero even when the system bar is hidden), with a sane fallback for no-cutout
                // devices. Lets the themed bar sit in the cutout band, beside a centred punch-hole.
                val statusZone = with(density) {
                    WindowInsets.displayCutout.getTop(this).toDp()
                }.coerceAtLeast(24.dp)
                // Height of the still-visible system status bar; reserved as internal top padding so the
                // themed bar's scrim can extend up behind it to the top edge (no gap on tall-status-bar
                // phones like the Pixel 10 Pro) while the glyphs sit below the system bar.
                val systemBarInset = with(density) {
                    WindowInsets.statusBars.getTop(this).toDp()
                }
                // Blank the themed bar whenever the system status bar sits (or is about to sit) on top of
                // it, so the two never overlap:
                //  - systemBarInset > 0: the system bar takes real layout space — a permanent show
                //    (freeform/desktop windowing, lock-task) or a frame of our own hide/show animation.
                //  - headsUpActive: a heads-up notification TRANSIENTLY reveals the system bar OVER our
                //    content; WindowManager deliberately withholds that reveal from WindowInsets to keep the
                //    layout stable (verified against AOSP), so it can't be seen via insets — we detect it
                //    from our own NotificationListener instead and blank the bar for the heads-up window.
                val hideSystemBar = settings.hideSystemStatusBar
                val systemBarShown = systemBarInset > 0.dp
                StatusBar(
                    // Only align to the camera cutout when we own the top zone (system bar hidden).
                    alignToCutout = settings.hideSystemStatusBar,
                    scrimAlpha = settings.statusBarScrimOpacity,
                    // When hidden we own the whole top zone (no inset, self-aligns to the cutout);
                    // otherwise reserve the system bar's height so the scrim fills it and glyphs clear it.
                    topInset = if (settings.hideSystemStatusBar) 0.dp else systemBarInset,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Blank with alpha, NOT by leaving composition: the bar is the Column sibling
                        // that pushes the workspace down, so removing it would jump the whole grid up
                        // for the heads-up window and back — and let the top row sit under a
                        // force-shown system bar. Only the pixels must vanish; the slot stays.
                        .graphicsLayer {
                            val suppressThemedBar = hideSystemBar && (systemBarShown || headsUpActive.value)
                            alpha = if (suppressThemedBar) 0f else 1f
                        }
                        // When the system bar is hidden, occupy its zone at the very top (replacing it).
                        // Otherwise the scrim + topInset (inside StatusBar) handle the system-bar band, so
                        // no external statusBarsPadding here — that padding is what left the scrim gap.
                        .then(
                            if (settings.hideSystemStatusBar) Modifier.heightIn(min = statusZone)
                            else Modifier,
                        ),
                )
            }
            // Book half-fold: one page per half, nothing on the crease.
            val isDualPage = screenType == ScreenType.INNER && (settings.innerDualPageWorkspace || bookFold)
            Workspace(
                pageCount = uiState.pageCount,
                columns = settings.homeColumns,
                rows = settings.homeRows,
                entries = uiState.entries,
                badges = badges,
                badgeShowCount = badgeShowCount,
                badgeScale = badgeScale,
                showLabels = settings.showHomeLabels,
                labelColor = Color(settings.appLabelColor),
                glassBlurRadius = settings.glassBlurRadius,
                glassDarkTint = settings.glassDarkTint,
                showPageIndicator = settings.showPageIndicator,
                locked = settings.desktopLocked,
                isDualPage = isDualPage,
                pageBounceEnabled = settings.pageBounceEnabled,
                pageScrollEffect = settings.pageScrollEffect,
                allowNewPage = settings.newPageOnDrag,
                animationSpeed = settings.animationSpeed,
                onWidgetLookChange = { rowId, look -> viewModel.setWidgetLook(rowId, look) },
                homeSignals = homeSignals,
                homePage = if (uiState.loaded) uiState.homePage else null,
                pageRequests = pageRequests,
                dragController = dragController,
                widgetDragController = widgetDrag,
                onAddWidget = ::addWidget,
                onAppClick = viewModel::launch,
                onAppMenu = { app, anchor, above -> menuTarget = AppMenuTarget(app, anchor, DragSource.Home, above) },
                onMove = viewModel::moveItem,
                onMoveFolder = viewModel::moveFolder,
                onMoveToDock = { app, index -> viewModel.moveToDock(app, index) },
                onRemoveFromHome = { viewModel.removeFromHome(it) },
                onOpenFolder = { openFolderId = it.id },
                onFolderTap = { folder ->
                    val first = folder.apps.firstOrNull()
                    if (settings.folderTapFirstApp && first != null) {
                        viewModel.launch(first)
                    } else {
                        openFolderId = folder.id
                    }
                },
                onLaunchShortcut = { viewModel.launchShortcut(it) },
                onRemoveShortcut = { viewModel.removeShortcut(it) },
                onCreateFolder = { target, dropped -> viewModel.createFolder(target, dropped, defaultFolderName) },
                onAddToFolder = { app, folderId -> viewModel.addToFolder(app, folderId) },
                onCreateWidgetStack = { targetRowId, droppedRowId -> viewModel.createWidgetStack(targetRowId, droppedRowId) },
                onAddToWidgetStack = { stackId, widgetRowId ->
                    if (widgetRowId < 0L) {
                        stackToAddTo = stackId
                        showWidgetPicker = true
                    } else {
                        viewModel.addToWidgetStack(stackId, widgetRowId)
                    }
                },
                onRemoveFromWidgetStack = { stackId, itemRowId ->
                    viewModel.removeFromWidgetStack(stackId, itemRowId) { freedId ->
                        runCatching { widgetHost?.deleteAppWidgetId(freedId) }
                    }
                },
                onEmptyAreaMenu = { anchor, above -> homeOptions = anchor to above },
                onEmptyAreaDoubleTap = {
                    val action = settings.doubleTapAction
                    if (action != LauncherSettings.GESTURE_NONE) {
                        executeAction(action)
                    }
                },
                onRemoveWidget = { rowId, appWidgetId ->
                    // A built-in widget has no host id to free (appWidgetId == null). A widget stack
                    // has none of its own either, but the widgets inside it do — the repository
                    // reports those once the rows are gone.
                    appWidgetId?.let { widgetHost?.deleteAppWidgetId(it) }
                    viewModel.removeWidget(rowId) { freed ->
                        freed.filter { it != appWidgetId }.forEach { id ->
                            runCatching { widgetHost?.deleteAppWidgetId(id) }
                        }
                    }
                },
                onRestorePendingWidget = { startRestoreWidget(it) },
                // A pending placeholder has no allocated host id yet, so just drop its row.
                onRemovePendingWidget = { viewModel.removeWidget(it) },
                onSetWidgetBounds = viewModel::setWidgetBounds,
                onReconfigureWidget = { id ->
                    val info = appWidgetManager.getAppWidgetInfo(id)
                    val configure = info?.configure
                    if (configure != null) {
                        runCatching {
                            reconfigureLauncher.launch(
                                android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                                    .setComponent(configure)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                            )
                        }
                    }
                },
                widgetViews = widgetViews,
                widgetScrollableById = widgetScrollableById,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Status-bar inset is consumed by the StatusBar above when it's shown; otherwise the
                    // workspace takes it itself.
                    .then(if (settings.showStatusBar) Modifier else Modifier.statusBarsPadding())
                    // No horizontal padding: the grid spans the full width so a "full width" widget
                    // (spanX = columns at cellX 0) reaches both screen edges with no side gap. Icons
                    // shift outward by a negligible ~8dp.
                    .padding(vertical = 12.dp),
            )

            // Show the dock whenever it's enabled — even with no favorites yet — so a fresh install
            // can be populated by dragging apps onto it (there is no "add to dock" menu item).
            val dockContent: @Composable ColumnScope.() -> Unit = { if (settings.dockEnabled) {
                // The narrower, alignable dock is for wide inner screens only; a flip phone's inner
                // display is phone-wide and keeps the full-width dock.
                val wideScreen = LocalConfiguration.current.screenWidthDp >= 600
                val dockAlignModifier = if (screenType == ScreenType.INNER && wideScreen) {
                    when (settings.innerDockAlignment) {
                        LauncherSettings.DOCK_ALIGNMENT_LEFT -> Modifier.fillMaxWidth(0.6f).align(Alignment.Start)
                        LauncherSettings.DOCK_ALIGNMENT_RIGHT -> Modifier.fillMaxWidth(0.6f).align(Alignment.End)
                        LauncherSettings.DOCK_ALIGNMENT_FLOATING -> Modifier.fillMaxWidth(0.75f).align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                        else -> Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally)
                    }
                } else {
                    Modifier.fillMaxWidth()
                }

                Dock(
                    apps = uiState.dockApps,
                    badges = badges,
                    badgeShowCount = badgeShowCount,
                    badgeScale = badgeScale,
                    showLabels = settings.showDockLabels,
                    labelColor = Color(settings.appLabelColor),
                    backgroundAlpha = settings.dockBackgroundOpacity,
                    locked = settings.desktopLocked,
                    dragController = dragController,
                    onAppClick = viewModel::launch,
                    onReorder = viewModel::reorderDock,
                    onMoveToHome = { app, page, cellX, cellY -> viewModel.moveToHome(app, page, cellX, cellY) },
                    onRemoveFromDock = { viewModel.removeFromDock(it) },
                    onAppMenu = { app, anchor -> menuTarget = AppMenuTarget(app, anchor, DragSource.Dock, anchor.y > windowHeightPx / 2) },
                    modifier = dockAlignModifier
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                )
            } }
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

        // Drop targets at the top while an icon is dragged, side by side:
        //  - "Remove" during any removable home drag (an app from home/dock, or a pinned shortcut via
        //    [localDragging]); dropping an icon on it removes it from home.
        //  - "Split" during any app drag (home, dock or drawer) when the setting is on; dropping an
        //    app on it opens the partner picker for a split screen.
        // Each lights up while the dragged icon is over it and publishes its bounds for the hit-test.
        val showRemoveTarget = (dragController.moving &&
            (dragController.source == DragSource.Home || dragController.source == DragSource.Dock)) ||
            dragController.localDragging
        val showSplitTarget = dragController.moving && settings.splitLaunchEnabled
        if (showRemoveTarget || showSplitTarget) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    // Clear the themed status bar (when shown) so the pills don't overlap it.
                    .padding(top = if (settings.showStatusBar) 44.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Derived, so a drag recomposes this row only when the finger enters or leaves a target
                // (reading the position here re-ran the whole home screen on every pointer move).
                val overRemove by remember(dragController) {
                    derivedStateOf { dragController.isOverRemove(dragController.rootPosition) }
                }
                val overSplit by remember(dragController) {
                    derivedStateOf { dragController.isOverSplit(dragController.rootPosition) }
                }
                if (showRemoveTarget) {
                    DragTargetPill(
                        icon = LauncherIcons.Close,
                        label = stringResource(R.string.drag_remove),
                        active = overRemove,
                        activeColor = Color(0xFFD32F2F),
                        modifier = Modifier.onGloballyPositioned { dragController.removeBounds = it.boundsInRoot() },
                    )
                }
                if (showSplitTarget) {
                    DragTargetPill(
                        icon = LauncherIcons.SwapHoriz,
                        label = stringResource(R.string.drag_split),
                        active = overSplit,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.onGloballyPositioned { dragController.splitBounds = it.boundsInRoot() },
                    )
                }
            }
        }
        // Not-default reminder card: shown until ARK-launcher is the default home — but never under
        // the full first-run intro, which has its own set-as-default step.
        if (!isDefaultLauncher && !showOnboarding) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.onboarding_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = {
                        runCatching { setDefaultLauncher.launch(DefaultLauncher.requestIntent(context)) }
                    }) {
                        Text(stringResource(R.string.onboarding_set_default))
                    }
                }
            }
        }

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

    // An app is waiting for its split-screen partner (dropped on the split target, or chosen from a
    // long-press menu on home, the dock or the drawer).
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

    // "Change icon" was chosen for an app (from a menu on home, the dock or the drawer).
    dragController.iconPickRequest?.let { app ->
        val iconPacks by viewModel.iconPacks.collectAsStateWithLifecycle()
        val customIcons by viewModel.customIcons.collectAsStateWithLifecycle()
        IconPickerDialog(
            app = app,
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

    val menu = menuTarget
    if (menu != null) {
        AppActionPopup(
            app = menu.app,
            anchor = menu.anchor,
            // Flip the popup above the icon when it's in the lower half (e.g. dock or a bottom-row
            // icon), so it never runs off the bottom of the screen. Decided where the anchor is built.
            preferAbove = menu.preferAbove,
            actions = listOfNotNull(
                PopupAction(stringResource(R.string.app_info), LauncherIcons.Info) { AppActions.openAppInfo(context, menu.app) },
                PopupAction(stringResource(R.string.rename), LauncherIcons.Edit) { renameTarget = menu.app },
                PopupAction(stringResource(R.string.change_icon), LauncherIcons.SwapVert) {
                    dragController.iconPickRequest = menu.app
                },
                if (settings.floatingLaunchEnabled) {
                    PopupAction(stringResource(R.string.open_floating), LauncherIcons.OpenInNew) {
                        viewModel.launchFloating(menu.app)
                    }
                } else {
                    null
                },
                if (settings.floatingLaunchEnabled) {
                    PopupAction(stringResource(R.string.add_floating_shortcut), LauncherIcons.Add) {
                        viewModel.addFloatingShortcut(menu.app)
                    }
                } else {
                    null
                },
                if (settings.splitLaunchEnabled) {
                    PopupAction(stringResource(R.string.open_split), LauncherIcons.SwapHoriz) {
                        dragController.splitRequest = menu.app
                    }
                } else {
                    null
                },
                PopupAction(
                    stringResource(if (menu.source == DragSource.Dock) R.string.dock_remove else R.string.home_remove),
                    LauncherIcons.Close,
                ) {
                    if (menu.source == DragSource.Dock) viewModel.removeFromDock(menu.app)
                    else viewModel.removeFromHome(menu.app)
                },
                PopupAction(stringResource(R.string.uninstall), LauncherIcons.Delete) { AppActions.uninstall(context, menu.app) },
            ),
            onDismiss = { menuTarget = null },
            onPinShortcut = { item -> viewModel.pinShortcut(item) },
            onRename = { newLabel -> viewModel.setCustomAppLabel(menu.app.key, newLabel) },
        )
    }

    homeOptions?.let { (anchor, above) ->
        IconMenuPopup(
            anchor = anchor,
            preferAbove = above,
            items = buildList {
                if (!settings.desktopLocked) {
                    add(IconMenuItem(R.drawable.ic_widgets, stringResource(R.string.home_options_widgets)) {
                        stackToAddTo = null
                        showWidgetPicker = true
                    })
                }
                add(IconMenuItem(R.drawable.ic_home_settings, stringResource(R.string.home_options_settings)) {
                    onOpenSettings()
                })
                add(IconMenuItem(R.drawable.ic_wallpaper, stringResource(R.string.home_options_wallpaper)) {
                    runCatching {
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
                if (!settings.desktopLocked && uiState.pageCount > 1 && viewModel.isPageEmpty(page)) {
                    add(IconMenuItem(LauncherIcons.Delete, stringResource(R.string.home_options_page_remove)) {
                        widgetScope.launch {
                            when (val target = viewModel.removeEmptyPage(page)) {
                                null -> widgetMessage(R.string.home_options_page_not_empty)
                                PAGE_FAILED -> widgetMessage(R.string.home_options_page_failed)
                                else -> pageRequests.tryEmit(target)
                            }
                        }
                    })
                }
            },
            onDismiss = { homeOptions = null },
        )
    }

    AnimatedVisibility(
        visible = showWidgetPicker,
        enter = fadeIn(animationSpec = tween(220)) + scaleIn(animationSpec = tween(220), initialScale = 0.94f),
        exit = fadeOut(animationSpec = tween(180)) + scaleOut(animationSpec = tween(180), targetScale = 0.94f),
    ) {
        CompositionLocalProvider(LocalTonalWidgets provides settings.widgetTonalBackground) {
            WidgetPickerScreen(
                dragController = widgetDrag,
                columns = settings.homeColumns,
                rows = settings.homeRows,
                screenType = screenType,
                settings = settings,
                onPick = { choice, sx, sy ->
                    val targetStack = stackToAddTo
                    if (targetStack != null) {
                        // The same bind → configure → save pipeline as a normal add (system bind
                        // dialog, the widget's own setup screen, process death), ending in the stack.
                        when (choice) {
                            is WidgetChoice.App -> startBind(choice.provider, restoreRowId = null, stackId = targetStack)
                            is WidgetChoice.Builtin -> viewModel.addBuiltinToStack(targetStack, choice.type)
                        }
                        stackToAddTo = null
                        showWidgetPicker = false
                    } else {
                        val target = widgetDrag.findSpace?.invoke(sx, sy)
                        if (target == null) widgetMessage(R.string.widget_no_space) else addWidget(choice, target)
                    }
                },
                onDismiss = { widgetDrag.cancel(); showWidgetPicker = false; stackToAddTo = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    renameTarget?.let { app ->
        RenameDialog(
            initialName = app.label,
            onConfirm = { viewModel.setCustomAppLabel(app.key, it) },
            onReset = { viewModel.setCustomAppLabel(app.key, "") },
            onDismiss = { renameTarget = null },
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
            glassBlurRadius = settings.glassBlurRadius,
            glassDarkTint = settings.glassDarkTint,
            openAnimation = settings.folderOpenAnimation,
            animationSpeed = settings.animationSpeed,
            onRename = { viewModel.renameFolder(openFolder.id, it) },
            onAppClick = { viewModel.launch(it) },
            onRemoveFromFolder = { viewModel.removeFromFolder(it, openFolder.id) },
            onDismiss = { openFolderId = null },
        )
    }
    }
    }
}

/**
 * Root-level home swipe-up/down detector — the Pixel-style fix for "the drawer swipe only catches on
 * empty space". Applied to HomeScreen's root Box and run in [PointerEventPass.Initial] (parent before
 * children), so it sees movement *before* the icons, folders, shortcuts, dock and the HorizontalPager.
 *
 * It does NOT consume the DOWN and only consumes once the gesture is clearly vertical past the touch
 * slop — so taps, long-presses and horizontal page swipes pass through untouched, while a flick up
 * from anywhere on the home surface reliably "catches" and drives the drawer (the root cause of the
 * old bug: the detector lived on the page background, so icons/dock won the gesture first).
 *
 * - Swipe up (with [swipeUpAction]) → finger-following drawer via [onDrawerDrag] / [onDrawerSettle].
 * - Swipe down (with [swipeDownAction]) → [onAction] (one-shot).
 * - A real long-press drag — an app/dock item ([HomeDragController.isDragging]) or a folder/shortcut
 *   ([HomeDragController.localGestureActive] / [HomeDragController.localDragging]) — is never stolen;
 *   the detector bails out so the drag owns the gesture.
 *
 * The velocity tracker is fed the UP position *before* `calculateVelocity()` so a fast flick reports
 * its true release speed (the old detector computed velocity without the final sample, which made fast
 * flicks under-settle and snap shut).
 */
@Composable
private fun Modifier.pixelHomeSwipe(
    swipeUpAction: String,
    swipeDownAction: String,
    dragController: HomeDragController,
    onDrawerDrag: (Float) -> Unit,
    onDrawerSettle: (Float) -> Unit,
    onAction: (String) -> Unit,
    leftEdgeEnabled: Boolean = false,
    atLeftEdge: () -> Boolean = { false },
    onLeftEdgeAction: () -> Unit = {},
    rightEdgeEnabled: Boolean = false,
    atRightEdge: () -> Boolean = { false },
    onRightEdgeAction: () -> Unit = {},
    iconSwipeEnabled: Boolean = false,
    onIconSwipe: (AppItem, Offset) -> Unit = { _, _ -> },
): Modifier {
    val latestSwipeUpAction by rememberUpdatedState(swipeUpAction)
    val latestSwipeDownAction by rememberUpdatedState(swipeDownAction)
    val latestOnDrawerDrag by rememberUpdatedState(onDrawerDrag)
    val latestOnDrawerSettle by rememberUpdatedState(onDrawerSettle)
    val latestOnAction by rememberUpdatedState(onAction)
    val latestOnLeftEdgeAction by rememberUpdatedState(onLeftEdgeAction)
    val latestOnRightEdgeAction by rememberUpdatedState(onRightEdgeAction)
    val latestAtLeftEdge by rememberUpdatedState(atLeftEdge)
    val latestAtRightEdge by rememberUpdatedState(atRightEdge)
    val latestIconSwipeEnabled by rememberUpdatedState(iconSwipeEnabled)
    val latestOnIconSwipe by rememberUpdatedState(onIconSwipe)

    return pointerInput(swipeUpAction, swipeDownAction, leftEdgeEnabled, rightEdgeEnabled, dragController) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // A touch that starts inside a scrollable collection widget (e.g. WhatsApp's chat list)
            // belongs to that widget's own list — bail without consuming so the scroll falls through to
            // the embedded AppWidgetHostView instead of the swipe-up stealing the vertical drag.
            if (dragController.isOverScrollableWidget(down.position)) return@awaitEachGesture
            // A touch that starts on the dock belongs to the dock's own gestures (tap / long-press for
            // the shortcut popup / drag to reorder or onto home). Bail so the dock owns it end-to-end —
            // otherwise this Initial-pass detector steals a small upward drift during a dock long-press,
            // cancelling the popup and half-opening the drawer (the "2/10 on Samsung" flakiness). Swipe
            // up to open the drawer still works from anywhere above the dock.
            if (dragController.isOverDock(down.position)) return@awaitEachGesture
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            // 0 = undecided, 1 = drawer up-drag, 2 = left-edge action, 3 = notification shade, 4 = right-edge action
            // 5 = swipe up that started on an app icon (see [iconSwipeEnabled]).
            var mode = 0
            var swipedApp: AppItem? = null
            var lastY = down.position.y

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                // Never steal a real long-press drag (an app/dock item, or a folder/shortcut).
                if (dragController.isDragging ||
                    dragController.localGestureActive ||
                    dragController.localDragging
                ) {
                    break
                }

                if (!change.pressed) {
                    if (mode == 1) {
                        val finalDy = change.position.y - lastY
                        if (finalDy != 0f) latestOnDrawerDrag(finalDy)
                        latestOnDrawerSettle(velocityTracker.calculateVelocity().y)
                        mode = 0
                    } else if (mode == 2) {
                        if (change.position.x - down.position.x > size.width * 0.25f) latestOnLeftEdgeAction()
                    } else if (mode == 4) {
                        if (down.position.x - change.position.x > size.width * 0.25f) latestOnRightEdgeAction()
                    } else if (mode == 5) {
                        val app = swipedApp
                        // A deliberate flick, not a twitch: the finger has to travel a little way up.
                        if (app != null && down.position.y - change.position.y > 40.dp.toPx()) {
                            latestOnIconSwipe(app, down.position)
                        }
                    }
                    break
                }

                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y

                if (mode == 0) {
                    when {
                        abs(dy) > touchSlop && abs(dy) > abs(dx) -> when {
                            dy < 0f -> {
                                val action = latestSwipeUpAction
                                val iconApp = if (latestIconSwipeEnabled) dragController.appAt?.invoke(down.position) else null
                                if (iconApp != null) {
                                    // The swipe started on an app icon that has its own swipe action.
                                    swipedApp = iconApp
                                    mode = 5
                                    change.consume()
                                } else if ((action == LauncherSettings.GESTURE_DRAWER || action == LauncherSettings.GESTURE_SEARCH) &&
                                    down.position.y > size.height * 0.15f
                                ) {
                                    mode = 1
                                    change.consume()
                                    latestOnDrawerDrag(dy) // jump to the finger, including the slop moved
                                    lastY = change.position.y
                                } else {
                                    break
                                }
                            }
                            dy > 0f -> {
                                val action = latestSwipeDownAction
                                if (action != LauncherSettings.GESTURE_NONE) {
                                    mode = 3
                                    change.consume()
                                    latestOnAction(action)
                                } else {
                                    break
                                }
                            }
                            else -> {
                                break
                            }
                        }
                        // Horizontal gesture.
                        abs(dx) > touchSlop && abs(dx) >= abs(dy) -> {
                            if (leftEdgeEnabled && dx > 0f && latestAtLeftEdge()) {
                                mode = 2
                                change.consume()
                            } else if (rightEdgeEnabled && dx < 0f && latestAtRightEdge()) {
                                mode = 4
                                change.consume()
                            } else {
                                break
                            }
                        }
                    }
                } else if (mode == 1) {
                    change.consume()
                    latestOnDrawerDrag(change.position.y - lastY)
                    lastY = change.position.y
                } else {
                    // mode >= 2: keep consuming so pager never grabs the rest of the gesture.
                    change.consume()
                }
            }
            if (mode == 1) {
                latestOnDrawerSettle(velocityTracker.calculateVelocity().y)
            }
        }
    }
}

/** In-flight widget bind/configure state. [restoreRowId] non-null = re-binding a restored placeholder
 *  row (from a backup) rather than adding a new widget. */
private data class PendingWidgetBind(
    val appWidgetId: Int,
    val provider: AppWidgetProviderInfo,
    val restoreRowId: Long?,
    val placement: WidgetPlacement? = null,
    /** Non-null = the new widget goes into this widget stack rather than onto a grid [placement]. */
    val stackId: Long? = null,
)

/** Bundles [PendingWidgetBind] into saved instance state so an in-flight bind survives process death
 *  while the system bind dialog or the widget's configuration activity is in the foreground. */
private val PendingWidgetBindSaver = Saver<PendingWidgetBind?, android.os.Bundle>(
    save = { p ->
        p?.let {
            android.os.Bundle().apply {
                putInt("id", it.appWidgetId)
                putParcelable("provider", it.provider)
                putLong("row", it.restoreRowId ?: -1L)
                putLong("stack", it.stackId ?: -1L)
                it.placement?.let { p ->
                    putIntArray("placement", intArrayOf(p.page, p.cellX, p.cellY, p.spanX, p.spanY))
                }
            }
        }
    },
    restore = { b ->
        @Suppress("DEPRECATION")
        val provider = b.getParcelable<AppWidgetProviderInfo>("provider")
        provider?.let {
            PendingWidgetBind(b.getInt("id"), it, b.getLong("row").takeIf { row -> row >= 0 },
                b.getIntArray("placement")?.takeIf { p -> p.size == 5 }?.let { p ->
                    WidgetPlacement(p[0], p[1], p[2], p[3], p[4])
                },
                b.getLong("stack", -1L).takeIf { stack -> stack >= 0 })
        }
    },
)

/** The long-pressed app and where its menu should anchor (which surface it came from). */
private data class AppMenuTarget(
    val app: AppItem,
    val anchor: IntOffset,
    val source: DragSource,
    val preferAbove: Boolean,
)

/** The long-press menu is the shared [org.arkikeskus.launcher.ui.AppActionPopup] (core/ui). */

/**
 * Runs the parallax sensor while home is in front, not covered and not in battery saver, and hands
 * its offset (one [State] for the screen's lifetime) to [out] for the home layer to read.
 */
@Composable
private fun HomeParallax(enabledSetting: Boolean, out: MutableState<State<Offset>?>) {
    var homeResumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        homeResumed = true
        onPauseOrDispose { homeResumed = false }
    }
    val active = enabledSetting && homeResumed && !LocalBatterySaver.current && !LocalHomeCovered.current
    val offset = rememberParallaxOffset(enabled = active)
    SideEffect { if (out.value !== offset) out.value = offset }
}
