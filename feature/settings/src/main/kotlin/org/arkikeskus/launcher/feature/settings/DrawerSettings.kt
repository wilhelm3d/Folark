package org.arkikeskus.launcher.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.model.AzPosition
import org.arkikeskus.launcher.model.DrawerAlignment
import org.arkikeskus.launcher.model.DrawerHandle
import org.arkikeskus.launcher.model.DrawerLayout
import org.arkikeskus.launcher.model.DrawerStyle
import org.arkikeskus.launcher.model.DrawerTheme
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.SearchPosition
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.component.supportsWindowBlur
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import kotlin.math.roundToInt
import org.arkikeskus.launcher.data.createDrawerFolder

// The App drawer section and its colour presets (split out of SettingsScreen.kt).

internal val LABEL_COLOR_PRESETS = listOf(
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
internal fun AppDrawerSection(
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
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_drawer_style),
            options = listOf(
                DrawerStyle.STANDARD_GRID to stringResource(R.string.settings_drawer_style_standard_grid),
                DrawerStyle.ONE_UI to stringResource(R.string.settings_drawer_style_one_ui),
                DrawerStyle.NOTHING_OS to stringResource(R.string.settings_drawer_style_nothing_os),
                DrawerStyle.MOTO to stringResource(R.string.settings_drawer_style_moto),
            ),
            selected = s.drawer.style,
            onSelect = { viewModel.setDrawerStyle(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_drawer_theme_tint),
            options = DrawerTheme.entries.map { it to drawerThemeLabel(it) },
            selected = s.drawer.themeTint,
            onSelect = { theme ->
                // "Custom" needs a colour first: open the picker, which also selects the theme.
                if (theme == DrawerTheme.CUSTOM && s.drawer.themeTint != theme) {
                    viewModel.setDrawerThemeTint(theme, screenType)
                    onPickColor(ColorTarget.DRAWER_BACKGROUND)
                } else {
                    viewModel.setDrawerThemeTint(theme, screenType)
                }
            },
        )
        if (s.drawer.themeTint == DrawerTheme.CUSTOM) {
            ExpressiveRowDivider()
            ColorSwatchRow(
                title = stringResource(R.string.settings_drawer_custom_color),
                presets = DRAWER_BACKGROUND_PRESETS,
                selected = s.drawer.customColor,
                onPick = { viewModel.setDrawerCustomColor(it, screenType) },
                onCustom = { onPickColor(ColorTarget.DRAWER_BACKGROUND) },
            )
        }
        // Monochrome and Material You bring their own accent, so the accent row would do nothing.
        val themeOwnsAccent = s.drawer.themeTint == DrawerTheme.MONOCHROME ||
            s.drawer.themeTint == DrawerTheme.MATERIAL_YOU
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
                selected = s.drawer.accentColor,
                onPick = { viewModel.setDrawerAccentColor(it, screenType) },
                onCustom = { onPickColor(ColorTarget.DRAWER_ACCENT) },
            )
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_glass),
            subtitle = stringResource(R.string.settings_drawer_glass_desc),
            checked = s.drawer.glassEnabled,
            onCheckedChange = { viewModel.setDrawerGlassEnabled(it, screenType) },
        )
        if (s.drawer.glassEnabled) {
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_drawer_panel_opacity),
                value = s.drawer.panelOpacity,
                valueRange = LauncherSettings.MIN_DRAWER_PANEL_OPACITY..LauncherSettings.MAX_DRAWER_PANEL_OPACITY,
                onValueChange = { viewModel.setDrawerPanelOpacity(it, screenType) },
                valueDisplay = "%d%%".format((s.drawer.panelOpacity * 100).roundToInt()),
            )
        }
        ExpressiveRowDivider()

        val blurSupported = remember(context) { supportsWindowBlur(context) }
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_blur),
            value = s.drawer.backgroundBlur / LauncherSettings.MAX_DRAWER_BLUR,
            onValueChange = { viewModel.setDrawerBackgroundBlur(it * LauncherSettings.MAX_DRAWER_BLUR, screenType) },
            valueDisplay = "%d%%".format((s.drawer.backgroundBlur / LauncherSettings.MAX_DRAWER_BLUR * 100).roundToInt()),
            subtitle = if (blurSupported) null else stringResource(R.string.settings_drawer_blur_unsupported),
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_drawer_scrim),
            value = s.drawer.scrimOpacity,
            onValueChange = { viewModel.setDrawerScrimOpacity(it, screenType) },
            valueDisplay = "%d%%".format((s.drawer.scrimOpacity * 100).roundToInt()),
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
                when (s.drawer.layoutMode) {
                    DrawerLayout.LIST -> R.string.settings_drawer_layout_list
                    else -> R.string.settings_drawer_layout_grid
                }
            ),
            onClick = onLayoutMode,
        )
        ExpressiveRowDivider()
        ExpressiveStepperRow(
            title = stringResource(R.string.settings_columns),
            value = s.drawer.columns,
            min = 3,
            max = 8,
            onValueChange = { viewModel.setDrawerColumns(it, screenType) },
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_index_bar),
            checked = s.drawer.indexBarEnabled,
            onCheckedChange = { viewModel.setDrawerIndexBarEnabled(it, screenType) },
        )
        if (s.drawer.indexBarEnabled) {
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_az_index_position),
                options = listOf(
                    AzPosition.VERTICAL_RIGHT to stringResource(R.string.settings_az_position_vertical_right),
                    AzPosition.VERTICAL_LEFT to stringResource(R.string.settings_az_position_vertical_left),
                    AzPosition.HORIZONTAL_TOP to stringResource(R.string.settings_az_position_horizontal_top),
                    AzPosition.HORIZONTAL_BOTTOM to stringResource(R.string.settings_az_position_horizontal_bottom),
                ),
                selected = s.drawer.azIndexPosition,
                onSelect = { viewModel.setAzIndexPosition(it, screenType) },
            )
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_az_index_size),
                value = s.drawer.azIndexScale,
                valueRange = LauncherSettings.MIN_AZ_INDEX_SCALE..LauncherSettings.MAX_AZ_INDEX_SCALE,
                onValueChange = { viewModel.setAzIndexScale((it * 20f).roundToInt() / 20f, screenType) },
                valueDisplay = "%d%%".format((s.drawer.azIndexScale * 100).roundToInt()),
            )
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_drawer_az_hold_scroll),
                subtitle = stringResource(R.string.settings_drawer_az_hold_scroll_desc),
                checked = s.drawer.azHoldScroll,
                onCheckedChange = viewModel::setDrawerAzHoldScroll,
                shared = true,
            )
        }
        // A sidebar drawer (inner display) is always full height, so the height slider is moot there.
        val sidebar = screenType == ScreenType.INNER && s.drawer.innerSidebar
        if (!sidebar) {
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_drawer_height),
                value = s.drawer.heightFraction,
                valueRange = 0.4f..1.0f,
                onValueChange = { viewModel.setDrawerHeightFraction(it, screenType) },
                valueDisplay = "%d%%".format((s.drawer.heightFraction * 100).roundToInt()),
            )
        }
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_letter_headers),
            checked = s.drawer.letterHeaders,
            onCheckedChange = { viewModel.setDrawerLetterHeaders(it, screenType) },
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_swipe_tabs),
            subtitle = stringResource(R.string.settings_drawer_swipe_tabs_desc),
            checked = s.drawer.swipeTabs,
            onCheckedChange = { viewModel.setDrawerSwipeTabs(it, screenType) },
        )
        SettingsAdvanced {
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_drawer_handle),
                options = listOf(
                    DrawerHandle.BAR to stringResource(R.string.settings_drawer_handle_bar),
                    DrawerHandle.ARROW to stringResource(R.string.settings_drawer_handle_arrow),
                ),
                selected = s.drawer.handleStyle,
                onSelect = { viewModel.setDrawerHandleStyle(it, screenType) },
            )
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_drawer_bottom_space),
                subtitle = stringResource(R.string.settings_drawer_bottom_space_desc),
                value = s.drawer.bottomSpace.toFloat(),
                valueRange = 0f..LauncherSettings.MAX_DRAWER_BOTTOM_SPACE.toFloat(),
                onValueChange = { viewModel.setDrawerBottomSpace((it / 4f).roundToInt() * 4, screenType) },
                valueDisplay = "%d dp".format(s.drawer.bottomSpace),
            )
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_drawer_letter_popup),
                subtitle = stringResource(R.string.settings_drawer_letter_popup_desc),
                checked = s.drawer.largeLetterPopup,
                onCheckedChange = { viewModel.setDrawerLargeLetterPopup(it, screenType) },
            )
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_drawer_flick_close),
                subtitle = stringResource(R.string.settings_drawer_flick_close_desc),
                checked = s.drawer.flickClose,
                onCheckedChange = { viewModel.setDrawerFlickClose(it, screenType) },
            )
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_drawer_corner),
                value = s.drawer.cornerRadius.toFloat(),
                valueRange = LauncherSettings.DRAWER_CORNER_AUTO.toFloat()..LauncherSettings.MAX_DRAWER_CORNER_RADIUS.toFloat(),
                onValueChange = { viewModel.setDrawerCornerRadius(it.roundToInt(), screenType) },
                valueDisplay = if (s.drawer.cornerRadius < 0) stringResource(R.string.settings_drawer_corner_auto) else "%d dp".format(s.drawer.cornerRadius),
            )
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_drawer_width),
                value = s.drawer.widthFraction,
                valueRange = 0.4f..1.0f,
                onValueChange = { viewModel.setDrawerWidthFraction(it, screenType) },
                valueDisplay = "%d%%".format((s.drawer.widthFraction * 100).roundToInt()),
            )
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_drawer_alignment),
                options = listOf(
                    DrawerAlignment.LEFT to stringResource(R.string.settings_drawer_align_left),
                    DrawerAlignment.CENTER to stringResource(R.string.settings_drawer_align_center),
                    DrawerAlignment.RIGHT to stringResource(R.string.settings_drawer_align_right),
                ),
                selected = s.drawer.alignment,
                onSelect = { viewModel.setDrawerAlignment(it, screenType) },
            )
        }
    }

    // ---- Search ------------------------------------------------------------------------------
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_drawer_search_group),
        icon = Icons.Rounded.Search,
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_search),
            checked = s.drawer.showSearch,
            onCheckedChange = { viewModel.setShowDrawerSearch(it, screenType) },
        )
        if (s.drawer.showSearch) {
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_drawer_search_position),
                options = listOf(
                    SearchPosition.TOP to stringResource(R.string.settings_drawer_search_top),
                    SearchPosition.BOTTOM to stringResource(R.string.settings_drawer_search_bottom),
                ),
                selected = s.drawer.searchPosition,
                onSelect = { viewModel.setDrawerSearchPosition(it, screenType) },
            )
            ExpressiveRowDivider()
            ContactSearchRow(checked = s.search.contacts, onCheckedChange = viewModel::setSearchContacts)
            ExpressiveRowDivider()
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_search_actions),
                subtitle = stringResource(R.string.settings_search_actions_desc),
                checked = s.search.actions,
                onCheckedChange = viewModel::setSearchActions,
                shared = true,
            )
        }
        DrawerSearchRows(s, viewModel)
    }

    // ---- Content & behaviour -------------------------------------------------------------------
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_drawer_content),
        icon = Icons.Rounded.Apps,
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_frequent_apps),
            checked = s.drawer.showFrequentApps,
            onCheckedChange = { viewModel.setShowFrequentApps(it, screenType) },
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_clipboard),
            subtitle = stringResource(R.string.settings_drawer_clipboard_desc),
            checked = s.drawer.clipboardRow,
            onCheckedChange = { viewModel.setDrawerClipboard(it, screenType) },
        )
        ExpressiveRowDivider()
        // The history behind the clipboard row, the clipboard widget and search.
        val keepMinutes by viewModel.clipboardKeepMinutes.collectAsStateWithLifecycle()
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_clipboard_keep),
            options = listOf(
                0 to stringResource(R.string.settings_clipboard_keep_always),
                60 to stringResource(R.string.settings_clipboard_keep_hour),
                1_440 to stringResource(R.string.settings_clipboard_keep_day),
                10_080 to stringResource(R.string.settings_clipboard_keep_week),
            ),
            selected = keepMinutes,
            onSelect = { viewModel.setClipboardKeepMinutes(it) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_show_labels),
            checked = s.drawer.showLabels,
            onCheckedChange = { viewModel.setShowDrawerLabels(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_two_line_labels),
            checked = s.drawer.twoLineLabels,
            onCheckedChange = { viewModel.setTwoLineDrawerLabels(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_open_top),
            checked = s.drawer.opensAtTop,
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
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_hidden_lock),
            subtitle = stringResource(R.string.settings_hidden_lock_desc),
            checked = s.drawer.hiddenLock,
            onCheckedChange = viewModel::setHiddenAppsLock,
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
private fun drawerThemeLabel(theme: DrawerTheme): String = stringResource(
    when (theme) {
        DrawerTheme.MATERIAL_YOU -> R.string.settings_drawer_theme_material_you
        DrawerTheme.DEFAULT_DARK -> R.string.settings_drawer_theme_default_dark
        DrawerTheme.OBSIDIAN_BLACK -> R.string.settings_drawer_theme_obsidian_black
        DrawerTheme.LIGHT -> R.string.settings_drawer_theme_light
        DrawerTheme.ACCENT_TINT -> R.string.settings_drawer_theme_accent_tint
        DrawerTheme.MONOCHROME -> R.string.settings_drawer_theme_monochrome
        DrawerTheme.CUSTOM -> R.string.settings_drawer_theme_custom
        else -> R.string.settings_drawer_theme_system
    },
)
