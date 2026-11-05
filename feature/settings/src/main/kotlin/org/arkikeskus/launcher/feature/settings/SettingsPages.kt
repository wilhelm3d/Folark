package org.arkikeskus.launcher.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import org.arkikeskus.launcher.data.IconPackInfo
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType

// The pages of Settings, put together from the cards. Each page reads top to bottom from the
// settings used most to the ones set once; its live preview is pinned above it (CategoryPreview.kt).

/** Look & feel ▸ Theme: presets first, then glass, dark mode, menus, the home backdrop and fonts. */
@Composable
internal fun ThemePage(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    ThemePresetsCard(s = s, viewModel = viewModel, screenType = screenType)
    LiquidGlassEverywhereSection(s, viewModel)
    BaseLookCard(s = s, viewModel = viewModel, screenType = screenType)
    MenusAndStacksCard(s = s, viewModel = viewModel, screenType = screenType)
    HomeBackdropCard(s = s, viewModel = viewModel, screenType = screenType)
    TypographySection(s = s, viewModel = viewModel, screenType = screenType)
    SettingsLookCard(s = s, viewModel = viewModel, screenType = screenType)
}

/** Look & feel ▸ Icons & badges: the icons themselves, then the dots and notifications on them. */
@Composable
internal fun IconsPage(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType,
    iconPacks: List<IconPackInfo>,
    previewIcon: ImageBitmap?,
    onIconPack: () -> Unit,
) {
    IconsCard(s = s, viewModel = viewModel, screenType = screenType, iconPacks = iconPacks, onIconPack = onIconPack)
    NotificationsSection(s = s, viewModel = viewModel, screenType = screenType, previewIcon = previewIcon)
}

/** Look & feel ▸ Motion & haptics. */
@Composable
internal fun MotionPage(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    AnimationsCard(s = s, viewModel = viewModel, screenType = screenType)
    HapticsSection(s = s, viewModel = viewModel)
}

/** Home screen ▸ Home screen: grid and size, pages, the status bar and the widgets. */
@Composable
internal fun HomeScreenPage(
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    screenType: ScreenType,
    onPickColor: (ColorTarget) -> Unit,
    onCountStyle: () -> Unit,
    onPeoplePrivacy: () -> Unit,
    onBatchTimes: () -> Unit,
) {
    GridSizeCard(s = s, viewModel = viewModel, screenType = screenType, onPickColor = onPickColor)
    HomePagesCard(s = s, viewModel = viewModel, screenType = screenType)
    StatusBarCard(s = s, viewModel = viewModel, screenType = screenType)
    WidgetsCard(
        s = s,
        viewModel = viewModel,
        screenType = screenType,
        onCountStyle = onCountStyle,
        onPeoplePrivacy = onPeoplePrivacy,
        onBatchTimes = onBatchTimes,
    )
    FolarkWidgetsSection(s = s, viewModel = viewModel)
}

/** Home screen ▸ Dock & folders. */
@Composable
internal fun DockFoldersPage(s: LauncherSettings, viewModel: SettingsViewModel, screenType: ScreenType) {
    DockCard(s = s, viewModel = viewModel, screenType = screenType)
    FoldersCard(s = s, viewModel = viewModel, screenType = screenType)
}

/**
 * Apps & multitasking ▸ Split screen & peek: everything about two or three apps side by side in
 * one place, wherever the split is started from (an edge, Recents, the taskbar, a Scene).
 */
@Composable
internal fun SplitPage(s: LauncherSettings, viewModel: SettingsViewModel, onAppPairs: () -> Unit) {
    SplitScreenSection(s, viewModel)
    RecentsSplitCard(s, viewModel)
    TaskbarSplitCard(s, viewModel)
    LaunchModesCard(s, viewModel, onAppPairs)
}

/** Phone & system ▸ System: Android settings Folark can change, and Motorola's own. */
@Composable
internal fun SystemPage(s: LauncherSettings, viewModel: SettingsViewModel) {
    SystemControlsSection()
    MotorolaSection(s = s, viewModel = viewModel)
}
