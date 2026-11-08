package org.arkikeskus.launcher.xposed

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.Collections
import java.util.WeakHashMap
import org.arkikeskus.launcher.model.catching

/**
 * Settings ▸ Motorola ▸ Status bar margins: keeps at least some room at the left and right ends of
 * Android's own status bar, one amount for the inner screen (its punch-hole camera, the rounded
 * corners) and one for the cover screen. Off by default; off, nothing here touches the status bar.
 *
 * Android lays the status bar out itself (PhoneStatusBarView sets its side padding from the display
 * cutout and corners in `updateSafeInsets`); this runs right after that and only ever widens the
 * padding Android chose, never narrows it, so the clock and icons can't end up under the camera.
 * The amounts come from Folark (its RecentsHookState provider), asked off the main thread and again
 * whenever Folark says they changed.
 */
internal object StatusBarPaddingHook {
    private const val STATUS_BAR = "com.android.systemui.statusbar.phone.PhoneStatusBarView"
    /** Motorola's own status bar for an outer display, on phones that have one. */
    private const val MOTO_COVER_BAR = "com.motorola.systemui.cli.statusbar.CliStatusbarLinearLayout"

    /** Folark's answer, asked with this method on its provider. */
    const val METHOD = "statusbar_padding"
    const val EXTRA_ON = "on"
    const val EXTRA_INNER = "inner"
    const val EXTRA_COVER = "cover"
    val CHANGED_URI: Uri = Uri.parse("content://${RecentsHook.STATE_AUTHORITY}/statusbar")

    private const val INNER_MIN_DP = 600

    @Volatile private var on = false
    @Volatile private var innerDp = 0
    @Volatile private var coverDp = 0
    @Volatile private var asked = false
    private var log: (String) -> Unit = {}

    /** Each status bar view and the side padding Android last gave it (before ours). */
    private val bars = Collections.synchronizedMap(WeakHashMap<View, IntArray>())
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val worker by lazy { Handler(HandlerThread("folark-statusbar").apply { start() }.looper) }

    fun install(loader: ClassLoader, log: (String) -> Unit) {
        this.log = log
        var hooked = 0
        val bar = catching { XposedHelpers.findClass(STATUS_BAR, loader) }.getOrNull()
        if (bar != null) {
            // Where Android sets the side padding; on builds without it, after insets and config changes.
            val exact = bar.declaredMethods.any { it.name == "updateSafeInsets" }
            val methods = if (exact) listOf("updateSafeInsets") else listOf("onApplyWindowInsets", "onConfigurationChanged", "onAttachedToWindow")
            methods.forEach { name ->
                hooked += catching { XposedBridge.hookAllMethods(bar, name, after { v -> laidOut(v, fresh = exact) }).size }.getOrDefault(0)
            }
        }
        val cover = catching { XposedHelpers.findClass(MOTO_COVER_BAR, loader) }.getOrNull()
        if (cover != null) {
            listOf("onFinishInflate", "onCliDensityOrFontScaleChanged").forEach { name ->
                hooked += catching { XposedBridge.hookAllMethods(cover, name, after { v -> laidOut(v, fresh = name == "onFinishInflate", cover = true) }).size }.getOrDefault(0)
            }
        }
        log("status bar margins: hooked $hooked method(s)")
    }

    private fun after(block: (View) -> Unit) = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val v = param.thisObject as? View ?: return
            catching { block(v) }.onFailure { log("status bar margins failed: $it") }
        }
    }

    /**
     * [view] was just laid out ([fresh]: by the method where Android sets its padding, so what is
     * there now is Android's own): that is kept as the base, then widened to the amount for its
     * screen. Otherwise the padding there is the base unless it is still ours from last time.
     */
    private fun laidOut(view: View, fresh: Boolean, cover: Boolean = false) {
        ask(view.context)
        val base = bars[view]
        val ours = base != null && view.paddingLeft == base[3] && view.paddingRight == base[4]
        if (base == null || fresh || !ours) {
            bars[view] = intArrayOf(view.paddingLeft, view.paddingRight, if (cover) 1 else 0, -1, -1)
        }
        apply(view)
    }

    private fun apply(view: View) {
        val base = bars[view] ?: return
        val cover = base[2] == 1 || view.resources.configuration.smallestScreenWidthDp < INNER_MIN_DP
        val px = if (!on) 0 else ((if (cover) coverDp else innerDp) * view.resources.displayMetrics.density).toInt()
        val left = maxOf(base[0], px)
        val right = maxOf(base[1], px)
        if (view.paddingLeft != left || view.paddingRight != right) {
            view.setPadding(left, view.paddingTop, right, view.paddingBottom)
        }
        base[3] = left
        base[4] = right
    }

    /** Asks Folark once for the amounts, and watches for changes from then on. */
    private fun ask(context: Context) {
        if (asked) return
        asked = true
        val app = context.applicationContext ?: context
        worker.post {
            fetch(app)
            catching {
                app.contentResolver.registerContentObserver(CHANGED_URI, false, object : ContentObserver(worker) {
                    override fun onChange(selfChange: Boolean) = fetch(app)
                })
            }.onFailure { log("status bar margins: can't watch Folark ($it)") }
        }
    }

    private fun fetch(context: Context) {
        val answer: Bundle = catching {
            context.contentResolver.call(Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), METHOD, null, null)
        }.getOrNull() ?: return
        on = answer.getBoolean(EXTRA_ON, false)
        innerDp = answer.getInt(EXTRA_INNER, 0)
        coverDp = answer.getInt(EXTRA_COVER, 0)
        // Every bar again, on the main thread (turning it off gives Android's padding back).
        main.post { bars.keys.toList().forEach { v -> catching { apply(v) } } }
    }
}
