package org.arkikeskus.launcher.xposed

import org.arkikeskus.launcher.model.LauncherSettings

/**
 * The settings the module asks for, as last read from the app's own settings stream
 * ([LauncherApplication] refreshes them whenever settings change), so [RecentsHookState]'s
 * provider methods answer from memory instead of blocking a binder thread on DataStore.
 *
 * Values are null until the first settings snapshot arrives; the provider falls back to reading
 * the settings then (the app may have been started by the module's question itself).
 */
/** Public: the app's own settings stream is what keeps it current (see LauncherApplication). */
object HookStateCache {
    @Volatile var replaceRecents: Boolean? = null
    @Volatile var navHandleSearch: Boolean? = null
    @Volatile var statusBarOn: Boolean? = null
    @Volatile var statusBarInner: Int? = null
    @Volatile var statusBarCover: Int? = null
    @Volatile var dividerOn: Boolean? = null
    @Volatile var dividerFree: Boolean? = null
    @Volatile var dividerLock: String? = null
    @Volatile var dividerMarks: Boolean? = null
    @Volatile var dividerHaptics: Boolean? = null
    @Volatile var keepSplitSize: Boolean? = null
    @Volatile var peekFlingClose: Boolean? = null

    fun update(s: LauncherSettings) {
        replaceRecents = s.recents.enabled && s.recents.replaceSystem
        navHandleSearch = s.recents.navHandleOpensSearch
        statusBarOn = s.statusBar.systemPadding
        statusBarInner = s.statusBar.systemPaddingInner
        statusBarCover = s.statusBar.systemPaddingCover
        dividerOn = s.recents.dividerStops
        dividerFree = s.recents.freeDivider
        dividerLock = s.recents.dividerLock
        dividerMarks = s.recents.dividerMarks
        dividerHaptics = s.recents.dividerHaptics
        keepSplitSize = s.recents.keepSplitSize
        peekFlingClose = s.recents.peekFlingClose
    }
}
