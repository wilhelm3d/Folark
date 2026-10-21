package org.arkikeskus.launcher.xposed

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Folark's module for Vector (LSPosed): runs inside the system launcher (Motorola's, or Android's
 * own Launcher3 / Pixel launcher) and hands its Recents over to Folark Recents, with no flash:
 *
 * - The Recents command (Android's taskbar button, the Recents key, Alt+Tab) opens Folark Recents
 *   instead (`OverviewCommandHelper.addCommand`).
 * - Swipe up and hold ends as a swipe home, then Folark Recents opens
 *   (`AbsSwipeUpHandler.calculateEndTarget`).
 *
 * Everything else (gestures, the taskbar, switching apps) stays the launcher's own. It only acts
 * while Settings ▸ Recents ▸ Replace Android's Recents is on (asked from Folark each time, briefly
 * cached), and does nothing if Folark is missing or the launcher's code differs.
 *
 * Runs in the launcher's process: no Hilt, no Folark classes beyond this file.
 */
class RecentsHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == FOLARK) return
        val loader = lpparam.classLoader
        val helper = find("com.android.quickstep.OverviewCommandHelper", loader) ?: return
        log("hooking Recents in ${lpparam.packageName}")

        XposedBridge.hookAllMethods(helper, "addCommand", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val type = param.args?.getOrNull(0) as? Int ?: return
                if (type != TYPE_SHOW && type != TYPE_TOGGLE && type != TYPE_KEYBOARD_INPUT) return
                // Swipe-to-split also sends TOGGLE, but through its own method (not this one).
                val context = appContext() ?: return
                if (!replaceOn(context)) return
                openFolarkRecents(context, toggle = type == TYPE_TOGGLE)
                param.result = null
            }
        })

        find("com.android.quickstep.AbsSwipeUpHandler", loader)?.let { swipe ->
            XposedBridge.hookAllMethods(swipe, "calculateEndTarget", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val target = param.result as? Enum<*> ?: return
                    if (target.name != "RECENTS") return
                    val context = appContext() ?: return
                    if (!replaceOn(context)) return
                    val home = target.declaringJavaClass.enumConstants?.firstOrNull { (it as Enum<*>).name == "HOME" } ?: return
                    param.result = home
                    main.postDelayed({ openFolarkRecents(context, toggle = false) }, AFTER_HOME_MS)
                }
            })
        }
    }

    private fun find(name: String, loader: ClassLoader): Class<*>? =
        runCatching { XposedHelpers.findClass(name, loader) }.getOrNull()

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var cachedAt = 0L
    @Volatile private var cached = false

    /** Whether Folark's switch is on (asked from Folark, kept for a second). */
    private fun replaceOn(context: Context): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - cachedAt < CACHE_MS) return cached
        val on = runCatching {
            context.contentResolver.call(Uri.parse("content://$STATE_AUTHORITY"), "state", null, null)
                ?.getBoolean("replace", false) == true
        }.getOrDefault(false)
        cached = on
        cachedAt = now
        return on
    }

    private fun openFolarkRecents(context: Context, toggle: Boolean) {
        runCatching {
            context.startActivity(
                Intent()
                    .setComponent(ComponentName(FOLARK, "$FOLARK.recents.RecentsActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_TOGGLE, toggle),
            )
        }.onFailure { log("can't open Folark Recents: $it") }
    }

    private fun appContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()

    private fun log(text: String) = runCatching { XposedBridge.log("Folark: $text") }

    companion object {
        const val FOLARK = "org.arkikeskus.launcher"
        const val STATE_AUTHORITY = "org.arkikeskus.launcher.recentshook"
        const val EXTRA_TOGGLE = "org.arkikeskus.launcher.extra.TOGGLE"

        // OverviewCommandHelper's command types.
        private const val TYPE_SHOW = 1
        private const val TYPE_KEYBOARD_INPUT = 2
        private const val TYPE_TOGGLE = 4

        /** Lets the launcher's own swipe-home finish before Folark Recents opens over home. */
        private const val AFTER_HOME_MS = 260L
        private const val CACHE_MS = 1_000L
    }
}
