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

        const val UNLOCK_STYLE_DISPLAY = "display"
        const val UNLOCK_STYLE_ZOOM = "zoom"
        const val UNLOCK_STYLE_RISE = "rise"
        const val UNLOCK_STYLE_BLOOM = "bloom"
        const val UNLOCK_STYLE_SWEEP = "sweep"
        const val UNLOCK_STYLE_FOCUS = "focus"
        val UNLOCK_STYLES = listOf(
            UNLOCK_STYLE_DISPLAY, UNLOCK_STYLE_ZOOM, UNLOCK_STYLE_RISE,
            UNLOCK_STYLE_BLOOM, UNLOCK_STYLE_SWEEP, UNLOCK_STYLE_FOCUS,
        )

        const val MINUS_ONE_NONE = "none"
        const val MINUS_ONE_GOOGLE = "google"
        const val MINUS_ONE_SMART_FEED = "smart_feed"
        val MINUS_ONE_OPTIONS = listOf(MINUS_ONE_NONE, MINUS_ONE_GOOGLE, MINUS_ONE_SMART_FEED)

        const val DRAWER_LAYOUT_GRID = "grid"
        const val DRAWER_LAYOUT_LIST = "list"

        const val AZ_POSITION_VERTICAL_RIGHT = "VERTICAL_RIGHT"
        const val AZ_POSITION_VERTICAL_LEFT = "VERTICAL_LEFT"
        const val AZ_POSITION_HORIZONTAL_TOP = "HORIZONTAL_TOP"
        const val AZ_POSITION_HORIZONTAL_BOTTOM = "HORIZONTAL_BOTTOM"

        const val DOCK_ALIGNMENT_CENTER = "center"
        const val DOCK_ALIGNMENT_LEFT = "left"
        const val DOCK_ALIGNMENT_RIGHT = "right"
        const val DOCK_ALIGNMENT_FLOATING = "floating"

        const val MIN_ICON_SIZE_SCALE = 0.7f
        const val MAX_ICON_SIZE_SCALE = 1.4f

        const val FOLDER_SHAPE_SQUIRCLE = "squircle"
        const val FOLDER_SHAPE_CIRCLE = "circle"
        const val FOLDER_SHAPE_SQUARE = "square"
        val FOLDER_SHAPES = setOf(FOLDER_SHAPE_SQUIRCLE, FOLDER_SHAPE_CIRCLE, FOLDER_SHAPE_SQUARE)

        const val FOLDER_THEME_GLASS = "glass"
        const val FOLDER_THEME_DARK = "dark"
        const val FOLDER_THEME_LIGHT = "light"
        const val FOLDER_THEME_ACCENT = "accent"
        val FOLDER_THEMES = setOf(FOLDER_THEME_GLASS, FOLDER_THEME_DARK, FOLDER_THEME_LIGHT, FOLDER_THEME_ACCENT)

        /** Closed-tile preview: a 2×2 or 3×3 grid of the first apps, or the first app alone as a cover. */
        const val FOLDER_PREVIEW_GRID_2 = "grid2"
        const val FOLDER_PREVIEW_GRID_3 = "grid3"
        const val FOLDER_PREVIEW_COVER = "cover"
        val FOLDER_PREVIEWS = setOf(FOLDER_PREVIEW_GRID_2, FOLDER_PREVIEW_GRID_3, FOLDER_PREVIEW_COVER)

        const val FOLDER_OPEN_SMALL = "small"
        const val FOLDER_OPEN_MEDIUM = "medium"
        const val FOLDER_OPEN_LARGE = "large"
        val FOLDER_OPEN_SIZES = setOf(FOLDER_OPEN_SMALL, FOLDER_OPEN_MEDIUM, FOLDER_OPEN_LARGE)

        const val POPUP_THEME_CLASSIC = "classic"
        const val POPUP_THEME_MATERIAL = "material"
        const val POPUP_THEME_DARK = "dark"
        const val POPUP_THEME_LIGHT = "light"
        /** Liquid Glass: see-through over a soft dark tint, a bright rim and a sheen. */
        const val POPUP_THEME_GLASS = "glass"
        val POPUP_THEMES = setOf(POPUP_THEME_CLASSIC, POPUP_THEME_MATERIAL, POPUP_THEME_DARK, POPUP_THEME_LIGHT, POPUP_THEME_GLASS)

        const val MAX_STACK_CORNER_RADIUS = 32
        const val MAX_STACK_AUTO_ROTATE_SECONDS = 120
        const val STACK_INDICATOR_DOTS = "dots"
        const val STACK_INDICATOR_BAR = "bar"
        const val STACK_INDICATOR_NONE = "none"
        val STACK_INDICATORS = setOf(STACK_INDICATOR_DOTS, STACK_INDICATOR_BAR, STACK_INDICATOR_NONE)

        const val DOT_SHAPE_CIRCLE = "circle"
        const val DOT_SHAPE_SQUIRCLE = "squircle"
        const val DOT_SHAPE_SQUARE = "square"
        val DOT_SHAPES = setOf(DOT_SHAPE_CIRCLE, DOT_SHAPE_SQUIRCLE, DOT_SHAPE_SQUARE)
        const val DOT_COLOR_RED = "red"
        const val DOT_COLOR_ACCENT = "accent"
        const val DOT_COLOR_WHITE = "white"
        val DOT_COLORS = setOf(DOT_COLOR_RED, DOT_COLOR_ACCENT, DOT_COLOR_WHITE)
        const val DOT_POSITION_TOP_END = "top_end"
        const val DOT_POSITION_TOP_START = "top_start"
        const val DOT_POSITION_BOTTOM_END = "bottom_end"
        const val DOT_POSITION_BOTTOM_START = "bottom_start"
        val DOT_POSITIONS = setOf(DOT_POSITION_TOP_END, DOT_POSITION_TOP_START, DOT_POSITION_BOTTOM_END, DOT_POSITION_BOTTOM_START)

        const val MAX_HOME_WALLPAPER_DIM = 0.8f
        const val MAX_HOME_WALLPAPER_BLUR = 40f

        const val ICON_SWIPE_NONE = "none"
        const val ICON_SWIPE_MENU = "menu"
        const val ICON_SWIPE_FLOATING = "floating"
        const val ICON_SWIPE_SPLIT = "split"
        val ICON_SWIPES = setOf(ICON_SWIPE_NONE, ICON_SWIPE_MENU, ICON_SWIPE_FLOATING, ICON_SWIPE_SPLIT)

        const val FLOATING_SIZE_SMALL = "small"
        const val FLOATING_SIZE_MEDIUM = "medium"
        const val FLOATING_SIZE_LARGE = "large"
        val FLOATING_SIZES = setOf(FLOATING_SIZE_SMALL, FLOATING_SIZE_MEDIUM, FLOATING_SIZE_LARGE)
        const val MIN_AZ_INDEX_SCALE = 0.7f
        const val MAX_AZ_INDEX_SCALE = 2f
        const val DRAWER_CORNER_AUTO = -1
        const val MAX_DRAWER_BOTTOM_SPACE = 120
        const val THEMED_STYLE_CLASSIC = "classic"
        const val THEMED_STYLE_INVERTED = "inverted"
        const val THEMED_STYLE_GLASS = "glass"
        const val THEMED_STYLE_DUOTONE = "duotone"
        /** In each app's own colour: a bold plate of it, the glyph almost white. */
        const val THEMED_STYLE_OWN = "own"
        /** In each app's own colour, quietly: a pale (or deep, when dark) plate, the glyph in the colour. */
        const val THEMED_STYLE_SOFT = "soft"
        val THEMED_STYLES = listOf(
            THEMED_STYLE_CLASSIC, THEMED_STYLE_INVERTED, THEMED_STYLE_GLASS, THEMED_STYLE_DUOTONE,
            THEMED_STYLE_OWN, THEMED_STYLE_SOFT,
        )
        const val BATTERY_SAVER_OFF = "off"
        const val BATTERY_SAVER_AUTO = "auto"
        const val BATTERY_SAVER_ON = "on"
        /** "With system" also kicks the saver in at or below this battery %, when not charging. */
        const val BATTERY_SAVER_LOW_PERCENT = 20
        const val FONT_FAMILY_DEFAULT = "default"
        const val FONT_FAMILY_NDOT = "ndot"
        const val FONT_FAMILY_NTYPE = "ntype"
        const val FONT_FAMILY_SPACE_GROTESK = "space_grotesk"
        const val FONT_FAMILY_SERIF = "serif"
        const val FONT_FAMILY_MONOSPACE = "monospace"
        /** Fonts that ship on Motorola phones (/product/fonts); offered only where they exist. */
        const val FONT_FAMILY_MOTO_ROOKERY = "moto_rookery"
        const val FONT_FAMILY_MOTO_WORK_SANS = "moto_work_sans"
        const val FONT_FAMILY_MOTO_EXO2 = "moto_exo2"
        const val FONT_FAMILY_MOTO_ROBOTO_SLAB = "moto_roboto_slab"
        /** Families every Android phone carries (no font files of Folark's own). */
        const val FONT_FAMILY_CONDENSED = "condensed"
        const val FONT_FAMILY_LIGHT = "light"
        const val FONT_FAMILY_MEDIUM = "medium"
        const val FONT_FAMILY_CASUAL = "casual"
        const val FONT_FAMILY_SMALLCAPS = "smallcaps"
        const val FONT_FAMILY_CURSIVE = "cursive"
        /** Headings only: the same font as the rest of the text. */
        const val FONT_FAMILY_SAME = "same"
        val FONT_FAMILIES = listOf(
            FONT_FAMILY_DEFAULT, FONT_FAMILY_NDOT, FONT_FAMILY_NTYPE,
            FONT_FAMILY_SPACE_GROTESK, FONT_FAMILY_SERIF, FONT_FAMILY_MONOSPACE,
            FONT_FAMILY_CONDENSED, FONT_FAMILY_LIGHT, FONT_FAMILY_MEDIUM,
            FONT_FAMILY_CASUAL, FONT_FAMILY_SMALLCAPS, FONT_FAMILY_CURSIVE,
            FONT_FAMILY_MOTO_ROOKERY, FONT_FAMILY_MOTO_WORK_SANS, FONT_FAMILY_MOTO_EXO2,
            FONT_FAMILY_MOTO_ROBOTO_SLAB,
        )

        const val CLOCK_STYLE_CLASSIC = "classic"
        const val CLOCK_STYLE_DUO = "duo"
        const val CLOCK_STYLE_LIGHT = "light"
        const val CLOCK_STYLE_DOT = "dot"
        const val CLOCK_STYLE_STACKED = "stacked"
        const val CLOCK_STYLE_GLASS_PILL = "glass_pill"
        const val CLOCK_STYLE_OUTLINE = "outline"
        const val CLOCK_STYLE_VERTICAL_STACK = "vertical_stack"
        const val CLOCK_STYLE_NEUMORPHIC = "neumorphic"
        const val CLOCK_STYLE_NOTHING_NDOT = "nothing_ndot"

        const val TASKBAR_THEME_CLEAR = "clear"
        const val TASKBAR_THEME_FROSTED = "frosted"
        const val TASKBAR_THEME_DARK = "dark"
        const val TASKBAR_THEME_LIGHT = "light"
        const val TASKBAR_THEME_ACCENT = "accent"
        /** Liquid Glass: see-through with a bright rim and a sheen along the top. */
        const val TASKBAR_THEME_GLASS = "glass"
        /** Pure black, for OLED screens: the bar disappears into a dark app. */
        const val TASKBAR_THEME_AMOLED = "amoled"
        /** Near-black with a glowing accent-coloured rim. */
        const val TASKBAR_THEME_NEON = "neon"
        val TASKBAR_THEMES = listOf(
            TASKBAR_THEME_CLEAR, TASKBAR_THEME_FROSTED, TASKBAR_THEME_DARK, TASKBAR_THEME_LIGHT, TASKBAR_THEME_ACCENT,
            TASKBAR_THEME_GLASS, TASKBAR_THEME_AMOLED, TASKBAR_THEME_NEON,
        )
        const val TASKBAR_MEDIA_POS_START = "start"
        const val TASKBAR_MEDIA_POS_MIDDLE = "middle"
        const val DIVIDER_LOCK_OFF = "off"
        const val DIVIDER_LOCK_TWO = "two"
        const val DIVIDER_LOCK_THREE = "three"
        const val DIVIDER_LOCK_ALL = "all"
        val DIVIDER_LOCKS = listOf(DIVIDER_LOCK_OFF, DIVIDER_LOCK_TWO, DIVIDER_LOCK_THREE, DIVIDER_LOCK_ALL)
        const val TASKBAR_DIVIDER_LINE = "line"
        const val TASKBAR_DIVIDER_BOLD = "bold"
        const val TASKBAR_DIVIDER_DOT = "dot"
        const val TASKBAR_DIVIDER_GAP = "gap"
        const val TASKBAR_DIVIDER_NONE = "none"
        const val TASKBAR_MEDIA_POS_END = "end"
        const val TASKBAR_MEDIA_TAP_OPEN = "open"
        const val TASKBAR_MEDIA_TAP_PLAY_PAUSE = "play_pause"
        const val TASKBAR_MEDIA_SHAPE_CIRCLE = "circle"
        const val TASKBAR_MEDIA_SHAPE_ROUNDED = "rounded"

        const val TASKBAR_SOURCE_FOLARK = "folark"
        const val TASKBAR_SOURCE_SYSTEM = "system"
        const val TASKBAR_RECENTS_OFF = "off"
        const val TASKBAR_RECENTS_SYSTEM = "system"
        const val TASKBAR_RECENTS_DESKTOP = "desktop"
        /** Folark's own Recents screen. */
        const val TASKBAR_RECENTS_FOLARK = "folark"

        const val RECENTS_CARD_HEADER = "header"
        const val RECENTS_CARD_OVERLAY = "overlay"
        const val RECENTS_SORT_RECENT = "recent"
        const val RECENTS_SORT_NAME = "name"
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
        /** Recents themes: Folark's card grid, iPad (big windows in a row), Carousel (one big card
         *  in the middle), Deck (overlapping cards), Compact (a list), Switcher (big icons). */
        const val RECENTS_THEME_FOLARK = "folark"
        const val RECENTS_THEME_IPAD = "ipad"
        const val RECENTS_THEME_CAROUSEL = "carousel"
        const val RECENTS_THEME_DECK = "deck"
        const val RECENTS_THEME_COMPACT = "compact"
        const val RECENTS_THEME_SWITCHER = "switcher"
        const val RECENTS_THEME_STACK = "stack"
        const val RECENTS_THEME_ARC = "arc"
        /** Tall rounded cards you snap sideways, the app's icon and name above each. */
        const val RECENTS_THEME_PORTRAIT = "portrait"
        /** The middle card large and centred, its icon and name above it; neighbours peek in. */
        const val RECENTS_THEME_SPOTLIGHT = "spotlight"
        /** A row of larger cards, two or three in view, for the unfolded screen. */
        const val RECENTS_THEME_GALLERY = "gallery"
        val RECENTS_THEMES = listOf(
            RECENTS_THEME_FOLARK, RECENTS_THEME_IPAD, RECENTS_THEME_CAROUSEL,
            RECENTS_THEME_DECK, RECENTS_THEME_COMPACT, RECENTS_THEME_SWITCHER,
            RECENTS_THEME_STACK, RECENTS_THEME_ARC,
            RECENTS_THEME_PORTRAIT, RECENTS_THEME_SPOTLIGHT, RECENTS_THEME_GALLERY,
        )
        const val MAX_RECENTS_GLASS_BLUR = 80
        const val MIN_RECENTS_GLASS_TINT = 0.04f
        const val MAX_RECENTS_GLASS_TINT = 0.4f
        const val RECENTS_ACTION_NONE = "none"
        const val RECENTS_ACTION_CLOSE = "close"
        const val RECENTS_ACTION_SPLIT = "split"
        const val RECENTS_ACTION_POPUP = "popup"
        const val RECENTS_ACTION_UNSPLIT = "unsplit"
        const val RECENTS_ACTION_INFO = "info"
        const val RECENTS_ACTION_OPEN = "open"
        const val RECENTS_ACTION_PIN = "pin"
        /** A menu on the card: split screen, freeform, lock, close. */
        const val RECENTS_ACTION_MENU = "menu"
        /** Peek view: one app big, another peeking in from the edge (on a split card: that split). */
        const val RECENTS_ACTION_PEEK = "peek"
        /** Split 3 apps: pick two more (needs the three-app split switch and the SystemUI hook). */
        const val RECENTS_ACTION_SPLIT3 = "split3"
        /** Closes every other unpinned app (needs root, like Close). */
        const val RECENTS_ACTION_CLOSE_OTHERS = "close_others"
        /** Same as the Close all button. */
        const val RECENTS_ACTION_CLOSE_ALL = "close_all"
        val RECENTS_ACTIONS = listOf(
            RECENTS_ACTION_NONE, RECENTS_ACTION_MENU, RECENTS_ACTION_CLOSE, RECENTS_ACTION_SPLIT, RECENTS_ACTION_POPUP,
            RECENTS_ACTION_UNSPLIT, RECENTS_ACTION_PIN, RECENTS_ACTION_INFO, RECENTS_ACTION_OPEN,
            RECENTS_ACTION_PEEK, RECENTS_ACTION_SPLIT3, RECENTS_ACTION_CLOSE_OTHERS, RECENTS_ACTION_CLOSE_ALL,
        )
        const val RECENTS_ATTACHED_LEFT = "left"
        const val RECENTS_ATTACHED_RIGHT = "right"
        const val MIN_RECENTS_ATTACHED_PEEK = 0.05f
        const val MAX_RECENTS_ATTACHED_PEEK = 0.3f
        const val MAX_RECENTS_ANIM_SPEED = 2.5f

        /** Recents entrances: up from below, out of the distance, falling in diagonally, turning
         *  over, fanning out from a stack, coming into focus. */
        const val RECENTS_ENTER_RISE = "rise"
        const val RECENTS_ENTER_ZOOM = "zoom"
        const val RECENTS_ENTER_CASCADE = "cascade"
        const val RECENTS_ENTER_FLIP = "flip"
        const val RECENTS_ENTER_FAN = "fan"
        const val RECENTS_ENTER_FOCUS = "focus"
        const val RECENTS_ENTER_NONE = "none"
        /** A ripple running across the cards: each rises with a little overshoot, left to right. */
        const val RECENTS_ENTER_WAVE = "wave"
        /** Each card swings down into place from its top edge, like a sign on a hinge. */
        const val RECENTS_ENTER_SWING = "swing"
        /** Cards fall in from above and settle with a bounce. */
        const val RECENTS_ENTER_DROP = "drop"
        val RECENTS_ENTERS = listOf(
            RECENTS_ENTER_RISE, RECENTS_ENTER_ZOOM, RECENTS_ENTER_CASCADE, RECENTS_ENTER_FLIP,
            RECENTS_ENTER_FAN, RECENTS_ENTER_FOCUS, RECENTS_ENTER_WAVE, RECENTS_ENTER_SWING,
            RECENTS_ENTER_DROP, RECENTS_ENTER_NONE,
        )

        /** A card picked for split view: a hop, a spreading ring, lifted up, leaning to its side,
         *  a breathing halo, a little shake. */
        const val RECENTS_SELECT_POP = "pop"
        const val RECENTS_SELECT_RING = "ring"
        const val RECENTS_SELECT_LIFT = "lift"
        const val RECENTS_SELECT_TILT = "tilt"
        const val RECENTS_SELECT_GLOW = "glow"
        const val RECENTS_SELECT_JIGGLE = "jiggle"
        const val RECENTS_SELECT_NONE = "none"
        val RECENTS_SELECTS = listOf(
            RECENTS_SELECT_POP, RECENTS_SELECT_RING, RECENTS_SELECT_LIFT, RECENTS_SELECT_TILT,
            RECENTS_SELECT_GLOW, RECENTS_SELECT_JIGGLE, RECENTS_SELECT_NONE,
        )

        /** Button press animations: sink in, overshoot, a halo, a spreading ring, lean in, wobble. */
        const val PRESS_SHRINK = "shrink"
        const val PRESS_BOUNCE = "bounce"
        const val PRESS_GLOW = "glow"
        const val PRESS_RIPPLE = "ripple"
        const val PRESS_TILT = "tilt"
        const val PRESS_JELLY = "jelly"
        const val PRESS_NONE = "none"
        val PRESS_EFFECTS = listOf(PRESS_SHRINK, PRESS_BOUNCE, PRESS_GLOW, PRESS_RIPPLE, PRESS_TILT, PRESS_JELLY, PRESS_NONE)

        /** Settings ▸ Root: Android's way only, root when Android's way can't, or root first. */
        const val ROOT_MODE_OFF = "off"
        const val ROOT_MODE_FALLBACK = "fallback"
        const val ROOT_MODE_ALWAYS = "always"
        val ROOT_MODES = listOf(ROOT_MODE_OFF, ROOT_MODE_FALLBACK, ROOT_MODE_ALWAYS)
        const val TASKBAR_SPLIT_SIDE_LEFT = "left"
        const val TASKBAR_SPLIT_SIDE_RIGHT = "right"

        /** Settings look: follows the system light/dark theme. */
        const val SETTINGS_STYLE_STANDARD = "standard"
        /** Settings look: always dark charcoal, outlined cards, uppercase titles. */
        const val SETTINGS_STYLE_STUDIO = "studio"

        const val ICON_SHAPE_SYSTEM = "system"
        const val ICON_SHAPE_CIRCLE = "circle"
        const val ICON_SHAPE_SQUIRCLE = "squircle"
        const val ICON_SHAPE_ROUNDED = "rounded"
        const val ICON_SHAPE_TEARDROP = "teardrop"
        const val ICON_SHAPE_PEBBLE = "pebble"
        const val ICON_SHAPE_SQUARE = "square"
        const val ICON_SHAPE_ARCH = "arch"
        const val ICON_SHAPE_HEXAGON = "hexagon"
        const val ICON_SHAPE_DIAMOND = "diamond"
        const val ICON_SHAPE_COOKIE = "cookie"
        val ICON_SHAPES = listOf(
            ICON_SHAPE_SYSTEM, ICON_SHAPE_CIRCLE, ICON_SHAPE_SQUIRCLE,
            ICON_SHAPE_ROUNDED, ICON_SHAPE_TEARDROP, ICON_SHAPE_PEBBLE,
            ICON_SHAPE_SQUARE, ICON_SHAPE_ARCH, ICON_SHAPE_HEXAGON, ICON_SHAPE_DIAMOND, ICON_SHAPE_COOKIE,
        )
        const val TASKBAR_HANDLES_BOTH = "both"
        const val TASKBAR_HANDLES_LEFT = "left"
        const val TASKBAR_HANDLES_RIGHT = "right"
        const val TASKBAR_HANDLES_CENTER = "center"

        /** Pull-hint looks (Settings ▸ Taskbar ▸ Pull style). */
        const val TASKBAR_HANDLE_LINE = "line"
        const val TASKBAR_HANDLE_ARROW = "arrow"
        const val TASKBAR_HANDLE_DOUBLE = "double"
        const val TASKBAR_HANDLE_DOTS = "dots"
        const val TASKBAR_HANDLE_TEXT = "text"

        /** Show/hide animations (Settings ▸ Taskbar ▸ Animation). */
        const val TASKBAR_ANIM_SLIDE = "slide"
        const val TASKBAR_ANIM_FADE = "fade"
        const val TASKBAR_ANIM_SCALE = "scale"
        const val TASKBAR_ANIM_RISE = "rise"
        const val TASKBAR_ANIM_BOUNCE = "bounce"
        /** Tips up from the bottom edge like a lid. */
        const val TASKBAR_ANIM_FLIP = "flip"
        /** Opens out sideways from the middle, like the phone unfolding. */
        const val TASKBAR_ANIM_UNFOLD = "unfold"
        /** Pops up small and springs to size. */
        const val TASKBAR_ANIM_POP = "pop"

        /** The glow when an app is started from the taskbar (Settings ▸ Taskbar ▸ Launch glow). */
        const val LAUNCH_GLOW_APP = "app"
        const val LAUNCH_GLOW_ACCENT = "accent"
        const val LAUNCH_GLOW_WHITE = "white"
        const val LAUNCH_GLOW_OFF = "off"

        /** Minutes as the clock style colours them (accent or like the hours). */
        const val CLOCK_MINUTE_STYLE = "style"
        const val CLOCK_MINUTE_WHITE = "white"
        const val CLOCK_MINUTE_LIGHT_GREY = "light_grey"
        const val CLOCK_MINUTE_GREY = "grey"
        const val CLOCK_MINUTE_DARK_GREY = "dark_grey"
        const val CLOCK_MINUTE_BLACK = "black"
        val CLOCK_MINUTE_COLORS = listOf(
            CLOCK_MINUTE_STYLE, CLOCK_MINUTE_WHITE, CLOCK_MINUTE_LIGHT_GREY,
            CLOCK_MINUTE_GREY, CLOCK_MINUTE_DARK_GREY, CLOCK_MINUTE_BLACK,
        )
        val CLOCK_STYLES = listOf(
            CLOCK_STYLE_CLASSIC, CLOCK_STYLE_DUO, CLOCK_STYLE_LIGHT, CLOCK_STYLE_DOT,
            CLOCK_STYLE_STACKED, CLOCK_STYLE_GLASS_PILL, CLOCK_STYLE_OUTLINE,
            CLOCK_STYLE_VERTICAL_STACK, CLOCK_STYLE_NEUMORPHIC, CLOCK_STYLE_NOTHING_NDOT,
        )
        const val DRAWER_HANDLE_BAR = "bar"
        const val DRAWER_HANDLE_ARROW = "arrow"
        const val MAX_DRAWER_CORNER_RADIUS = 56
        const val MIN_ANIMATION_SPEED = 0.5f
        const val MAX_ANIMATION_SPEED = 2f
        const val APP_OPEN_SYSTEM = "system"

        /** The app grows out of the icon that was tapped. */
        const val APP_OPEN_ICON = "icon"
        const val APP_OPEN_ZOOM = "zoom"
        const val APP_OPEN_SLIDE = "slide"
        const val APP_OPEN_FADE = "fade"
        const val APP_OPEN_NONE = "none"
        /** The app's window is revealed from the icon that was tapped (a growing clip). */
        const val APP_OPEN_REVEAL = "reveal"
        /** Springs up from small, a touch past full size and back. */
        const val HAPTICS_LIGHT = "light"
        const val HAPTICS_MEDIUM = "medium"
        const val HAPTICS_STRONG = "strong"
        val HAPTICS_STRENGTHS = listOf(HAPTICS_LIGHT, HAPTICS_MEDIUM, HAPTICS_STRONG)
        const val APP_OPEN_POP = "pop"
        /** Opens out sideways from the middle, like the phone unfolding. */
        const val APP_OPEN_UNFOLD = "unfold"
        val APP_OPENS = listOf(
            APP_OPEN_SYSTEM, APP_OPEN_ICON, APP_OPEN_REVEAL, APP_OPEN_ZOOM, APP_OPEN_SLIDE, APP_OPEN_FADE,
            APP_OPEN_POP, APP_OPEN_UNFOLD, APP_OPEN_NONE,
        )
        const val FOLDER_ANIM_SHEET = "sheet"
        const val FOLDER_ANIM_ZOOM = "zoom"
        const val FOLDER_ANIM_FADE = "fade"
        const val FOLDER_ANIM_NONE = "none"
        val FOLDER_ANIMS = listOf(FOLDER_ANIM_SHEET, FOLDER_ANIM_ZOOM, FOLDER_ANIM_FADE, FOLDER_ANIM_NONE)
        const val PAGE_EFFECT_SLIDE = "slide"
        const val PAGE_EFFECT_FADE = "fade"
        const val PAGE_EFFECT_ZOOM = "zoom"
        const val PAGE_EFFECT_CUBE = "cube"
        const val PAGE_EFFECT_STACK = "stack"
        val PAGE_EFFECTS = listOf(PAGE_EFFECT_SLIDE, PAGE_EFFECT_FADE, PAGE_EFFECT_ZOOM, PAGE_EFFECT_CUBE, PAGE_EFFECT_STACK)

        /** One-tap theme presets: each sets the drawer, folders, menus, dots and dock together. */
        const val THEME_PRESET_GLASS = "glass"
        const val THEME_PRESET_DARK = "dark"
        const val THEME_PRESET_LIGHT = "light"
        const val THEME_PRESET_MONO = "mono"

        /** Themes of Folark's own widgets: the standard translucent card, or a solid one. */
        const val WIDGET_THEME_DEFAULT = "default"
        const val WIDGET_THEME_DARK = "dark"
        const val WIDGET_THEME_LIGHT = "light"
        const val WIDGET_THEME_ACCENT = "accent"
        val WIDGET_THEMES = setOf(WIDGET_THEME_DEFAULT, WIDGET_THEME_DARK, WIDGET_THEME_LIGHT, WIDGET_THEME_ACCENT)

        const val MIN_FOLDER_OPEN_COLUMNS = 3
        const val MAX_FOLDER_OPEN_COLUMNS = 6

        const val MIN_DPI_SCALE = 0.7f
        const val MAX_DPI_SCALE = 1.4f

        /** Search's web engines: Android's own search, or a named engine's page in the browser. */
        const val SEARCH_ENGINE_SYSTEM = "system"
        const val SEARCH_ENGINE_GOOGLE = "google"
        const val SEARCH_ENGINE_DUCKDUCKGO = "duckduckgo"
        const val SEARCH_ENGINE_BING = "bing"
        const val SEARCH_ENGINE_STARTPAGE = "startpage"
        val SEARCH_ENGINES = listOf(
            SEARCH_ENGINE_SYSTEM, SEARCH_ENGINE_GOOGLE, SEARCH_ENGINE_DUCKDUCKGO,
            SEARCH_ENGINE_BING, SEARCH_ENGINE_STARTPAGE,
        )

        const val SEARCH_TOP = "top"
        const val SEARCH_BOTTOM = "bottom"

        const val DRAWER_STYLE_STANDARD_GRID = "standard_grid"
        const val DRAWER_STYLE_ONE_UI = "one_ui"
        const val DRAWER_STYLE_NOTHING_OS = "nothing_os"
        const val DRAWER_STYLE_MOTO = "moto"
        const val DRAWER_STYLE_VERTICAL = "vertical"
        const val DRAWER_STYLE_HORIZONTAL = "horizontal"

        const val DRAWER_THEME_SYSTEM = "system"
        const val DRAWER_THEME_MATERIAL_YOU = "material_you"
        const val DRAWER_THEME_LIGHT = "light"
        const val DRAWER_THEME_CUSTOM = "custom"
        const val DRAWER_THEME_DEFAULT_DARK = "default_dark"
        const val DRAWER_THEME_OBSIDIAN_BLACK = "obsidian_black"
        const val DRAWER_THEME_ACCENT_TINT = "accent_tint"
        const val DRAWER_THEME_MONOCHROME = "monochrome"

        /** Every valid [Drawer.themeTint] value, in the order the settings picker shows them. */
        val DRAWER_THEMES = listOf(
            DRAWER_THEME_SYSTEM,
            DRAWER_THEME_MATERIAL_YOU,
            DRAWER_THEME_DEFAULT_DARK,
            DRAWER_THEME_OBSIDIAN_BLACK,
            DRAWER_THEME_LIGHT,
            DRAWER_THEME_ACCENT_TINT,
            DRAWER_THEME_MONOCHROME,
            DRAWER_THEME_CUSTOM,
        )

        const val DEFAULT_DRAWER_ACCENT = 0xFF00B0FF.toInt()
        const val DEFAULT_DRAWER_CUSTOM_COLOR = 0xFF1B2A41.toInt()
        const val DEFAULT_DRAWER_PANEL_OPACITY = 0.55f
        const val MIN_DRAWER_PANEL_OPACITY = 0.15f
        const val MAX_DRAWER_PANEL_OPACITY = 0.95f
        /** Upper bound of [Drawer.backgroundBlur] (dp of wallpaper blur behind the open drawer). */
        const val MAX_DRAWER_BLUR = 25f

        /** The accent each drawer style ships with; a style change only replaces the accent while
         *  it still is one of these (i.e. the user never picked their own). */
        fun defaultAccentForStyle(style: String): Int = when (style) {
            DRAWER_STYLE_ONE_UI -> 0xFF0A84FF.toInt()
            DRAWER_STYLE_MOTO -> 0xFF00BFA5.toInt()
            DRAWER_STYLE_NOTHING_OS -> 0xFFFF3B30.toInt()
            else -> DEFAULT_DRAWER_ACCENT
        }

        val STYLE_DEFAULT_ACCENTS: Set<Int> = setOf(
            DRAWER_STYLE_STANDARD_GRID, DRAWER_STYLE_ONE_UI, DRAWER_STYLE_MOTO, DRAWER_STYLE_NOTHING_OS,
        ).mapTo(HashSet()) { defaultAccentForStyle(it) }

        const val DRAWER_ALIGNMENT_LEFT = "left"
        const val DRAWER_ALIGNMENT_CENTER = "center"
        const val DRAWER_ALIGNMENT_RIGHT = "right"

        const val COUNT_NUMBER = "number"
        const val COUNT_DOT = "dot"
        const val COUNT_NONE = "none"

        const val PRIVACY_ALL = "all"
        const val PRIVACY_SENDER = "sender"
        const val PRIVACY_COUNT = "count"

        const val DENSITY_COMPACT = "compact"
        const val DENSITY_NORMAL = "normal"
        const val DENSITY_SPACIOUS = "spacious"

        const val GESTURE_NOTIFICATIONS = "notifications"
        const val GESTURE_DRAWER = "drawer"
        const val GESTURE_SEARCH = "search"
        const val GESTURE_LOCK = "lock"
        const val GESTURE_NONE = "none"
        /** Folark's own Recents screen. */
        const val GESTURE_RECENTS = "recents"
        /** Motorola screens as home gestures (offered only where the phone has them, see MotoLinks). */
        const val GESTURE_MOTO_NOTE = "moto_note"
        const val GESTURE_MOTO_VAULT = "moto_vault"
        const val GESTURE_MOTO_READY_FOR = "moto_ready_for"
        const val GESTURE_MOTO_GAMES = "moto_games"
        const val GESTURE_MOTO_WEATHER = "moto_weather"
        const val GESTURE_MOTO_STYLES = "moto_styles"
        val MOTO_GESTURES = listOf(
            GESTURE_MOTO_NOTE, GESTURE_MOTO_VAULT, GESTURE_MOTO_READY_FOR,
            GESTURE_MOTO_GAMES, GESTURE_MOTO_WEATHER, GESTURE_MOTO_STYLES,
        )

        const val ACTION_PAGE_0 = "page_0"
        const val ACTION_LOCK = "lock"
        const val ACTION_SEARCH = "search"
        const val ACTION_DRAWER = "drawer"
        const val ACTION_STAY = "stay"

        const val TRANSITION_MORPH_SCALE_FADE = "morph_scale_fade"
        const val TRANSITION_BOOK_UNFOLD_SWEEP = "book_unfold_sweep"
        const val TRANSITION_AQUAMORPHIC_RIPPLE = "aquamorphic_ripple"
        const val TRANSITION_CROSSFADE = "crossfade"
        const val TRANSITION_SCALE = "scale"
        const val TRANSITION_SLIDE = "slide"
        /** Follows the hinge while the phone opens (needs a hinge sensor; the inner display only). */
        const val TRANSITION_LIVE_HINGE = "live_hinge"
        const val MIN_FOLD_TRANSITION_SPEED = 0.1f
        const val MAX_FOLD_TRANSITION_SPEED = 1.5f
        const val MIN_LIVE_HINGE_END_ANGLE = 120
        const val MAX_LIVE_HINGE_SMOOTHING_MS = 120
        const val MAX_LIVE_HINGE_BLUR_DP = 48
        const val MAX_LIVE_HINGE_FROST = 0.7f
        /** Two pages folding flat around the hinge. */
        const val LIVE_HINGE_STYLE_PAGES = "pages"
        /** Rings spreading from the hinge, like water settling. */
        const val LIVE_HINGE_STYLE_WATER = "water"
        /** Black and white and dim, colour flowing in. */
        const val LIVE_HINGE_STYLE_INK = "ink"
        /** Out of black. */
        const val LIVE_HINGE_STYLE_FADE = "fade"
        /** Falling in from above, as if dropping off the outer screen. */
        const val LIVE_HINGE_STYLE_DROP = "drop"
        /** Two halves sliding in to meet at the hinge. */
        const val LIVE_HINGE_STYLE_DOORS = "doors"
        /** A circle opening from the hinge. */
        const val LIVE_HINGE_STYLE_PORTAL = "portal"
        /** Coming forward out of depth. */
        const val LIVE_HINGE_STYLE_ZOOM = "zoom"
        val LIVE_HINGE_STYLES = listOf(
            LIVE_HINGE_STYLE_PAGES, LIVE_HINGE_STYLE_WATER, LIVE_HINGE_STYLE_INK, LIVE_HINGE_STYLE_FADE,
            LIVE_HINGE_STYLE_DROP, LIVE_HINGE_STYLE_DOORS, LIVE_HINGE_STYLE_PORTAL, LIVE_HINGE_STYLE_ZOOM,
        )

        /** Live activity pill effects: a breathing halo, a light sweeping across, filling up with
         *  the progress, a light running round the rim, a gentle pulse, drifting colour, or none. */
        /** Icon nudge effects: a hop, a shake, a heartbeat, a halo, rings spreading, squash and stretch. */
        const val NUDGE_BOUNCE = "bounce"
        const val RING_OFF = "off"
        const val RING_PROGRESS = "progress"
        const val RING_ALL = "all"
        const val NUDGE_WIGGLE = "wiggle"
        const val NUDGE_PULSE = "pulse"
        const val NUDGE_GLOW = "glow"
        const val NUDGE_RIPPLE = "ripple"
        const val NUDGE_JELLY = "jelly"
        /** The icon fills with the app's colour. */
        const val NUDGE_TINT = "tint"
        val NUDGE_EFFECTS = listOf(NUDGE_BOUNCE, NUDGE_WIGGLE, NUDGE_PULSE, NUDGE_GLOW, NUDGE_RIPPLE, NUDGE_JELLY, NUDGE_TINT)

        const val LIVE_EFFECT_GLOW = "glow"
        const val LIVE_EFFECT_SHIMMER = "shimmer"
        const val LIVE_EFFECT_PROGRESS = "progress"
        const val LIVE_EFFECT_ORBIT = "orbit"
        const val LIVE_EFFECT_BREATHE = "breathe"
        const val LIVE_EFFECT_AURORA = "aurora"
        const val LIVE_EFFECT_STILL = "still"
        val LIVE_EFFECTS = listOf(
            LIVE_EFFECT_GLOW, LIVE_EFFECT_SHIMMER, LIVE_EFFECT_PROGRESS, LIVE_EFFECT_ORBIT,
            LIVE_EFFECT_BREATHE, LIVE_EFFECT_AURORA, LIVE_EFFECT_STILL,
        )

        const val DEFAULT_BATCH_TIMES = "08:00,12:00,17:00"
    }
}
