package org.arkikeskus.launcher.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.DefaultLauncher
import org.arkikeskus.launcher.ui.component.LocalIconPack
import org.arkikeskus.launcher.ui.component.LocalIconShape
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.LocalThemedIcons
import org.arkikeskus.launcher.ui.expressive.DarkExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LightExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import org.arkikeskus.launcher.model.catching

/** Minimum window width (dp) for the two-pane settings layout — the large-screen breakpoint. */
private const val TWO_PANE_MIN_WIDTH_DP = 600

/** Which colour the colour-picker dialog is editing. */
internal enum class ColorTarget(@StringRes val titleRes: Int) {
    DRAWER_ACCENT(R.string.settings_drawer_accent_color),
    DRAWER_BACKGROUND(R.string.settings_drawer_custom_color),
    APP_LABEL(R.string.settings_label_color),
}

/** The groups the categories are listed under (the Start page stands above them all). */
internal enum class SettingsGroup(val titleRes: Int) {
    LOOK(R.string.settings_group_look),
    HOME(R.string.settings_group_home),
    APPS(R.string.settings_group_apps),
    DEVICE(R.string.settings_group_device),
}

internal enum class SettingsCategory(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    /** The group it is listed under; null for the Start page, listed first on its own. */
    val group: SettingsGroup?,
    /** False for categories whose settings are all shared by both displays. */
    val perDisplay: Boolean = true,
) {
    START(R.string.settings_start, R.string.settings_category_start_desc, Icons.Rounded.Home, null, perDisplay = false),
    // Look & feel
    THEME(R.string.settings_cat_theme, R.string.settings_cat_theme_desc, Icons.Rounded.Palette, SettingsGroup.LOOK),
    ICONS(R.string.settings_cat_icons, R.string.settings_cat_icons_desc, Icons.Rounded.Category, SettingsGroup.LOOK),
    MOTION(R.string.settings_cat_motion, R.string.settings_cat_motion_desc, Icons.Rounded.AutoAwesome, SettingsGroup.LOOK),
    // Home screen
    HOME_SCREEN(R.string.settings_cat_home, R.string.settings_cat_home_desc, Icons.Rounded.GridOn, SettingsGroup.HOME),
    DOCK_FOLDERS(R.string.settings_cat_dock_folders, R.string.settings_cat_dock_folders_desc, Icons.Rounded.Dock, SettingsGroup.HOME),
    GESTURES(R.string.settings_gestures, R.string.settings_category_gestures_desc, Icons.Rounded.Gesture, SettingsGroup.HOME),
    // Apps & multitasking
    APP_DRAWER(R.string.settings_app_drawer_customization, R.string.settings_category_app_drawer_desc, Icons.Rounded.Apps, SettingsGroup.APPS),
    TASKBAR(R.string.settings_taskbar_category, R.string.settings_cat_taskbar_desc, Icons.Rounded.CallToAction, SettingsGroup.APPS, perDisplay = false),
    RECENTS(R.string.settings_recents, R.string.settings_cat_recents_desc, Icons.Rounded.GridView, SettingsGroup.APPS, perDisplay = false),
    SPLIT(R.string.settings_cat_split, R.string.settings_cat_split_desc, Icons.Rounded.VerticalSplit, SettingsGroup.APPS, perDisplay = false),
    // Phone & system
    DISPLAY_PROFILES(R.string.settings_cat_displays, R.string.settings_category_display_profiles_desc, Icons.Rounded.Smartphone, SettingsGroup.DEVICE),
    SYSTEM(R.string.settings_cat_system, R.string.settings_cat_system_desc, Icons.Rounded.Tune, SettingsGroup.DEVICE, perDisplay = false),
    PERMISSIONS(R.string.settings_permissions, R.string.settings_category_permissions_desc, Icons.Rounded.Shield, SettingsGroup.DEVICE, perDisplay = false),
    ROOT(R.string.settings_root, R.string.settings_category_root_desc, Icons.Rounded.AdminPanelSettings, SettingsGroup.DEVICE, perDisplay = false),
    SUPPORT(R.string.settings_support, R.string.settings_support_desc, Icons.Rounded.HealthAndSafety, SettingsGroup.DEVICE, perDisplay = false),
    BACKUP(R.string.settings_cat_backup, R.string.settings_cat_backup_desc, Icons.Rounded.SettingsBackupRestore, SettingsGroup.DEVICE, perDisplay = false),
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    screenType: ScreenType = LocalScreenType.current,
) {
    // The display profile being edited. It follows the display the settings are open on (and snaps
    // back to it when the phone is folded/unfolded), but either profile can be picked by hand, so
    // the inner screen can be set up from the outer screen and vice versa. `screenType` stays the
    // PHYSICAL display: it alone decides the layout (two-pane vs. phone) and the settings UI scale.
    var editingProfile by rememberSaveable { mutableStateOf<ScreenType?>(null) }
    LaunchedEffect(screenType) { editingProfile = null }
    val profile = editingProfile ?: screenType
    LaunchedEffect(profile) { viewModel.setScreenType(profile) }
    // The setting picked in the search: its row scrolls into view and glows (SettingsSearch.kt).
    val searchFocus = remember { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalScreenType provides screenType, LocalSettingsFocus provides searchFocus) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val allApps by viewModel.apps.collectAsStateWithLifecycle()
    val hiddenKeys by viewModel.hiddenKeys.collectAsStateWithLifecycle()
    val iconPacks by viewModel.iconPacks.collectAsStateWithLifecycle()
    val appPairs by viewModel.appPairs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val previewIcon = rememberLauncherIconBitmap()

    // What is open over the pages, one at a time: a manager, a picker or a dialog (SettingsDialogs.kt).
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val profileSwitcher: @Composable () -> Unit = {
        DisplayProfileSwitcher(
            selected = profile,
            active = screenType,
            onSelect = { editingProfile = it },
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }

    var selectedTabletCategory by rememberSaveable { mutableStateOf(SettingsCategory.START) }
    var selectedOuterCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var tabletQuery by rememberSaveable { mutableStateOf("") }

    val mainScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    // Own scroll position for a category page: sharing the overview's state opened every category
    // scrolled to wherever the overview list happened to be.
    val subpageScrollState = remember(selectedOuterCategory) { ScrollState(0) }
    val detailScrollState = remember(selectedTabletCategory) { ScrollState(0) }
    var isDefaultLauncher by remember { mutableStateOf(DefaultLauncher.isDefault(context)) }
    val setDefaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { isDefaultLauncher = DefaultLauncher.isDefault(context) }

    var notifAccessGranted by remember { mutableStateOf(isNotificationAccessGranted(context)) }
    LifecycleResumeEffect(Unit) {
        notifAccessGranted = isNotificationAccessGranted(context)
        onPauseOrDispose { }
    }
    // One page with its pinned preview, the same in the two-pane and the one-column layout.
    val previewCollapsed = rememberSaveable { mutableStateOf(false) }
    val page: SettingsPageSlot = { category, scroll, pageModifier, compact ->
        PageWithPreview(category, scroll, pageModifier, compact, s, previewIcon, allApps, previewCollapsed, profileSwitcher) {
            SettingsCategoryPage(
                category = category,
                s = s,
                viewModel = viewModel,
                profile = profile,
                allApps = allApps,
                hiddenCount = hiddenKeys.size,
                iconPacks = iconPacks,
                previewIcon = previewIcon,
                isDefaultLauncher = isDefaultLauncher,
                notifAccessGranted = notifAccessGranted,
                onSetDefault = { catching { setDefaultLauncher.launch(DefaultLauncher.requestIntent(context)) } },
                onNotifAccess = { openNotificationAccess(context) },
                onOpen = { target ->
                    selectedTabletCategory = target
                    selectedOuterCategory = target
                },
                onDialog = { dialog = it },
            )
        }
    }

    val studio = s.appearance.settingsStyle == LauncherSettings.SETTINGS_STYLE_STUDIO
    val palette = when {
        studio -> StudioExpressivePalette
        isSystemInDarkTheme() -> DarkExpressivePalette
        else -> LightExpressivePalette
    }
    val previewApps = remember(allApps) { allApps.take(PREVIEW_APP_COUNT) }
    // Two panes need the room, whichever display this is: a flip phone's inner screen is the inner
    // display but only phone-wide, and a 280dp sidebar would leave its detail pane unusable.
    val twoPane = LocalConfiguration.current.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP

    val physicalSettings by remember(viewModel, screenType) {
        viewModel.settingsForScreen(screenType)
    }.collectAsStateWithLifecycle(initialValue = LauncherSettings())

    val systemDensity = LocalDensity.current
    val dpiDensity = remember(systemDensity, physicalSettings.appearance.dpiSettings) {
        Density(
            density = systemDensity.density * physicalSettings.appearance.dpiSettings,
            fontScale = systemDensity.fontScale * physicalSettings.appearance.dpiSettings,
        )
    }

    CompositionLocalProvider(
        LocalDensity provides dpiDensity,
        LocalExpressivePalette provides palette,
        LocalSettingsStudio provides studio,
        LocalPreviewApps provides previewApps,
        LocalIconPack provides s.icons.iconPack,
        LocalIconShape provides s.icons.shape,
        LocalThemedIcons provides s.icons.useThemedIcons,
    ) {
        // The two managers take the whole screen.
        when (dialog) {
            SettingsDialog.HiddenApps -> {
                HiddenAppsManager(
                    apps = allApps,
                    hiddenKeys = hiddenKeys,
                    onSetHidden = viewModel::setAppHidden,
                    onBack = { dialog = null },
                    modifier = modifier.fillMaxSize(),
                )
                return@CompositionLocalProvider
            }
            SettingsDialog.AppPairs -> {
                AppPairsManager(
                    apps = allApps,
                    appPairs = appPairs,
                    schedules = viewModel.pairSchedules.collectAsStateWithLifecycle().value,
                    onSetSchedule = viewModel::setPairSchedule,
                    onSaveAppPair = viewModel::saveAppPair,
                    onDeleteAppPair = viewModel::deleteAppPair,
                    onLaunchAppPair = { viewModel.launchAppPair(it) },
                    onAddAppPairToHome = { viewModel.addAppPairToHome(it) },
                    taskbarPairs = s.taskbar.pinnedPairs,
                    onToggleTaskbar = viewModel::toggleTaskbarPair,
                    onBack = { dialog = null },
                    modifier = modifier.fillMaxSize(),
                )
                return@CompositionLocalProvider
            }
            else -> Unit
        }

        Box(modifier = modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = palette.bg) {
                if (twoPane) {
                    SettingsTwoPane(
                        profileSwitcher = profileSwitcher,
                        query = tabletQuery,
                        onQueryChange = { tabletQuery = it },
                        selected = selectedTabletCategory,
                        onSelect = { selectedTabletCategory = it },
                        onSearchPick = { category, title ->
                            searchFocus.value = title
                            selectedTabletCategory = category
                            tabletQuery = ""
                        },
                        detailScroll = detailScrollState,
                        page = page,
                    )
                } else {
                    SettingsOnePane(
                        profileSwitcher = profileSwitcher,
                        selected = selectedOuterCategory,
                        onSelect = { selectedOuterCategory = it },
                        searchActive = isSearchActive,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onToggleSearch = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        onCloseSearch = {
                            isSearchActive = false
                            searchQuery = ""
                        },
                        onSearchPick = { category, title ->
                            searchFocus.value = title
                            selectedOuterCategory = category
                        },
                        overviewScroll = mainScrollState,
                        pageScroll = subpageScrollState,
                        page = page,
                    )
                }
            }
        }

        dialog?.let { open ->
            SettingsDialogHost(open, s, viewModel, profile, allApps, iconPacks, onDismiss = { dialog = null })
        }
    }
    }
}
