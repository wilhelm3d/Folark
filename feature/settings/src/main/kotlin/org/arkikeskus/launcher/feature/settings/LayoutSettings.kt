package org.arkikeskus.launcher.feature.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.badgeAlignment
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.NotificationBadge
import org.arkikeskus.launcher.ui.expressive.Accent
import kotlin.math.roundToInt

// Display profiles, Grid & DPI, and Dock & taskbar sections, with their small previews (split out of SettingsScreen.kt).

@Composable
internal fun rememberLauncherIconBitmap(): ImageBitmap? {
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
internal fun BadgePreview(icon: ImageBitmap?, showDots: Boolean, showCount: Boolean, scale: Float) {
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
internal fun DisplayProfilesSection(
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
                checked = s.home.innerDualPage,
                onCheckedChange = { viewModel.setInnerDualPageWorkspace(it, screenType) },
            )
            ExpressiveRowDivider()

            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_inner_sidebar_drawer),
                subtitle = stringResource(R.string.settings_inner_sidebar_drawer_desc),
                checked = s.drawer.innerSidebar,
                onCheckedChange = { viewModel.setInnerSidebarAppDrawer(it, screenType) },
            )
            ExpressiveRowDivider()

            ExpressiveActionItem(
                title = stringResource(R.string.settings_unfold_action),
                description = unfoldActionLabel(s.fold.unfoldAction),
                onClick = onUnfoldAction,
            )
            ExpressiveRowDivider()
        } else {
            ExpressiveActionItem(
                title = stringResource(R.string.settings_fold_action),
                description = foldActionLabel(s.fold.foldAction),
                onClick = onFoldAction,
            )
            ExpressiveRowDivider()
        }

        ExpressiveActionItem(
            title = stringResource(R.string.settings_fold_transition),
            description = transitionLabel(s.fold.transitionStyle, inner),
            onClick = onFoldTransition,
        )
        ExpressiveRowDivider()
        TransitionSpeedRow(s = s, viewModel = viewModel, screenType = screenType)
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_unlock_transition),
            subtitle = stringResource(R.string.settings_unlock_transition_desc),
            checked = s.motion.unlockTransition,
            onCheckedChange = viewModel::setUnlockTransition,
            shared = true,
        )
        if (s.motion.unlockTransition) {
            ExpressiveRowDivider()
            UnlockStyleRow(s = s, viewModel = viewModel)
            ExpressiveRowDivider()
            UnlockSyncRow(s = s, viewModel = viewModel)
        }
        if (s.fold.transitionStyle == LauncherSettings.TRANSITION_LIVE_HINGE) {
            LiveHingeRows(s = s, viewModel = viewModel, inner = inner)
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_allow_landscape),
            checked = s.home.allowLandscape,
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
            checked = s.fold.halfOpenedModeEnabled,
            onCheckedChange = viewModel::setHalfOpenedModeEnabled,
        )
        ExpressiveRowDivider()

        if (s.fold.halfOpenedModeEnabled && hasHingeSensor(LocalContext.current)) {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_hinge_tilt),
                subtitle = stringResource(R.string.settings_hinge_tilt_desc),
                checked = s.fold.hingeTilt,
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
            checked = s.launch.splitEnabled,
            onCheckedChange = viewModel::setSplitLaunchEnabled,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_floating_launch),
            subtitle = stringResource(R.string.settings_floating_launch_desc),
            checked = s.launch.floatingEnabled,
            onCheckedChange = viewModel::setFloatingLaunchEnabled,
        )
        ExpressiveRowDivider()

        FloatingSizeRow(s = s, viewModel = viewModel)
        MotoPopupGestureRow()
    }
}

@Composable
internal fun GridAndDpiSection(
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
            value = s.home.columns,
            min = 3,
            max = 8,
            onValueChange = { viewModel.setHomeColumns(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveStepperRow(
            title = stringResource(R.string.settings_rows) + " (" + stringResource(R.string.settings_home) + ")",
            value = s.home.rows,
            min = 4,
            max = 8,
            onValueChange = { viewModel.setHomeRows(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_show_labels),
            checked = s.home.showLabels,
            onCheckedChange = { viewModel.setShowHomeLabels(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_two_line_labels),
            checked = s.home.twoLineLabels,
            onCheckedChange = { viewModel.setTwoLineHomeLabels(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_label_size),
            value = s.icons.labelTextScale,
            onValueChange = { viewModel.setAppLabelTextScale(it, screenType) },
            valueRange = 0.8f..1.6f,
            valueDisplay = "%.1fx".format(s.icons.labelTextScale),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_icon_size),
            value = s.icons.sizeScale,
            onValueChange = { viewModel.setIconSizeScale(it, screenType) },
            valueRange = LauncherSettings.MIN_ICON_SIZE_SCALE..LauncherSettings.MAX_ICON_SIZE_SCALE,
            valueDisplay = "%d%%".format((s.icons.sizeScale * 100).roundToInt()),
            subtitle = stringResource(R.string.settings_icon_size_desc),
        )
        ExpressiveRowDivider()

        AutoRemovePagesRow(s = s, viewModel = viewModel, screenType = screenType)
        ExpressiveRowDivider()

        ColorSwatchRow(
            title = stringResource(R.string.settings_label_color),
            presets = LABEL_COLOR_PRESETS,
            selected = s.icons.labelColor,
            onPick = { viewModel.setAppLabelColor(it, screenType) },
            onCustom = { onPickColor(ColorTarget.APP_LABEL) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_dpi_workspace),
            value = s.appearance.dpiWorkspace,
            onValueChange = { viewModel.setDpiWorkspace(it, screenType) },
            valueRange = LauncherSettings.MIN_DPI_SCALE..LauncherSettings.MAX_DPI_SCALE,
            valueDisplay = "%d%%".format((s.appearance.dpiWorkspace * 100).roundToInt()),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_dpi_app_drawer),
            value = s.appearance.dpiAppDrawer,
            onValueChange = { viewModel.setDpiAppDrawer(it, screenType) },
            valueRange = LauncherSettings.MIN_DPI_SCALE..LauncherSettings.MAX_DPI_SCALE,
            valueDisplay = "%d%%".format((s.appearance.dpiAppDrawer * 100).roundToInt()),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_dpi_settings),
            value = s.appearance.dpiSettings,
            onValueChange = { viewModel.setDpiSettings(it, screenType) },
            valueRange = LauncherSettings.MIN_DPI_SCALE..LauncherSettings.MAX_DPI_SCALE,
            valueDisplay = "%d%%".format((s.appearance.dpiSettings * 100).roundToInt()),
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
            selected = s.folders.theme,
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
            selected = s.folders.shape,
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
            selected = s.folders.preview,
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
            selected = s.folders.openSize,
            onSelect = { viewModel.setFolderOpenSize(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveStepperRow(
            title = stringResource(R.string.settings_folder_open_columns),
            value = s.folders.openColumns,
            min = LauncherSettings.MIN_FOLDER_OPEN_COLUMNS,
            max = LauncherSettings.MAX_FOLDER_OPEN_COLUMNS,
            onValueChange = { viewModel.setFolderOpenColumns(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_folder_tap_first),
            subtitle = stringResource(R.string.settings_folder_tap_first_desc),
            checked = s.folders.tapFirstApp,
            onCheckedChange = { viewModel.setFolderTapFirstApp(it, screenType) },
        )
    }
}

@Composable
internal fun DockAndTaskbarSection(
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
            checked = s.dock.enabled,
            onCheckedChange = { viewModel.setDockEnabled(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveStepperRow(
            title = stringResource(R.string.settings_dock_icons),
            value = s.dock.columns,
            min = 3,
            max = 8,
            onValueChange = { viewModel.setDockColumns(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_show_labels),
            checked = s.dock.showLabels,
            onCheckedChange = { viewModel.setShowDockLabels(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_dock_bg),
            value = s.dock.backgroundOpacity,
            onValueChange = { viewModel.setDockBackgroundOpacity(it, screenType) },
            valueDisplay = "%d%%".format((s.dock.backgroundOpacity * 100).toInt()),
        )
        ExpressiveRowDivider()

        DockPreview(opacity = s.dock.backgroundOpacity, icon = previewIcon)
        // Dock alignment only exists on the wide inner screen; the outer screen's dock is full width.
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
                selected = s.dock.innerAlignment,
                onSelect = { viewModel.setInnerDockAlignment(it, screenType) },
            )
        }
    }
}

/** Installed apps the theme previews draw (home row, folder, dock). */
internal const val PREVIEW_APP_COUNT = 12
