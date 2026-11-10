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
    val searchPosition: String = LauncherSettings.SEARCH_TOP,
    val indexBarEnabled: Boolean = true,
    val azIndexPosition: String = LauncherSettings.AZ_POSITION_VERTICAL_RIGHT,
    /** Size of the A-Z index bar's letters and touch strip: 1 = normal. */
    val azIndexScale: Float = 1f,
    /** Corner radius of the app drawer panel in dp; [LauncherSettings.DRAWER_CORNER_AUTO] follows the drawer style. */
    val cornerRadius: Int = LauncherSettings.DRAWER_CORNER_AUTO,
    /** The pull handle at the top of the drawer: a bar, or a small arrow that turns as the drawer moves. */
    val handleStyle: String = LauncherSettings.DRAWER_HANDLE_BAR,
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
    val style: String = LauncherSettings.DRAWER_STYLE_STANDARD_GRID,
    /** Drawer background theme, one of the DRAWER_THEME_* values. [LauncherSettings.DRAWER_THEME_SYSTEM] follows the
     *  launcher theme (light/dark), which is what the drawer always did before themes were wired up. */
    val themeTint: String = LauncherSettings.DRAWER_THEME_SYSTEM,
    val accentColor: Int = LauncherSettings.DEFAULT_DRAWER_ACCENT,
    /** ARGB background used by [LauncherSettings.DRAWER_THEME_CUSTOM]. */
    val customColor: Int = LauncherSettings.DEFAULT_DRAWER_CUSTOM_COLOR,
    /** Liquid Glass: the drawer panel becomes translucent ([panelOpacity]) with a specular rim
     *  and sheen, over the wallpaper blurred by [backgroundBlur]. */
    val glassEnabled: Boolean = false,
    /** Opacity of the drawer panel while [glassEnabled] is on (lower = more see-through). */
    val panelOpacity: Float = LauncherSettings.DEFAULT_DRAWER_PANEL_OPACITY,
    val heightFraction: Float = 1.0f,
    val widthFraction: Float = 1.0f,
    val alignment: String = LauncherSettings.DRAWER_ALIGNMENT_CENTER,
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
    val layoutMode: String = LauncherSettings.DRAWER_LAYOUT_GRID,
)
