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
        // SystemUI: only the unlock sync lives there (UnlockHook); the Recents hooks are launcher-only.
        if (lpparam.packageName == SYSTEMUI) {
            // Only SystemUI's own main process, where the lock screen and WM Shell live. Other
            // apps (the system launcher among them) also load SystemUI's code into their process
            // now and then; there's nothing to hook there (and no split screen to find).
            if (!lpparam.isFirstApplication || lpparam.processName != SYSTEMUI) return
            UnlockHook.install(loader, ::log)
            SplitThreeHook.install(loader, ::log)
            return
        }
        val helper = find("com.android.quickstep.OverviewCommandHelper", loader)
        if (helper == null) {
            // Only worth a line for something that looks like a launcher (the scope also holds
            // system packages, which load in many processes).
            if (lpparam.packageName.contains("launcher", ignoreCase = true)) {
                log("no OverviewCommandHelper in ${lpparam.packageName}; not a Quickstep launcher")
            }
            return
        }
        log("hooking Recents in ${lpparam.packageName}")
        // Tell Folark the module is live as soon as the launcher is ready (so it shows "active"
        // without having to open Recents first). Retries until the launcher's context exists.
        pingWhenReady(0)

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

        // A long press on the gesture handle: open Folark search instead of the assistant.
        find("com.android.quickstep.inputconsumers.NavHandleLongPressHandler", loader)?.let { handler ->
            XposedBridge.hookAllMethods(handler, "getLongPressRunnable", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = appContext() ?: return
                    if (!searchOn(context)) return
                    param.result = Runnable { openFolarkSearch(context) }
                }
            })
        }

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

    // Lazy, NOT a plain field initializer: Vector instantiates this module class very early during
    // bootstrap (XposedInit.initModule), and building a Handler in the constructor there throws and
    // makes Vector report "Failed to load class". First touch happens inside handleLoadPackage, by
    // which point the launcher's main looper is ready.
    private val main by lazy { Handler(Looper.getMainLooper()) }

    @Volatile private var cachedAt = 0L
    @Volatile private var cached = false

    @Volatile private var cachedSearch = false

    /** Folark's two switches, asked together and kept for a second. */
    private fun state(context: Context): Int {
        val now = SystemClock.uptimeMillis()
        if (now - cachedAt < CACHE_MS) return packed()
        val bundle = runCatching {
            context.contentResolver.call(Uri.parse("content://$STATE_AUTHORITY"), "state", null, null)
        }.getOrNull()
        cached = bundle?.getBoolean("replace", false) == true
        cachedSearch = bundle?.getBoolean("search", false) == true
        cachedAt = now
        return packed()
    }

    private fun packed() = (if (cached) 1 else 0) or (if (cachedSearch) 2 else 0)

    private fun replaceOn(context: Context): Boolean = state(context) and 1 != 0
    private fun searchOn(context: Context): Boolean = state(context) and 2 != 0

    /** Pings Folark's state provider once the launcher's app context is up (announces the module). */
    private fun pingWhenReady(tries: Int) {
        val context = appContext()
        if (context != null) {
            cachedAt = 0L
            runCatching { state(context) }
            return
        }
        if (tries < PING_MAX_TRIES) main.postDelayed({ pingWhenReady(tries + 1) }, PING_RETRY_MS)
    }

    private fun openFolarkSearch(context: Context) {
        runCatching {
            context.startActivity(
                Intent()
                    .setComponent(ComponentName(FOLARK, "$FOLARK.LauncherActivity"))
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(SEARCH_EXTRA, SEARCH_ACTION),
            )
        }.onFailure { log("can't open Folark search: $it") }
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

    private fun log(text: String) {
        runCatching { XposedBridge.log("Folark: $text") }
    }

    companion object {
        const val FOLARK = "org.arkikeskus.launcher"
        const val SYSTEMUI = "com.android.systemui"
        const val STATE_AUTHORITY = "org.arkikeskus.launcher.recentshook"
        const val EXTRA_TOGGLE = "org.arkikeskus.launcher.extra.TOGGLE"
        const val SEARCH_EXTRA = "org.arkikeskus.launcher.extra.ACTION"
        const val SEARCH_ACTION = "open_search"

        // OverviewCommandHelper's command types.
        private const val TYPE_SHOW = 1
        private const val TYPE_KEYBOARD_INPUT = 2
        private const val TYPE_TOGGLE = 4

        /** Lets the launcher's own swipe-home finish before Folark Recents opens over home. */
        private const val AFTER_HOME_MS = 260L
        private const val CACHE_MS = 1_000L
        private const val PING_RETRY_MS = 1_500L
        private const val PING_MAX_TRIES = 12
    }
}
