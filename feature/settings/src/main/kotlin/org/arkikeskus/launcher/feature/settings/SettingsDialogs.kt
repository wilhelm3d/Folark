package org.arkikeskus.launcher.feature.settings

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.arkikeskus.launcher.data.IconPackInfo
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.CountStyle
import org.arkikeskus.launcher.model.DrawerLayout
import org.arkikeskus.launcher.model.DrawerTheme
import org.arkikeskus.launcher.model.FoldAction
import org.arkikeskus.launcher.model.FoldTransition
import org.arkikeskus.launcher.model.HomeGesture
import org.arkikeskus.launcher.model.LauncherSettings
import org.arkikeskus.launcher.model.PeoplePrivacy
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.ui.expressive.Accent
import org.arkikeskus.launcher.ui.expressive.LocalExpressivePalette

// What opens over the settings pages (split out of SettingsScreen.kt): one state for all of it, as
// only one can be open at a time, and the dialogs themselves.

/** What is open over the settings pages: a full-screen manager, a picker, or a dialog. */
internal sealed interface SettingsDialog {
    /** Full-screen: Settings ▸ App drawer ▸ hidden apps. */
    data object HiddenApps : SettingsDialog
    /** Full-screen: Settings ▸ Split screen ▸ app pairs. */
    data object AppPairs : SettingsDialog
    data object LeftSwipeApp : SettingsDialog
    data object RightSwipeApp : SettingsDialog
    data object IconPack : SettingsDialog
    data object CountStyle : SettingsDialog
    data object PeoplePrivacy : SettingsDialog
    data object DrawerLayout : SettingsDialog
    data object BatchTimes : SettingsDialog
    data object SwipeDown : SettingsDialog
    data object SwipeUp : SettingsDialog
    data object FoldAction : SettingsDialog
    data object UnfoldAction : SettingsDialog
    data object FoldTransition : SettingsDialog
    data object CopyProfile : SettingsDialog
    data class PickColor(val target: ColorTarget) : SettingsDialog
}

/**
 * The picker or dialog [dialog] stands for (the full-screen managers are drawn by SettingsScreen
 * itself). Every choice is saved for the display profile [profile] where the setting is per display;
 * [onDismiss] closes it.
 */
@Composable
internal fun SettingsDialogHost(
    dialog: SettingsDialog,
    s: LauncherSettings,
    viewModel: SettingsViewModel,
    profile: ScreenType,
    allApps: List<AppItem>,
    iconPacks: List<IconPackInfo>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalExpressivePalette.current
    when (dialog) {
        SettingsDialog.HiddenApps, SettingsDialog.AppPairs -> Unit
        SettingsDialog.LeftSwipeApp -> EdgeSwipeAppPicker(
            titleRes = R.string.settings_left_edge_pick,
            noneRes = R.string.settings_left_edge_none,
            apps = allApps,
            onPick = { key ->
                viewModel.setLeftSwipeAppKey(key, profile)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.RightSwipeApp -> EdgeSwipeAppPicker(
            titleRes = R.string.settings_right_edge_pick,
            noneRes = R.string.settings_right_edge_none,
            apps = allApps,
            onPick = { key ->
                viewModel.setRightSwipeAppKey(key, profile)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.IconPack -> IconPackPicker(
            packs = iconPacks,
            selected = s.icons.iconPack,
            onPick = { pkg ->
                viewModel.setIconPack(pkg, profile)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.CountStyle -> ChoicePicker(
            title = R.string.settings_notif_widget_count,
            options = listOf(
                CountStyle.NUMBER to R.string.settings_notif_widget_count_number,
                CountStyle.DOT to R.string.settings_notif_widget_count_dot,
                CountStyle.NONE to R.string.settings_notif_widget_count_none,
            ),
            selected = s.widgets.notificationCountStyle,
            onPick = {
                viewModel.setNotificationWidgetCountStyle(it, profile)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.DrawerLayout -> ChoicePicker(
            title = R.string.settings_drawer_layout_mode,
            options = listOf(
                DrawerLayout.GRID to R.string.settings_drawer_layout_grid,
                DrawerLayout.LIST to R.string.settings_drawer_layout_list,
            ),
            selected = s.drawer.layoutMode,
            onPick = {
                viewModel.setDrawerLayoutMode(it, profile)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.BatchTimes -> BatchTimesEditor(
            initial = s.widgets.peopleBatchTimes.replace(",", ", "),
            onSave = {
                viewModel.setPeopleBatchTimes(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.PeoplePrivacy -> ChoicePicker(
            title = R.string.settings_people_privacy,
            options = listOf(
                PeoplePrivacy.ALL to R.string.settings_people_privacy_all,
                PeoplePrivacy.SENDER to R.string.settings_people_privacy_sender,
                PeoplePrivacy.COUNT to R.string.settings_people_privacy_count,
            ),
            selected = s.widgets.peoplePrivacy,
            onPick = {
                viewModel.setPeoplePrivacy(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsDialog.SwipeDown -> ChoicePicker(
            title = R.string.settings_swipe_down_action,
            options = gestureOptions(context),
            selected = s.home.swipeDownAction,
            onPick = { viewModel.setSwipeDownAction(it, profile); onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.SwipeUp -> ChoicePicker(
            title = R.string.settings_swipe_up_action,
            options = listOf(
                HomeGesture.DRAWER to R.string.settings_gesture_drawer,
                HomeGesture.SEARCH to R.string.settings_gesture_search,
                HomeGesture.NONE to R.string.settings_gesture_none,
            ),
            selected = s.home.swipeUpAction,
            onPick = { viewModel.setSwipeUpAction(it, profile); onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.FoldAction -> ChoicePicker(
            title = R.string.settings_fold_action,
            options = listOf(
                FoldAction.STAY to R.string.settings_action_stay,
                FoldAction.PAGE_0 to R.string.settings_action_page_0,
                FoldAction.LOCK to R.string.settings_action_lock,
            ),
            selected = s.fold.foldAction,
            onPick = { viewModel.setFoldAction(it, profile); onDismiss() },
            onDismiss = onDismiss,
        )
        SettingsDialog.UnfoldAction -> ChoicePicker(
            title = R.string.settings_unfold_action,
            options = listOf(
                FoldAction.STAY to R.string.settings_action_stay,
                FoldAction.SEARCH to R.string.settings_action_search,
                FoldAction.DRAWER to R.string.settings_action_drawer,
            ),
            selected = s.fold.unfoldAction,
            onPick = { viewModel.setUnfoldAction(it, profile); onDismiss() },
            onDismiss = onDismiss,
        )
        is SettingsDialog.PickColor -> {
            val target = dialog.target
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
                            viewModel.setDrawerThemeTint(DrawerTheme.CUSTOM, profile)
                        }
                        ColorTarget.APP_LABEL -> viewModel.setAppLabelColor(argb, profile)
                    }
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        SettingsDialog.CopyProfile -> {
            val source = if (profile == ScreenType.OUTER) ScreenType.INNER else ScreenType.OUTER
            val copiedMsg = stringResource(R.string.settings_copy_profile_done)
            AlertDialog(
                onDismissRequest = onDismiss,
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
                        onDismiss()
                        viewModel.copyProfile(from = source, to = profile) {
                            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.settings_copy_profile_confirm), color = Accent) }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_cancel), color = palette.dim)
                    }
                },
            )
        }
        SettingsDialog.FoldTransition -> ChoicePicker(
            title = R.string.settings_fold_transition,
            options = listOf(
                FoldTransition.MORPH_SCALE_FADE to R.string.settings_transition_morph_scale_fade,
                FoldTransition.BOOK_UNFOLD_SWEEP to R.string.settings_transition_book_unfold_sweep,
                FoldTransition.AQUAMORPHIC_RIPPLE to R.string.settings_transition_aquamorphic_ripple,
                FoldTransition.CROSSFADE to R.string.settings_transition_crossfade,
                FoldTransition.SCALE to R.string.settings_transition_scale,
                FoldTransition.SLIDE to R.string.settings_transition_slide,
            ) + listOfNotNull(
                // Inner display: follows the hinge while opening (phones with a hinge sensor).
                // Outer display: the same effects, played on a timer after closing.
                if (profile == ScreenType.INNER) {
                    (FoldTransition.LIVE_HINGE to R.string.settings_transition_live_hinge)
                        .takeIf { hasHingeSensor(context) }
                } else {
                    FoldTransition.LIVE_HINGE to R.string.settings_transition_hinge_timed
                },
            ),
            selected = s.fold.transitionStyle,
            onPick = { viewModel.setFoldTransitionStyle(it, profile); onDismiss() },
            onDismiss = onDismiss,
        )
    }
}
