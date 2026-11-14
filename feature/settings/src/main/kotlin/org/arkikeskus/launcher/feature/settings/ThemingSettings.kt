package org.arkikeskus.launcher.feature.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.arkikeskus.launcher.data.DrawerFolder
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
import org.arkikeskus.launcher.model.AppOpenAnimation
import org.arkikeskus.launcher.model.BatterySaverMode
import org.arkikeskus.launcher.model.ClockMinuteColor
import org.arkikeskus.launcher.model.ClockStyle
import org.arkikeskus.launcher.model.DotColor
import org.arkikeskus.launcher.model.DotPosition
import org.arkikeskus.launcher.model.DotShape
import org.arkikeskus.launcher.model.FolderAnimation
import org.arkikeskus.launcher.model.FontChoice
import org.arkikeskus.launcher.model.IconSwipe
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.PageEffect
import org.arkikeskus.launcher.model.PopupTheme
import org.arkikeskus.launcher.model.SearchEngine
import org.arkikeskus.launcher.model.SizeChoice
import org.arkikeskus.launcher.model.StackIndicator
import org.arkikeskus.launcher.model.ThemePreset
import org.arkikeskus.launcher.model.WidgetTheme
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette
import org.arkikeskus.launcher.ui.component.LocalScreenType
import org.arkikeskus.launcher.ui.theme.LauncherFontFamily
import java.io.File
import kotlin.math.roundToInt
import org.arkikeskus.launcher.data.drawerFolders
import org.arkikeskus.launcher.data.moveDrawerFolder
import org.arkikeskus.launcher.model.catching

// The theming cards of the Theme page: one-tap presets, the look of menus and widget stacks, the
// home backdrop and Folark's own widgets. Settings that are not about looks live as rows on the
// page they belong to (see the *Row / *Rows functions below).

/** Presets: each sets the drawer, folders, menus, dots, stack indicator and dock at once. */
@Composable
internal fun ThemePresetsCard(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
) {
    val context = LocalContext.current
    val presetApplied = stringResource(R.string.settings_theme_preset_applied)
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_theme_presets),
        icon = Icons.Rounded.AutoAwesome,
        subtitle = stringResource(R.string.settings_theme_presets_desc),
    ) {
        listOf(
            Triple(ThemePreset.GLASS, R.string.settings_theme_preset_glass, R.string.settings_theme_preset_glass_desc),
            Triple(ThemePreset.DARK, R.string.settings_theme_preset_dark, R.string.settings_theme_preset_dark_desc),
            Triple(ThemePreset.LIGHT, R.string.settings_theme_preset_light, R.string.settings_theme_preset_light_desc),
            Triple(ThemePreset.MONO, R.string.settings_theme_preset_mono, R.string.settings_theme_preset_mono_desc),
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
}

/** Menus and widget stacks. */
@Composable
internal fun MenusAndStacksCard(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
) {
    val overrides = LocalPreviewOverrides.current
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_theme_details),
        icon = Icons.Rounded.Palette,
        subtitle = stringResource(R.string.settings_theme_details_desc),
    ) {
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_popup_theme),
            options = listOf(
                PopupTheme.CLASSIC to stringResource(R.string.settings_popup_theme_classic),
                PopupTheme.MATERIAL to stringResource(R.string.settings_popup_theme_material),
                PopupTheme.DARK to stringResource(R.string.settings_folder_theme_dark),
                PopupTheme.LIGHT to stringResource(R.string.settings_folder_theme_light),
                PopupTheme.GLASS to stringResource(R.string.settings_folder_theme_glass),
            ),
            selected = s.appearance.popupTheme,
            onSelect = { viewModel.setPopupTheme(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_stack_corner),
            value = s.widgets.stackCornerRadius.toFloat(),
            onValueChange = { viewModel.setStackCornerRadius(it.roundToInt(), screenType) },
            valueRange = 0f..LauncherSettings.MAX_STACK_CORNER_RADIUS.toFloat(),
            valueDisplay = "%d dp".format(s.widgets.stackCornerRadius),
            onDragValue = { overrides?.stackCorner = it },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_stack_auto_rotate),
            value = s.widgets.stackAutoRotateSeconds.toFloat(),
            // Steps of five seconds; 0 switches it off.
            onValueChange = { viewModel.setStackAutoRotateSeconds((it / 5f).roundToInt() * 5, screenType) },
            valueRange = 0f..LauncherSettings.MAX_STACK_AUTO_ROTATE_SECONDS.toFloat(),
            valueDisplay = if (s.widgets.stackAutoRotateSeconds == 0) stringResource(R.string.settings_stack_auto_rotate_off) else "%d s".format(s.widgets.stackAutoRotateSeconds),
            subtitle = stringResource(R.string.settings_stack_auto_rotate_desc),
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_stack_indicator),
            options = listOf(
                StackIndicator.DOTS to stringResource(R.string.settings_stack_indicator_dots),
                StackIndicator.BAR to stringResource(R.string.settings_stack_indicator_bar),
                StackIndicator.NONE to stringResource(R.string.settings_stack_indicator_none),
            ),
            selected = s.widgets.stackIndicator,
            onSelect = { viewModel.setStackIndicator(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_stack_pill),
            subtitle = stringResource(R.string.settings_stack_pill_desc),
            checked = s.widgets.stackShowPill,
            onCheckedChange = { viewModel.setStackShowPill(it, screenType) },
        )
    }
}

/** Home backdrop: dim, blur and this display's own picture. */
@Composable
internal fun HomeBackdropCard(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overrides = LocalPreviewOverrides.current
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
            value = s.home.wallpaperDim,
            onValueChange = { viewModel.setHomeWallpaperDim(it, screenType) },
            valueRange = 0f..LauncherSettings.MAX_HOME_WALLPAPER_DIM,
            valueDisplay = "%d%%".format((s.home.wallpaperDim * 100).roundToInt()),
            onDragValue = { overrides?.homeDim = it },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_home_blur),
            value = s.home.wallpaperBlur,
            onValueChange = { viewModel.setHomeWallpaperBlur(it, screenType) },
            valueRange = 0f..LauncherSettings.MAX_HOME_WALLPAPER_BLUR,
            valueDisplay = "%d dp".format(s.home.wallpaperBlur.roundToInt()),
            subtitle = stringResource(R.string.settings_home_blur_desc),
            onDragValue = { overrides?.homeBlur = it },
        )
        ExpressiveRowDivider()
        if (hasHingeSensor(context)) {
            ExpressiveSwitchRow(
                title = stringResource(R.string.settings_wallpaper_hinge),
                subtitle = stringResource(R.string.settings_wallpaper_hinge_desc),
                checked = s.fold.wallpaperHinge,
                onCheckedChange = viewModel::setWallpaperHinge,
                shared = true,
            )
            ExpressiveRowDivider()
        }

        ExpressiveActionItem(
            title = stringResource(R.string.settings_home_background),
            description = stringResource(
                if (s.home.backgroundImage.isBlank()) R.string.settings_home_background_none
                else R.string.settings_home_background_set,
            ),
            onClick = { catching { pickBackground.launch("image/*") } },
        )
        ExpressiveRowDivider()
        ExpressiveActionItem(
            title = stringResource(R.string.settings_home_video),
            description = stringResource(R.string.settings_home_video_desc),
            onClick = { catching { pickVideo.launch("video/*") } },
        )
        // Only once a video is the background: how long it plays before resting on a still frame.
        if (s.home.backgroundImage.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS) {
            ExpressiveRowDivider()
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_video_awake),
                options = listOf(
                    "15" to "15 s",
                    "30" to "30 s",
                    "120" to "2 min",
                    "0" to stringResource(R.string.settings_weather_awake_always),
                ),
                selected = s.home.videoAwakeSeconds.toString(),
                onSelect = { viewModel.setVideoAwakeSeconds(it.toInt()) },
            )
            Text(
                text = stringResource(R.string.settings_video_awake_desc),
                color = LocalExpressivePalette.current.dim,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        if (phoneWallpapers.isNotEmpty()) {
            ExpressiveRowDivider()
            ExpressiveActionItem(
                title = stringResource(R.string.settings_moto_wallpapers),
                description = stringResource(R.string.settings_moto_wallpapers_desc),
                onClick = { showPhoneWallpapers = true },
            )
        }
        if (s.home.backgroundImage.isNotBlank()) {
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
}

/** Per-display typography customization card (font family, bold weight, font size scaling). */
@Composable
internal fun TypographySection(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType = LocalScreenType.current,
) {
    // Which fonts this phone can show (the Motorola ones are files on the phone), checked once.
    val phoneFonts = remember { FontChoice.BODY.associateWith { LauncherFontFamily.isAvailable(it) } }
    ExpressiveCategoryContainer(
        title = stringResource(R.string.settings_typography),
        icon = Icons.Rounded.AutoAwesome,
        subtitle = stringResource(R.string.settings_typography_desc),
    ) {
        val fontOptions = listOf(
            FontChoice.DEFAULT to stringResource(R.string.settings_font_default),
            FontChoice.NDOT to stringResource(R.string.settings_font_ndot),
            FontChoice.NTYPE to stringResource(R.string.settings_font_ntype),
            FontChoice.SPACE_GROTESK to stringResource(R.string.settings_font_space_grotesk),
            FontChoice.SERIF to stringResource(R.string.settings_font_serif),
            FontChoice.MONOSPACE to stringResource(R.string.settings_font_monospace),
            FontChoice.CONDENSED to stringResource(R.string.settings_font_condensed),
            FontChoice.LIGHT to stringResource(R.string.settings_font_light),
            FontChoice.MEDIUM to stringResource(R.string.settings_font_medium),
            FontChoice.CASUAL to stringResource(R.string.settings_font_casual),
            FontChoice.SMALLCAPS to stringResource(R.string.settings_font_smallcaps),
            FontChoice.CURSIVE to stringResource(R.string.settings_font_cursive),
            FontChoice.MOTO_ROOKERY to stringResource(R.string.settings_font_moto_rookery),
            FontChoice.MOTO_WORK_SANS to stringResource(R.string.settings_font_moto_work_sans),
            FontChoice.MOTO_EXO2 to stringResource(R.string.settings_font_moto_exo2),
            FontChoice.MOTO_ROBOTO_SLAB to stringResource(R.string.settings_font_moto_roboto_slab),
        ).filter { (key, _) -> phoneFonts.getValue(key) }
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_font_family),
            options = fontOptions,
            selected = s.appearance.fontFamily,
            onSelect = { viewModel.setAppFontFamily(it, screenType) },
            icon = null,
        )
        ExpressiveRowDivider()

        // Headings apart from the text: pixel titles over plain text, or plain titles over a pixel font.
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_heading_font),
            options = listOf(FontChoice.SAME to stringResource(R.string.settings_heading_font_same)) + fontOptions,
            selected = s.appearance.headingFont,
            onSelect = { viewModel.setAppHeadingFont(it, screenType) },
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_font_bold),
            subtitle = stringResource(R.string.settings_font_bold_desc),
            checked = s.appearance.fontBold,
            onCheckedChange = { viewModel.setAppFontBold(it, screenType) },
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_font_size_scale),
            value = s.appearance.fontSizeScale,
            onValueChange = { viewModel.setAppFontSizeScale(it, screenType) },
            valueRange = 0.7f..1.4f,
            valueDisplay = "%.2fx".format(s.appearance.fontSizeScale),
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
            checked = s.widgets.weatherAnimated,
            onCheckedChange = viewModel::setWeatherWidgetAnimated,
        )
        ExpressiveRowDivider()
        if (s.widgets.weatherAnimated) {
            ExpressiveSegmentedControl(
                title = stringResource(R.string.settings_weather_awake),
                options = listOf(
                    "15" to "15 s",
                    "45" to "45 s",
                    "120" to "2 min",
                    "0" to stringResource(R.string.settings_weather_awake_always),
                ),
                selected = s.widgets.weatherAwakeSeconds.toString(),
                onSelect = { viewModel.setWeatherAwakeSeconds(it.toInt()) },
            )
            Text(
                text = stringResource(R.string.settings_weather_awake_desc),
                color = LocalExpressivePalette.current.dim,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            ExpressiveRowDivider()
        }

        WeatherLocationRow()
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_clock_style),
            options = listOf(
                ClockStyle.CLASSIC to stringResource(R.string.settings_clock_style_classic),
                ClockStyle.DUO to stringResource(R.string.settings_clock_style_duo),
                ClockStyle.LIGHT to stringResource(R.string.settings_clock_style_light),
                ClockStyle.DOT to stringResource(R.string.settings_clock_style_dot),
                ClockStyle.STACKED to stringResource(R.string.settings_clock_style_stacked),
                ClockStyle.GLASS_PILL to "Glass Pill",
                ClockStyle.OUTLINE to "Outline",
                ClockStyle.VERTICAL_STACK to "Vertical Stack",
                ClockStyle.NEUMORPHIC to "Embossed Glass",
                ClockStyle.NOTHING_NDOT to "Nothing NDot 57",
            ),
            selected = s.widgets.clockStyle,
            onSelect = viewModel::setClockWidgetStyle,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_clock_glass),
            subtitle = stringResource(R.string.settings_clock_glass_desc),
            checked = s.widgets.clockGlassEffect,
            onCheckedChange = viewModel::setClockWidgetGlassEffect,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_clock_text),
            options = listOf(
                false to stringResource(R.string.settings_clock_text_white),
                true to stringResource(R.string.settings_clock_text_dark),
            ),
            selected = s.widgets.clockDarkText,
            onSelect = viewModel::setClockWidgetDarkText,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_clock_minutes),
            options = listOf(
                ClockMinuteColor.STYLE to stringResource(R.string.settings_clock_minutes_style),
                ClockMinuteColor.WHITE to stringResource(R.string.settings_clock_minutes_white),
                ClockMinuteColor.LIGHT_GREY to stringResource(R.string.settings_clock_minutes_light_grey),
                ClockMinuteColor.GREY to stringResource(R.string.settings_clock_minutes_grey),
                ClockMinuteColor.DARK_GREY to stringResource(R.string.settings_clock_minutes_dark_grey),
                ClockMinuteColor.BLACK to stringResource(R.string.settings_clock_minutes_black),
            ),
            selected = s.widgets.clockMinuteColor,
            onSelect = viewModel::setClockMinuteColor,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSliderRow(
            title = stringResource(R.string.settings_clock_minutes_opacity),
            value = s.widgets.clockMinuteOpacity,
            onValueChange = viewModel::setClockMinuteOpacity,
            valueRange = 0.1f..1f,
            valueDisplay = "${(s.widgets.clockMinuteOpacity * 100).roundToInt()}%",
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_widget_media_theme),
            options = listOf(
                WidgetTheme.DEFAULT to stringResource(R.string.settings_widget_theme_default),
                WidgetTheme.DARK to stringResource(R.string.settings_folder_theme_dark),
                WidgetTheme.LIGHT to stringResource(R.string.settings_folder_theme_light),
                WidgetTheme.ACCENT to stringResource(R.string.settings_folder_theme_accent),
            ),
            selected = s.widgets.mediaTheme,
            onSelect = viewModel::setMediaWidgetTheme,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_media_artwork),
            checked = s.widgets.mediaArtwork,
            onCheckedChange = viewModel::setMediaWidgetArtwork,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_widget_apps_theme),
            options = listOf(
                WidgetTheme.DEFAULT to stringResource(R.string.settings_widget_theme_default),
                WidgetTheme.DARK to stringResource(R.string.settings_folder_theme_dark),
                WidgetTheme.LIGHT to stringResource(R.string.settings_folder_theme_light),
                WidgetTheme.ACCENT to stringResource(R.string.settings_folder_theme_accent),
            ),
            selected = s.widgets.appsTheme,
            onSelect = viewModel::setAppsWidgetTheme,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_apps_labels),
            checked = s.widgets.appsLabels,
            onCheckedChange = viewModel::setAppsWidgetLabels,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_widget_stats_theme),
            options = listOf(
                WidgetTheme.DEFAULT to stringResource(R.string.settings_widget_theme_default),
                WidgetTheme.DARK to stringResource(R.string.settings_folder_theme_dark),
                WidgetTheme.LIGHT to stringResource(R.string.settings_folder_theme_light),
                WidgetTheme.ACCENT to stringResource(R.string.settings_folder_theme_accent),
            ),
            selected = s.widgets.statsTheme,
            onSelect = viewModel::setStatsWidgetTheme,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_stats_battery),
            checked = s.widgets.statsShowBattery,
            onCheckedChange = viewModel::setStatsShowBattery,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_stats_storage),
            checked = s.widgets.statsShowStorage,
            onCheckedChange = viewModel::setStatsShowStorage,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_stats_memory),
            checked = s.widgets.statsShowMemory,
            onCheckedChange = viewModel::setStatsShowMemory,
        )
        ExpressiveRowDivider()

        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_widget_stats_bluetooth),
            checked = s.widgets.statsShowBluetooth,
            onCheckedChange = viewModel::setStatsShowBluetooth,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_widget_note_theme),
            options = listOf(
                WidgetTheme.DEFAULT to stringResource(R.string.settings_widget_theme_default),
                WidgetTheme.DARK to stringResource(R.string.settings_folder_theme_dark),
                WidgetTheme.LIGHT to stringResource(R.string.settings_folder_theme_light),
                WidgetTheme.ACCENT to stringResource(R.string.settings_folder_theme_accent),
            ),
            selected = s.widgets.noteTheme,
            onSelect = viewModel::setNoteWidgetTheme,
            icon = null,
        )
        ExpressiveRowDivider()

        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_widget_note_size),
            options = listOf(
                SizeChoice.SMALL to stringResource(R.string.settings_folder_open_small),
                SizeChoice.MEDIUM to stringResource(R.string.settings_folder_open_medium),
                SizeChoice.LARGE to stringResource(R.string.settings_folder_open_large),
            ),
            selected = s.widgets.noteTextSize,
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
            DotShape.CIRCLE to stringResource(R.string.settings_folder_shape_circle),
            DotShape.SQUIRCLE to stringResource(R.string.settings_folder_shape_squircle),
            DotShape.SQUARE to stringResource(R.string.settings_folder_shape_square),
        ),
        selected = s.dots.shape,
        onSelect = { viewModel.setDotShape(it, screenType) },
    )
    ExpressiveRowDivider()

    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_dot_color),
        options = listOf(
            DotColor.RED to stringResource(R.string.settings_dot_color_red),
            DotColor.ACCENT to stringResource(R.string.settings_folder_theme_accent),
            DotColor.WHITE to stringResource(R.string.settings_dot_color_white),
        ),
        selected = s.dots.color,
        onSelect = { viewModel.setDotColor(it, screenType) },
    )
    ExpressiveRowDivider()

    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_dot_position),
        options = listOf(
            DotPosition.TOP_END to stringResource(R.string.settings_dot_position_top_end),
            DotPosition.TOP_START to stringResource(R.string.settings_dot_position_top_start),
            DotPosition.BOTTOM_END to stringResource(R.string.settings_dot_position_bottom_end),
            DotPosition.BOTTOM_START to stringResource(R.string.settings_dot_position_bottom_start),
        ),
        selected = s.dots.position,
        onSelect = { viewModel.setDotPosition(it, screenType) },
    )
}

/** Gestures page: what a swipe up on a home app icon does. */
@Composable
internal fun IconSwipeRow(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_icon_swipe),
        options = listOf(
            IconSwipe.NONE to stringResource(R.string.settings_icon_swipe_none),
            IconSwipe.MENU to stringResource(R.string.settings_icon_swipe_menu),
            IconSwipe.FLOATING to stringResource(R.string.settings_icon_swipe_floating),
            IconSwipe.SPLIT to stringResource(R.string.settings_icon_swipe_split),
        ),
        selected = s.home.iconSwipeUpAction,
        onSelect = { viewModel.setIconSwipeUpAction(it, screenType) },
    )
}

/** Home grid page: remove a page when its last item leaves it. */
@Composable
internal fun AutoRemovePagesRow(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_auto_remove_pages),
        subtitle = stringResource(R.string.settings_auto_remove_pages_desc),
        checked = s.home.autoRemoveEmptyPages,
        onCheckedChange = { viewModel.setAutoRemoveEmptyPages(it, screenType) },
    )
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_new_page_on_drag),
        subtitle = stringResource(R.string.settings_new_page_on_drag_desc),
        checked = s.home.newPageOnDrag,
        onCheckedChange = { viewModel.setNewPageOnDrag(it, screenType) },
    )
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_add_new_apps),
        subtitle = stringResource(R.string.settings_add_new_apps_desc),
        checked = s.home.addNewApps,
        onCheckedChange = viewModel::setAddNewApps,
        shared = true,
    )
}

/** App drawer page: folders as tabs, the two-pane drawer, and Private Space. */
@Composable
internal fun DrawerExtrasRows(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_drawer_tabs),
        subtitle = stringResource(R.string.settings_drawer_tabs_desc),
        checked = s.drawer.folderTabs,
        onCheckedChange = { viewModel.setDrawerFolderTabs(it, screenType) },
    )
    if (s.drawer.folderTabs) {
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_tabs_exclusive),
            subtitle = stringResource(R.string.settings_drawer_tabs_exclusive_desc),
            checked = s.drawer.tabsExclusive,
            onCheckedChange = viewModel::setDrawerTabsExclusive,
            shared = true,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_two_pane),
            subtitle = stringResource(R.string.settings_drawer_two_pane_desc),
            checked = s.drawer.twoPane,
            onCheckedChange = viewModel::setDrawerTwoPane,
            shared = true,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_drawer_tabs_lock),
            subtitle = stringResource(R.string.settings_drawer_tabs_lock_desc),
            checked = s.drawer.lockTabOrder,
            onCheckedChange = viewModel::setDrawerTabsLocked,
            shared = true,
        )
        ExpressiveRowDivider()
        DrawerTabOrder(viewModel, locked = s.drawer.lockTabOrder)
    }

    // Private Space exists from Android 15 on.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_private_space),
            subtitle = stringResource(R.string.settings_private_space_desc),
            checked = s.drawer.showPrivateSpace,
            onCheckedChange = viewModel::setShowPrivateSpace,
            shared = true,
        )
    }
}

/**
 * The order of the drawer's tabs, as a list you drag by the handle. The tabs are the drawer folders,
 * so moving a row here moves the chip in the drawer's tab row (and the matching folder tile).
 */
@Composable
private fun DrawerTabOrder(viewModel: SettingsViewModel, locked: Boolean = false) {
    val folders by viewModel.drawerFolders.collectAsStateWithLifecycle()
    val palette = LocalExpressivePalette.current
    // A local copy so a row follows the finger before the write lands; reset when the store emits.
    var order by remember(folders) { mutableStateOf(folders) }
    val rowHeight = 54.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = stringResource(R.string.settings_drawer_tab_order),
            color = palette.text,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = stringResource(R.string.settings_drawer_tab_order_desc),
            color = palette.dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp),
        )
        if (order.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_drawer_tab_order_empty),
                color = palette.dim,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        order.forEachIndexed { index, folder ->
            val lifted = draggingIndex == index
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (lifted) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (lifted) dragOffset else 0f
                        val s = if (lifted) 1.02f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .background(
                        if (lifted) palette.surfaceHi else Color.Transparent,
                        RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 8.dp),
            ) {
                Icon(
                    Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.settings_drawer_tab_order),
                    tint = if (locked) palette.faint else palette.dim,
                    modifier = Modifier
                        .size(22.dp)
                        .pointerInput(order, locked) {
                            // Locked from the switch above: the handles stay put, so the order can
                            // only be changed by unlocking it here.
                            if (locked) return@pointerInput
                            detectDragGestures(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                },
                                onDragEnd = {
                                    val from = draggingIndex
                                    val to = (from + (dragOffset / rowHeightPx).roundToInt())
                                        .coerceIn(0, order.lastIndex)
                                    draggingIndex = -1
                                    dragOffset = 0f
                                    if (from in order.indices && from != to) {
                                        order = order.toMutableList().apply { add(to, removeAt(from)) }
                                        viewModel.moveDrawerFolder(from, to)
                                    }
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffset = 0f
                                },
                            )
                        },
                )
                Text(
                    text = folder.name,
                    color = palette.text,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
            }
        }
    }
}

/** App drawer page ▸ Search: what search finds besides apps (both displays). */
@Composable
internal fun DrawerSearchRows(s: LauncherSettings, viewModel: SettingsViewModel) {
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_search_shortcuts),
        subtitle = stringResource(R.string.settings_search_shortcuts_desc),
        checked = s.search.shortcuts,
        onCheckedChange = viewModel::setSearchShortcuts,
        shared = true,
    )
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_search_inline_shortcuts),
        subtitle = stringResource(R.string.settings_search_inline_shortcuts_desc),
        checked = s.search.inlineShortcuts,
        onCheckedChange = viewModel::setSearchInlineShortcuts,
        shared = true,
    )
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_search_settings_pages),
        subtitle = stringResource(R.string.settings_search_settings_pages_desc),
        checked = s.search.settingsPages,
        onCheckedChange = viewModel::setSearchSettingsPages,
        shared = true,
    )
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_search_web),
        checked = s.search.web,
        onCheckedChange = viewModel::setSearchWeb,
        shared = true,
    )
    if (s.search.web) {
        ExpressiveSegmentedControl(
            title = stringResource(R.string.settings_search_web_engine),
            options = listOf(
                SearchEngine.SYSTEM to stringResource(R.string.settings_search_engine_system),
                SearchEngine.GOOGLE to stringResource(R.string.settings_search_engine_google),
                SearchEngine.DUCKDUCKGO to stringResource(R.string.settings_search_engine_duckduckgo),
                SearchEngine.BING to stringResource(R.string.settings_search_engine_bing),
                SearchEngine.STARTPAGE to stringResource(R.string.settings_search_engine_startpage),
            ),
            selected = s.search.webEngine,
            onSelect = viewModel::setSearchWebEngine,
        )
    }
    ExpressiveRowDivider()
    FileSearchRow(checked = s.search.files, onCheckedChange = viewModel::setSearchFiles)
}

/** Foldable page (shared card): the size a floating window opens at. */
@Composable
internal fun FloatingSizeRow(s: LauncherSettings, viewModel: SettingsViewModel) {
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_floating_size),
        options = listOf(
            SizeChoice.SMALL to stringResource(R.string.settings_folder_open_small),
            SizeChoice.MEDIUM to stringResource(R.string.settings_folder_open_medium),
            SizeChoice.LARGE to stringResource(R.string.settings_folder_open_large),
        ),
        selected = s.launch.floatingWindowSize,
        onSelect = viewModel::setFloatingWindowSize,
    )
}

private fun homeBackgroundPrefix(screenType: ScreenType): String =
    "home_background_" + screenType.name.lowercase() + "_"

/** Removes every stored background picture of [screenType] (only one is ever current). */
private fun deleteHomeBackgrounds(context: Context, screenType: ScreenType) {
    val prefix = homeBackgroundPrefix(screenType)
    catching {
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
    return catching {
        // Copy first, then drop the older pictures: a failed copy must leave the current one in place.
        // A video keeps its extension: that is how home tells it from a picture.
        val target = File(context.filesDir, homeBackgroundPrefix(screenType) + System.currentTimeMillis() + suffix)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val copied = catching { input.use { source -> target.outputStream().use { source.copyTo(it) } } }.isSuccess
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
            BatterySaverMode.OFF to stringResource(R.string.settings_battery_saver_off),
            BatterySaverMode.AUTO to stringResource(R.string.settings_battery_saver_auto),
            BatterySaverMode.ON to stringResource(R.string.settings_battery_saver_on),
        ),
        selected = s.motion.batterySaverMode,
        onSelect = viewModel::setBatterySaverMode,
    )
    Text(
        text = stringResource(R.string.settings_battery_saver_desc),
        color = LocalExpressivePalette.current.dim,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    if (s.motion.batterySaverMode == BatterySaverMode.AUTO) {
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_battery_saver_low),
            subtitle = stringResource(R.string.settings_battery_saver_low_desc),
            checked = s.motion.batterySaverLowBattery,
            onCheckedChange = viewModel::setBatterySaverLowBattery,
        )
        ExpressiveRowDivider()
        ExpressiveSwitchRow(
            title = stringResource(R.string.settings_battery_saver_hot),
            subtitle = stringResource(R.string.settings_battery_saver_hot_desc),
            checked = s.motion.batterySaverWhenHot,
            onCheckedChange = viewModel::setBatterySaverWhenHot,
        )
    }
    ExpressiveRowDivider()
    ExpressiveSliderRow(
        title = stringResource(R.string.settings_animation_speed),
        value = s.motion.animationSpeed,
        onValueChange = { viewModel.setAnimationSpeed((it * 4f).roundToInt() / 4f) },
        valueRange = LauncherSettings.MIN_ANIMATION_SPEED..LauncherSettings.MAX_ANIMATION_SPEED,
        valueDisplay = "%.2f×".format(s.motion.animationSpeed),
        subtitle = stringResource(R.string.settings_animation_speed_desc),
    )
    ExpressiveRowDivider()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_app_open_animation),
        options = listOf(
            AppOpenAnimation.SYSTEM to stringResource(R.string.settings_anim_system),
            AppOpenAnimation.ICON to stringResource(R.string.settings_anim_icon),
            AppOpenAnimation.REVEAL to stringResource(R.string.settings_anim_reveal),
            AppOpenAnimation.ZOOM to stringResource(R.string.settings_anim_zoom),
            AppOpenAnimation.SLIDE to stringResource(R.string.settings_anim_slide),
            AppOpenAnimation.FADE to stringResource(R.string.settings_anim_fade),
            AppOpenAnimation.POP to stringResource(R.string.settings_anim_pop),
            AppOpenAnimation.UNFOLD to stringResource(R.string.settings_anim_unfold),
            AppOpenAnimation.NONE to stringResource(R.string.settings_anim_none),
        ),
        selected = s.motion.appOpenAnimation,
        onSelect = viewModel::setAppOpenAnimation,
    )
    ExpressiveRowDivider()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_folder_open_animation),
        options = listOf(
            FolderAnimation.SHEET to stringResource(R.string.settings_anim_sheet),
            FolderAnimation.ZOOM to stringResource(R.string.settings_anim_zoom),
            FolderAnimation.FADE to stringResource(R.string.settings_anim_fade),
            FolderAnimation.NONE to stringResource(R.string.settings_anim_none),
        ),
        selected = s.folders.openAnimation,
        onSelect = viewModel::setFolderOpenAnimation,
    )
    ExpressiveRowDivider()
    ExpressiveSegmentedControl(
        title = stringResource(R.string.settings_page_scroll_effect),
        options = listOf(
            PageEffect.SLIDE to stringResource(R.string.settings_anim_slide),
            PageEffect.FADE to stringResource(R.string.settings_anim_fade),
            PageEffect.ZOOM to stringResource(R.string.settings_anim_zoom),
            PageEffect.CUBE to stringResource(R.string.settings_anim_cube),
            PageEffect.STACK to stringResource(R.string.settings_anim_stack),
        ),
        selected = s.home.pageScrollEffect,
        onSelect = viewModel::setPageScrollEffect,
    )
    ExpressiveRowDivider()
    ExpressiveSwitchRow(
        title = stringResource(R.string.settings_unfold_animation),
        subtitle = stringResource(R.string.settings_unfold_animation_desc),
        checked = s.home.unfoldAnimation,
        onCheckedChange = viewModel::setUnfoldAnimation,
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

/** The file types home plays as a video background (as VideoBackground.kt decides). */
private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "3gp", "mov")
