package org.arkikeskus.launcher.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arkikeskus.launcher.data.IconPackInfo
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import kotlin.math.roundToInt

// The Appearance section (split out of SettingsScreen.kt).

@Composable
internal fun AppearanceSection(
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
            selected = s.appearance.settingsStyle,
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
            checked = s.motion.aquamorphicTouchEnabled,
            onCheckedChange = { viewModel.setAquamorphicTouchEnabled(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_app_launch_zoom),
            checked = s.motion.appLaunchZoomEnabled,
            onCheckedChange = { viewModel.setAppLaunchZoomEnabled(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_page_bounce),
            checked = s.home.pageBounceEnabled,
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
            checked = s.appearance.amoledDark,
            onCheckedChange = { viewModel.setAmoledDark(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_parallax_wallpaper),
            checked = s.home.parallaxWallpaper,
            onCheckedChange = { viewModel.setParallaxWallpaper(it, screenType) },
        )
        ExpressiveRowDivider()

        // Glass look of home-screen folders (the drawer has its own glass controls).
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_glass_blur_radius),
            value = s.drawer.glassBlurRadius,
            valueRange = 0f..50f,
            onValueChange = { viewModel.setGlassBlurRadius(it, screenType) },
            valueDisplay = "%d%%".format((s.drawer.glassBlurRadius * 2).roundToInt()),
            onDragValue = { overrides.glassBlur = it },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_glass_dark_tint),
            value = s.drawer.glassDarkTint,
            valueRange = 0f..0.9f,
            onValueChange = { viewModel.setGlassDarkTint(it, screenType) },
            valueDisplay = "%d%%".format((s.drawer.glassDarkTint * 100).roundToInt()),
            onDragValue = { overrides.glassTint = it },
        )
        GlassBlurPreview(s)
        ExpressiveRowDivider()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_themed_icons),
                checked = s.icons.useThemedIcons,
                onCheckedChange = { viewModel.setUseThemedIcons(it, screenType) },
            )
            ExpressiveRowDivider()
            if (s.icons.useThemedIcons) {
                ExpressiveSegmentedControl(
                    title = stringResource(R.string.settings_themed_style),
                    options = listOf(
                        LauncherSettings.THEMED_STYLE_CLASSIC to stringResource(R.string.settings_themed_style_classic),
                        LauncherSettings.THEMED_STYLE_INVERTED to stringResource(R.string.settings_themed_style_inverted),
                        LauncherSettings.THEMED_STYLE_GLASS to stringResource(R.string.settings_themed_style_glass),
                        LauncherSettings.THEMED_STYLE_DUOTONE to stringResource(R.string.settings_themed_style_duotone),
                    ),
                    selected = s.icons.themedIconStyle,
                    onSelect = viewModel::setThemedIconStyle,
                )
                ExpressiveRowDivider()
                ExpressiveSwitchRow(
                    title = stringResource(R.string.settings_force_themed_icons),
                    subtitle = stringResource(R.string.settings_force_themed_icons_desc),
                    checked = s.icons.forceThemedIcons,
                    onCheckedChange = viewModel::setForceThemedIcons,
                    shared = true,
                )
                ExpressiveRowDivider()
            }
        }

        val iconPackName = iconPacks.firstOrNull { it.packageName == s.icons.iconPack }?.label
            ?: stringResource(R.string.settings_icon_pack_system)
        ExpressiveActionItem(
            title = stringResource(R.string.settings_icon_pack),
            description = iconPackName,
            onClick = onIconPack,
        )
        ExpressiveRowDivider()

        IconShapeRow(selected = s.icons.shape, onSelect = viewModel::setIconShape)
        ExpressiveRowDivider()

        WeatherToggle(enabled = s.widgets.showWeather, onSetEnabled = { viewModel.setShowWeather(it, screenType) })
        ExpressiveRowDivider()

        StatusBarToggle(enabled = s.statusBar.enabled, onSetEnabled = { viewModel.setShowStatusBar(it, screenType) })
        if (s.statusBar.enabled) {
            ExpressiveRowDivider()
            ExpressiveSliderRow(
                title = stringResource(R.string.settings_status_bar_scrim),
                value = s.statusBar.scrimOpacity,
                onValueChange = { viewModel.setStatusBarScrimOpacity(it, screenType) },
                valueDisplay = "%d%%".format((s.statusBar.scrimOpacity * 100).toInt()),
            )
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_hide_status_bar),
            checked = s.statusBar.hideSystem,
            onCheckedChange = { viewModel.setHideSystemStatusBar(it, screenType) },
        )
        ExpressiveRowDivider()
        StatusBarIconsRow()
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_tonal),
            checked = s.widgets.tonalBackground,
            onCheckedChange = { viewModel.setWidgetTonalBackground(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_widget_background_opacity),
            value = s.widgets.backgroundOpacity,
            onValueChange = { viewModel.setWidgetBackgroundOpacity((it * 20f).roundToInt() / 20f) },
            valueDisplay = "%d%%".format((s.widgets.backgroundOpacity * 100).roundToInt()),
            subtitle = stringResource(R.string.settings_widget_background_opacity_desc),
            onDragValue = { overrides.widgetOpacity = it },
        )
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_notif_widget_count),
            description = stringResource(
                when (s.widgets.notificationCountStyle) {
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
                when (s.widgets.peoplePrivacy) {
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
            checked = s.widgets.peopleShowApps,
            onCheckedChange = viewModel::setPeopleShowApps,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_people_batch),
            shared = true,
            checked = s.widgets.peopleBatchEnabled,
            onCheckedChange = viewModel::setPeopleBatchEnabled,
        )
        if (s.widgets.peopleBatchEnabled) {
            ExpressiveRowDivider()
            ExpressiveActionItem(
                title = stringResource(R.string.settings_people_batch_times),
            shared = true,
                description = s.widgets.peopleBatchTimes.replace(",", ", "),
                onClick = onBatchTimes,
            )
        }
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_page_indicator),
            checked = s.home.showPageIndicator,
            onCheckedChange = { viewModel.setShowPageIndicator(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_lock_desktop),
            shared = true,
            checked = s.home.desktopLocked,
            onCheckedChange = viewModel::setDesktopLocked,
        )
    }
}
