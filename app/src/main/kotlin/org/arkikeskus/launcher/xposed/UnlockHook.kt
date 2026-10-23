package org.arkikeskus.launcher.xposed

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * The SystemUI half of Folark's Vector module: tells Folark the moment the lock screen starts its
 * exit animation, so Folark's unlock-to-home animation starts in step with it (instead of Folark
 * guessing from "unlocked within 20 seconds of the screen coming on").
 *
 * It hooks `KeyguardUnlockAnimationController.notifyStartSurfaceBehindRemoteAnimation`, which
 * SystemUI calls as the app behind the lock screen starts to appear. The hook only reads: it never
 * changes what SystemUI does (skipping or delaying SystemUI's own part could leave the lock screen
 * stuck). The message to Folark is sent from a background thread, so SystemUI is never held up,
 * and everything is wrapped so an error here can't affect SystemUI.
 *
 * What it sends: when the exit started (uptime), and the package of the app the lock screen is
 * revealing (so Folark only animates when that is Folark itself). Nothing else.
 */
internal object UnlockHook {
    private const val CONTROLLER = "com.android.systemui.keyguard.KeyguardUnlockAnimationController"
    private const val METHOD = "notifyStartSurfaceBehindRemoteAnimation"

    @Volatile private var installed = false

    // Created on first use (inside a hook), never in a constructor: see RecentsHook.main.
    private val worker by lazy { Handler(HandlerThread("folark-unlock").apply { start() }.looper) }

    fun install(loader: ClassLoader, log: (String) -> Unit) {
        if (installed) return
        val controller = runCatching { XposedHelpers.findClass(CONTROLLER, loader) }.getOrNull()
        if (controller == null) {
            log("no $CONTROLLER in SystemUI; unlock sync stays off")
            return
        }
        val hooks = runCatching {
            XposedBridge.hookAllMethods(controller, METHOD, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    runCatching { onExitStart(param) }
                }
            })
        }.getOrNull()
        installed = true
        log("unlock sync: hooked ${hooks?.size ?: 0} SystemUI method(s)")
    }

    private fun onExitStart(param: XC_MethodHook.MethodHookParam) {
        val at = SystemClock.uptimeMillis()
        // Last argument: SystemUI was asked to show the app behind as the finger lifts the lock
        // screen (a swipe), rather than playing its own canned animation (fingerprint, face, PIN).
        val bySwipe = param.args?.lastOrNull() as? Boolean ?: false
        val context = appContext() ?: return
        worker.post {
            runCatching {
                val extras = Bundle().apply {
                    putLong(SystemUnlock.EXTRA_AT, at)
                    putString(SystemUnlock.EXTRA_TOP, topPackage(context))
                    putBoolean(SystemUnlock.EXTRA_SWIPE, bySwipe)
                }
                context.contentResolver.call(
                    Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SystemUnlock.METHOD_UNLOCK, null, extras,
                )
            }
        }
    }

    /** The app at the top of the task stack, i.e. what the lock screen is uncovering. */
    @Suppress("DEPRECATION")
    private fun topPackage(context: Context): String? = runCatching {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningTasks(1).firstOrNull()?.topActivity?.packageName
    }.getOrNull()

    private fun appContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()

}
