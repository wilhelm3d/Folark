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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import org.arkikeskus.launcher.ui.component.LocalIconPack
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.LocalThemedIcons
import org.arkikeskus.launcher.ui.component.NotificationBadge
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.DarkExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LightExpressivePalette
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import java.util.UUID
import kotlin.math.roundToInt

private enum class SettingsCategory(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
) {
    DISPLAY_PROFILES(
        R.string.settings_display_screen_profiles,
        R.string.settings_category_display_profiles_desc,
        Icons.Rounded.Smartphone,
    ),
    GRID_DPI(
        R.string.settings_grid_dpi,
        R.string.settings_category_grid_dpi_desc,
        Icons.Rounded.GridOn,
    ),
    DOCK_TASKBAR(
        R.string.settings_dock_taskbar,
        R.string.settings_category_dock_taskbar_desc,
        Icons.Rounded.Dock,
    ),
    APP_DRAWER(
        R.string.settings_app_drawer_customization,
        R.string.settings_category_app_drawer_desc,
        Icons.Rounded.Apps,
    ),
    GESTURES(
        R.string.settings_gestures,
        R.string.settings_category_gestures_desc,
        Icons.Rounded.Gesture,
    ),
    APPEARANCE(
        R.string.settings_appearance,
        R.string.settings_category_appearance_desc,
        Icons.Rounded.Palette,
    ),
    NOTIFICATIONS(
        R.string.settings_notifications,
        R.string.settings_category_notifications_desc,
        Icons.Rounded.Notifications,
    ),
    PERMISSIONS(
        R.string.settings_permissions,
        R.string.settings_category_permissions_desc,
        Icons.Rounded.Shield,
    ),
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    screenType: ScreenType = LocalScreenType.current,
) {
    LaunchedEffect(screenType) { viewModel.setScreenType(screenType) }
    CompositionLocalProvider(LocalScreenType provides screenType) {
        val s by viewModel.settings.collectAsStateWithLifecycle()
    val allApps by viewModel.apps.collectAsStateWithLifecycle()
    val hiddenKeys by viewModel.hiddenKeys.collectAsStateWithLifecycle()
    val iconPacks by viewModel.iconPacks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val applySuccessMsg = stringResource(R.string.settings_apply_all_success)
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

    var selectedTabletCategory by rememberSaveable { mutableStateOf(SettingsCategory.DISPLAY_PROFILES) }
    var selectedOuterCategory by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val mainScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    var isDefaultLauncher by remember { mutableStateOf(DefaultLauncher.isDefault(context)) }
    val setDefaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { isDefaultLauncher = DefaultLauncher.isDefault(context) }

    var notifAccessGranted by remember { mutableStateOf(isNotificationAccessGranted(context)) }
    LifecycleResumeEffect(Unit) {
        notifAccessGranted = isNotificationAccessGranted(context)
        onPauseOrDispose { }
    }
    val palette = if (isSystemInDarkTheme()) DarkExpressivePalette else LightExpressivePalette

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
        LocalIconPack provides s.iconPackPackage,
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
                onBack = { showAppPairsManager = false },
                modifier = modifier.fillMaxSize(),
            )
            return@CompositionLocalProvider
        }

        Box(modifier = modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = palette.bg) {
                if (screenType == ScreenType.INNER) {
                    // Tablet Master-Detail Two-Pane Layout when Unfolded (ScreenType.INNER active)
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

                            Spacer(modifier = Modifier.height(16.dp))

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
                                .verticalScroll(mainScrollState)
                                .padding(bottom = 28.dp),
                        ) {
                            when (selectedTabletCategory) {
                                SettingsCategory.DISPLAY_PROFILES -> DisplayProfilesSection(
                                    s = s,
                                    viewModel = viewModel,
                                    screenType = screenType,
                                    onFoldAction = { showFoldActionPicker = true },
                                    onUnfoldAction = { showUnfoldActionPicker = true },
                                    onFoldTransition = { showFoldTransitionPicker = true },
                                    onAppPairs = { showAppPairsManager = true },
                                )
                                SettingsCategory.GRID_DPI -> GridAndDpiSection(s = s, viewModel = viewModel, screenType = screenType)
                                SettingsCategory.DOCK_TASKBAR -> DockAndTaskbarSection(s = s, viewModel = viewModel, screenType = screenType, previewIcon = previewIcon)
                                SettingsCategory.APP_DRAWER -> AppDrawerSection(
                                    s = s,
                                    viewModel = viewModel,
                                    screenType = screenType,
                                    hiddenCount = hiddenKeys.size,
                                    onShowHidden = { showHiddenManager = true },
                                    onLayoutMode = { showDrawerLayoutPicker = true },
                                )
                                SettingsCategory.GESTURES -> GesturesSection(
                                    s = s,
                                    viewModel = viewModel,
                                    screenType = screenType,
                                    allApps = allApps,
                                    onSwipeUp = { showSwipeUpPicker = true },
                                    onSwipeDown = { showSwipeDownPicker = true },
                                    onLeftSwipe = { showLeftSwipePicker = true },
                                    onRightSwipe = { showRightSwipePicker = true },
                                )
                                SettingsCategory.APPEARANCE -> AppearanceSection(
                                    s = s,
                                    viewModel = viewModel,
                                    screenType = screenType,
                                    iconPacks = iconPacks,
                                    onIconPack = { showIconPackPicker = true },
                                    onCountStyle = { showCountStylePicker = true },
                                    onPeoplePrivacy = { showPeoplePrivacyPicker = true },
                                    onBatchTimes = { showBatchTimesEditor = true },
                                    onFoldTransition = { showFoldTransitionPicker = true },
                                )
                                SettingsCategory.NOTIFICATIONS -> NotificationsSection(s = s, viewModel = viewModel, screenType = screenType, previewIcon = previewIcon)
                                SettingsCategory.PERMISSIONS -> PermissionsSection(
                                    isDefaultLauncher = isDefaultLauncher,
                                    notifAccessGranted = notifAccessGranted,
                                    onSetDefault = { runCatching { setDefaultLauncher.launch(DefaultLauncher.requestIntent(context)) } },
                                    onNotifAccess = { openNotificationAccess(context) },
                                )
                            }
                        }
                    }
                } else {
                    // Phone Settings Layout for Folded State (ScreenType.OUTER)
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
                                    .padding(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
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
                                    .verticalScroll(mainScrollState)
                                    .padding(bottom = 80.dp),
                            ) {
                                when (activeCategory) {
                                    SettingsCategory.DISPLAY_PROFILES -> DisplayProfilesSection(
                                        s = s,
                                        viewModel = viewModel,
                                        screenType = screenType,
                                        onFoldAction = { showFoldActionPicker = true },
                                        onUnfoldAction = { showUnfoldActionPicker = true },
                                        onFoldTransition = { showFoldTransitionPicker = true },
                                        onAppPairs = { showAppPairsManager = true },
                                    )
                                    SettingsCategory.GRID_DPI -> GridAndDpiSection(s = s, viewModel = viewModel, screenType = screenType)
                                    SettingsCategory.DOCK_TASKBAR -> DockAndTaskbarSection(s = s, viewModel = viewModel, screenType = screenType, previewIcon = previewIcon)
                                    SettingsCategory.APP_DRAWER -> AppDrawerSection(
                                        s = s,
                                        viewModel = viewModel,
                                        screenType = screenType,
                                        hiddenCount = hiddenKeys.size,
                                        onShowHidden = { showHiddenManager = true },
                                        onLayoutMode = { showDrawerLayoutPicker = true },
                                    )
                                    SettingsCategory.GESTURES -> GesturesSection(
                                        s = s,
                                        viewModel = viewModel,
                                        screenType = screenType,
                                        allApps = allApps,
                                        onSwipeUp = { showSwipeUpPicker = true },
                                        onSwipeDown = { showSwipeDownPicker = true },
                                        onLeftSwipe = { showLeftSwipePicker = true },
                                        onRightSwipe = { showRightSwipePicker = true },
                                    )
                                    SettingsCategory.APPEARANCE -> AppearanceSection(
                                        s = s,
                                        viewModel = viewModel,
                                        screenType = screenType,
                                        iconPacks = iconPacks,
                                        onIconPack = { showIconPackPicker = true },
                                        onCountStyle = { showCountStylePicker = true },
                                        onPeoplePrivacy = { showPeoplePrivacyPicker = true },
                                        onBatchTimes = { showBatchTimesEditor = true },
                                        onFoldTransition = { showFoldTransitionPicker = true },
                                    )
                                    SettingsCategory.NOTIFICATIONS -> NotificationsSection(s = s, viewModel = viewModel, screenType = screenType, previewIcon = previewIcon)
                                    SettingsCategory.PERMISSIONS -> PermissionsSection(
                                        isDefaultLauncher = isDefaultLauncher,
                                        notifAccessGranted = notifAccessGranted,
                                        onSetDefault = { runCatching { setDefaultLauncher.launch(DefaultLauncher.requestIntent(context)) } },
                                        onNotifAccess = { openNotificationAccess(context) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.applyAllSettings {
                        Toast.makeText(context, applySuccessMsg, Toast.LENGTH_SHORT).show()
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Done,
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.settings_apply_all),
                        fontWeight = FontWeight.Bold,
                    )
                },
                containerColor = Accent,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .safeDrawingPadding(),
            )
        }

        // Dialogs & Pickers
        if (showLeftSwipePicker) {
            EdgeSwipeAppPicker(
                titleRes = R.string.settings_left_edge_pick,
                noneRes = R.string.settings_left_edge_none,
                apps = allApps,
                onPick = { key ->
                    viewModel.setLeftSwipeAppKey(key, screenType)
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
                    viewModel.setRightSwipeAppKey(key, screenType)
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
                    viewModel.setIconPack(pkg)
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
                    viewModel.setNotificationWidgetCountStyle(it)
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
                    viewModel.setDrawerLayoutMode(it, screenType)
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
                options = gestureOptions(),
                selected = s.swipeDownAction,
                onPick = { viewModel.setSwipeDownAction(it, screenType); showSwipeDownPicker = false },
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
                onPick = { viewModel.setSwipeUpAction(it, screenType); showSwipeUpPicker = false },
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
                onPick = { viewModel.setFoldAction(it, screenType); showFoldActionPicker = false },
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
                onPick = { viewModel.setUnfoldAction(it, screenType); showUnfoldActionPicker = false },
                onDismiss = { showUnfoldActionPicker = false },
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
                ),
                selected = s.foldTransitionStyle,
                onPick = { viewModel.setFoldTransitionStyle(it, screenType); showFoldTransitionPicker = false },
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
        else -> R.string.settings_gesture_notifications
    }
)

private fun gestureOptions(): List<Pair<String, Int>> = listOf(
    LauncherSettings.GESTURE_NOTIFICATIONS to R.string.settings_gesture_notifications,
    LauncherSettings.GESTURE_DRAWER to R.string.settings_gesture_drawer,
    LauncherSettings.GESTURE_SEARCH to R.string.settings_gesture_search,
    LauncherSettings.GESTURE_LOCK to R.string.settings_gesture_lock,
    LauncherSettings.GESTURE_NONE to R.string.settings_gesture_none,
)

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

private const val LOCK_SERVICE_CLASS = "org.arkikeskus.launcher.feature.home.LockAccessibilityService"

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

@Composable
private fun DoubleTapLockToggle(enabled: Boolean, onSetEnabled: (Boolean) -> Unit) {
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
        title = stringResource(R.string.settings_double_tap_lock),
        checked = enabled,
        onCheckedChange = { wantOn ->
            onSetEnabled(wantOn)
            if (wantOn && !lockServiceEnabled(context)) openAccessibilitySettings()
        },
    )
    if (enabled && !serviceEnabled) {
        ExpressiveActionItem(
            title = stringResource(R.string.settings_double_tap_lock_grant),
            description = stringResource(R.string.settings_double_tap_lock_grant_desc),
            onClick = { openAccessibilitySettings() },
        )
    }
}

@Composable
private fun LabelColorRow(selected: Int, onPick: (Int) -> Unit) {
    val p = LocalExpressivePalette.current
    val swatches = remember {
        listOf(
            0xFFFFFFFF, 0xFF000000, 0xFFBDBDBD, 0xFFEF5350,
            0xFFFFA726, 0xFFFFEE58, 0xFF66BB6A, 0xFF42A5F5, 0xFFAB47BC,
        ).map { it.toInt() }
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.settings_label_color),
            color = p.text,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            swatches.forEach { c ->
                val isSel = c == selected
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            width = if (isSel) 3.dp else 1.dp,
                            color = if (isSel) Accent else p.faint,
                            shape = CircleShape,
                        )
                        .clickable { onPick(c) },
                )
            }
        }
    }
}

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
        contentAlignment = Alignment.TopEnd,
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
private fun transitionLabel(style: String): String = when (style) {
    LauncherSettings.TRANSITION_MORPH_SCALE_FADE -> stringResource(R.string.settings_transition_morph_scale_fade)
    LauncherSettings.TRANSITION_BOOK_UNFOLD_SWEEP -> stringResource(R.string.settings_transition_book_unfold_sweep)
    LauncherSettings.TRANSITION_AQUAMORPHIC_RIPPLE -> stringResource(R.string.settings_transition_aquamorphic_ripple)
    LauncherSettings.TRANSITION_SCALE -> stringResource(R.string.settings_transition_scale)
    LauncherSettings.TRANSITION_SLIDE -> stringResource(R.string.settings_transition_slide)
    else -> stringResource(R.string.settings_transition_crossfade)
}

@Composable
private fun AppPairsManager(
    apps: List<AppItem>,
    appPairs: List<AppPair>,
    onSaveAppPair: (AppPair) -> Unit,
    onDeleteAppPair: (String) -> Unit,
    onLaunchAppPair: (AppPair) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            onConfirm = { name, app1, app2 ->
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
    onDelete: () -> Unit,
) {
    val palette = LocalExpressivePalette.current
    val app1 = apps.firstOrNull { it.key == pair.app1Key }
    val app2 = apps.firstOrNull { it.key == pair.app2Key }

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
                    Text("${app1?.label ?: ""} & ${app2?.label ?: ""}", fontSize = 12.sp, color = palette.faint)
                }
            }
            TextButton(onClick = onDelete) {
                Text("Delete", color = Color.Red)
            }
        }
    }
}

@Composable
private fun AppPairCreatorDialog(
    apps: List<AppItem>,
    onDismiss: () -> Unit,
    onConfirm: (String, AppItem, AppItem) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedApp1 by remember { mutableStateOf<AppItem?>(null) }
    var selectedApp2 by remember { mutableStateOf<AppItem?>(null) }
    var showApp1Picker by remember { mutableStateOf(false) }
    var showApp2Picker by remember { mutableStateOf(false) }

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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && selectedApp1 != null && selectedApp2 != null) {
                        onConfirm(name, selectedApp1!!, selectedApp2!!)
                    }
                },
                enabled = name.isNotBlank() && selectedApp1 != null && selectedApp2 != null,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
}

@Composable
private fun AppPickerDialog(
    apps: List<AppItem>,
    onDismiss: () -> Unit,
    onSelect: (AppItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose App") },
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_display_screen_profiles),
        icon = Icons.Rounded.Smartphone,
        subtitle = stringResource(R.string.settings_category_display_profiles_desc),
    ) {
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

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_allow_landscape),
            checked = s.allowLandscape,
            onCheckedChange = { viewModel.setAllowLandscape(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_fold_action),
            description = foldActionLabel(s.foldAction),
            onClick = onFoldAction,
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_unfold_action),
            description = unfoldActionLabel(s.unfoldAction),
            onClick = onUnfoldAction,
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_fold_transition),
            description = transitionLabel(s.foldTransitionStyle),
            onClick = onFoldTransition,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_half_opened_mode),
            checked = s.halfOpenedModeEnabled,
            onCheckedChange = viewModel::setHalfOpenedModeEnabled,
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_outer_wallpaper_scale),
            value = s.outerWallpaperScale,
            onValueChange = viewModel::setOuterWallpaperScale,
            valueRange = 1f..2.5f,
            valueDisplay = "%.1fx".format(s.outerWallpaperScale),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_inner_wallpaper_scale),
            value = s.innerWallpaperScale,
            onValueChange = viewModel::setInnerWallpaperScale,
            valueRange = 1f..2.5f,
            valueDisplay = "%.1fx".format(s.innerWallpaperScale),
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_app_pairs),
            description = stringResource(R.string.settings_app_pairs_desc),
            onClick = onAppPairs,
        )
    }
}

@Composable
private fun GridAndDpiSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
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

        ExpressiveStepperRow(
            title = stringResource(R.string.settings_columns) + " (" + stringResource(R.string.settings_drawer) + ")",
            value = s.drawerColumns,
            min = 3,
            max = 8,
            onValueChange = { viewModel.setDrawerColumns(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveStepperRow(
            title = stringResource(R.string.settings_rows) + " (" + stringResource(R.string.settings_drawer) + ")",
            value = s.drawerRows,
            min = 3,
            max = 8,
            onValueChange = { viewModel.setDrawerRows(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_workspace_density),
            options = listOf(
                LauncherSettings.DENSITY_COMPACT to stringResource(R.string.settings_density_compact),
                LauncherSettings.DENSITY_NORMAL to stringResource(R.string.settings_density_normal),
                LauncherSettings.DENSITY_SPACIOUS to stringResource(R.string.settings_density_spacious),
            ),
            selected = s.workspaceDensity,
            onSelect = { viewModel.setWorkspaceDensity(it, screenType) },
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

        LabelColorRow(selected = s.appLabelColor, onPick = { viewModel.setAppLabelColor(it, screenType) })
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
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_inner_taskbar),
            checked = s.innerTaskbarEnabled,
            onCheckedChange = { viewModel.setInnerTaskbarEnabled(it, screenType) },
        )
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

@Composable
private fun AppDrawerSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
    hiddenCount: Int,
    onShowHidden: () -> Unit,
    onLayoutMode: () -> Unit,
) {
    val context = LocalContext.current
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_app_drawer_customization),
        icon = Icons.Rounded.Apps,
        subtitle = stringResource(R.string.settings_category_app_drawer_desc),
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
            title = stringResource(R.string.settings_columns) + " (" + stringResource(R.string.settings_drawer) + ")",
            value = s.drawerColumns,
            min = 3,
            max = 8,
            onValueChange = { viewModel.setDrawerColumns(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveStepperRow(
            title = stringResource(R.string.settings_rows) + " (" + stringResource(R.string.settings_drawer) + ")",
            value = s.drawerRows,
            min = 3,
            max = 8,
            onValueChange = { viewModel.setDrawerRows(it, screenType) },
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
        }
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_blur),
            value = s.drawerBackgroundBlur / 25f,
            onValueChange = { viewModel.setDrawerBackgroundBlur(it * 25f, screenType) },
            valueDisplay = "%d%%".format((s.drawerBackgroundBlur / 25f * 100).toInt()),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_scrim),
            value = s.drawerScrimOpacity,
            onValueChange = { viewModel.setDrawerScrimOpacity(it, screenType) },
            valueDisplay = "%d%%".format((s.drawerScrimOpacity * 100).toInt()),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_glass_blur_radius),
            value = s.glassBlurRadius,
            valueRange = 0f..50f,
            onValueChange = { viewModel.setGlassBlurRadius(it, screenType) },
            valueDisplay = "%ddp".format(s.glassBlurRadius.roundToInt()),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_glass_dark_tint),
            value = s.glassDarkTint,
            valueRange = 0f..0.9f,
            onValueChange = { viewModel.setGlassDarkTint(it, screenType) },
            valueDisplay = "%d%%".format((s.glassDarkTint * 100).roundToInt()),
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_drawer_styles_and_themes),
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
            options = listOf(
                LauncherSettings.DRAWER_THEME_DEFAULT_DARK to stringResource(R.string.settings_drawer_theme_default_dark),
                LauncherSettings.DRAWER_THEME_OBSIDIAN_BLACK to stringResource(R.string.settings_drawer_theme_obsidian_black),
                LauncherSettings.DRAWER_THEME_ACCENT_TINT to stringResource(R.string.settings_drawer_theme_accent_tint),
                LauncherSettings.DRAWER_THEME_MONOCHROME to stringResource(R.string.settings_drawer_theme_monochrome),
            ),
            selected = s.drawerThemeTint,
            onSelect = { viewModel.setDrawerThemeTint(it, screenType) },
        )
        ExpressiveRowDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_drawer_accent_color),
                color = LocalExpressivePalette.current.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val presets = listOf(
                    0xFF00BCD4.toInt() to stringResource(R.string.settings_drawer_accent_teal_cyan),
                    0xFF00B0FF.toInt() to stringResource(R.string.settings_drawer_accent_electric_blue),
                    0xFF00C853.toInt() to stringResource(R.string.settings_drawer_accent_emerald_green),
                    0xFFAA00FF.toInt() to stringResource(R.string.settings_drawer_accent_purple),
                    0xFFFF1744.toInt() to stringResource(R.string.settings_drawer_accent_rose),
                    0xFFFFAB00.toInt() to stringResource(R.string.settings_drawer_accent_gold),
                )
                for ((colorInt, label) in presets) {
                    val selected = s.drawerAccentColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(colorInt))
                            .clickable { viewModel.setDrawerAccentColor(colorInt, screenType) }
                            .then(
                                if (selected) Modifier.border(3.dp, LocalExpressivePalette.current.text, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_height),
            value = s.drawerHeightFraction,
            valueRange = 0.4f..1.0f,
            onValueChange = { viewModel.setDrawerHeightFraction(it, screenType) },
            valueDisplay = "%d%%".format((s.drawerHeightFraction * 100).toInt()),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_width),
            value = s.drawerWidthFraction,
            valueRange = 0.4f..1.0f,
            onValueChange = { viewModel.setDrawerWidthFraction(it, screenType) },
            valueDisplay = "%d%%".format((s.drawerWidthFraction * 100).toInt()),
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
        ExpressiveRowDivider()

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
        )
        ExpressiveRowDivider()

        val newFolderName = stringResource(R.string.drawer_folder_default)
        val folderCreatedMsg = stringResource(R.string.settings_drawer_folder_created)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_new_drawer_folder),
            description = stringResource(R.string.settings_new_drawer_folder_desc),
            trailingIcon = Icons.Rounded.Add,
            onClick = {
                viewModel.createDrawerFolder(newFolderName)
                Toast.makeText(context, folderCreatedMsg, Toast.LENGTH_SHORT).show()
            },
        )
    }
}

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

        DoubleTapLockToggle(enabled = s.doubleTapToLock, onSetEnabled = { viewModel.setDoubleTapToLock(it, screenType) })
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

        ExpressiveActionItem(
            title = stringResource(R.string.settings_fold_transition),
            description = transitionLabel(s.foldTransitionStyle),
            onClick = onFoldTransition,
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_themed_icons),
                checked = s.useThemedIcons,
                onCheckedChange = viewModel::setUseThemedIcons,
            )
            ExpressiveRowDivider()
        }

        val iconPackName = iconPacks.firstOrNull { it.packageName == s.iconPackPackage }?.label
            ?: stringResource(R.string.settings_icon_pack_system)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_icon_pack),
            description = iconPackName,
            onClick = onIconPack,
        )
        ExpressiveRowDivider()

        WeatherToggle(enabled = s.showWeather, onSetEnabled = viewModel::setShowWeather)
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
            onCheckedChange = viewModel::setWidgetTonalBackground,
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
            checked = s.peopleShowApps,
            onCheckedChange = viewModel::setPeopleShowApps,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_people_batch),
            checked = s.peopleBatchEnabled,
            onCheckedChange = viewModel::setPeopleBatchEnabled,
        )
        if (s.peopleBatchEnabled) {
            ExpressiveRowDivider()
            ExpressiveActionItem(
                title = stringResource(R.string.settings_people_batch_times),
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
            onCheckedChange = viewModel::setShowNotificationDots,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_notif_count),
            checked = s.notificationDotCount,
            onCheckedChange = viewModel::setNotificationDotCount,
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_notif_size),
            value = s.notificationDotScale,
            onValueChange = viewModel::setNotificationDotScale,
            valueRange = 0.6f..1.8f,
            valueDisplay = "%.1fx".format(s.notificationDotScale),
        )
        ExpressiveRowDivider()

        BadgePreview(
            icon = previewIcon,
            showDots = s.showNotificationDots,
            showCount = s.notificationDotCount,
            scale = s.notificationDotScale,
        )
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
}
