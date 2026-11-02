package org.arkikeskus.launcher.data

import android.content.Context
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.model.LauncherSettings

/**
 * How a split can be made right now, as the Split engine setting allows it: [native] (Folark's
 * module in System UI), the WM Shell [commands] (through root when [root]), or neither. One rule,
 * used by [SplitCoordinator] and shown by Settings ▸ Recents ▸ Split screen ▸ Check split screen.
 */
data class SplitRoute(val native: Boolean, val commands: Boolean, val root: Boolean) {
    val any: Boolean get() = native || commands

    companion object {
        /** Blocking (it may ask `su` once): call it off the main thread. */
        fun resolve(context: Context, engine: Int): SplitRoute {
            val dump = ShellSplit.hasDump(context)
            val rootOnly = !dump && RootShell.available()
            val canRead = dump || rootOnly
            // The module needs Folark to read the window list too (to find the two tasks).
            val module = canRead && SplitThree.ready(context)
            return when (engine) {
                LauncherSettings.SPLIT_ENGINE_CLASSIC -> SplitRoute(native = false, commands = dump, root = false)
                LauncherSettings.SPLIT_ENGINE_ROOT -> {
                    val r = RootShell.available()
                    SplitRoute(native = false, commands = r, root = r)
                }
                LauncherSettings.SPLIT_ENGINE_LSPOSED -> SplitRoute(native = module, commands = canRead && !module, root = rootOnly)
                else -> SplitRoute(native = module, commands = canRead, root = rootOnly)
            }
        }
    }
}
