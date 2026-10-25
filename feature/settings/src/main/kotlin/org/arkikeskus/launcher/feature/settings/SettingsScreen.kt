package org.arkikeskus.launcher.feature.settings

import androidx.compose.material3.OutlinedTextFieldDefaults
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.DarkExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LightExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

/** Minimum window width (dp) for the two-pane settings layout — the large-screen breakpoint. */
private const val TWO_PANE_MIN_WIDTH_DP = 600

/** Which colour the colour-picker dialog is editing. */
internal enum class ColorTarget(@StringRes val titleRes: Int) {
    DRAWER_ACCENT(R.string.settings_drawer_accent_color),
    DRAWER_BACKGROUND(R.string.settings_drawer_custom_color),
    APP_LABEL(R.string.settings_label_color),
}

internal enum class SettingsCategory(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    /** False for categories whose settings are all shared by both displays. */
    val perDisplay: Boolean = true,
) {
    APPEARANCE(
        R.string.settings_appearance,
        R.string.settings_category_appearance_desc,
        Icons.Rounded.Palette,
    ),
    GRID_DPI(
        R.string.settings_grid_dpi,
        R.string.settings_category_grid_dpi_desc,
        Icons.Rounded.GridOn,
    ),
    APP_DRAWER(
        R.string.settings_app_drawer_customization,
        R.string.settings_category_app_drawer_desc,
        Icons.Rounded.Apps,
    ),
    DOCK_TASKBAR(
        R.string.settings_dock_taskbar,
        R.string.settings_category_dock_taskbar_desc,
        Icons.Rounded.Dock,
    ),
    TASKBAR(
        R.string.settings_taskbar_category,
        R.string.settings_category_taskbar_desc,
        Icons.Rounded.CallToAction,
        perDisplay = false,
    ),
    GESTURES(
        R.string.settings_gestures,
        R.string.settings_category_gestures_desc,
        Icons.Rounded.Gesture,
    ),
    NOTIFICATIONS(
        R.string.settings_notifications,
        R.string.settings_category_notifications_desc,
        Icons.Rounded.Notifications,
    ),
    RECENTS(
        R.string.settings_recents,
        R.string.settings_category_recents_desc,
        Icons.Rounded.GridView,
        perDisplay = false,
    ),
    ROOT(
        R.string.settings_root,
        R.string.settings_category_root_desc,
        Icons.Rounded.AdminPanelSettings,
        perDisplay = false,
    ),
    DISPLAY_PROFILES(
        R.string.settings_display_screen_profiles,
        R.string.settings_category_display_profiles_desc,
        Icons.Rounded.Smartphone,
    ),
    PERMISSIONS(
        R.string.settings_permissions,
        R.string.settings_category_permissions_desc,
        Icons.Rounded.Shield,
        perDisplay = false,
    ),
    BACKUP(
        R.string.settings_backup,
        R.string.settings_category_backup_desc,
        Icons.Rounded.SettingsBackupRestore,
        perDisplay = false,
    ),
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
    val context = LocalContext.current
    val previewIcon = rememberLauncherIconBitmap()

    var showHiddenManager by remember { mutableStateOf(false) }
    var showLeftSwipePicker by remember { mutableStateOf(false) }
    var showRightSwipePicker by remember { mutableStateOf(false) }
    var showIconPackPicker by remember { mutableStateOf(false) }
    var showCountStylePicker by remember { mutableStateOf(false) }
    var showPeoplePrivacyPicker by remember { mutableStateOf(false) }
    var showDrawerLayoutPicker by remember { mutableStateOf(false) }
    var showBatchTimesEditor by remember { mutableStateOf(false) }
    var showSwipeDownPicker by remember { mutableStateOf(false) }
    var showSwipeUpPicker by remember { mutableStateOf(false) }
    val appPairs by viewModel.appPairs.collectAsStateWithLifecycle()
    var showAppPairsManager by remember { mutableStateOf(false) }
    var showFoldActionPicker by remember { mutableStateOf(false) }
    var showUnfoldActionPicker by remember { mutableStateOf(false) }
    var showFoldTransitionPicker by remember { mutableStateOf(false) }
    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var showCopyProfile by remember { mutableStateOf(false) }
    val profileSwitcher: @Composable () -> Unit = {
        DisplayProfileSwitcher(
            selected = profile,
            active = screenType,
            onSelect = { editingProfile = it },
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }

    var selectedTabletCategory by rememberSaveable { mutableStateOf(SettingsCategory.APPEARANCE) }
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
        if (showHiddenManager) {
            HiddenAppsManager(
                apps = allApps,
                hiddenKeys = hiddenKeys,
                onSetHidden = viewModel::setAppHidden,
                onBack = { showHiddenManager = false },
                modifier = modifier.fillMaxSize(),
            )
            return@CompositionLocalProvider
        }

        if (showAppPairsManager) {
            AppPairsManager(
                apps = allApps,
                appPairs = appPairs,
                onSaveAppPair = viewModel::saveAppPair,
                onDeleteAppPair = viewModel::deleteAppPair,
                onLaunchAppPair = { viewModel.launchAppPair(it) },
                onAddAppPairToHome = { viewModel.addAppPairToHome(it) },
                taskbarPairs = s.taskbar.pinnedPairs,
                onToggleTaskbar = viewModel::toggleTaskbarPair,
                onBack = { showAppPairsManager = false },
                modifier = modifier.fillMaxSize(),
            )
            return@CompositionLocalProvider
        }

        Box(modifier = modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = palette.bg) {
                if (twoPane) {
                    // Master-detail two-pane layout on a wide window (an unfolded book foldable)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Left Master Sidebar Pane
                        Column(
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_title),
                                color = palette.text,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.4).sp,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp, end = 4.dp),
                            )

                            profileSwitcher()

                            SearchFieldHeader(query = tabletQuery, onQueryChange = { tabletQuery = it }, onClose = { tabletQuery = "" })
                            if (tabletQuery.isNotBlank()) {
                                val any = SettingsSearchResults(tabletQuery) { category, title ->
                                    searchFocus.value = title
                                    selectedTabletCategory = category
                                    tabletQuery = ""
                                }
                                if (!any) {
                                    Text(
                                        text = stringResource(R.string.settings_no_results),
                                        color = palette.faint,
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            } else SettingsCategory.entries.forEach { category ->
                                val isSelected = selectedTabletCategory == category
                                val categoryTitle = stringResource(category.titleRes)

                                Surface(
                                    onClick = { selectedTabletCategory = category },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) Accent.copy(alpha = 0.2f) else palette.surfaceHi,
                                    border = BorderStroke(1.dp, if (isSelected) Accent else Color.Transparent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Accent else palette.text,
                                            modifier = Modifier.size(22.dp),
                                        )
                                        Text(
                                            text = categoryTitle,
                                            color = if (isSelected) palette.text else palette.faint,
                                            fontSize = 15.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }

                        // Right Detail Pane
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(detailScrollState)
                                .padding(bottom = 28.dp),
                        ) {
                            if (selectedTabletCategory.perDisplay) profileSwitcher()
                            when (selectedTabletCategory) {
                                SettingsCategory.DISPLAY_PROFILES -> DisplayProfilesSection(
                                    s = s,
                                    viewModel = viewModel,
                                    screenType = profile,
                                    onFoldAction = { showFoldActionPicker = true },
                                    onUnfoldAction = { showUnfoldActionPicker = true },
                                    onFoldTransition = { showFoldTransitionPicker = true },
                                    onAppPairs = { showAppPairsManager = true },
                                    onCopyProfile = { showCopyProfile = true },
                                )
                                SettingsCategory.GRID_DPI -> GridAndDpiSection(s = s, viewModel = viewModel, screenType = profile, onPickColor = { colorTarget = it })
                                SettingsCategory.DOCK_TASKBAR -> DockAndTaskbarSection(s = s, viewModel = viewModel, screenType = profile, previewIcon = previewIcon)
                                SettingsCategory.TASKBAR -> TaskbarSection(s = s, viewModel = viewModel)
                                SettingsCategory.RECENTS -> RecentsSection(s = s, viewModel = viewModel)
                                SettingsCategory.ROOT -> RootSection(s = s, viewModel = viewModel)
                                SettingsCategory.APP_DRAWER -> AppDrawerSection(
                                    s = s,
                                    viewModel = viewModel,
                                    screenType = profile,
                                    hiddenCount = hiddenKeys.size,
                                    onShowHidden = { showHiddenManager = true },
                                    onLayoutMode = { showDrawerLayoutPicker = true },
                                    onPickColor = { colorTarget = it },
                                )
                                SettingsCategory.GESTURES -> GesturesSection(
                                    s = s,
                                    viewModel = viewModel,
                                    screenType = profile,
                                    allApps = allApps,
                                    onSwipeUp = { showSwipeUpPicker = true },
                                    onSwipeDown = { showSwipeDownPicker = true },
                                    onLeftSwipe = { showLeftSwipePicker = true },
                                    onRightSwipe = { showRightSwipePicker = true },
                                )
                                SettingsCategory.APPEARANCE -> AppearanceSection(
                                    s = s,
                                    viewModel = viewModel,
                                    screenType = profile,
                                    iconPacks = iconPacks,
                                    onIconPack = { showIconPackPicker = true },
                                    onCountStyle = { showCountStylePicker = true },
                                    onPeoplePrivacy = { showPeoplePrivacyPicker = true },
                                    onBatchTimes = { showBatchTimesEditor = true },
                                    onFoldTransition = { showFoldTransitionPicker = true },
                                )
                                SettingsCategory.NOTIFICATIONS -> NotificationsSection(s = s, viewModel = viewModel, screenType = profile, previewIcon = previewIcon)
                                SettingsCategory.PERMISSIONS -> PermissionsSection(
                                    isDefaultLauncher = isDefaultLauncher,
                                    notifAccessGranted = notifAccessGranted,
                                    onSetDefault = { runCatching { setDefaultLauncher.launch(DefaultLauncher.requestIntent(context)) } },
                                    onNotifAccess = { openNotificationAccess(context) },
                                )
                                SettingsCategory.BACKUP -> BackupSection()
                            }
                        }
                    }
                } else {
                    // Single-column layout for narrow windows: outer screens and flip-phone inner screens
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(horizontal = 16.dp),
                    ) {
                        if (selectedOuterCategory == null) {
                            // Main Category Overview List
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_title),
                                    color = palette.text,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp,
                                )

                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(palette.surfaceHi)
                                        .clickable {
                                            isSearchActive = !isSearchActive
                                            if (!isSearchActive) searchQuery = ""
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.settings_search),
                                        tint = palette.text,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isSearchActive,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                SearchFieldHeader(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onClose = {
                                        isSearchActive = false
                                        searchQuery = ""
                                    },
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(mainScrollState)
                                    .padding(bottom = 28.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                profileSwitcher()
                                val queryLower = searchQuery.trim().lowercase()
                                val filteredCategories = if (queryLower.isBlank()) {
                                    SettingsCategory.entries
                                } else {
                                    SettingsCategory.entries.filter { cat ->
                                        stringResource(cat.titleRes).lowercase().contains(queryLower) ||
                                            stringResource(cat.subtitleRes).lowercase().contains(queryLower)
                                    }
                                }

                                filteredCategories.forEach { category ->
                                    CategoryOverviewCard(
                                        category = category,
                                        onClick = { selectedOuterCategory = category },
                                    )
                                }
                                // Single settings matching the search, below the matching pages.
                                val anySetting = queryLower.isNotBlank() && SettingsSearchResults(searchQuery) { category, title ->
                                    searchFocus.value = title
                                    selectedOuterCategory = category
                                }
                                if (filteredCategories.isEmpty() && !anySetting) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_no_results),
                                            color = palette.faint,
                                            fontSize = 15.sp,
                                        )
                                    }
                                }
                            }
                        } else {
                            // Phone Category Subpage View
                            val activeCategory = selectedOuterCategory!!
                            BackHandler { selectedOuterCategory = null }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(palette.surfaceHi)
                                            .clickable { selectedOuterCategory = null },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = stringResource(R.string.settings_close),
                                            tint = palette.text,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    Text(
                                        text = stringResource(activeCategory.titleRes),
                                        color = palette.text,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.3).sp,
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(palette.surfaceHi)
                                        .clickable {
                                            isSearchActive = !isSearchActive
                                            if (!isSearchActive) searchQuery = ""
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = stringResource(R.string.settings_search),
                                        tint = palette.text,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isSearchActive,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                SearchFieldHeader(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onClose = {
                                        isSearchActive = false
                                        searchQuery = ""
                                    },
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(subpageScrollState)
                                    .padding(bottom = 28.dp),
                            ) {
                                // Categories that hold only shared settings don't offer a profile choice.
                                if (activeCategory.perDisplay) profileSwitcher()
                                when (activeCategory) {
                                    SettingsCategory.DISPLAY_PROFILES -> DisplayProfilesSection(
                                        s = s,
                                        viewModel = viewModel,
                                        screenType = profile,
                                        onFoldAction = { showFoldActionPicker = true },
                                        onUnfoldAction = { showUnfoldActionPicker = true },
                                        onFoldTransition = { showFoldTransitionPicker = true },
                                        onAppPairs = { showAppPairsManager = true },
                                        onCopyProfile = { showCopyProfile = true },
                                    )
                                    SettingsCategory.GRID_DPI -> GridAndDpiSection(s = s, viewModel = viewModel, screenType = profile, onPickColor = { colorTarget = it })
                                    SettingsCategory.DOCK_TASKBAR -> DockAndTaskbarSection(s = s, viewModel = viewModel, screenType = profile, previewIcon = previewIcon)
                                    SettingsCategory.TASKBAR -> TaskbarSection(s = s, viewModel = viewModel)
                                    SettingsCategory.RECENTS -> RecentsSection(s = s, viewModel = viewModel)
                                    SettingsCategory.ROOT -> RootSection(s = s, viewModel = viewModel)
                                    SettingsCategory.APP_DRAWER -> AppDrawerSection(
                                        s = s,
                                        viewModel = viewModel,
                                        screenType = profile,
                                        hiddenCount = hiddenKeys.size,
                                        onShowHidden = { showHiddenManager = true },
                                        onLayoutMode = { showDrawerLayoutPicker = true },
                                        onPickColor = { colorTarget = it },
                                    )
                                    SettingsCategory.GESTURES -> GesturesSection(
                                        s = s,
                                        viewModel = viewModel,
                                        screenType = profile,
                                        allApps = allApps,
                                        onSwipeUp = { showSwipeUpPicker = true },
                                        onSwipeDown = { showSwipeDownPicker = true },
                                        onLeftSwipe = { showLeftSwipePicker = true },
                                        onRightSwipe = { showRightSwipePicker = true },
                                    )
                                    SettingsCategory.APPEARANCE -> AppearanceSection(
                                        s = s,
                                        viewModel = viewModel,
                                        screenType = profile,
                                        iconPacks = iconPacks,
                                        onIconPack = { showIconPackPicker = true },
                                        onCountStyle = { showCountStylePicker = true },
                                        onPeoplePrivacy = { showPeoplePrivacyPicker = true },
                                        onBatchTimes = { showBatchTimesEditor = true },
                                        onFoldTransition = { showFoldTransitionPicker = true },
                                    )
                                    SettingsCategory.NOTIFICATIONS -> NotificationsSection(s = s, viewModel = viewModel, screenType = profile, previewIcon = previewIcon)
                                    SettingsCategory.PERMISSIONS -> PermissionsSection(
                                        isDefaultLauncher = isDefaultLauncher,
                                        notifAccessGranted = notifAccessGranted,
                                        onSetDefault = { runCatching { setDefaultLauncher.launch(DefaultLauncher.requestIntent(context)) } },
                                        onNotifAccess = { openNotificationAccess(context) },
                                    )
                                    SettingsCategory.BACKUP -> BackupSection()
                                }
                            }
                        }
                    }
                }
            }

        }

        // Dialogs & Pickers
        if (showLeftSwipePicker) {
            EdgeSwipeAppPicker(
                titleRes = R.string.settings_left_edge_pick,
                noneRes = R.string.settings_left_edge_none,
                apps = allApps,
                onPick = { key ->
                    viewModel.setLeftSwipeAppKey(key, profile)
                    showLeftSwipePicker = false
                },
                onDismiss = { showLeftSwipePicker = false },
            )
        }
        if (showRightSwipePicker) {
            EdgeSwipeAppPicker(
                titleRes = R.string.settings_right_edge_pick,
                noneRes = R.string.settings_right_edge_none,
                apps = allApps,
                onPick = { key ->
                    viewModel.setRightSwipeAppKey(key, profile)
                    showRightSwipePicker = false
                },
                onDismiss = { showRightSwipePicker = false },
            )
        }
        if (showIconPackPicker) {
            IconPackPicker(
                packs = iconPacks,
                selected = s.icons.iconPack,
                onPick = { pkg ->
                    viewModel.setIconPack(pkg, profile)
                    showIconPackPicker = false
                },
                onDismiss = { showIconPackPicker = false },
            )
        }
        if (showCountStylePicker) {
            ChoicePicker(
                title = R.string.settings_notif_widget_count,
                options = listOf(
                    LauncherSettings.COUNT_NUMBER to R.string.settings_notif_widget_count_number,
                    LauncherSettings.COUNT_DOT to R.string.settings_notif_widget_count_dot,
                    LauncherSettings.COUNT_NONE to R.string.settings_notif_widget_count_none,
                ),
                selected = s.widgets.notificationCountStyle,
                onPick = {
                    viewModel.setNotificationWidgetCountStyle(it, profile)
                    showCountStylePicker = false
                },
                onDismiss = { showCountStylePicker = false },
            )
        }
        if (showDrawerLayoutPicker) {
            ChoicePicker(
                title = R.string.settings_drawer_layout_mode,
                options = listOf(
                    LauncherSettings.DRAWER_LAYOUT_GRID to R.string.settings_drawer_layout_grid,
                    LauncherSettings.DRAWER_LAYOUT_LIST to R.string.settings_drawer_layout_list,
                ),
                selected = s.drawer.layoutMode,
                onPick = {
                    viewModel.setDrawerLayoutMode(it, profile)
                    showDrawerLayoutPicker = false
                },
                onDismiss = { showDrawerLayoutPicker = false },
            )
        }
        if (showBatchTimesEditor) {
            BatchTimesEditor(
                initial = s.widgets.peopleBatchTimes.replace(",", ", "),
                onSave = {
                    viewModel.setPeopleBatchTimes(it)
                    showBatchTimesEditor = false
                },
                onDismiss = { showBatchTimesEditor = false },
            )
        }
        if (showPeoplePrivacyPicker) {
            ChoicePicker(
                title = R.string.settings_people_privacy,
                options = listOf(
                    LauncherSettings.PRIVACY_ALL to R.string.settings_people_privacy_all,
                    LauncherSettings.PRIVACY_SENDER to R.string.settings_people_privacy_sender,
                    LauncherSettings.PRIVACY_COUNT to R.string.settings_people_privacy_count,
                ),
                selected = s.widgets.peoplePrivacy,
                onPick = {
                    viewModel.setPeoplePrivacy(it)
                    showPeoplePrivacyPicker = false
                },
                onDismiss = { showPeoplePrivacyPicker = false },
            )
        }
        if (showSwipeDownPicker) {
            ChoicePicker(
                title = R.string.settings_swipe_down_action,
                options = gestureOptions(context),
                selected = s.home.swipeDownAction,
                onPick = { viewModel.setSwipeDownAction(it, profile); showSwipeDownPicker = false },
                onDismiss = { showSwipeDownPicker = false },
            )
        }

        if (showSwipeUpPicker) {
            ChoicePicker(
                title = R.string.settings_swipe_up_action,
                options = listOf(
                    LauncherSettings.GESTURE_DRAWER to R.string.settings_gesture_drawer,
                    LauncherSettings.GESTURE_SEARCH to R.string.settings_gesture_search,
                    LauncherSettings.GESTURE_NONE to R.string.settings_gesture_none,
                ),
                selected = s.home.swipeUpAction,
                onPick = { viewModel.setSwipeUpAction(it, profile); showSwipeUpPicker = false },
                onDismiss = { showSwipeUpPicker = false },
            )
        }
        if (showFoldActionPicker) {
            ChoicePicker(
                title = R.string.settings_fold_action,
                options = listOf(
                    LauncherSettings.ACTION_STAY to R.string.settings_action_stay,
                    LauncherSettings.ACTION_PAGE_0 to R.string.settings_action_page_0,
                    LauncherSettings.ACTION_LOCK to R.string.settings_action_lock,
                ),
                selected = s.fold.foldAction,
                onPick = { viewModel.setFoldAction(it, profile); showFoldActionPicker = false },
                onDismiss = { showFoldActionPicker = false },
            )
        }
        if (showUnfoldActionPicker) {
            ChoicePicker(
                title = R.string.settings_unfold_action,
                options = listOf(
                    LauncherSettings.ACTION_STAY to R.string.settings_action_stay,
                    LauncherSettings.ACTION_SEARCH to R.string.settings_action_search,
                    LauncherSettings.ACTION_DRAWER to R.string.settings_action_drawer,
                ),
                selected = s.fold.unfoldAction,
                onPick = { viewModel.setUnfoldAction(it, profile); showUnfoldActionPicker = false },
                onDismiss = { showUnfoldActionPicker = false },
            )
        }
        colorTarget?.let { target ->
            ColorPickerDialog(
                title = stringResource(target.titleRes),
                initial = when (target) {
                    ColorTarget.DRAWER_ACCENT -> s.drawer.accentColor
                    ColorTarget.DRAWER_BACKGROUND -> s.drawer.customColor
                    ColorTarget.APP_LABEL -> s.icons.labelColor
                },
                onConfirm = { argb ->
                    when (target) {
                        ColorTarget.DRAWER_ACCENT -> viewModel.setDrawerAccentColor(argb, profile)
                        ColorTarget.DRAWER_BACKGROUND -> {
                            viewModel.setDrawerCustomColor(argb, profile)
                            viewModel.setDrawerThemeTint(LauncherSettings.DRAWER_THEME_CUSTOM, profile)
                        }
                        ColorTarget.APP_LABEL -> viewModel.setAppLabelColor(argb, profile)
                    }
                    colorTarget = null
                },
                onDismiss = { colorTarget = null },
            )
        }
        if (showCopyProfile) {
            val source = if (profile == ScreenType.OUTER) ScreenType.INNER else ScreenType.OUTER
            val copiedMsg = stringResource(R.string.settings_copy_profile_done)
            AlertDialog(
                onDismissRequest = { showCopyProfile = false },
                containerColor = palette.surfaceHi,
                title = { Text(stringResource(R.string.settings_copy_profile), color = palette.text) },
                text = {
                    Text(
                        stringResource(
                            if (profile == ScreenType.OUTER) R.string.settings_copy_profile_to_outer
                            else R.string.settings_copy_profile_to_inner,
                        ),
                        color = palette.dim,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCopyProfile = false
                        viewModel.copyProfile(from = source, to = profile) {
                            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.settings_copy_profile_confirm), color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { showCopyProfile = false }) {
                        Text(stringResource(R.string.settings_cancel), color = palette.dim)
                    }
                },
            )
        }
        if (showFoldTransitionPicker) {
            ChoicePicker(
                title = R.string.settings_fold_transition,
                options = listOf(
                    LauncherSettings.TRANSITION_MORPH_SCALE_FADE to R.string.settings_transition_morph_scale_fade,
                    LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP to R.string.settings_transition_book_unfold_sweep,
                    LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE to R.string.settings_transition_aquamorphic_ripple,
                    LauncherSettings.TRANSITION_CROSSFADE to R.string.settings_transition_crossfade,
                    LauncherSettings.TRANSITION_SCALE to R.string.settings_transition_scale,
                    LauncherSettings.TRANSITION_SLIDE to R.string.settings_transition_slide,
                ) + listOfNotNull(
                    // Inner display: follows the hinge while opening (phones with a hinge sensor).
                    // Outer display: the same effects, played on a timer after closing.
                    if (profile == ScreenType.INNER) {
                        (LauncherSettings.TRANSITION_LIVE_HINGE to R.string.settings_transition_live_hinge)
                            .takeIf { hasHingeSensor(context) }
                    } else {
                        LauncherSettings.TRANSITION_LIVE_HINGE to R.string.settings_transition_hinge_timed
                    },
                ),
                selected = s.fold.transitionStyle,
                onPick = { viewModel.setFoldTransitionStyle(it, profile); showFoldTransitionPicker = false },
                onDismiss = { showFoldTransitionPicker = false },
            )
        }
    }
}
}

@Composable
private fun SearchFieldHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalExpressivePalette.current
    val shape = RoundedCornerShape(18.dp)
    // The field draws its own rounded outline (no clipping box around it), so no corner is cut off.
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.settings_search_hint), color = palette.faint) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Accent) },
        trailingIcon = {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(8.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = palette.dim)
            }
        },
        singleLine = true,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = palette.surfaceHi,
            unfocusedContainerColor = palette.surfaceHi,
            focusedBorderColor = Accent.copy(alpha = 0.8f),
            unfocusedBorderColor = palette.faint.copy(alpha = 0.25f),
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            cursorColor = Accent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    )
}

@Composable
private fun CategoryOverviewCard(
    category: SettingsCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalExpressivePalette.current

    Surface(
        onClick = onClick,
        color = palette.surfaceHi,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = palette.shadow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.titleRes),
                    color = palette.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(category.subtitleRes),
                    color = palette.dim,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = palette.faint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
