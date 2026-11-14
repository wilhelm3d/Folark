package org.arkikeskus.launcher.feature.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.arkikeskus.launcher.data.FolarkRecents
import org.arkikeskus.launcher.data.MotoLinks
import org.arkikeskus.launcher.feature.home.taskbar.LockAccessibilityService
import org.arkikeskus.launcher.feature.home.widgets.BadgeCounts
import org.arkikeskus.launcher.feature.home.widgets.LocalMotoWeatherTap
import org.arkikeskus.launcher.feature.home.widgets.LocalStackStyle
import org.arkikeskus.launcher.feature.home.widgets.LocalTonalWidgets
import org.arkikeskus.launcher.feature.home.widgets.LocalWidgetBackgroundAlpha
import org.arkikeskus.launcher.feature.home.widgets.NotificationShade
import org.arkikeskus.launcher.feature.home.widgets.StackStyle
import org.arkikeskus.launcher.feature.home.workspace.Dock
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.DockAlignment
import org.arkikeskus.launcher.model.HomeGesture
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.catching
import org.arkikeskus.launcher.ui.DefaultLauncher
import org.arkikeskus.launcher.ui.DragSource
import org.arkikeskus.launcher.ui.HomeDragController
import org.arkikeskus.launcher.ui.component.LocalAppLabelLines
import org.arkikeskus.launcher.ui.component.LocalAppLabelScale
import org.arkikeskus.launcher.ui.component.LocalIconPack
import org.arkikeskus.launcher.ui.component.LocalIconShape
import org.arkikeskus.launcher.ui.component.LocalIconSizeScale
import org.arkikeskus.launcher.ui.component.LocalTaskbarInset
import org.arkikeskus.launcher.ui.component.LocalThemedIcons

// The frame around home's pages (split out of HomeScreen.kt): the system status bar, the themed
// status bar, the dock, the look settings every icon reads, the "make Folark your launcher" card and
// the swipe gestures' actions.

/**
 * Optionally hides the system status bar while the launcher is foreground (immersive home). No root
 * needed; the bar returns for any other app. Re-applied on every ON_RESUME so returning from another
 * app re-hides it. A swipe from the top still transiently reveals it (Android safety behaviour).
 */
@Composable
internal fun HideSystemStatusBar(hide: Boolean) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(hide, lifecycleOwner, view) {
        val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()?.window
        fun apply() {
            val controller = window?.insetsController ?: return
            if (hide) {
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
}

/**
 * Provides what every icon and widget on home reads: the workspace density (Settings ▸ Displays),
 * the themed-icons setting, the user's app-label text size, the stack and folder styles, and the
 * saved widget looks. The layout is forced left-to-right.
 */
@Composable
internal fun ProvideHomeLocals(
    settings: LauncherSettings,
    widgetLooks: Map<Long, WidgetLook>,
    content: @Composable () -> Unit,
) {
    val systemDensity = LocalDensity.current
    val dpiDensity = remember(systemDensity, settings.appearance.dpiWorkspace) {
        Density(
            density = systemDensity.density * settings.appearance.dpiWorkspace,
            fontScale = systemDensity.fontScale * settings.appearance.dpiWorkspace,
        )
    }
    // All app icons on home (workspace, dock, folders) honour the themed-icons setting and the
    // user's app-label text-size multiplier.
    CompositionLocalProvider(
        LocalDensity provides dpiDensity,
        LocalTonalWidgets provides settings.widgets.tonalBackground,
        LocalWidgetBackgroundAlpha provides settings.widgets.backgroundOpacity,
        LocalThemedIcons provides settings.icons.useThemedIcons,
        LocalIconPack provides settings.icons.iconPack,
        LocalIconShape provides settings.icons.shape,
        LocalMotoWeatherTap provides settings.widgets.motoWeatherTap,
        LocalAppLabelScale provides settings.icons.labelTextScale,
        LocalAppLabelLines provides if (settings.home.twoLineLabels) 2 else 1,
        LocalIconSizeScale provides settings.icons.sizeScale,
        LocalWidgetLooks provides widgetLooks,
        LocalStackStyle provides StackStyle(
            cornerRadius = settings.widgets.stackCornerRadius,
            indicator = settings.widgets.stackIndicator,
            showPill = settings.widgets.stackShowPill,
            autoRotateSeconds = settings.widgets.stackAutoRotateSeconds,
        ),
        LocalFolderStyle provides FolderStyle(
            shape = settings.folders.shape,
            theme = settings.folders.theme,
            preview = settings.folders.preview,
            openColumns = settings.folders.openColumns,
            openSize = settings.folders.openSize,
            directApps = settings.folders.directApps,
        ),
        // Force the workspace left-to-right even in RTL locales (ar/he/fa/ur). The grid renders with
        // RTL-aware Modifier.offset {} but the drag/drop, drop-ring and popup-anchor math is all
        // absolute left-origin, so under RTL they disagree and a drop lands in the mirrored cell.
        // Pinning LTR keeps render + touch consistent; the drawer/settings keep their natural RTL.
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        content()
    }
}

/**
 * Moves home's whole layer with the parallax sensor, and leans it with the hinge when half folded.
 * Both are read here, in the graphics layer, not in composition: the sensor moves this layer and
 * nothing else, so moving it reuses what every icon and widget already drew.
 */
internal fun Modifier.parallaxAndHingeLayer(
    parallaxOffset: State<State<Offset>?>,
    hingeFold: State<Float>,
    tabletop: Boolean,
): Modifier = graphicsLayer {
    val shift = parallaxOffset.value?.value ?: Offset.Zero
    translationX = shift.x
    translationY = shift.y
    val fold = hingeFold.value
    if (fold > 0f) {
        cameraDistance = 20f * density
        if (tabletop) {
            // Tabletop: the upper half stands up, and home leans back with it.
            transformOrigin = TransformOrigin(0.5f, 1f)
            rotationX = fold * HINGE_TILT_DEG
        } else {
            // Book: home turns a little towards the crease.
            rotationY = -fold * HINGE_TILT_DEG * 0.6f
            translationX += fold * HINGE_SHIFT * size.width
        }
    }
}

/**
 * Runs the action of a home gesture ([action], the Motorola ones included): the notification shade, the drawer, lock, Recents, or a Motorola feature.
 */
internal fun runHomeGesture(
    context: Context,
    action: HomeGesture,
    recentsEnabled: Boolean,
    onOpenDrawer: () -> Unit,
    message: (Int) -> Unit,
) {
    when (action) {
        HomeGesture.NOTIFICATIONS -> NotificationShade.expand(context)
        HomeGesture.DRAWER -> onOpenDrawer()
        HomeGesture.SEARCH -> onOpenDrawer()
        HomeGesture.LOCK -> {
            if (!LockAccessibilityService.lock()) {
                message(R.string.double_tap_lock_needs_service)
            }
        }
        HomeGesture.RECENTS -> {
            if (!recentsEnabled || !FolarkRecents.open(context)) LockAccessibilityService.openRecents()
        }
        HomeGesture.NONE -> {}
        // Motorola gestures (MotoLinks): a note, Secure Vault, Ready For, Game Center, ...
        else -> if (!MotoLinks.runGesture(context, action)) message(R.string.gesture_moto_unavailable)
    }
}

/**
 * Unfolded just now (outer screen → inner): the pages open out from the hinge. The system's
 * animation scale applies (none when animations are off). Returns how far the animation is, 0 → 1
 * (1 = at rest).
 */
@Composable
internal fun rememberUnfoldProgress(screenType: ScreenType, animate: Boolean): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val unfold = remember { Animatable(1f) }
    var lastScreen by remember { mutableStateOf(screenType) }
    LaunchedEffect(screenType) {
        val unfolded = lastScreen == ScreenType.OUTER && screenType == ScreenType.INNER
        lastScreen = screenType
        if (unfolded && animate) {
            unfold.snapTo(0f)
            unfold.animateTo(1f, tween(UNFOLD_MS, easing = FastOutSlowInEasing))
        } else {
            unfold.snapTo(1f)
        }
    }
    return unfold
}

/** How long the home pages take to open out after unfolding. */
private const val UNFOLD_MS = 520

/**
 * Folark's themed status bar above the pages (when it is on in Settings ▸ Status bar). It sits in
 * the display-cutout band beside a punch-hole camera; with the system bar hidden it owns the whole
 * top zone, otherwise it reserves the system bar's height so its scrim reaches the top edge.
 * [headsUpActive] is read only where the bar fades (in its layer), so a heads-up doesn't recompose home.
 */
@Composable
internal fun ColumnScope.HomeStatusBar(settings: LauncherSettings, headsUpActive: State<Boolean>) {
    if (!settings.statusBar.enabled) return
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
    val hideSystemBar = settings.statusBar.hideSystem
    val systemBarShown = systemBarInset > 0.dp
    StatusBar(
        // Only align to the camera cutout when we own the top zone (system bar hidden).
        alignToCutout = settings.statusBar.hideSystem,
        scrimAlpha = settings.statusBar.scrimOpacity,
        // When hidden we own the whole top zone (no inset, self-aligns to the cutout);
        // otherwise reserve the system bar's height so the scrim fills it and glyphs clear it.
        topInset = if (settings.statusBar.hideSystem) 0.dp else systemBarInset,
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
                if (settings.statusBar.hideSystem) Modifier.heightIn(min = statusZone)
                else Modifier,
            ),
    )
}

/**
 * The dock, shown whenever it's enabled — even with no favorites yet — so a fresh install can be
 * populated by dragging apps onto it (there is no "add to dock" menu item).
 */
@Composable
internal fun ColumnScope.HomeDock(
    apps: List<AppItem>,
    settings: LauncherSettings,
    screenType: ScreenType,
    badges: BadgeCounts,
    badgeShowCount: Boolean,
    badgeScale: Float,
    dragController: HomeDragController,
    windowHeightPx: Int,
    onAppClick: (AppItem) -> Unit,
    onReorder: (List<AppItem>) -> Unit,
    onMoveToHome: (app: AppItem, page: Int, cellX: Int, cellY: Int) -> Unit,
    onRemoveFromDock: (AppItem) -> Unit,
    onAppMenu: (AppItem, IntOffset) -> Unit,
) {
    if (!settings.dock.enabled) return
    // The narrower, alignable dock is for wide inner screens only; a flip phone's inner
    // display is phone-wide and keeps the full-width dock.
    val wideScreen = LocalConfiguration.current.screenWidthDp >= 600
    val dockAlignModifier = if (screenType == ScreenType.INNER && wideScreen) {
        when (settings.dock.innerAlignment) {
            DockAlignment.LEFT -> Modifier.fillMaxWidth(0.6f).align(Alignment.Start)
            DockAlignment.RIGHT -> Modifier.fillMaxWidth(0.6f).align(Alignment.End)
            DockAlignment.FLOATING -> Modifier.fillMaxWidth(0.75f).align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
            else -> Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally)
        }
    } else {
        Modifier.fillMaxWidth()
    }

    Dock(
        apps = apps,
        badges = badges,
        badgeShowCount = badgeShowCount,
        badgeScale = badgeScale,
        showLabels = settings.dock.showLabels,
        labelColor = Color(settings.icons.labelColor),
        backgroundAlpha = settings.dock.backgroundOpacity,
        locked = settings.home.desktopLocked,
        dragController = dragController,
        onAppClick = onAppClick,
        onReorder = onReorder,
        onMoveToHome = onMoveToHome,
        onRemoveFromDock = onRemoveFromDock,
        onAppMenu = onAppMenu,
        modifier = dockAlignModifier
            .navigationBarsPadding()
            // Above Folark's taskbar when it stays on home.
            .padding(bottom = LocalTaskbarInset.current)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    )
}

/** Edit home: the bar at the bottom and its two dialogs (move to a page, add apps from a list). */
@Composable
internal fun BoxScope.HomeEditLayer(
    homeEdit: HomeEditState,
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    defaultFolderName: String,
) {
    var movingPage by remember { mutableStateOf(false) }
    var addingApps by remember { mutableStateOf(false) }
    val placedApps = uiState.entries.filterIsInstance<PlacedApp>()
    fun selectedApps() = homeEdit.selected.mapNotNull { key -> placedApps.firstOrNull { it.app.key == key }?.app }
    EditHomeBar(
        edit = homeEdit,
        onRemove = {
            viewModel.removeAll(selectedApps())
            homeEdit.selected.clear()
        },
        onFolder = {
            viewModel.folderOf(selectedApps(), defaultFolderName)
            homeEdit.selected.clear()
        },
        onMove = { movingPage = true },
        onAdd = { addingApps = true },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    if (movingPage) {
        MoveToPageDialog(
            pageCount = uiState.pageCount,
            onPick = { page ->
                viewModel.moveAllToPage(selectedApps(), page)
                homeEdit.selected.clear()
                movingPage = false
            },
            onDismiss = { movingPage = false },
        )
    }
    if (addingApps) {
        val allApps by viewModel.allApps.collectAsStateWithLifecycle()
        val onHome = placedApps.map { it.app.key }.toSet()
        AddAppsDialog(
            apps = allApps.filter { it.key !in onHome }.sortedBy { it.label.lowercase() },
            onAdd = { picked ->
                viewModel.addAll(picked)
                addingApps = false
            },
            onDismiss = { addingApps = false },
        )
    }
}

/**
 * Not-default reminder card: shown until Folark is the default home — but never under the full
 * first-run intro ([showOnboarding]), which has its own set-as-default step. When we are not the
 * default home it surfaces the system "set default launcher" dialog (RoleManager), so a fresh
 * install — e.g. downloaded from GitHub — can become the launcher like Lawnchair/Launcher3, without
 * adb or hunting through settings. Re-checked on resume.
 */
@Composable
internal fun BoxScope.DefaultLauncherReminder(showOnboarding: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    if (isDefaultLauncher || showOnboarding) return
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
                catching { setDefaultLauncher.launch(DefaultLauncher.requestIntent(context)) }
            }) {
                Text(stringResource(R.string.onboarding_set_default))
            }
        }
    }
}
