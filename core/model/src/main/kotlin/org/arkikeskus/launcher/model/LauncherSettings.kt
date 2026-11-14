package org.arkikeskus.launcher.model

/** User-configurable launcher settings (persisted in DataStore). */
import androidx.compose.runtime.Immutable

@Immutable
data class LauncherSettings(
    /** The dock row on home. */
    val dock: DockSettings = DockSettings(),
    /** The home screen: grid, pages, labels, wallpaper and gestures. */
    val home: HomeSettings = HomeSettings(),
    /** The app drawer. */
    val drawer: DrawerSettings = DrawerSettings(),
    /** What the drawer's search looks through. */
    val search: SearchSettings = SearchSettings(),
    /** App icons and their labels. */
    val icons: IconSettings = IconSettings(),
    /** Notification dots on icons. */
    val dots: DotSettings = DotSettings(),
    /** Home folders. */
    val folders: FolderSettings = FolderSettings(),
    /** Folark's built-in widgets. */
    val widgets: WidgetSettings = WidgetSettings(),
    /** Folark's own status bar and the system one. */
    val statusBar: StatusBarSettings = StatusBarSettings(),
    /** Folding: half-open mode, fold/unfold actions and the live hinge. */
    val fold: FoldSettings = FoldSettings(),
    /** Animations and effects. */
    val motion: MotionSettings = MotionSettings(),
    /** Theme, fonts, glass and per-surface density. */
    val appearance: AppearanceSettings = AppearanceSettings(),
    /** Folark's taskbar (and which taskbar is used). */
    val taskbar: TaskbarSettings = TaskbarSettings(),
    /** Live activities and notification pop-ups. */
    val live: LiveSettings = LiveSettings(),
    /** Folark Recents. */
    val recents: RecentsSettings = RecentsSettings(),
    /** Root features (all off unless root is enabled). */
    val root: RootSettings = RootSettings(),
    /** How apps open: split screen and floating windows. */
    val launch: LaunchSettings = LaunchSettings(),
) {

    companion object {
        const val MIN_SYSTEM_PADDING = 0
        const val MAX_SYSTEM_PADDING = 40
        const val MIN_COVER_BAR = 3
        const val MAX_COVER_BAR = 7
        /** Split engines. Automatic: the Vector module's native split when it runs in System UI,
         *  else WM Shell's commands (with the DUMP grant, or through root). */
        const val SPLIT_ENGINE_INTENT_ROUTING = 1
        /** WM Shell's split commands only (needs the one-time DUMP grant). */
        const val SPLIT_ENGINE_CLASSIC = 2
        /** The Vector module's native split only (commands only while the module isn't running). */
        const val SPLIT_ENGINE_LSPOSED = 3
        /** WM Shell's split commands through root (no DUMP grant needed). */
        const val SPLIT_ENGINE_ROOT = 4
        val SPLIT_ENGINES = listOf(SPLIT_ENGINE_INTENT_ROUTING, SPLIT_ENGINE_CLASSIC, SPLIT_ENGINE_LSPOSED, SPLIT_ENGINE_ROOT)

        const val MIN_ICON_SIZE_SCALE = 0.7f
        const val MAX_ICON_SIZE_SCALE = 1.4f

        const val MAX_STACK_CORNER_RADIUS = 32
        const val MAX_STACK_AUTO_ROTATE_SECONDS = 120

        const val MAX_HOME_WALLPAPER_DIM = 0.8f
        const val MAX_HOME_WALLPAPER_BLUR = 40f

        const val MIN_AZ_INDEX_SCALE = 0.7f
        const val MAX_AZ_INDEX_SCALE = 2f
        const val DRAWER_CORNER_AUTO = -1
        const val MAX_DRAWER_BOTTOM_SPACE = 120
        /** "With system" also kicks the saver in at or below this battery %, when not charging. */
        const val BATTERY_SAVER_LOW_PERCENT = 20

        const val MIN_RECENTS_COLUMNS = 1
        const val MAX_RECENTS_COLUMNS_INNER = 6
        const val MAX_RECENTS_COLUMNS_OUTER = 2
        const val MIN_RECENTS_APPS = 4
        const val MAX_RECENTS_APPS = 30
        const val MIN_RECENTS_CARD_HEIGHT = 160
        const val MAX_RECENTS_CARD_HEIGHT = 440
        const val MAX_RECENTS_CORNER_DP = 40
        const val MIN_RECENTS_LABEL_SCALE = 0.7f
        const val MAX_RECENTS_LABEL_SCALE = 1.5f
        const val MIN_RECENTS_ANIM_SPEED = 0.25f
        const val MAX_RECENTS_GLASS_BLUR = 80
        const val MIN_RECENTS_GLASS_TINT = 0.04f
        const val MAX_RECENTS_GLASS_TINT = 0.4f
        const val MIN_RECENTS_ATTACHED_PEEK = 0.05f
        const val MAX_RECENTS_ATTACHED_PEEK = 0.3f
        const val MAX_RECENTS_ANIM_SPEED = 2.5f

        const val MAX_DRAWER_CORNER_RADIUS = 56
        const val MIN_ANIMATION_SPEED = 0.5f
        const val MAX_ANIMATION_SPEED = 2f

        const val MIN_FOLDER_OPEN_COLUMNS = 3
        const val MAX_FOLDER_OPEN_COLUMNS = 6

        const val MIN_DPI_SCALE = 0.7f
        const val MAX_DPI_SCALE = 1.4f

        const val DEFAULT_DRAWER_ACCENT = 0xFF00B0FF.toInt()
        const val DEFAULT_DRAWER_CUSTOM_COLOR = 0xFF1B2A41.toInt()
        const val DEFAULT_DRAWER_PANEL_OPACITY = 0.55f
        const val MIN_DRAWER_PANEL_OPACITY = 0.15f
        const val MAX_DRAWER_PANEL_OPACITY = 0.95f
        /** Upper bound of [Drawer.backgroundBlur] (dp of wallpaper blur behind the open drawer). */
        const val MAX_DRAWER_BLUR = 25f

        const val MIN_FOLD_TRANSITION_SPEED = 0.1f
        const val MAX_FOLD_TRANSITION_SPEED = 1.5f
        const val MIN_LIVE_HINGE_END_ANGLE = 120
        const val MAX_LIVE_HINGE_SMOOTHING_MS = 120
        const val MAX_LIVE_HINGE_BLUR_DP = 48
        const val MAX_LIVE_HINGE_FROST = 0.7f

        const val DEFAULT_BATCH_TIMES = "08:00,12:00,17:00"
    }
}
