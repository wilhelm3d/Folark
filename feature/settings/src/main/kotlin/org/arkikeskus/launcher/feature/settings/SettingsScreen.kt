package org.arkikeskus.launcher.feature.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.arkikeskus.launcher.data.IconPackInfo
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.AppPair
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.DefaultLauncher
import org.arkikeskus.launcher.ui.component.AppIcon
import org.arkikeskus.launcher.ui.component.badgeAlignment
import org.arkikeskus.launcher.ui.component.LocalBadgeStyle
import org.arkikeskus.launcher.ui.component.BadgeStyle
import org.arkikeskus.launcher.ui.component.LocalIconPack
import org.arkikeskus.launcher.data.MotoLinks
import org.arkikeskus.launcher.ui.component.LocalIconShape
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.LocalThemedIcons
import org.arkikeskus.launcher.ui.component.NotificationBadge
import org.arkikeskus.launcher.ui.component.supportsWindowBlur
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.DarkExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LightExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import java.util.UUID
import kotlin.math.roundToInt

/** Minimum window width (dp) for the two-pane settings layout — the large-screen breakpoint. */
private const val TWO_PANE_MIN_WIDTH_DP = 600

/** Which colour the colour-picker dialog is editing. */
private enum class ColorTarget(@StringRes val titleRes: Int) {
    DRAWER_ACCENT(R.string.settings_drawer_accent_color),
    DRAWER_BACKGROUND(R.string.settings_drawer_custom_color),
    APP_LABEL(R.string.settings_label_color),
}

private enum class SettingsCategory(
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
    // the inner screen can be set up from the cover screen and vice versa. `screenType` stays the
    // PHYSICAL display: it alone decides the layout (two-pane vs. phone) and the settings UI scale.
    var editingProfile by rememberSaveable { mutableStateOf<ScreenType?>(null) }
    LaunchedEffect(screenType) { editingProfile = null }
    val profile = editingProfile ?: screenType
    LaunchedEffect(profile) { viewModel.setScreenType(profile) }
    CompositionLocalProvider(LocalScreenType provides screenType) {
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
    val studio = s.settingsStyle == LauncherSettings.SETTINGS_STYLE_STUDIO
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
    val dpiDensity = remember(systemDensity, physicalSettings.dpiSettings) {
        Density(
            density = systemDensity.density * physicalSettings.dpiSettings,
            fontScale = systemDensity.fontScale * physicalSettings.dpiSettings,
        )
    }

    CompositionLocalProvider(
        LocalDensity provides dpiDensity,
        LocalExpressivePalette provides palette,
        LocalSettingsStudio provides studio,
        LocalPreviewApps provides previewApps,
        LocalIconPack provides s.iconPackPackage,
        LocalIconShape provides s.iconShape,
        LocalThemedIcons provides s.useThemedIcons,
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

                            SettingsCategory.entries.forEach { category ->
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
                    // Single-column layout for narrow windows: cover screens and flip-phone inner screens
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

                                if (filteredCategories.isEmpty()) {
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
                                } else {
                                    filteredCategories.forEach { category ->
                                        CategoryOverviewCard(
                                            category = category,
                                            onClick = { selectedOuterCategory = category },
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
                selected = s.iconPackPackage,
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
                selected = s.notificationWidgetCountStyle,
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
                selected = s.drawerLayoutMode,
                onPick = {
                    viewModel.setDrawerLayoutMode(it, profile)
                    showDrawerLayoutPicker = false
                },
                onDismiss = { showDrawerLayoutPicker = false },
            )
        }
        if (showBatchTimesEditor) {
            BatchTimesEditor(
                initial = s.peopleBatchTimes.replace(",", ", "),
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
                selected = s.peoplePrivacy,
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
                selected = s.swipeDownAction,
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
                selected = s.swipeUpAction,
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
                selected = s.foldAction,
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
                selected = s.unfoldAction,
                onPick = { viewModel.setUnfoldAction(it, profile); showUnfoldActionPicker = false },
                onDismiss = { showUnfoldActionPicker = false },
            )
        }
        colorTarget?.let { target ->
            ColorPickerDialog(
                title = stringResource(target.titleRes),
                initial = when (target) {
                    ColorTarget.DRAWER_ACCENT -> s.drawerAccentColor
                    ColorTarget.DRAWER_BACKGROUND -> s.drawerCustomColor
                    ColorTarget.APP_LABEL -> s.appLabelColor
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
                selected = s.foldTransitionStyle,
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
    Surface(
        color = palette.surfaceHi,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.settings_search_hint), color = palette.faint) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Accent)
            },
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
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

@Composable
private fun gestureLabel(action: String): String = stringResource(
    when (action) {
        LauncherSettings.GESTURE_DRAWER -> R.string.settings_gesture_drawer
        LauncherSettings.GESTURE_SEARCH -> R.string.settings_gesture_search
        LauncherSettings.GESTURE_LOCK -> R.string.settings_gesture_lock
        LauncherSettings.GESTURE_NONE -> R.string.settings_gesture_none
        LauncherSettings.GESTURE_MOTO_NOTE -> R.string.settings_gesture_moto_note
        LauncherSettings.GESTURE_MOTO_VAULT -> R.string.settings_gesture_moto_vault
        LauncherSettings.GESTURE_MOTO_READY_FOR -> R.string.settings_gesture_moto_ready_for
        LauncherSettings.GESTURE_MOTO_GAMES -> R.string.settings_gesture_moto_games
        LauncherSettings.GESTURE_MOTO_WEATHER -> R.string.settings_gesture_moto_weather
        LauncherSettings.GESTURE_MOTO_STYLES -> R.string.settings_gesture_moto_styles
        else -> R.string.settings_gesture_notifications
    }
)

/** Home gesture choices: Folark's own, then the Motorola screens this phone has, then none. */
private fun gestureOptions(context: android.content.Context): List<Pair<String, Int>> = listOf(
    LauncherSettings.GESTURE_NOTIFICATIONS to R.string.settings_gesture_notifications,
    LauncherSettings.GESTURE_DRAWER to R.string.settings_gesture_drawer,
    LauncherSettings.GESTURE_SEARCH to R.string.settings_gesture_search,
    LauncherSettings.GESTURE_LOCK to R.string.settings_gesture_lock,
) + listOf(
    LauncherSettings.GESTURE_MOTO_NOTE to R.string.settings_gesture_moto_note,
    LauncherSettings.GESTURE_MOTO_VAULT to R.string.settings_gesture_moto_vault,
    LauncherSettings.GESTURE_MOTO_READY_FOR to R.string.settings_gesture_moto_ready_for,
    LauncherSettings.GESTURE_MOTO_GAMES to R.string.settings_gesture_moto_games,
    LauncherSettings.GESTURE_MOTO_WEATHER to R.string.settings_gesture_moto_weather,
    LauncherSettings.GESTURE_MOTO_STYLES to R.string.settings_gesture_moto_styles,
).filter { (gesture, _) -> MotoLinks.gestureAvailable(context, gesture) } + listOf(
    LauncherSettings.GESTURE_NONE to R.string.settings_gesture_none,
)

/** Gestures page: what a double tap on empty home space does (any home gesture, Motorola's too). */
@Composable
private fun DoubleTapActionRow(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(lockServiceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = lockServiceEnabled(context)
        onPauseOrDispose { }
    }
    fun openAccessibilitySettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    var picking by remember { mutableStateOf(false) }
    ExpressiveActionItem(
        title = stringResource(R.string.settings_double_tap_action),
        description = gestureLabel(s.doubleTapAction),
        onClick = { picking = true },
    )
    if (s.doubleTapAction == LauncherSettings.GESTURE_LOCK && !serviceEnabled) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_double_tap_lock_grant),
            description = stringResource(R.string.settings_double_tap_lock_grant_desc),
            onClick = { openAccessibilitySettings() },
        )
    }
    if (picking) {
        ChoicePicker(
            title = R.string.settings_double_tap_action,
            options = remember { gestureOptions(context) },
            selected = s.doubleTapAction,
            onPick = { choice ->
                viewModel.setDoubleTapAction(choice, screenType)
                if (choice == LauncherSettings.GESTURE_LOCK && !lockServiceEnabled(context)) {
                    openAccessibilitySettings()
                }
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun BatchTimesEditor(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val p = LocalExpressivePalette.current
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(stringResource(R.string.settings_people_batch_times), color = p.text) },
        text = {
            Column {
                Text(stringResource(R.string.settings_people_batch_times_hint), color = p.text, fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.settings_people_batch_times_save), color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close), color = Accent) }
        },
    )
}

@Composable
private fun ChoicePicker(
    @StringRes title: Int,
    options: List<Pair<String, Int>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalExpressivePalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(stringResource(title), color = p.text) },
        text = {
            Column {
                options.forEach { (value, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(labelRes),
                            color = if (selected == value) Accent else p.text,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close), color = Accent) }
        },
    )
}

@Composable
private fun IconPackPicker(
    packs: List<IconPackInfo>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalExpressivePalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(stringResource(R.string.settings_icon_pack), color = p.text) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick("") }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_icon_pack_system),
                        color = if (selected.isBlank()) Accent else p.text,
                        fontSize = 16.sp,
                    )
                }
                packs.forEach { pack ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(pack.packageName) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pack.label,
                            color = if (selected == pack.packageName) Accent else p.text,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close), color = Accent) }
        },
    )
}



@Composable
private fun PermissionRow(label: String, permission: String) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    LifecycleResumeEffect(permission) {
        granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        val activity = context.findActivity()
        if (!result && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        ) {
            openAppDetailsSettings(context)
        }
    }
    ExpressiveActionItem(
        title = label,
        description = stringResource(
            if (granted) R.string.settings_permission_granted else R.string.settings_permission_not_granted,
        ),
        onClick = {
            if (!granted) launcher.launch(permission)
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun lockServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val target = ComponentName(context.packageName, LOCK_SERVICE_CLASS)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == target }
}

private fun openAppDetailsSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun isNotificationAccessGranted(context: Context): Boolean =
    runCatching {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }.getOrDefault(false)

private fun openNotificationAccess(context: Context) {
    val component = ComponentName(
        context.packageName,
        "org.arkikeskus.launcher.notifications.NotificationDotListenerService",
    )
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(detail) }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

@Composable
private fun WeatherToggle(enabled: Boolean, onSetEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_show_weather),
        checked = enabled,
        onCheckedChange = { wantOn ->
            onSetEnabled(wantOn)
            if (wantOn &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
    )
}

@Composable
private fun StatusBarToggle(enabled: Boolean, onSetEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_status_bar),
        checked = enabled,
        onCheckedChange = { wantOn ->
            onSetEnabled(wantOn)
            if (wantOn &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.READ_PHONE_STATE)
            }
        },
    )
}

/** The taskbar over other apps: it is drawn by the accessibility service, so that must be allowed. */
@Composable
internal fun TaskbarToggle(enabled: Boolean, onSetEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(lockServiceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = lockServiceEnabled(context)
        onPauseOrDispose { }
    }
    fun openAccessibilitySettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_taskbar),
        subtitle = stringResource(R.string.settings_taskbar_desc),
        checked = enabled,
        onCheckedChange = { wantOn ->
            onSetEnabled(wantOn)
            if (wantOn && !lockServiceEnabled(context)) openAccessibilitySettings()
        },
    )
    if (enabled && !serviceEnabled) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_double_tap_lock_grant),
            description = stringResource(R.string.settings_taskbar_grant_desc),
            onClick = { openAccessibilitySettings() },
        )
    }
}

private val LABEL_COLOR_PRESETS = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFBDBDBD, 0xFFEF5350,
    0xFFFFA726, 0xFFFFEE58, 0xFF66BB6A, 0xFF42A5F5, 0xFFAB47BC,
).map { it.toInt() }

private val DRAWER_ACCENT_PRESETS = listOf(
    0xFF00B0FF, 0xFF0A84FF, 0xFF00BCD4, 0xFF00BFA5, 0xFF00C853,
    0xFFFFAB00, 0xFFE2714A, 0xFFFF3B30, 0xFFFF1744, 0xFFAA00FF,
).map { it.toInt() }

private val DRAWER_BACKGROUND_PRESETS = listOf(
    0xFF1B2A41, 0xFF0F2B2A, 0xFF2A1B3D, 0xFF3A1F1F, 0xFF2B2B2B, 0xFFE9EEF7, 0xFFF7EFE5,
).map { it.toInt() }

@Composable
private fun HiddenAppsManager(
    apps: List<AppItem>,
    hiddenKeys: Set<String>,
    onSetHidden: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalExpressivePalette.current
    BackHandler(onBack = onBack)
    Surface(modifier = modifier, color = p.bg) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_hidden_apps),
                    color = p.text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.settings_close), color = Accent, fontSize = 16.sp)
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = apps, key = { it.key }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            appItem = app,
                            labelColor = p.text,
                            showLabel = false,
                            iconSize = 40.dp,
                        )
                        Text(
                            text = app.label,
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                            color = p.text,
                            fontSize = 16.sp,
                        )
                        Switch(
                            checked = app.key in hiddenKeys,
                            onCheckedChange = { onSetHidden(app.key, it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Accent,
                                checkedThumbColor = Color.White,
                                checkedBorderColor = Accent,
                                uncheckedTrackColor = p.trackOff,
                                uncheckedThumbColor = p.thumbOff,
                                uncheckedBorderColor = p.trackOff,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLauncherIconBitmap(): ImageBitmap? {
    val context = LocalContext.current
    return remember {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val size = 144
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()
    }
}

@Composable
private fun BadgePreview(icon: ImageBitmap?, showDots: Boolean, showCount: Boolean, scale: Float) {
    Box(
        modifier = Modifier.padding(bottom = 6.dp, top = 4.dp),
        contentAlignment = badgeAlignment(),
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(52.dp))
        } else {
            Box(Modifier.size(52.dp).background(Accent, RoundedCornerShape(14.dp)))
        }
        if (showDots) {
            NotificationBadge(count = 5, showCount = showCount, scale = scale)
        }
    }
}

@Composable
private fun DockPreview(opacity: Float, icon: ImageBitmap?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFFB9C3D4), RoundedCornerShape(20.dp))
            .padding(8.dp),
    ) {
        Surface(
            color = Color.Black.copy(alpha = opacity),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                repeat(4) {
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(34.dp))
                    } else {
                        Box(Modifier.size(34.dp).background(Color.White.copy(alpha = 0.6f), CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun foldActionLabel(action: String): String = when (action) {
    LauncherSettings.ACTION_PAGE_0 -> stringResource(R.string.settings_action_page_0)
    LauncherSettings.ACTION_LOCK -> stringResource(R.string.settings_action_lock)
    else -> stringResource(R.string.settings_action_stay)
}

@Composable
private fun unfoldActionLabel(action: String): String = when (action) {
    LauncherSettings.ACTION_SEARCH -> stringResource(R.string.settings_action_search)
    LauncherSettings.ACTION_DRAWER -> stringResource(R.string.settings_action_drawer)
    else -> stringResource(R.string.settings_action_stay)
}

@Composable
private fun transitionLabel(style: String, inner: Boolean = true): String = when (style) {
    LauncherSettings.TRANSITION_MORPH_SCALE_FADE -> stringResource(R.string.settings_transition_morph_scale_fade)
    LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP -> stringResource(R.string.settings_transition_book_unfold_sweep)
    LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE -> stringResource(R.string.settings_transition_aquamorphic_ripple)
    LauncherSettings.TRANSITION_SCALE -> stringResource(R.string.settings_transition_scale)
    LauncherSettings.TRANSITION_SLIDE -> stringResource(R.string.settings_transition_slide)
    LauncherSettings.TRANSITION_LIVE_HINGE ->
        stringResource(if (inner) R.string.settings_transition_live_hinge else R.string.settings_transition_hinge_timed)
    else -> stringResource(R.string.settings_transition_crossfade)
}

@Composable
private fun AppPairsManager(
    apps: List<AppItem>,
    appPairs: List<AppPair>,
    onSaveAppPair: (AppPair) -> Unit,
    onDeleteAppPair: (String) -> Unit,
    onLaunchAppPair: (AppPair) -> Unit,
    onAddAppPairToHome: (AppPair) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // System Back returns to Settings instead of closing it.
    BackHandler(onBack = onBack)
    val palette = LocalExpressivePalette.current
    var showCreator by remember { mutableStateOf(false) }

    Surface(modifier = modifier, color = palette.bg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_app_pairs),
                    color = palette.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.settings_close), color = Accent)
                }
            }

            ExpressiveActionItem(
                title = stringResource(R.string.settings_create_app_pair),
                description = stringResource(R.string.settings_app_pairs_desc),
                trailingIcon = Icons.Rounded.Add,
                onClick = { showCreator = true },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(appPairs, key = { it.id }) { pair ->
                    AppPairRow(
                        pair = pair,
                        apps = apps,
                        onLaunch = { onLaunchAppPair(pair) },
                        onAddToHome = { onAddAppPairToHome(pair) },
                        onDelete = { onDeleteAppPair(pair.id) },
                    )
                }
            }
        }
    }

    if (showCreator) {
        AppPairCreatorDialog(
            apps = apps,
            onDismiss = { showCreator = false },
            onConfirm = { name, app1, app2, app3 ->
                val pair = AppPair(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    app1Key = app1.key,
                    app1Package = app1.packageName,
                    app1ClassName = app1.className,
                    app1UserSerial = app1.userSerial,
                    app2Key = app2.key,
                    app2Package = app2.packageName,
                    app2ClassName = app2.className,
                    app2UserSerial = app2.userSerial,
                    app3Key = app3?.key.orEmpty(),
                    app3Package = app3?.packageName.orEmpty(),
                    app3ClassName = app3?.className.orEmpty(),
                    app3UserSerial = app3?.userSerial ?: 0L,
                )
                onSaveAppPair(pair)
                showCreator = false
            },
        )
    }
}

@Composable
private fun AppPairRow(
    pair: AppPair,
    apps: List<AppItem>,
    onLaunch: () -> Unit,
    onAddToHome: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalExpressivePalette.current
    val context = LocalContext.current
    val addedMessage = stringResource(R.string.settings_app_pair_added_home)
    val app1 = apps.firstOrNull { it.key == pair.app1Key }
    val app2 = apps.firstOrNull { it.key == pair.app2Key }
    val app3 = if (pair.hasThirdApp) apps.firstOrNull { it.key == pair.app3Key } else null

    Surface(
        color = palette.surfaceHi,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLaunch),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (app1 != null) AppIcon(appItem = app1, labelColor = Color.Transparent, showLabel = false, iconSize = 36.dp)
                if (app2 != null) AppIcon(appItem = app2, labelColor = Color.Transparent, showLabel = false, iconSize = 36.dp)
                Column {
                    Text(pair.name, fontWeight = FontWeight.Bold, color = palette.text)
                    Text(
                        "${app1?.label ?: ""} & ${app2?.label ?: ""}" + (app3?.let { " + ${it.label}" } ?: ""),
                        fontSize = 12.sp,
                        color = palette.faint,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = {
                    onAddToHome()
                    android.widget.Toast.makeText(context, addedMessage, android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.settings_app_pair_add_home), color = Accent)
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.settings_delete), color = Color.Red)
                }
            }
        }
    }
}

@Composable
private fun AppPairCreatorDialog(
    apps: List<AppItem>,
    onDismiss: () -> Unit,
    onConfirm: (String, AppItem, AppItem, AppItem?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedApp1 by remember { mutableStateOf<AppItem?>(null) }
    var selectedApp2 by remember { mutableStateOf<AppItem?>(null) }
    // Optional: a third app that opens in a floating window over the split pair.
    var selectedApp3 by remember { mutableStateOf<AppItem?>(null) }
    var showApp1Picker by remember { mutableStateOf(false) }
    var showApp2Picker by remember { mutableStateOf(false) }
    var showApp3Picker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_create_app_pair)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_app_pair_name)) },
                    singleLine = true,
                )
                ExpressiveActionItem(
                    title = stringResource(R.string.settings_select_app_1),
                    description = selectedApp1?.label ?: "Tap to choose",
                    onClick = { showApp1Picker = true },
                )
                ExpressiveActionItem(
                    title = stringResource(R.string.settings_select_app_2),
                    description = selectedApp2?.label ?: "Tap to choose",
                    onClick = { showApp2Picker = true },
                )
                ExpressiveActionItem(
                    title = stringResource(R.string.settings_select_app_3),
                    description = selectedApp3?.label ?: stringResource(R.string.settings_select_app_3_none),
                    // Tapping again with an app chosen clears it: the third app is optional.
                    onClick = { if (selectedApp3 != null) selectedApp3 = null else showApp3Picker = true },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && selectedApp1 != null && selectedApp2 != null) {
                        onConfirm(name, selectedApp1!!, selectedApp2!!, selectedApp3)
                    }
                },
                enabled = name.isNotBlank() && selectedApp1 != null && selectedApp2 != null,
            ) {
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )

    if (showApp1Picker) {
        AppPickerDialog(apps = apps, onDismiss = { showApp1Picker = false }) {
            selectedApp1 = it
            showApp1Picker = false
        }
    }
    if (showApp2Picker) {
        AppPickerDialog(apps = apps, onDismiss = { showApp2Picker = false }) {
            selectedApp2 = it
            showApp2Picker = false
        }
    }
    if (showApp3Picker) {
        AppPickerDialog(apps = apps, onDismiss = { showApp3Picker = false }) {
            selectedApp3 = it
            showApp3Picker = false
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<AppItem>,
    onDismiss: () -> Unit,
    onSelect: (AppItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_choose_app)) },
        text = {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(apps, key = { it.key }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(app) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(appItem = app, labelColor = Color.Transparent, showLabel = false, iconSize = 40.dp)
                        Text(app.label, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun DisplayProfilesSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
    onFoldAction: () -> Unit,
    onUnfoldAction: () -> Unit,
    onFoldTransition: () -> Unit,
    onAppPairs: () -> Unit,
    onCopyProfile: () -> Unit,
) {
    val inner = screenType == ScreenType.INNER
    ExpressiveCategoryContainer(
        title = stringResource(if (inner) R.string.settings_profile_inner else R.string.settings_profile_outer),
        icon = if (inner) Icons.Rounded.TabletAndroid else Icons.Rounded.Smartphone,
        subtitle = stringResource(
            if (inner) R.string.settings_profile_inner_hint else R.string.settings_profile_outer_hint,
        ),
    ) {
        // Each display only lists what can actually happen on it: the inner screen's layout
        // options and the action that runs when arriving on that display.
        if (inner) {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_inner_dual_page),
                subtitle = stringResource(R.string.settings_inner_dual_page_desc),
                checked = s.innerDualPageWorkspace,
                onCheckedChange = { viewModel.setInnerDualPageWorkspace(it, screenType) },
            )
            ExpressiveRowDivider()

            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_inner_sidebar_drawer),
                subtitle = stringResource(R.string.settings_inner_sidebar_drawer_desc),
                checked = s.innerSidebarAppDrawer,
                onCheckedChange = { viewModel.setInnerSidebarAppDrawer(it, screenType) },
            )
            ExpressiveRowDivider()

            ExpressiveActionItem(
                title = stringResource(R.string.settings_unfold_action),
                description = unfoldActionLabel(s.unfoldAction),
                onClick = onUnfoldAction,
            )
            ExpressiveRowDivider()
        } else {
            ExpressiveActionItem(
                title = stringResource(R.string.settings_fold_action),
                description = foldActionLabel(s.foldAction),
                onClick = onFoldAction,
            )
            ExpressiveRowDivider()
        }

        ExpressiveActionItem(
            title = stringResource(R.string.settings_fold_transition),
            description = transitionLabel(s.foldTransitionStyle, inner),
            onClick = onFoldTransition,
        )
        ExpressiveRowDivider()
        TransitionSpeedRow(s = s, viewModel = viewModel, screenType = screenType)
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_unlock_transition),
            subtitle = stringResource(R.string.settings_unlock_transition_desc),
            checked = s.unlockTransition,
            onCheckedChange = viewModel::setUnlockTransition,
            shared = true,
        )
        if (s.foldTransitionStyle == LauncherSettings.TRANSITION_LIVE_HINGE) {
            LiveHingeRows(s = s, viewModel = viewModel, inner = inner)
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_allow_landscape),
            checked = s.allowLandscape,
            onCheckedChange = { viewModel.setAllowLandscape(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_copy_profile),
            description = stringResource(
                if (inner) R.string.settings_copy_profile_from_outer else R.string.settings_copy_profile_from_inner,
            ),
            onClick = onCopyProfile,
        )
    }

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_foldable_shared),
        icon = Icons.Rounded.Devices,
        sharedByBothDisplays = true,
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_half_opened_mode),
            subtitle = stringResource(R.string.settings_half_opened_mode_desc),
            checked = s.halfOpenedModeEnabled,
            onCheckedChange = viewModel::setHalfOpenedModeEnabled,
        )
        ExpressiveRowDivider()

        if (s.halfOpenedModeEnabled && hasHingeSensor(LocalContext.current)) {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_hinge_tilt),
                subtitle = stringResource(R.string.settings_hinge_tilt_desc),
                checked = s.hingeTilt,
                onCheckedChange = viewModel::setHingeTilt,
            )
            ExpressiveRowDivider()
        }

        ExpressiveActionItem(
            title = stringResource(R.string.settings_app_pairs),
            description = stringResource(R.string.settings_app_pairs_desc),
            onClick = onAppPairs,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_split_launch),
            subtitle = stringResource(R.string.settings_split_launch_desc),
            checked = s.splitLaunchEnabled,
            onCheckedChange = viewModel::setSplitLaunchEnabled,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_floating_launch),
            subtitle = stringResource(R.string.settings_floating_launch_desc),
            checked = s.floatingLaunchEnabled,
            onCheckedChange = viewModel::setFloatingLaunchEnabled,
        )
        ExpressiveRowDivider()

        FloatingSizeRow(s = s, viewModel = viewModel)
        MotoPopupGestureRow()
    }
}

@Composable
private fun GridAndDpiSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
    onPickColor: (ColorTarget) -> Unit,
) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_grid_dpi),
        icon = Icons.Rounded.GridOn,
        subtitle = stringResource(R.string.settings_category_grid_dpi_desc),
    ) {
        ExpressiveStepperRow(
            title = stringResource(R.string.settings_columns) + " (" + stringResource(R.string.settings_home) + ")",
            value = s.homeColumns,
            min = 3,
            max = 8,
            onValueChange = { viewModel.setHomeColumns(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveStepperRow(
            title = stringResource(R.string.settings_rows) + " (" + stringResource(R.string.settings_home) + ")",
            value = s.homeRows,
            min = 4,
            max = 8,
            onValueChange = { viewModel.setHomeRows(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_show_labels),
            checked = s.showHomeLabels,
            onCheckedChange = { viewModel.setShowHomeLabels(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_two_line_labels),
            checked = s.twoLineHomeLabels,
            onCheckedChange = { viewModel.setTwoLineHomeLabels(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_label_size),
            value = s.appLabelTextScale,
            onValueChange = { viewModel.setAppLabelTextScale(it, screenType) },
            valueRange = 0.8f..1.6f,
            valueDisplay = "%.1fx".format(s.appLabelTextScale),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_icon_size),
            value = s.iconSizeScale,
            onValueChange = { viewModel.setIconSizeScale(it, screenType) },
            valueRange = LauncherSettings.MIN_ICON_SIZE_SCALE..LauncherSettings.MAX_ICON_SIZE_SCALE,
            valueDisplay = "%d%%".format((s.iconSizeScale * 100).roundToInt()),
            subtitle = stringResource(R.string.settings_icon_size_desc),
        )
        ExpressiveRowDivider()

        AutoRemovePagesRow(s = s, viewModel = viewModel, screenType = screenType)
        ExpressiveRowDivider()

        ColorSwatchRow(
            title = stringResource(R.string.settings_label_color),
            presets = LABEL_COLOR_PRESETS,
            selected = s.appLabelColor,
            onPick = { viewModel.setAppLabelColor(it, screenType) },
            onCustom = { onPickColor(ColorTarget.APP_LABEL) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_dpi_workspace),
            value = s.dpiWorkspace,
            onValueChange = { viewModel.setDpiWorkspace(it, screenType) },
            valueRange = LauncherSettings.MIN_DPI_SCALE..LauncherSettings.MAX_DPI_SCALE,
            valueDisplay = "%d%%".format((s.dpiWorkspace * 100).roundToInt()),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_dpi_app_drawer),
            value = s.dpiAppDrawer,
            onValueChange = { viewModel.setDpiAppDrawer(it, screenType) },
            valueRange = LauncherSettings.MIN_DPI_SCALE..LauncherSettings.MAX_DPI_SCALE,
            valueDisplay = "%d%%".format((s.dpiAppDrawer * 100).roundToInt()),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_dpi_settings),
            value = s.dpiSettings,
            onValueChange = { viewModel.setDpiSettings(it, screenType) },
            valueRange = LauncherSettings.MIN_DPI_SCALE..LauncherSettings.MAX_DPI_SCALE,
            valueDisplay = "%d%%".format((s.dpiSettings * 100).roundToInt()),
        )
    }

    // ---- Home folders: the closed tile, and the folder when opened ------------------------------
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_folders),
        icon = Icons.Rounded.Folder,
        subtitle = stringResource(R.string.settings_folders_desc),
    ) {
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_folder_theme),
            options = listOf(
                LauncherSettings.FOLDER_THEME_GLASS to stringResource(R.string.settings_folder_theme_glass),
                LauncherSettings.FOLDER_THEME_DARK to stringResource(R.string.settings_folder_theme_dark),
                LauncherSettings.FOLDER_THEME_LIGHT to stringResource(R.string.settings_folder_theme_light),
                LauncherSettings.FOLDER_THEME_ACCENT to stringResource(R.string.settings_folder_theme_accent),
            ),
            selected = s.folderTheme,
            onSelect = { viewModel.setFolderTheme(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_folder_shape),
            options = listOf(
                LauncherSettings.FOLDER_SHAPE_SQUIRCLE to stringResource(R.string.settings_folder_shape_squircle),
                LauncherSettings.FOLDER_SHAPE_CIRCLE to stringResource(R.string.settings_folder_shape_circle),
                LauncherSettings.FOLDER_SHAPE_SQUARE to stringResource(R.string.settings_folder_shape_square),
            ),
            selected = s.folderShape,
            onSelect = { viewModel.setFolderShape(it, screenType) },
        )
        FolderStylePreview(s)
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_folder_preview),
            options = listOf(
                LauncherSettings.FOLDER_PREVIEW_GRID_2 to stringResource(R.string.settings_folder_preview_grid2),
                LauncherSettings.FOLDER_PREVIEW_GRID_3 to stringResource(R.string.settings_folder_preview_grid3),
                LauncherSettings.FOLDER_PREVIEW_COVER to stringResource(R.string.settings_folder_preview_cover),
            ),
            selected = s.folderPreview,
            onSelect = { viewModel.setFolderPreview(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_folder_open_size),
            options = listOf(
                LauncherSettings.FOLDER_OPEN_SMALL to stringResource(R.string.settings_folder_open_small),
                LauncherSettings.FOLDER_OPEN_MEDIUM to stringResource(R.string.settings_folder_open_medium),
                LauncherSettings.FOLDER_OPEN_LARGE to stringResource(R.string.settings_folder_open_large),
            ),
            selected = s.folderOpenSize,
            onSelect = { viewModel.setFolderOpenSize(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveStepperRow(
            title = stringResource(R.string.settings_folder_open_columns),
            value = s.folderOpenColumns,
            min = LauncherSettings.MIN_FOLDER_OPEN_COLUMNS,
            max = LauncherSettings.MAX_FOLDER_OPEN_COLUMNS,
            onValueChange = { viewModel.setFolderOpenColumns(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_folder_tap_first),
            subtitle = stringResource(R.string.settings_folder_tap_first_desc),
            checked = s.folderTapFirstApp,
            onCheckedChange = { viewModel.setFolderTapFirstApp(it, screenType) },
        )
    }
}

@Composable
private fun DockAndTaskbarSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
    previewIcon: ImageBitmap?,
) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_dock_taskbar),
        icon = Icons.Rounded.Dock,
        subtitle = stringResource(R.string.settings_category_dock_taskbar_desc),
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_dock_show),
            checked = s.dockEnabled,
            onCheckedChange = { viewModel.setDockEnabled(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveStepperRow(
            title = stringResource(R.string.settings_dock_icons),
            value = s.dockColumns,
            min = 3,
            max = 8,
            onValueChange = { viewModel.setDockColumns(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_show_labels),
            checked = s.showDockLabels,
            onCheckedChange = { viewModel.setShowDockLabels(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_dock_bg),
            value = s.dockBackgroundOpacity,
            onValueChange = { viewModel.setDockBackgroundOpacity(it, screenType) },
            valueDisplay = "%d%%".format((s.dockBackgroundOpacity * 100).toInt()),
        )
        ExpressiveRowDivider()

        DockPreview(opacity = s.dockBackgroundOpacity, icon = previewIcon)
        // Dock alignment only exists on the wide inner screen; the cover screen's dock is full width.
        if (screenType == ScreenType.INNER) {
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_inner_dock_alignment),
                options = listOf(
                    LauncherSettings.DOCK_ALIGNMENT_CENTER to stringResource(R.string.settings_dock_align_center),
                    LauncherSettings.DOCK_ALIGNMENT_LEFT to stringResource(R.string.settings_dock_align_left),
                    LauncherSettings.DOCK_ALIGNMENT_RIGHT to stringResource(R.string.settings_dock_align_right),
                    LauncherSettings.DOCK_ALIGNMENT_FLOATING to stringResource(R.string.settings_dock_align_floating),
                ),
                selected = s.innerDockAlignment,
                onSelect = { viewModel.setInnerDockAlignment(it, screenType) },
            )
        }
    }
}

@Composable
private fun AppDrawerSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
    hiddenCount: Int,
    onShowHidden: () -> Unit,
    onLayoutMode: () -> Unit,
    onPickColor: (ColorTarget) -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalExpressivePalette.current

    // ---- Look: colours, style and Liquid Glass, with a live miniature on top -------------------
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_drawer_look),
        icon = Icons.Rounded.Palette,
        subtitle = stringResource(R.string.settings_drawer_look_desc),
    ) {
        DrawerPreview(s = s)
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_drawer_style),
            options = listOf(
                LauncherSettings.DRAWER_STYLE_STANDARD_GRID to stringResource(R.string.settings_drawer_style_standard_grid),
                LauncherSettings.DRAWER_STYLE_ONE_UI to stringResource(R.string.settings_drawer_style_one_ui),
                LauncherSettings.DRAWER_STYLE_NOTHING_OS to stringResource(R.string.settings_drawer_style_nothing_os),
                LauncherSettings.DRAWER_STYLE_MOTO to stringResource(R.string.settings_drawer_style_moto),
            ),
            selected = s.drawerStyle,
            onSelect = { viewModel.setDrawerStyle(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_drawer_theme_tint),
            options = LauncherSettings.DRAWER_THEMES.map { it to drawerThemeLabel(it) },
            selected = s.drawerThemeTint,
            onSelect = { theme ->
                // "Custom" needs a colour first: open the picker, which also selects the theme.
                if (theme == LauncherSettings.DRAWER_THEME_CUSTOM && s.drawerThemeTint != theme) {
                    viewModel.setDrawerThemeTint(theme, screenType)
                    onPickColor(ColorTarget.DRAWER_BACKGROUND)
                } else {
                    viewModel.setDrawerThemeTint(theme, screenType)
                }
            },
        )
        if (s.drawerThemeTint == LauncherSettings.DRAWER_THEME_CUSTOM) {
            ExpressiveRowDivider()
            ColorSwatchRow(
                title = stringResource(R.string.settings_drawer_custom_color),
                presets = DRAWER_BACKGROUND_PRESETS,
                selected = s.drawerCustomColor,
                onPick = { viewModel.setDrawerCustomColor(it, screenType) },
                onCustom = { onPickColor(ColorTarget.DRAWER_BACKGROUND) },
            )
        }
        // Monochrome and Material You bring their own accent, so the accent row would do nothing.
        val themeOwnsAccent = s.drawerThemeTint == LauncherSettings.DRAWER_THEME_MONOCHROME ||
            s.drawerThemeTint == LauncherSettings.DRAWER_THEME_MATERIAL_YOU
        ExpressiveRowDivider()
        if (themeOwnsAccent) {
            Text(
                text = stringResource(R.string.settings_drawer_accent_from_theme),
                color = palette.dim,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            ColorSwatchRow(
                title = stringResource(R.string.settings_drawer_accent_color),
                presets = DRAWER_ACCENT_PRESETS,
                selected = s.drawerAccentColor,
                onPick = { viewModel.setDrawerAccentColor(it, screenType) },
                onCustom = { onPickColor(ColorTarget.DRAWER_ACCENT) },
            )
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_glass),
            subtitle = stringResource(R.string.settings_drawer_glass_desc),
            checked = s.drawerGlassEnabled,
            onCheckedChange = { viewModel.setDrawerGlassEnabled(it, screenType) },
        )
        if (s.drawerGlassEnabled) {
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_drawer_panel_opacity),
                value = s.drawerPanelOpacity,
                valueRange = LauncherSettings.MIN_DRAWER_PANEL_OPACITY..LauncherSettings.MAX_DRAWER_PANEL_OPACITY,
                onValueChange = { viewModel.setDrawerPanelOpacity(it, screenType) },
                valueDisplay = "%d%%".format((s.drawerPanelOpacity * 100).roundToInt()),
            )
        }
        ExpressiveRowDivider()

        val blurSupported = remember(context) { supportsWindowBlur(context) }
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_blur),
            value = s.drawerBackgroundBlur / LauncherSettings.MAX_DRAWER_BLUR,
            onValueChange = { viewModel.setDrawerBackgroundBlur(it * LauncherSettings.MAX_DRAWER_BLUR, screenType) },
            valueDisplay = "%d%%".format((s.drawerBackgroundBlur / LauncherSettings.MAX_DRAWER_BLUR * 100).roundToInt()),
            subtitle = if (blurSupported) null else stringResource(R.string.settings_drawer_blur_unsupported),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_scrim),
            value = s.drawerScrimOpacity,
            onValueChange = { viewModel.setDrawerScrimOpacity(it, screenType) },
            valueDisplay = "%d%%".format((s.drawerScrimOpacity * 100).roundToInt()),
        )
    }

    // ---- Layout ------------------------------------------------------------------------------
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_drawer_layout),
        icon = Icons.Rounded.GridOn,
    ) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_drawer_layout_mode),
            description = stringResource(
                when (s.drawerLayoutMode) {
                    LauncherSettings.DRAWER_LAYOUT_LIST -> R.string.settings_drawer_layout_list
                    else -> R.string.settings_drawer_layout_grid
                }
            ),
            onClick = onLayoutMode,
        )
        ExpressiveRowDivider()

        ExpressiveStepperRow(
            title = stringResource(R.string.settings_columns),
            value = s.drawerColumns,
            min = 3,
            max = 8,
            onValueChange = { viewModel.setDrawerColumns(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_search),
            checked = s.showDrawerSearch,
            onCheckedChange = { viewModel.setShowDrawerSearch(it, screenType) },
        )
        if (s.showDrawerSearch) {
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_drawer_search_position),
                options = listOf(
                    LauncherSettings.SEARCH_TOP to stringResource(R.string.settings_drawer_search_top),
                    LauncherSettings.SEARCH_BOTTOM to stringResource(R.string.settings_drawer_search_bottom),
                ),
                selected = s.drawerSearchPosition,
                onSelect = { viewModel.setDrawerSearchPosition(it, screenType) },
            )
            ExpressiveRowDivider()
            ContactSearchRow(checked = s.searchContacts, onCheckedChange = viewModel::setSearchContacts)
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_search_actions),
                subtitle = stringResource(R.string.settings_search_actions_desc),
                checked = s.searchActions,
                onCheckedChange = viewModel::setSearchActions,
                shared = true,
            )
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_index_bar),
            checked = s.drawerIndexBarEnabled,
            onCheckedChange = { viewModel.setDrawerIndexBarEnabled(it, screenType) },
        )
        if (s.drawerIndexBarEnabled) {
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_az_index_position),
                options = listOf(
                    LauncherSettings.AZ_POSITION_VERTICAL_RIGHT to stringResource(R.string.settings_az_position_vertical_right),
                    LauncherSettings.AZ_POSITION_VERTICAL_LEFT to stringResource(R.string.settings_az_position_vertical_left),
                    LauncherSettings.AZ_POSITION_HORIZONTAL_TOP to stringResource(R.string.settings_az_position_horizontal_top),
                    LauncherSettings.AZ_POSITION_HORIZONTAL_BOTTOM to stringResource(R.string.settings_az_position_horizontal_bottom),
                ),
                selected = s.azIndexPosition,
                onSelect = { viewModel.setAzIndexPosition(it, screenType) },
            )
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_az_index_size),
                value = s.azIndexScale,
                valueRange = LauncherSettings.MIN_AZ_INDEX_SCALE..LauncherSettings.MAX_AZ_INDEX_SCALE,
                onValueChange = { viewModel.setAzIndexScale((it * 20f).roundToInt() / 20f, screenType) },
                valueDisplay = "%d%%".format((s.azIndexScale * 100).roundToInt()),
            )
        }
        ExpressiveRowDivider()

        // A sidebar drawer (inner display) is always full height, so the height slider is moot there.
        val sidebar = screenType == ScreenType.INNER && s.innerSidebarAppDrawer
        if (!sidebar) {
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_drawer_height),
                value = s.drawerHeightFraction,
                valueRange = 0.4f..1.0f,
                onValueChange = { viewModel.setDrawerHeightFraction(it, screenType) },
                valueDisplay = "%d%%".format((s.drawerHeightFraction * 100).roundToInt()),
            )
            ExpressiveRowDivider()
        }

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_drawer_handle),
            options = listOf(
                LauncherSettings.DRAWER_HANDLE_BAR to stringResource(R.string.settings_drawer_handle_bar),
                LauncherSettings.DRAWER_HANDLE_ARROW to stringResource(R.string.settings_drawer_handle_arrow),
            ),
            selected = s.drawerHandleStyle,
            onSelect = { viewModel.setDrawerHandleStyle(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_bottom_space),
            subtitle = stringResource(R.string.settings_drawer_bottom_space_desc),
            value = s.drawerBottomSpace.toFloat(),
            valueRange = 0f..LauncherSettings.MAX_DRAWER_BOTTOM_SPACE.toFloat(),
            onValueChange = { viewModel.setDrawerBottomSpace((it / 4f).roundToInt() * 4, screenType) },
            valueDisplay = "%d dp".format(s.drawerBottomSpace),
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_letter_headers),
            checked = s.drawerLetterHeaders,
            onCheckedChange = { viewModel.setDrawerLetterHeaders(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_letter_popup),
            subtitle = stringResource(R.string.settings_drawer_letter_popup_desc),
            checked = s.drawerLargeLetterPopup,
            onCheckedChange = { viewModel.setDrawerLargeLetterPopup(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_swipe_tabs),
            subtitle = stringResource(R.string.settings_drawer_swipe_tabs_desc),
            checked = s.drawerSwipeTabs,
            onCheckedChange = { viewModel.setDrawerSwipeTabs(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_flick_close),
            subtitle = stringResource(R.string.settings_drawer_flick_close_desc),
            checked = s.drawerFlickClose,
            onCheckedChange = { viewModel.setDrawerFlickClose(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_corner),
            value = s.drawerCornerRadius.toFloat(),
            valueRange = LauncherSettings.DRAWER_CORNER_AUTO.toFloat()..LauncherSettings.MAX_DRAWER_CORNER_RADIUS.toFloat(),
            onValueChange = { viewModel.setDrawerCornerRadius(it.roundToInt(), screenType) },
            valueDisplay = if (s.drawerCornerRadius < 0) stringResource(R.string.settings_drawer_corner_auto) else "%d dp".format(s.drawerCornerRadius),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_width),
            value = s.drawerWidthFraction,
            valueRange = 0.4f..1.0f,
            onValueChange = { viewModel.setDrawerWidthFraction(it, screenType) },
            valueDisplay = "%d%%".format((s.drawerWidthFraction * 100).roundToInt()),
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_drawer_alignment),
            options = listOf(
                LauncherSettings.DRAWER_ALIGNMENT_LEFT to stringResource(R.string.settings_drawer_align_left),
                LauncherSettings.DRAWER_ALIGNMENT_CENTER to stringResource(R.string.settings_drawer_align_center),
                LauncherSettings.DRAWER_ALIGNMENT_RIGHT to stringResource(R.string.settings_drawer_align_right),
            ),
            selected = s.drawerAlignment,
            onSelect = { viewModel.setDrawerAlignment(it, screenType) },
        )
    }

    // ---- Content & behaviour -------------------------------------------------------------------
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_drawer_content),
        icon = Icons.Rounded.Apps,
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_frequent_apps),
            checked = s.showFrequentApps,
            onCheckedChange = { viewModel.setShowFrequentApps(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_show_labels),
            checked = s.showDrawerLabels,
            onCheckedChange = { viewModel.setShowDrawerLabels(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_two_line_labels),
            checked = s.twoLineDrawerLabels,
            onCheckedChange = { viewModel.setTwoLineDrawerLabels(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_open_top),
            checked = s.drawerOpensAtTop,
            onCheckedChange = { viewModel.setDrawerOpensAtTop(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_hidden_apps),
            description = pluralStringResource(R.plurals.settings_hidden_apps_desc, hiddenCount, hiddenCount),
            onClick = onShowHidden,
            shared = true,
        )
        ExpressiveRowDivider()

        val newFolderName = stringResource(R.string.drawer_folder_default)
        val folderCreatedMsg = stringResource(R.string.settings_drawer_folder_created)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_new_drawer_folder),
            description = stringResource(R.string.settings_new_drawer_folder_desc),
            trailingIcon = Icons.Rounded.Add,
            shared = true,
            onClick = {
                viewModel.createDrawerFolder(newFolderName)
                Toast.makeText(context, folderCreatedMsg, Toast.LENGTH_SHORT).show()
            },
        )
        ExpressiveRowDivider()

        DrawerExtrasRows(s = s, viewModel = viewModel, screenType = screenType)
    }
}

@Composable
private fun drawerThemeLabel(theme: String): String = stringResource(
    when (theme) {
        LauncherSettings.DRAWER_THEME_MATERIAL_YOU -> R.string.settings_drawer_theme_material_you
        LauncherSettings.DRAWER_THEME_DEFAULT_DARK -> R.string.settings_drawer_theme_default_dark
        LauncherSettings.DRAWER_THEME_OBSIDIAN_BLACK -> R.string.settings_drawer_theme_obsidian_black
        LauncherSettings.DRAWER_THEME_LIGHT -> R.string.settings_drawer_theme_light
        LauncherSettings.DRAWER_THEME_ACCENT_TINT -> R.string.settings_drawer_theme_accent_tint
        LauncherSettings.DRAWER_THEME_MONOCHROME -> R.string.settings_drawer_theme_monochrome
        LauncherSettings.DRAWER_THEME_CUSTOM -> R.string.settings_drawer_theme_custom
        else -> R.string.settings_drawer_theme_system
    },
)

@Composable
private fun GesturesSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
    allApps: List<AppItem>,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onLeftSwipe: () -> Unit,
    onRightSwipe: () -> Unit,
) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_gestures),
        icon = Icons.Rounded.Gesture,
        subtitle = stringResource(R.string.settings_category_gestures_desc),
    ) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_swipe_up_action),
            description = gestureLabel(s.swipeUpAction),
            onClick = onSwipeUp,
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_swipe_down_action),
            description = gestureLabel(s.swipeDownAction),
            onClick = onSwipeDown,
        )
        ExpressiveRowDivider()

        val leftSwipeLabel = allApps.firstOrNull { it.key == s.leftSwipeAppKey }?.label
            ?: stringResource(R.string.settings_left_edge_none)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_left_edge),
            description = leftSwipeLabel,
            onClick = onLeftSwipe,
        )
        ExpressiveRowDivider()

        val rightSwipeLabel = allApps.firstOrNull { it.key == s.rightSwipeAppKey }?.label
            ?: stringResource(R.string.settings_right_edge_none)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_right_edge),
            description = rightSwipeLabel,
            onClick = onRightSwipe,
        )
        ExpressiveRowDivider()

        DoubleTapActionRow(s = s, viewModel = viewModel, screenType = screenType)
        ExpressiveRowDivider()

        var pickingMinusOne by remember { mutableStateOf(false) }
        ExpressiveActionItem(
            title = stringResource(R.string.settings_minus_one_title),
            description = when (s.minusOneScreen) {
                LauncherSettings.MINUS_ONE_GOOGLE -> stringResource(R.string.settings_minus_one_google)
                LauncherSettings.MINUS_ONE_SMART_FEED -> stringResource(R.string.settings_minus_one_smart_feed)
                else -> stringResource(R.string.settings_minus_one_none)
            },
            onClick = { pickingMinusOne = true },
        )
        if (pickingMinusOne) {
            ChoicePicker(
                title = R.string.settings_minus_one_title,
                options = listOf(
                    LauncherSettings.MINUS_ONE_NONE to R.string.settings_minus_one_none,
                    LauncherSettings.MINUS_ONE_GOOGLE to R.string.settings_minus_one_google,
                    LauncherSettings.MINUS_ONE_SMART_FEED to R.string.settings_minus_one_smart_feed,
                ),
                selected = s.minusOneScreen,
                onPick = { viewModel.setMinusOneScreen(it); pickingMinusOne = false },
                onDismiss = { pickingMinusOne = false },
            )
        }
        ExpressiveRowDivider()

        IconSwipeRow(s = s, viewModel = viewModel, screenType = screenType)
    }
}

@Composable
private fun EdgeSwipeAppPicker(
    titleRes: Int,
    noneRes: Int,
    apps: List<AppItem>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalExpressivePalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surfaceHi,
        title = { Text(stringResource(titleRes), color = p.text) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(null) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(noneRes), color = Accent, fontSize = 16.sp)
                }
                apps.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app.key) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(appItem = app, labelColor = p.text, showLabel = false, iconSize = 32.dp)
                        Text(app.label, modifier = Modifier.padding(start = 12.dp), color = p.text)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close), color = Accent) }
        },
    )
}

@Composable
private fun AppearanceSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
    iconPacks: List<IconPackInfo>,
    onIconPack: () -> Unit,
    onCountStyle: () -> Unit,
    onPeoplePrivacy: () -> Unit,
    onBatchTimes: () -> Unit,
    onFoldTransition: () -> Unit,
) {
    // Values of the slider being dragged, for the previews (ThemePreview.kt).
    val overrides = remember { PreviewOverrides() }
    CompositionLocalProvider(LocalPreviewOverrides provides overrides) {
        AppearanceSectionContent(
            s = s,
            viewModel = viewModel,
            screenType = screenType,
            iconPacks = iconPacks,
            overrides = overrides,
            onIconPack = onIconPack,
            onCountStyle = onCountStyle,
            onPeoplePrivacy = onPeoplePrivacy,
            onBatchTimes = onBatchTimes,
            onFoldTransition = onFoldTransition,
        )
    }
}

@Composable
private fun AppearanceSectionContent(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType,
    iconPacks: List<IconPackInfo>,
    overrides: PreviewOverrides,
    onIconPack: () -> Unit,
    onCountStyle: () -> Unit,
    onPeoplePrivacy: () -> Unit,
    onBatchTimes: () -> Unit,
    onFoldTransition: () -> Unit,
) {
    // The look of these settings screens, and a small home screen that shows every option below.
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_look),
        icon = Icons.Rounded.Palette,
        subtitle = stringResource(R.string.settings_look_desc),
    ) {
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_look_style),
            options = listOf(
                LauncherSettings.SETTINGS_STYLE_STANDARD to stringResource(R.string.settings_look_standard),
                LauncherSettings.SETTINGS_STYLE_STUDIO to stringResource(R.string.settings_look_studio),
            ),
            selected = s.settingsStyle,
            onSelect = viewModel::setSettingsStyle,
        )
        ExpressiveRowDivider()
        Text(
            text = stringResource(R.string.settings_preview_title),
            color = LocalExpressivePalette.current.dim,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 2.dp),
        )
        HomeThemePreview(s)
    }

    // Presets, menu / stack / dot styling, the home backdrop and related switches (ThemingSettings.kt).
    ThemingExtrasSection(s = s, viewModel = viewModel, screenType = screenType)

    // Motorola's own settings and Motorola Weather (MotorolaSettings.kt; hidden on other phones).
    MotorolaSection(s = s, viewModel = viewModel)

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_animations_fluid_motion),
        icon = Icons.Rounded.AutoAwesome,
        subtitle = stringResource(R.string.settings_category_animations_desc),
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_aquamorphic_touch),
            checked = s.aquamorphicTouchEnabled,
            onCheckedChange = { viewModel.setAquamorphicTouchEnabled(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_app_launch_zoom),
            checked = s.appLaunchZoomEnabled,
            onCheckedChange = { viewModel.setAppLaunchZoomEnabled(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_page_bounce),
            checked = s.pageBounceEnabled,
            onCheckedChange = { viewModel.setPageBounceEnabled(it, screenType) },
        )
        ExpressiveRowDivider()

        AnimationStyleRows(s = s, viewModel = viewModel)
    }

    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_appearance),
        icon = Icons.Rounded.Palette,
        subtitle = stringResource(R.string.settings_category_appearance_desc),
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_amoled_dark),
            checked = s.amoledDark,
            onCheckedChange = { viewModel.setAmoledDark(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_parallax_wallpaper),
            checked = s.parallaxWallpaper,
            onCheckedChange = { viewModel.setParallaxWallpaper(it, screenType) },
        )
        ExpressiveRowDivider()

        // Glass look of home-screen folders (the drawer has its own glass controls).
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_glass_blur_radius),
            value = s.glassBlurRadius,
            valueRange = 0f..50f,
            onValueChange = { viewModel.setGlassBlurRadius(it, screenType) },
            valueDisplay = "%d%%".format((s.glassBlurRadius * 2).roundToInt()),
            onDragValue = { overrides.glassBlur = it },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_glass_dark_tint),
            value = s.glassDarkTint,
            valueRange = 0f..0.9f,
            onValueChange = { viewModel.setGlassDarkTint(it, screenType) },
            valueDisplay = "%d%%".format((s.glassDarkTint * 100).roundToInt()),
            onDragValue = { overrides.glassTint = it },
        )
        GlassBlurPreview(s)
        ExpressiveRowDivider()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_themed_icons),
                checked = s.useThemedIcons,
                onCheckedChange = { viewModel.setUseThemedIcons(it, screenType) },
            )
            ExpressiveRowDivider()
            if (s.useThemedIcons) {
                ExpressiveSegmentedControl(
                    title = stringResource(R.string.settings_themed_style),
                    options = listOf(
                        LauncherSettings.THEMED_STYLE_CLASSIC to stringResource(R.string.settings_themed_style_classic),
                        LauncherSettings.THEMED_STYLE_INVERTED to stringResource(R.string.settings_themed_style_inverted),
                        LauncherSettings.THEMED_STYLE_GLASS to stringResource(R.string.settings_themed_style_glass),
                        LauncherSettings.THEMED_STYLE_DUOTONE to stringResource(R.string.settings_themed_style_duotone),
                    ),
                    selected = s.themedIconStyle,
                    onSelect = viewModel::setThemedIconStyle,
                )
                ExpressiveRowDivider()
                ExpressiveSwitchRow(
                    title = stringResource(R.string.settings_force_themed_icons),
                    subtitle = stringResource(R.string.settings_force_themed_icons_desc),
                    checked = s.forceThemedIcons,
                    onCheckedChange = viewModel::setForceThemedIcons,
                    shared = true,
                )
                ExpressiveRowDivider()
            }
        }

        val iconPackName = iconPacks.firstOrNull { it.packageName == s.iconPackPackage }?.label
            ?: stringResource(R.string.settings_icon_pack_system)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_icon_pack),
            description = iconPackName,
            onClick = onIconPack,
        )
        ExpressiveRowDivider()

        IconShapeRow(selected = s.iconShape, onSelect = viewModel::setIconShape)
        ExpressiveRowDivider()

        WeatherToggle(enabled = s.showWeather, onSetEnabled = { viewModel.setShowWeather(it, screenType) })
        ExpressiveRowDivider()

        StatusBarToggle(enabled = s.showStatusBar, onSetEnabled = { viewModel.setShowStatusBar(it, screenType) })
        if (s.showStatusBar) {
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_status_bar_scrim),
                value = s.statusBarScrimOpacity,
                onValueChange = { viewModel.setStatusBarScrimOpacity(it, screenType) },
                valueDisplay = "%d%%".format((s.statusBarScrimOpacity * 100).toInt()),
            )
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_hide_status_bar),
            checked = s.hideSystemStatusBar,
            onCheckedChange = { viewModel.setHideSystemStatusBar(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_tonal),
            checked = s.widgetTonalBackground,
            onCheckedChange = { viewModel.setWidgetTonalBackground(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_widget_background_opacity),
            value = s.widgetBackgroundOpacity,
            onValueChange = { viewModel.setWidgetBackgroundOpacity((it * 20f).roundToInt() / 20f) },
            valueDisplay = "%d%%".format((s.widgetBackgroundOpacity * 100).roundToInt()),
            subtitle = stringResource(R.string.settings_widget_background_opacity_desc),
            onDragValue = { overrides.widgetOpacity = it },
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_notif_widget_count),
            description = stringResource(
                when (s.notificationWidgetCountStyle) {
                    LauncherSettings.COUNT_DOT -> R.string.settings_notif_widget_count_dot
                    LauncherSettings.COUNT_NONE -> R.string.settings_notif_widget_count_none
                    else -> R.string.settings_notif_widget_count_number
                },
            ),
            onClick = onCountStyle,
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_people_privacy),
            shared = true,
            description = stringResource(
                when (s.peoplePrivacy) {
                    LauncherSettings.PRIVACY_SENDER -> R.string.settings_people_privacy_sender
                    LauncherSettings.PRIVACY_COUNT -> R.string.settings_people_privacy_count
                    else -> R.string.settings_people_privacy_all
                },
            ),
            onClick = onPeoplePrivacy,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_people_show_apps),
            shared = true,
            checked = s.peopleShowApps,
            onCheckedChange = viewModel::setPeopleShowApps,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_people_batch),
            shared = true,
            checked = s.peopleBatchEnabled,
            onCheckedChange = viewModel::setPeopleBatchEnabled,
        )
        if (s.peopleBatchEnabled) {
            ExpressiveRowDivider()
            ExpressiveActionItem(
                title = stringResource(R.string.settings_people_batch_times),
            shared = true,
                description = s.peopleBatchTimes.replace(",", ", "),
                onClick = onBatchTimes,
            )
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_page_indicator),
            checked = s.showPageIndicator,
            onCheckedChange = { viewModel.setShowPageIndicator(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_lock_desktop),
            shared = true,
            checked = s.desktopLocked,
            onCheckedChange = viewModel::setDesktopLocked,
        )
    }
}

@Composable
private fun NotificationsSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
    previewIcon: ImageBitmap?,
) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_notifications),
        icon = Icons.Rounded.Notifications,
        subtitle = stringResource(R.string.settings_category_notifications_desc),
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_notif_dots),
            checked = s.showNotificationDots,
            onCheckedChange = { viewModel.setShowNotificationDots(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_notif_count),
            checked = s.notificationDotCount,
            onCheckedChange = { viewModel.setNotificationDotCount(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_notif_size),
            value = s.notificationDotScale,
            onValueChange = { viewModel.setNotificationDotScale(it, screenType) },
            valueRange = 0.6f..1.8f,
            valueDisplay = "%.1fx".format(s.notificationDotScale),
        )
        ExpressiveRowDivider()

        NotificationDotStyleRows(s = s, viewModel = viewModel, screenType = screenType)
        ExpressiveRowDivider()

        // The preview follows the shape, colour and corner chosen in the rows above.
        CompositionLocalProvider(LocalBadgeStyle provides BadgeStyle(s.dotShape, s.dotColor, s.dotPosition)) {
            BadgePreview(
                icon = previewIcon,
                showDots = s.showNotificationDots,
                showCount = s.notificationDotCount,
                scale = s.notificationDotScale,
            )
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_dot_avatars),
            subtitle = stringResource(R.string.settings_dot_avatars_desc),
            checked = s.dotAvatars,
            onCheckedChange = viewModel::setDotAvatars,
            shared = true,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_popup_notifications),
            subtitle = stringResource(R.string.settings_popup_notifications_desc),
            checked = s.popupNotifications,
            onCheckedChange = viewModel::setPopupNotifications,
            shared = true,
        )
        ExpressiveRowDivider()

        IconNudgeRows(s, viewModel, previewIcon)
        ExpressiveRowDivider()

        LiveActivityRows(s, viewModel)
    }
}

@Composable
private fun PermissionsSection(
    isDefaultLauncher: Boolean,
    notifAccessGranted: Boolean,
    onSetDefault: () -> Unit,
    onNotifAccess: () -> Unit,
) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_permissions),
        icon = Icons.Rounded.Shield,
        subtitle = stringResource(R.string.settings_category_permissions_desc),
        sharedByBothDisplays = true,
    ) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_set_default),
            description = stringResource(
                if (isDefaultLauncher) R.string.settings_default_current else R.string.settings_default_tap,
            ),
            onClick = onSetDefault,
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_notif_access),
            description = stringResource(
                if (notifAccessGranted) R.string.settings_notif_access_granted
                else R.string.settings_notif_access_not_granted,
            ),
            onClick = onNotifAccess,
        )
        ExpressiveRowDivider()

        PermissionRow(
            label = stringResource(R.string.settings_permission_calendar),
            permission = Manifest.permission.READ_CALENDAR,
        )
        ExpressiveRowDivider()

        PermissionRow(
            label = stringResource(R.string.settings_permission_contacts),
            permission = Manifest.permission.READ_CONTACTS,
        )
        ExpressiveRowDivider()

        PermissionRow(
            label = stringResource(R.string.settings_permission_phone),
            permission = Manifest.permission.READ_PHONE_STATE,
        )
        ExpressiveRowDivider()

        PermissionRow(
            label = stringResource(R.string.settings_permission_location),
            permission = Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    SystemControlsSection()
}

/** Installed apps the theme previews draw (home row, folder, dock). */
private const val PREVIEW_APP_COUNT = 12
