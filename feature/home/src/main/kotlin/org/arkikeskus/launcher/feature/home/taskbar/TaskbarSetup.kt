package org.arkikeskus.launcher.feature.home.taskbar

import org.arkikeskus.launcher.feature.home.*
import org.arkikeskus.launcher.feature.home.R

import org.arkikeskus.launcher.model.AppPair
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.drop
import org.arkikeskus.launcher.data.AppRepository
import org.arkikeskus.launcher.data.AppUsageRepository
import org.arkikeskus.launcher.data.LauncherAppsSource
import org.arkikeskus.launcher.data.NotificationBadgeRepository
import org.arkikeskus.launcher.data.SettingsRepository
import org.arkikeskus.launcher.data.SplitCoordinator
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.DropZones

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AppTaskbarEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun appRepository(): AppRepository
    fun splitCoordinator(): SplitCoordinator
    fun appUsageRepository(): AppUsageRepository
    fun launcherAppsSource(): LauncherAppsSource
    fun notificationBadgeRepository(): NotificationBadgeRepository
}

/** Everything the bar draws from, in one value so a change rebuilds it once. */
internal data class TaskbarSetup(
    val enabled: Boolean,
    val pinned: List<AppItem>,
    val recentCount: Int,
    val iconSizeDp: Int,
    val spacingDp: Int,
    val bottomDp: Int,
    val clearInset: Boolean,
    val theme: String,
    val opacity: Float,
    val glassBlur: Int,
    val handles: String,
    val handleHint: Boolean,
    val handleStyle: String,
    val hideButton: Boolean,
    /** Pinned apps can't be added, removed or moved from the bar or by drops (Settings only). */
    val pinsLocked: Boolean = false,
    /** A swipe down on the opened bar hides it. */
    val swipeDownHide: Boolean = true,
    /** Open (recent) apps as pictures of the app instead of icons. */
    val openPreviews: Boolean = false,
    val taskbarAnim: String,
    /** The glow when an app starts from the bar (LauncherSettings.LAUNCH_GLOW_*). */
    val launchGlow: String = org.arkikeskus.launcher.model.LauncherSettings.LAUNCH_GLOW_APP,
    val onHome: Boolean,
    val keepOpen: Boolean,
    val themedIcons: Boolean,
    val iconPack: String,
    val customIcons: Map<String, String>,
    /** Per-app icon shapes (app key → shape), over [iconShape]. */
    val iconShapes: Map<String, String> = emptyMap(),
    val iconShape: String,
    /** Settings ▸ Shape every icon: plain icons on a plate of their colour, in the shape. */
    val shapeAllIcons: Boolean = true,
    val launchHistory: List<String>,
    val mediaEnabled: Boolean,
    val mediaShowPaused: Boolean,
    val mediaPosition: String,
    /** The mark between pinned and recent apps (LauncherSettings.TASKBAR_DIVIDER_*). */
    val dividerStyle: String = org.arkikeskus.launcher.model.LauncherSettings.TASKBAR_DIVIDER_LINE,
    /** Which split dividers are locked (LauncherSettings.DIVIDER_LOCK_*), for the split menu's toggle. */
    val dividerLock: String = org.arkikeskus.launcher.model.LauncherSettings.DIVIDER_LOCK_OFF,
    val mediaTap: String,
    val mediaControls: Boolean,
    val mediaShape: String,
    val mediaBadge: Boolean,
    val systemDrag: Boolean,
    val edgeSnap: Boolean,
    /** Which of the six drop zones are on, and the tick on entering one (shared with Recents). */
    val zones: DropZones = DropZones(),
    val recentsButton: String,
    val runningApps: Boolean,
    val runningDots: Boolean,
    val multiDots: Boolean,
    val quickSwitch: Boolean,
    val splitControls: Boolean,
    val splitSide: String,
    val threeApp: Boolean,
    val hideFullScreen: Boolean,
    val hiddenApps: Set<String>,
    val replaceRecents: Boolean,
    val alwaysApps: Set<String>,
    val autoHideApps: Set<String>,
    val miniApps: Set<String>,
    val liveEnabled: Boolean,
    val liveEffect: String,
    val liveExpand: Boolean,
    /** Leave out what Android shows as a status-bar chip (calls, Live updates). */
    val liveSkipChips: Boolean,
    val nudgeEnabled: Boolean,
    val nudgeEffect: String,
    val nudgeSoftness: Float,
    /** Saved app pairs / trios pinned on the bar (ids from settings, then the pairs themselves). */
    val pairIds: List<String>,
    /** Fixed scene slots at the start of the bar; 0 = just the pinned scenes. */
    val sceneSlots: Int = 0,
    val pairs: List<AppPair> = emptyList(),
    /** The slim bar on the cover screen ([coverCount] apps), and the split menu's divider sizes. */
    val coverBar: Boolean = false,
    val coverCount: Int = 5,
    val ratioMenu: Boolean = true,
    /** Long-press a live activity's card: "Split" opens it beside the last app. */
    val notificationSplit: Boolean = true,
)
