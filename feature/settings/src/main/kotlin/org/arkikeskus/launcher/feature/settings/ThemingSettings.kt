package org.arkikeskus.launcher.feature.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.MotoLinks
import org.arkikeskus.launcher.data.smartspace.WeatherPlace
import org.arkikeskus.launcher.data.smartspace.WeatherRepository
import javax.inject.Inject
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.theme.LauncherFontFamily
import java.io.File
import kotlin.math.roundToInt

/**
 * The theming cards of the Appearance page: one-tap presets, the look of menus and widget stacks,
 * the home backdrop and Folark's own widgets. Settings that are not about looks live as rows on
 * the page they belong to (see the *Row / *Rows functions below).
 */
@Composable
internal fun ThemingExtrasSection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presetApplied = stringResource(R.string.settings_theme_preset_applied)
    val overrides = LocalPreviewOverrides.current

    // ---- Liquid Glass everywhere: one switch and one level -----------------------------------------
    LiquidGlassEverywhereSection(s, viewModel)

    // ---- Presets: each sets the drawer, folders, menus, dots, stack indicator and dock at once ----
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_theme_presets),
        icon = Icons.Rounded.AutoAwesome,
        subtitle = stringResource(R.string.settings_theme_presets_desc),
    ) {
        listOf(
            Triple(LauncherSettings.THEME_PRESET_GLASS, R.string.settings_theme_preset_glass, R.string.settings_theme_preset_glass_desc),
            Triple(LauncherSettings.THEME_PRESET_DARK, R.string.settings_theme_preset_dark, R.string.settings_theme_preset_dark_desc),
            Triple(LauncherSettings.THEME_PRESET_LIGHT, R.string.settings_theme_preset_light, R.string.settings_theme_preset_light_desc),
            Triple(LauncherSettings.THEME_PRESET_MONO, R.string.settings_theme_preset_mono, R.string.settings_theme_preset_mono_desc),
        ).forEachIndexed { index, (preset, title, description) ->
            if (index > 0) ExpressiveRowDivider()
            ExpressiveActionItem(
                title = stringResource(title),
                description = stringResource(description),
                onClick = {
                    viewModel.applyThemePreset(preset, screenType)
                    Toast.makeText(context, presetApplied, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    // ---- Menus, widget stacks and notification dots ----------------------------------------------
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_theme_details),
        icon = Icons.Rounded.Palette,
        subtitle = stringResource(R.string.settings_theme_details_desc),
    ) {
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_popup_theme),
            options = listOf(
                LauncherSettings.POPUP_THEME_CLASSIC to stringResource(R.string.settings_popup_theme_classic),
                LauncherSettings.POPUP_THEME_MATERIAL to stringResource(R.string.settings_popup_theme_material),
                LauncherSettings.POPUP_THEME_DARK to stringResource(R.string.settings_folder_theme_dark),
                LauncherSettings.POPUP_THEME_LIGHT to stringResource(R.string.settings_folder_theme_light),
                LauncherSettings.POPUP_THEME_GLASS to stringResource(R.string.settings_folder_theme_glass),
            ),
            selected = s.popupTheme,
            onSelect = { viewModel.setPopupTheme(it, screenType) },
        )
        PopupThemePreview(s.popupTheme)
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_stack_corner),
            value = s.stackCornerRadius.toFloat(),
            onValueChange = { viewModel.setStackCornerRadius(it.roundToInt(), screenType) },
            valueRange = 0f..LauncherSettings.MAX_STACK_CORNER_RADIUS.toFloat(),
            valueDisplay = "%d dp".format(s.stackCornerRadius),
            onDragValue = { overrides?.stackCorner = it },
        )
        CornerPreview(s)
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_stack_auto_rotate),
            value = s.stackAutoRotateSeconds.toFloat(),
            // Steps of five seconds; 0 switches it off.
            onValueChange = { viewModel.setStackAutoRotateSeconds((it / 5f).roundToInt() * 5, screenType) },
            valueRange = 0f..LauncherSettings.MAX_STACK_AUTO_ROTATE_SECONDS.toFloat(),
            valueDisplay = if (s.stackAutoRotateSeconds == 0) stringResource(R.string.settings_stack_auto_rotate_off) else "%d s".format(s.stackAutoRotateSeconds),
            subtitle = stringResource(R.string.settings_stack_auto_rotate_desc),
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_stack_indicator),
            options = listOf(
                LauncherSettings.STACK_INDICATOR_DOTS to stringResource(R.string.settings_stack_indicator_dots),
                LauncherSettings.STACK_INDICATOR_BAR to stringResource(R.string.settings_stack_indicator_bar),
                LauncherSettings.STACK_INDICATOR_NONE to stringResource(R.string.settings_stack_indicator_none),
            ),
            selected = s.stackIndicator,
            onSelect = { viewModel.setStackIndicator(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_stack_pill),
            subtitle = stringResource(R.string.settings_stack_pill_desc),
            checked = s.stackShowPill,
            onCheckedChange = { viewModel.setStackShowPill(it, screenType) },
        )
    }

    // ---- Home backdrop: dim, blur and this display's own picture ---------------------------------
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyHomeBackground(context, uri, screenType) }
                if (path != null) viewModel.setHomeBackgroundImage(path, screenType)
            }
        }
    }
    // Wallpapers that came with the phone (Motorola keeps them in /product), as home backgrounds.
    val phoneWallpapers = remember { MotoLinks.wallpapers() }
    var showPhoneWallpapers by remember { mutableStateOf(false) }
    if (showPhoneWallpapers) {
        MotoWallpaperDialog(
            files = phoneWallpapers,
            onPick = { file ->
                showPhoneWallpapers = false
                scope.launch {
                    val path = withContext(Dispatchers.IO) { copyHomeBackground(context, Uri.fromFile(file), screenType) }
                    if (path != null) viewModel.setHomeBackgroundImage(path, screenType)
                }
            },
            onDismiss = { showPhoneWallpapers = false },
        )
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { copyHomeBackground(context, uri, screenType, suffix = ".mp4") }
                if (path != null) viewModel.setHomeBackgroundImage(path, screenType)
            }
        }
    }
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_home_backdrop),
        icon = Icons.Rounded.Palette,
        subtitle = stringResource(R.string.settings_home_backdrop_desc),
    ) {
        ExpressiveSliderRow(
            title = stringResource(R.string.settings_home_dim),
            value = s.homeWallpaperDim,
            onValueChange = { viewModel.setHomeWallpaperDim(it, screenType) },
            valueRange = 0f..LauncherSettings.MAX_HOME_WALLPAPER_DIM,
            valueDisplay = "%d%%".format((s.homeWallpaperDim * 100).roundToInt()),
            onDragValue = { overrides?.homeDim = it },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_home_blur),
            value = s.homeWallpaperBlur,
            onValueChange = { viewModel.setHomeWallpaperBlur(it, screenType) },
            valueRange = 0f..LauncherSettings.MAX_HOME_WALLPAPER_BLUR,
            valueDisplay = "%d dp".format(s.homeWallpaperBlur.roundToInt()),
            subtitle = stringResource(R.string.settings_home_blur_desc),
            onDragValue = { overrides?.homeBlur = it },
        )
        BackdropPreview(s)
        ExpressiveRowDivider()

        ExpressiveActionItem(
            title = stringResource(R.string.settings_home_background),
            description = stringResource(
                if (s.homeBackgroundImage.isBlank()) R.string.settings_home_background_none
                else R.string.settings_home_background_set,
            ),
            onClick = { runCatching { pickBackground.launch("image/*") } },
        )
        ExpressiveRowDivider()
        ExpressiveActionItem(
            title = stringResource(R.string.settings_home_video),
            description = stringResource(R.string.settings_home_video_desc),
            onClick = { runCatching { pickVideo.launch("video/*") } },
        )
        if (phoneWallpapers.isNotEmpty()) {
            ExpressiveRowDivider()
            ExpressiveActionItem(
                title = stringResource(R.string.settings_moto_wallpapers),
                description = stringResource(R.string.settings_moto_wallpapers_desc),
                onClick = { showPhoneWallpapers = true },
            )
        }
        if (s.homeBackgroundImage.isNotBlank()) {
            ExpressiveRowDivider()
            ExpressiveActionItem(
                title = stringResource(R.string.settings_home_background_remove),
                description = stringResource(R.string.settings_home_background_remove_desc),
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { deleteHomeBackgrounds(context, screenType) }
                        viewModel.setHomeBackgroundImage("", screenType)
                    }
                },
            )
        }
    }

    TypographySection(s = s, viewModel = viewModel, screenType = screenType)

    // ---- Folark's own widgets -----------------------------------------------------------------------
    FolarkWidgetsSection(s = s, viewModel = viewModel)
}

/** Per-display typography customization card (font family, bold weight, font size scaling). */
@Composable
internal fun TypographySection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
) {
    // Which fonts this phone can show (the Motorola ones are files on the phone), checked once.
    val phoneFonts = remember { LauncherSettings.FONT_FAMILIES.associateWith { LauncherFontFamily.isAvailable(it) } }
    ExpressiveCategoryContainer(
        title = "Typography & Fonts",
        icon = Icons.Rounded.AutoAwesome,
        subtitle = "Font family, bold weights and size scaling per display",
    ) {
        ExpressiveSegmentedControl(
            title = "Font Family",
            options = listOf(
                LauncherSettings.FONT_FAMILY_DEFAULT to "Default",
                LauncherSettings.FONT_FAMILY_NDOT to "Nothing NDot 57",
                LauncherSettings.FONT_FAMILY_NTYPE to "Nothing NType 82",
                LauncherSettings.FONT_FAMILY_SPACE_GROTESK to "Space Grotesk",
                LauncherSettings.FONT_FAMILY_SERIF to "Serif",
                LauncherSettings.FONT_FAMILY_MONOSPACE to "Monospace",
                LauncherSettings.FONT_FAMILY_MOTO_ROOKERY to stringResource(R.string.settings_font_moto_rookery),
                LauncherSettings.FONT_FAMILY_MOTO_WORK_SANS to stringResource(R.string.settings_font_moto_work_sans),
                LauncherSettings.FONT_FAMILY_MOTO_EXO2 to stringResource(R.string.settings_font_moto_exo2),
                LauncherSettings.FONT_FAMILY_MOTO_ROBOTO_SLAB to stringResource(R.string.settings_font_moto_roboto_slab),
            ).filter { (key, _) -> phoneFonts.getValue(key) },
            selected = s.appFontFamily,
            onSelect = { viewModel.setAppFontFamily(it, screenType) },
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = "Bold Font Style",
            subtitle = "Apply bold font weight across launcher labels and titles",
            checked = s.appFontBold,
            onCheckedChange = { viewModel.setAppFontBold(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = "Font Size Scale",
            value = s.appFontSizeScale,
            onValueChange = { viewModel.setAppFontSizeScale(it, screenType) },
            valueRange = 0.7f..1.4f,
            valueDisplay = "%.2fx".format(s.appFontSizeScale),
        )
    }
}

/**
 * Folark's own widgets (now playing, most used apps, device status, quick note): a theme each and
 * their options. One set for both displays — a widget looks the same wherever it is placed.
 */
@Composable
internal fun FolarkWidgetsSection(s: LauncherSettings, viewModel: SettingsViewModel) {
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_folark_widgets),
        icon = Icons.Rounded.AutoAwesome,
        subtitle = stringResource(R.string.settings_folark_widgets_desc),
        sharedByBothDisplays = true,
    ) {
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_weather_animated),
            subtitle = stringResource(R.string.settings_weather_animated_desc),
            checked = s.weatherWidgetAnimated,
            onCheckedChange = viewModel::setWeatherWidgetAnimated,
        )
        ExpressiveRowDivider()

        WeatherLocationRow()
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_clock_style),
            options = listOf(
                LauncherSettings.CLOCK_STYLE_CLASSIC to stringResource(R.string.settings_clock_style_classic),
                LauncherSettings.CLOCK_STYLE_DUO to stringResource(R.string.settings_clock_style_duo),
                LauncherSettings.CLOCK_STYLE_LIGHT to stringResource(R.string.settings_clock_style_light),
                LauncherSettings.CLOCK_STYLE_DOT to stringResource(R.string.settings_clock_style_dot),
                LauncherSettings.CLOCK_STYLE_STACKED to stringResource(R.string.settings_clock_style_stacked),
                LauncherSettings.CLOCK_STYLE_GLASS_PILL to "Glass Pill",
                LauncherSettings.CLOCK_STYLE_OUTLINE to "Outline",
                LauncherSettings.CLOCK_STYLE_VERTICAL_STACK to "Vertical Stack",
                LauncherSettings.CLOCK_STYLE_NEUMORPHIC to "Embossed Glass",
                LauncherSettings.CLOCK_STYLE_NOTHING_NDOT to "Nothing NDot 57",
            ),
            selected = s.clockWidgetStyle,
            onSelect = viewModel::setClockWidgetStyle,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = "Glass Card Effect",
            subtitle = "Render a frosted liquid glass card behind clock digits",
            checked = s.clockWidgetGlassEffect,
            onCheckedChange = viewModel::setClockWidgetGlassEffect,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_clock_text),
            options = listOf(
                false to stringResource(R.string.settings_clock_text_white),
                true to stringResource(R.string.settings_clock_text_dark),
            ),
            selected = s.clockWidgetDarkText,
            onSelect = viewModel::setClockWidgetDarkText,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_clock_minutes),
            options = listOf(
                LauncherSettings.CLOCK_MINUTE_STYLE to stringResource(R.string.settings_clock_minutes_style),
                LauncherSettings.CLOCK_MINUTE_WHITE to stringResource(R.string.settings_clock_minutes_white),
                LauncherSettings.CLOCK_MINUTE_LIGHT_GREY to stringResource(R.string.settings_clock_minutes_light_grey),
                LauncherSettings.CLOCK_MINUTE_GREY to stringResource(R.string.settings_clock_minutes_grey),
                LauncherSettings.CLOCK_MINUTE_DARK_GREY to stringResource(R.string.settings_clock_minutes_dark_grey),
                LauncherSettings.CLOCK_MINUTE_BLACK to stringResource(R.string.settings_clock_minutes_black),
            ),
            selected = s.clockMinuteColor,
            onSelect = viewModel::setClockMinuteColor,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_clock_minutes_opacity),
            value = s.clockMinuteOpacity,
            onValueChange = viewModel::setClockMinuteOpacity,
            valueRange = 0.1f..1f,
            valueDisplay = "${(s.clockMinuteOpacity * 100).roundToInt()}%",
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_widget_media_theme),
            options = listOf(
                LauncherSettings.WIDGET_THEME_DEFAULT to stringResource(R.string.settings_widget_theme_default),
                LauncherSettings.WIDGET_THEME_DARK to stringResource(R.string.settings_folder_theme_dark),
                LauncherSettings.WIDGET_THEME_LIGHT to stringResource(R.string.settings_folder_theme_light),
                LauncherSettings.WIDGET_THEME_ACCENT to stringResource(R.string.settings_folder_theme_accent),
            ),
            selected = s.mediaWidgetTheme,
            onSelect = viewModel::setMediaWidgetTheme,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_media_artwork),
            checked = s.mediaWidgetArtwork,
            onCheckedChange = viewModel::setMediaWidgetArtwork,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_widget_apps_theme),
            options = listOf(
                LauncherSettings.WIDGET_THEME_DEFAULT to stringResource(R.string.settings_widget_theme_default),
                LauncherSettings.WIDGET_THEME_DARK to stringResource(R.string.settings_folder_theme_dark),
                LauncherSettings.WIDGET_THEME_LIGHT to stringResource(R.string.settings_folder_theme_light),
                LauncherSettings.WIDGET_THEME_ACCENT to stringResource(R.string.settings_folder_theme_accent),
            ),
            selected = s.appsWidgetTheme,
            onSelect = viewModel::setAppsWidgetTheme,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_apps_labels),
            checked = s.appsWidgetLabels,
            onCheckedChange = viewModel::setAppsWidgetLabels,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_widget_stats_theme),
            options = listOf(
                LauncherSettings.WIDGET_THEME_DEFAULT to stringResource(R.string.settings_widget_theme_default),
                LauncherSettings.WIDGET_THEME_DARK to stringResource(R.string.settings_folder_theme_dark),
                LauncherSettings.WIDGET_THEME_LIGHT to stringResource(R.string.settings_folder_theme_light),
                LauncherSettings.WIDGET_THEME_ACCENT to stringResource(R.string.settings_folder_theme_accent),
            ),
            selected = s.statsWidgetTheme,
            onSelect = viewModel::setStatsWidgetTheme,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_stats_battery),
            checked = s.statsShowBattery,
            onCheckedChange = viewModel::setStatsShowBattery,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_stats_storage),
            checked = s.statsShowStorage,
            onCheckedChange = viewModel::setStatsShowStorage,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_stats_memory),
            checked = s.statsShowMemory,
            onCheckedChange = viewModel::setStatsShowMemory,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_stats_bluetooth),
            checked = s.statsShowBluetooth,
            onCheckedChange = viewModel::setStatsShowBluetooth,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_widget_note_theme),
            options = listOf(
                LauncherSettings.WIDGET_THEME_DEFAULT to stringResource(R.string.settings_widget_theme_default),
                LauncherSettings.WIDGET_THEME_DARK to stringResource(R.string.settings_folder_theme_dark),
                LauncherSettings.WIDGET_THEME_LIGHT to stringResource(R.string.settings_folder_theme_light),
                LauncherSettings.WIDGET_THEME_ACCENT to stringResource(R.string.settings_folder_theme_accent),
            ),
            selected = s.noteWidgetTheme,
            onSelect = viewModel::setNoteWidgetTheme,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_widget_note_size),
            options = listOf(
                LauncherSettings.FOLDER_OPEN_SMALL to stringResource(R.string.settings_folder_open_small),
                LauncherSettings.FOLDER_OPEN_MEDIUM to stringResource(R.string.settings_folder_open_medium),
                LauncherSettings.FOLDER_OPEN_LARGE to stringResource(R.string.settings_folder_open_large),
            ),
            selected = s.noteWidgetTextSize,
            onSelect = viewModel::setNoteWidgetTextSize,
            icon = null,
        )
    }
}

/** Notifications page: shape, colour and corner of the notification dots. */
@Composable
internal fun NotificationDotStyleRows(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_dot_shape),
        options = listOf(
            LauncherSettings.DOT_SHAPE_CIRCLE to stringResource(R.string.settings_folder_shape_circle),
            LauncherSettings.DOT_SHAPE_SQUIRCLE to stringResource(R.string.settings_folder_shape_squircle),
            LauncherSettings.DOT_SHAPE_SQUARE to stringResource(R.string.settings_folder_shape_square),
        ),
        selected = s.dotShape,
        onSelect = { viewModel.setDotShape(it, screenType) },
    )
    ExpressiveRowDivider()

    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_dot_color),
        options = listOf(
            LauncherSettings.DOT_COLOR_RED to stringResource(R.string.settings_dot_color_red),
            LauncherSettings.DOT_COLOR_ACCENT to stringResource(R.string.settings_folder_theme_accent),
            LauncherSettings.DOT_COLOR_WHITE to stringResource(R.string.settings_dot_color_white),
        ),
        selected = s.dotColor,
        onSelect = { viewModel.setDotColor(it, screenType) },
    )
    ExpressiveRowDivider()

    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_dot_position),
        options = listOf(
            LauncherSettings.DOT_POSITION_TOP_END to stringResource(R.string.settings_dot_position_top_end),
            LauncherSettings.DOT_POSITION_TOP_START to stringResource(R.string.settings_dot_position_top_start),
            LauncherSettings.DOT_POSITION_BOTTOM_END to stringResource(R.string.settings_dot_position_bottom_end),
            LauncherSettings.DOT_POSITION_BOTTOM_START to stringResource(R.string.settings_dot_position_bottom_start),
        ),
        selected = s.dotPosition,
        onSelect = { viewModel.setDotPosition(it, screenType) },
    )
}

/** Gestures page: what a swipe up on a home app icon does. */
@Composable
internal fun IconSwipeRow(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_icon_swipe),
        options = listOf(
            LauncherSettings.ICON_SWIPE_NONE to stringResource(R.string.settings_icon_swipe_none),
            LauncherSettings.ICON_SWIPE_MENU to stringResource(R.string.settings_icon_swipe_menu),
            LauncherSettings.ICON_SWIPE_FLOATING to stringResource(R.string.settings_icon_swipe_floating),
            LauncherSettings.ICON_SWIPE_SPLIT to stringResource(R.string.settings_icon_swipe_split),
        ),
        selected = s.iconSwipeUpAction,
        onSelect = { viewModel.setIconSwipeUpAction(it, screenType) },
    )
}

/** Home grid page: remove a page when its last item leaves it. */
@Composable
internal fun AutoRemovePagesRow(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_auto_remove_pages),
        subtitle = stringResource(R.string.settings_auto_remove_pages_desc),
        checked = s.autoRemoveEmptyPages,
        onCheckedChange = { viewModel.setAutoRemoveEmptyPages(it, screenType) },
    )
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_new_page_on_drag),
        subtitle = stringResource(R.string.settings_new_page_on_drag_desc),
        checked = s.newPageOnDrag,
        onCheckedChange = { viewModel.setNewPageOnDrag(it, screenType) },
    )
}

/** App drawer page: folders as tabs, and what search offers besides apps. */
@Composable
internal fun DrawerExtrasRows(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_drawer_tabs),
        subtitle = stringResource(R.string.settings_drawer_tabs_desc),
        checked = s.drawerFolderTabs,
        onCheckedChange = { viewModel.setDrawerFolderTabs(it, screenType) },
    )
    ExpressiveRowDivider()

    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_search_shortcuts),
        subtitle = stringResource(R.string.settings_search_shortcuts_desc),
        checked = s.searchShortcuts,
        onCheckedChange = viewModel::setSearchShortcuts,
        shared = true,
    )
    ExpressiveRowDivider()

    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_search_web),
        checked = s.searchWeb,
        onCheckedChange = viewModel::setSearchWeb,
        shared = true,
    )

    // Private Space exists from Android 15 on.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_private_space),
            subtitle = stringResource(R.string.settings_private_space_desc),
            checked = s.showPrivateSpace,
            onCheckedChange = viewModel::setShowPrivateSpace,
            shared = true,
        )
    }
}

/** Foldable page (shared card): the size a floating window opens at. */
@Composable
internal fun FloatingSizeRow(s: LauncherSettings, viewModel: SettingsViewModel) {
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_floating_size),
        options = listOf(
            LauncherSettings.FLOATING_SIZE_SMALL to stringResource(R.string.settings_folder_open_small),
            LauncherSettings.FLOATING_SIZE_MEDIUM to stringResource(R.string.settings_folder_open_medium),
            LauncherSettings.FLOATING_SIZE_LARGE to stringResource(R.string.settings_folder_open_large),
        ),
        selected = s.floatingWindowSize,
        onSelect = viewModel::setFloatingWindowSize,
    )
}

private fun homeBackgroundPrefix(screenType: ScreenType): String =
    "home_background_" + screenType.name.lowercase() + "_"

/** Removes every stored background picture of [screenType] (only one is ever current). */
private fun deleteHomeBackgrounds(context: Context, screenType: ScreenType) {
    val prefix = homeBackgroundPrefix(screenType)
    runCatching {
        context.filesDir.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }
}

/**
 * Copies the picked picture into the app's private storage (the picker's permission to read [uri]
 * does not outlive this screen) and returns the copy's path, or null if it could not be read. The
 * name carries a timestamp so a newly picked picture is never served from the image cache of the
 * previous one.
 */
private fun copyHomeBackground(context: Context, uri: Uri, screenType: ScreenType, suffix: String = ""): String? {
    return runCatching {
        // Copy first, then drop the older pictures: a failed copy must leave the current one in place.
        // A video keeps its extension: that is how home tells it from a picture.
        val target = File(context.filesDir, homeBackgroundPrefix(screenType) + System.currentTimeMillis() + suffix)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val copied = runCatching { input.use { source -> target.outputStream().use { source.copyTo(it) } } }.isSuccess
        if (!copied) {
            target.delete()
            return null
        }
        val prefix = homeBackgroundPrefix(screenType)
        context.filesDir.listFiles()?.filter { it.name.startsWith(prefix) && it != target }?.forEach { it.delete() }
        target.absolutePath
    }.getOrNull()
}

/** Animations card: speed, and the app-open, folder-open and page-swipe styles (both displays). */
@Composable
internal fun AnimationStyleRows(s: LauncherSettings, viewModel: SettingsViewModel) {
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_battery_saver),
        options = listOf(
            LauncherSettings.BATTERY_SAVER_OFF to stringResource(R.string.settings_battery_saver_off),
            LauncherSettings.BATTERY_SAVER_AUTO to stringResource(R.string.settings_battery_saver_auto),
            LauncherSettings.BATTERY_SAVER_ON to stringResource(R.string.settings_battery_saver_on),
        ),
        selected = s.batterySaverMode,
        onSelect = viewModel::setBatterySaverMode,
    )
    Text(
        text = stringResource(R.string.settings_battery_saver_desc),
        color = LocalExpressivePalette.current.dim,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    ExpressiveRowDivider()
    ExpressiveSliderRow(
        title = stringResource(R.string.settings_animation_speed),
        value = s.animationSpeed,
        onValueChange = { viewModel.setAnimationSpeed((it * 4f).roundToInt() / 4f) },
        valueRange = LauncherSettings.MIN_ANIMATION_SPEED..LauncherSettings.MAX_ANIMATION_SPEED,
        valueDisplay = "%.2f×".format(s.animationSpeed),
        subtitle = stringResource(R.string.settings_animation_speed_desc),
    )
    ExpressiveRowDivider()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_app_open_animation),
        options = listOf(
            LauncherSettings.APP_OPEN_SYSTEM to stringResource(R.string.settings_anim_system),
            LauncherSettings.APP_OPEN_ICON to stringResource(R.string.settings_anim_icon),
            LauncherSettings.APP_OPEN_REVEAL to stringResource(R.string.settings_anim_reveal),
            LauncherSettings.APP_OPEN_ZOOM to stringResource(R.string.settings_anim_zoom),
            LauncherSettings.APP_OPEN_SLIDE to stringResource(R.string.settings_anim_slide),
            LauncherSettings.APP_OPEN_FADE to stringResource(R.string.settings_anim_fade),
            LauncherSettings.APP_OPEN_NONE to stringResource(R.string.settings_anim_none),
        ),
        selected = s.appOpenAnimation,
        onSelect = viewModel::setAppOpenAnimation,
    )
    ExpressiveRowDivider()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_folder_open_animation),
        options = listOf(
            LauncherSettings.FOLDER_ANIM_SHEET to stringResource(R.string.settings_anim_sheet),
            LauncherSettings.FOLDER_ANIM_ZOOM to stringResource(R.string.settings_anim_zoom),
            LauncherSettings.FOLDER_ANIM_FADE to stringResource(R.string.settings_anim_fade),
            LauncherSettings.FOLDER_ANIM_NONE to stringResource(R.string.settings_anim_none),
        ),
        selected = s.folderOpenAnimation,
        onSelect = viewModel::setFolderOpenAnimation,
    )
    ExpressiveRowDivider()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_page_scroll_effect),
        options = listOf(
            LauncherSettings.PAGE_EFFECT_SLIDE to stringResource(R.string.settings_anim_slide),
            LauncherSettings.PAGE_EFFECT_FADE to stringResource(R.string.settings_anim_fade),
            LauncherSettings.PAGE_EFFECT_ZOOM to stringResource(R.string.settings_anim_zoom),
            LauncherSettings.PAGE_EFFECT_CUBE to stringResource(R.string.settings_anim_cube),
            LauncherSettings.PAGE_EFFECT_STACK to stringResource(R.string.settings_anim_stack),
        ),
        selected = s.pageScrollEffect,
        onSelect = viewModel::setPageScrollEffect,
    )
}

/** Looks a typed place up and stores it as the weather location (or clears it again). */
@HiltViewModel
class WeatherLocationViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val place: StateFlow<WeatherPlace?> = settingsRepository.weatherLocation
        .map { WeatherPlace.parse(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    /** Saves the best match for [query]; [onResult] gets it, or null when nothing was found. */
    fun search(query: String, onResult: (WeatherPlace?) -> Unit) {
        viewModelScope.launch {
            val found = weatherRepository.findPlace(query)
            if (found != null) settingsRepository.setWeatherLocation(found.serialize())
            onResult(found)
        }
    }

    fun useDeviceLocation() {
        viewModelScope.launch { settingsRepository.setWeatherLocation("") }
    }
}

/** "Weather location": the device's own location, or a town typed in by name. */
@Composable
internal fun WeatherLocationRow(viewModel: WeatherLocationViewModel = hiltViewModel()) {
    val place by viewModel.place.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    ExpressiveActionItem(
        title = stringResource(R.string.settings_weather_location),
        description = place?.name ?: stringResource(R.string.settings_weather_location_device),
        onClick = { open = true },
    )
    if (open) {
        var text by remember { mutableStateOf(place?.name.orEmpty()) }
        var searching by remember { mutableStateOf(false) }
        var notFound by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.settings_weather_location)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; notFound = false },
                        singleLine = true,
                        isError = notFound,
                        label = { Text(stringResource(R.string.settings_weather_location_hint)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    when {
                                        notFound -> R.string.settings_weather_location_not_found
                                        searching -> R.string.settings_weather_location_searching
                                        else -> R.string.settings_weather_location_desc
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() && !searching,
                    onClick = {
                        searching = true
                        viewModel.search(text) { found ->
                            searching = false
                            if (found != null) open = false else notFound = true
                        }
                    },
                ) { Text(stringResource(R.string.settings_weather_location_save)) }
            },
            dismissButton = {
                // Leaving the dialog must not wipe a saved place: that is its own button.
                Row {
                    TextButton(onClick = { viewModel.useDeviceLocation(); open = false }) {
                        Text(stringResource(R.string.settings_weather_location_device))
                    }
                    TextButton(onClick = { open = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            },
        )
    }
}
