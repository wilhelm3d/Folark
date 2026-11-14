package org.arkikeskus.launcher.model

import androidx.compose.runtime.Immutable

/** The app drawer. */
@Immutable
data class DrawerSettings(
    val columns: Int = 4,
    val rows: Int = 6,
    val showSearch: Boolean = true,
    val showLabels: Boolean = true,
    /** Let app-drawer labels (apps, folders, the "most used" row) wrap onto a second line — the
     *  drawer's long-standing look, so this defaults on. */
    val twoLineLabels: Boolean = true,
    val searchPosition: SearchPosition = SearchPosition.TOP,
    val indexBarEnabled: Boolean = true,
    val azIndexPosition: AzPosition = AzPosition.VERTICAL_RIGHT,
    /** Size of the A-Z index bar's letters and touch strip: 1 = normal. */
    val azIndexScale: Float = 1f,
    /** Corner radius of the app drawer panel in dp; [LauncherSettings.DRAWER_CORNER_AUTO] follows the drawer style. */
    val cornerRadius: Int = LauncherSettings.DRAWER_CORNER_AUTO,
    /** The pull handle at the top of the drawer: a bar, or a small arrow that turns as the drawer moves. */
    val handleStyle: DrawerHandle = DrawerHandle.BAR,
    /** Extra room kept free under the drawer, in dp (for a system taskbar that the system does not report). */
    val bottomSpace: Int = 0,
    /** Small "A", "B", "C" headings between the apps of the full list. */
    val letterHeaders: Boolean = false,
    /** Show the letter under the finger large in the middle of the drawer while using the A-Z bar. */
    val largeLetterPopup: Boolean = true,
    /** A sideways swipe on the app list moves between the drawer's folder tabs. */
    val swipeTabs: Boolean = true,
    /** A fast downward flick closes the drawer even when the list is scrolled down. */
    val flickClose: Boolean = false,
    val backgroundBlur: Float = 0f,
    val scrimOpacity: Float = 0.5f,
    val glassBlurRadius: Float = 25.0f,
    val glassDarkTint: Float = 0.45f,
    val style: DrawerStyle = DrawerStyle.STANDARD_GRID,
    /** Drawer background theme, one of [DrawerTheme]. [DrawerTheme.SYSTEM] follows the
     *  launcher theme (light/dark), which is what the drawer always did before themes were wired up. */
    val themeTint: DrawerTheme = DrawerTheme.SYSTEM,
    val accentColor: Int = LauncherSettings.DEFAULT_DRAWER_ACCENT,
    /** ARGB background used by [DrawerTheme.CUSTOM]. */
    val customColor: Int = LauncherSettings.DEFAULT_DRAWER_CUSTOM_COLOR,
    /** Liquid Glass: the drawer panel becomes translucent ([panelOpacity]) with a specular rim
     *  and sheen, over the wallpaper blurred by [backgroundBlur]. */
    val glassEnabled: Boolean = false,
    /** Opacity of the drawer panel while [glassEnabled] is on (lower = more see-through). */
    val panelOpacity: Float = LauncherSettings.DEFAULT_DRAWER_PANEL_OPACITY,
    val heightFraction: Float = 1.0f,
    val widthFraction: Float = 1.0f,
    val alignment: DrawerAlignment = DrawerAlignment.CENTER,
    /** Reopen the app drawer scrolled back to the top (Pixel-style) rather than remembering the last
     *  scroll position — keeps the "most used" row and A–Z start visible on every open. */
    val opensAtTop: Boolean = true,
    /** Show a "most used" row (top apps by decayed launch frequency) above the drawer's app list. */
    val showFrequentApps: Boolean = false,
    /** The last things you copied as a row at the top of the drawer, with quick actions. */
    val clipboardRow: Boolean = false,
    /** Show the drawer's folders as tabs above the app list instead of tiles inside it. */
    val folderTabs: Boolean = false,
    /** With folders as tabs: an app in a tab is shown only in that tab, not in "All" as well. */
    val tabsExclusive: Boolean = true,
    /** Keep the tabs in the order set in Settings ▸ App drawer: a chip can no longer be dragged
     *  to another place (the settings list is locked too). */
    val lockTabOrder: Boolean = false,
    /** The drawer's Private Space section (both displays; where the phone has a Private Space). */
    val showPrivateSpace: Boolean = true,
    val innerSidebar: Boolean = false,
    /** A-Z bar: hold a letter still for a moment, then sliding scrolls smoothly through the apps instead of jumping letter to letter. */
    val azHoldScroll: Boolean = true,
    /** "Hidden apps" at the end of the app list opens only after the fingerprint (or screen lock). */
    val hiddenLock: Boolean = false,
    /** Unfolded: the drawer's tabs as a list on the left, their apps on the right. */
    val twoPane: Boolean = false,
    val layoutMode: DrawerLayout = DrawerLayout.GRID,
)

/** Where the drawer's AâZ index sits. */
enum class AzPosition(override val stored: String) : StoredChoice {
    VERTICAL_RIGHT("VERTICAL_RIGHT"),
    VERTICAL_LEFT("VERTICAL_LEFT"),
    HORIZONTAL_TOP("HORIZONTAL_TOP"),
    HORIZONTAL_BOTTOM("HORIZONTAL_BOTTOM"),
}

/** How the drawer's list lines up its rows. */
enum class DrawerAlignment(override val stored: String) : StoredChoice {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
}

/** The hint at the bottom of home that the drawer pulls up from. */
enum class DrawerHandle(override val stored: String) : StoredChoice {
    BAR("bar"),
    ARROW("arrow"),
}

/** The app drawer as a grid of icons or as a list. */
enum class DrawerLayout(override val stored: String) : StoredChoice {
    GRID("grid"),
    LIST("list"),
}

/** The app drawer's style. */
enum class DrawerStyle(override val stored: String) : StoredChoice {
    STANDARD_GRID("standard_grid"),
    ONE_UI("one_ui"),
    NOTHING_OS("nothing_os"),
    MOTO("moto"),
    VERTICAL("vertical"),
    HORIZONTAL("horizontal"),
    ;

    /** The accent this style ships with. */
    val defaultAccent: Int
        get() = when (this) {
            ONE_UI -> 0xFF0A84FF.toInt()
            MOTO -> 0xFF00BFA5.toInt()
            NOTHING_OS -> 0xFFFF3B30.toInt()
            else -> LauncherSettings.DEFAULT_DRAWER_ACCENT
        }

    companion object {
        /** The accents the styles ship with: a style change only replaces the accent while it still
         *  is one of these (i.e. the user never picked their own). */
        val DEFAULT_ACCENTS: Set<Int> = entries.mapTo(HashSet()) { it.defaultAccent }
    }
}

/** The app drawer's colours, in the order the settings picker shows them. */
enum class DrawerTheme(override val stored: String) : StoredChoice {
    SYSTEM("system"),
    MATERIAL_YOU("material_you"),
    DEFAULT_DARK("default_dark"),
    OBSIDIAN_BLACK("obsidian_black"),
    LIGHT("light"),
    ACCENT_TINT("accent_tint"),
    MONOCHROME("monochrome"),
    CUSTOM("custom"),
}

/** Where the drawer's search bar sits. */
enum class SearchPosition(override val stored: String) : StoredChoice {
    TOP("top"),
    BOTTOM("bottom"),
}
