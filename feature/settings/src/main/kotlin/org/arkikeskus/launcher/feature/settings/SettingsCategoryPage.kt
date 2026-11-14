package org.arkikeskus.launcher.feature.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import org.arkikeskus.launcher.data.IconPackInfo
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.ScreenType

// One settings page, the same in the two-pane and the one-column layout (split out of
// SettingsScreen.kt): which page a category shows, and the page under its pinned live preview.

/**
 * The page of [category]. Settings that are per display are saved for [profile]; a row that opens a
 * picker or a manager asks for it through [onDialog], and the Start page's shortcuts open another
 * page through [onOpen].
 */
@Composable
internal fun SettingsCategoryPage(
    category: SettingsCategory,
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    profile: ScreenType,
    allApps: List<AppItem>,
    hiddenCount: Int,
    iconPacks: List<IconPackInfo>,
    previewIcon: ImageBitmap?,
    isDefaultLauncher: Boolean,
    notifAccessGranted: Boolean,
    onSetDefault: () -> Unit,
    onNotifAccess: () -> Unit,
    onOpen: (SettingsCategory) -> Unit,
    onDialog: (SettingsDialog) -> Unit,
) {
    when (category) {
        SettingsCategory.START -> StartSection(
            s = s,
            viewModel = viewModel,
            isDefaultLauncher = isDefaultLauncher,
            notifAccessGranted = notifAccessGranted,
            onSetDefault = onSetDefault,
            onNotifAccess = onNotifAccess,
            onOpen = onOpen,
        )
        SettingsCategory.THEME -> ThemePage(s = s, viewModel = viewModel, screenType = profile)
        SettingsCategory.ICONS -> IconsPage(
            s = s,
            viewModel = viewModel,
            screenType = profile,
            iconPacks = iconPacks,
            previewIcon = previewIcon,
            onIconPack = { onDialog(SettingsDialog.IconPack) },
        )
        SettingsCategory.MOTION -> MotionPage(s = s, viewModel = viewModel, screenType = profile)
        SettingsCategory.HOME_SCREEN -> HomeScreenPage(
            s = s,
            viewModel = viewModel,
            screenType = profile,
            onPickColor = { onDialog(SettingsDialog.PickColor(it)) },
            onCountStyle = { onDialog(SettingsDialog.CountStyle) },
            onPeoplePrivacy = { onDialog(SettingsDialog.PeoplePrivacy) },
            onBatchTimes = { onDialog(SettingsDialog.BatchTimes) },
        )
        SettingsCategory.DOCK_FOLDERS -> DockFoldersPage(s = s, viewModel = viewModel, screenType = profile)
        SettingsCategory.GESTURES -> GesturesSection(
            s = s,
            viewModel = viewModel,
            screenType = profile,
            allApps = allApps,
            onSwipeUp = { onDialog(SettingsDialog.SwipeUp) },
            onSwipeDown = { onDialog(SettingsDialog.SwipeDown) },
            onLeftSwipe = { onDialog(SettingsDialog.LeftSwipeApp) },
            onRightSwipe = { onDialog(SettingsDialog.RightSwipeApp) },
        )
        SettingsCategory.APP_DRAWER -> AppDrawerSection(
            s = s,
            viewModel = viewModel,
            screenType = profile,
            hiddenCount = hiddenCount,
            onShowHidden = { onDialog(SettingsDialog.HiddenApps) },
            onLayoutMode = { onDialog(SettingsDialog.DrawerLayout) },
            onPickColor = { onDialog(SettingsDialog.PickColor(it)) },
        )
        SettingsCategory.TASKBAR -> TaskbarSection(s = s, viewModel = viewModel)
        SettingsCategory.RECENTS -> RecentsSection(s = s, viewModel = viewModel)
        SettingsCategory.SPLIT -> SplitPage(s = s, viewModel = viewModel, onAppPairs = { onDialog(SettingsDialog.AppPairs) })
        SettingsCategory.DISPLAY_PROFILES -> DisplayProfilesSection(
            s = s,
            viewModel = viewModel,
            screenType = profile,
            onFoldAction = { onDialog(SettingsDialog.FoldAction) },
            onUnfoldAction = { onDialog(SettingsDialog.UnfoldAction) },
            onFoldTransition = { onDialog(SettingsDialog.FoldTransition) },
            onCopyProfile = { onDialog(SettingsDialog.CopyProfile) },
        )
        SettingsCategory.SYSTEM -> SystemPage(s = s, viewModel = viewModel)
        SettingsCategory.PERMISSIONS -> PermissionsSection(
            isDefaultLauncher = isDefaultLauncher,
            notifAccessGranted = notifAccessGranted,
            onSetDefault = onSetDefault,
            onNotifAccess = onNotifAccess,
        )
        SettingsCategory.ROOT -> RootSection(s = s, viewModel = viewModel)
        SettingsCategory.SUPPORT -> SupportPage(s = s, viewModel = viewModel)
        SettingsCategory.BACKUP -> BackupSection()
    }
}

/**
 * A page with its live preview pinned on top: the preview stays put while the settings scroll under
 * it, and shows each change as it is made (CategoryPreview.kt). Categories that hold only shared
 * settings don't offer the display profile choice ([profileSwitcher]).
 */
@Composable
internal fun PageWithPreview(
    category: SettingsCategory,
    scroll: ScrollState,
    modifier: Modifier,
    compact: Boolean,
    s: LauncherSettings,
    previewIcon: ImageBitmap?,
    allApps: List<AppItem>,
    collapsed: MutableState<Boolean>,
    profileSwitcher: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val overrides = remember(category) { PreviewOverrides() }
    CompositionLocalProvider(LocalPreviewOverrides provides overrides) {
        Column(modifier = modifier) {
            PinnedCategoryPreview(
                category = category,
                settings = s,
                previewIcon = previewIcon,
                allApps = allApps,
                compact = compact,
                collapsed = collapsed,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(bottom = 28.dp),
            ) {
                if (category.perDisplay) profileSwitcher()
                content()
            }
        }
    }
}
