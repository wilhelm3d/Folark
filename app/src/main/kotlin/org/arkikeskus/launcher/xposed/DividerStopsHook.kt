package org.arkikeskus.launcher.xposed

import android.content.Context
import android.database.ContentObserver
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * In System UI (WM Shell): the split divider's stops at 30 %, 50 % and 70 % (Settings ▸ Recents &
 * split screen ▸ Divider stops). The Razr's split screen only offers 50/50 besides the "close"
 * ends; every new DividerSnapAlgorithm gets its ⅓ and ⅔ stops replaced (or added) at 30 % and
 * 70 %, under the same snap positions (SNAP_TO_2_33_66 = 0, SNAP_TO_2_66_33 = 2), so dragging the
 * divider rests there and everything that asks for "a third" (Folark's layouts, saved pairs, the
 * system) lands on them. 50/50, peek stops and the dismiss ends stay as they are.
 *
 * Anything unexpected (a renamed field, a target it can't build) leaves the stops untouched.
 */
internal object DividerStopsHook {
    const val METHOD = "divider_stops"
    const val EXTRA_ON = "on"
    val CHANGED_URI: Uri = Uri.parse("content://${RecentsHook.STATE_AUTHORITY}/divider_stops")

    /** Off until Folark says otherwise (asked once System UI is up, and again when it changes). */
    @Volatile private var on = false
    @Volatile private var asked = false
    private var log: (String) -> Unit = {}
    private val worker by lazy { Handler(HandlerThread("folark-divider-stops").apply { start() }.looper) }

    private val CLASSES = listOf(
        "com.android.wm.shell.common.split.DividerSnapAlgorithm",
        "com.android.wm.shell.shared.split.DividerSnapAlgorithm",
    )

    private const val SNAP_FIRST_THIRD = 0
    private const val SNAP_SECOND_THIRD = 2
    private const val FIRST = 0.30f
    private const val SECOND = 0.70f

    fun install(loader: ClassLoader, log: (String) -> Unit) {
        this.log = log
        var hooked = 0
        for (name in CLASSES) {
            val cls = runCatching { XposedHelpers.findClass(name, loader) }.getOrNull() ?: continue
            hooked += runCatching {
                XposedBridge.hookAllConstructors(cls, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        ask()
                        if (on) runCatching { addStops(param.thisObject) }.onFailure { log("divider stops: left as they are ($it)") }
                    }
                }).size
            }.getOrDefault(0)
        }
        log(if (hooked > 0) "divider stops: hooked $hooked constructor(s)" else "divider stops: no DividerSnapAlgorithm here")
    }

    /** Replaces the ⅓ / ⅔ stops of [algorithm] with 30 % / 70 % ones (adds them where missing). */
    private fun addStops(algorithm: Any) {
        @Suppress("UNCHECKED_CAST")
        val targets = field(algorithm, "mTargets") as? MutableList<Any?> ?: error("no mTargets")
        if (targets.size < 3) error("only ${targets.size} targets")
        val middle = field(algorithm, "mMiddleTarget") ?: targets[targets.size / 2] ?: error("no middle")
        val middleAt = field(middle, "position") as? Int ?: error("no target position")

        // The length the divider moves along (between the insets), from the algorithm itself.
        val leftRight = (field(algorithm, "mIsLeftRightSplit") as? Boolean)
            ?: (field(algorithm, "mIsHorizontalDivision") as? Boolean)?.not()
            ?: true
        val width = field(algorithm, "mDisplayWidth") as? Int ?: error("no display width")
        val height = field(algorithm, "mDisplayHeight") as? Int ?: error("no display height")
        val insets = field(algorithm, "mInsets") as? Rect ?: Rect()
        val span = if (leftRight) width - insets.left - insets.right else height - insets.top - insets.bottom
        if (span <= 0) error("no room ($span)")
        // Measured from the 50/50 stop, so however Android places the divider (its own width,
        // insets), 30 % and 70 % sit the same way.
        fun at(ratio: Float) = middleAt + ((ratio - 0.5f) * span).toInt()

        val first = newTarget(middle, at(FIRST), SNAP_FIRST_THIRD) ?: error("can't build a stop")
        val second = newTarget(middle, at(SECOND), SNAP_SECOND_THIRD) ?: error("can't build a stop")
        val dismissStart = targets.first()
        val dismissEnd = targets.last()
        val kept = targets.subList(1, targets.size - 1).filterNotNull().filter {
            val snap = field(it, "snapPosition") as? Int
            snap != SNAP_FIRST_THIRD && snap != SNAP_SECOND_THIRD
        }
        val inner: List<Any> = (kept + first + second).sortedBy { field(it, "position") as? Int ?: 0 }
        targets.clear()
        targets.add(dismissStart)
        targets.addAll(inner)
        targets.add(dismissEnd)
        // The outermost stops the divider can rest at (beyond them it closes a side).
        setField(algorithm, "mFirstSplitTarget", inner.first())
        setField(algorithm, "mLastSplitTarget", inner.last())
    }

    /** A SnapTarget like [like] at [position] for [snapPosition]; other parameters neutral. */
    private fun newTarget(like: Any, position: Int, snapPosition: Int): Any? {
        val int = Int::class.javaPrimitiveType
        val constructor = like.javaClass.declaredConstructors
            .filter { it.parameterTypes.size >= 2 && it.parameterTypes[0] == int && it.parameterTypes[1] == int }
            .minByOrNull { it.parameterTypes.size } ?: return null
        constructor.isAccessible = true
        val args: List<Any?> = constructor.parameterTypes.mapIndexed { i, type ->
            when {
                i == 0 -> position
                i == 1 -> snapPosition
                type == int -> 0 // FLAG_NONE
                type == Float::class.javaPrimitiveType -> 1f // distance multiplier
                type == Boolean::class.javaPrimitiveType -> false
                type == Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        }
        return constructor.newInstance(*args.toTypedArray()).takeIf { field(it, "position") == position }
    }

    // ------------------------------------------------------------------ Folark's setting

    private fun ask() {
        if (asked) return
        val app = runCatching {
            Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
        }.getOrNull() ?: return
        asked = true
        worker.post {
            fetch(app)
            runCatching {
                app.contentResolver.registerContentObserver(CHANGED_URI, false, object : ContentObserver(worker) {
                    override fun onChange(selfChange: Boolean) = fetch(app)
                })
            }.onFailure { log("divider stops: can't watch Folark ($it)") }
        }
    }

    private fun fetch(context: Context) {
        val answer: Bundle = runCatching {
            context.contentResolver.call(Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), METHOD, null, null)
        }.getOrNull() ?: return
        on = answer.getBoolean(EXTRA_ON, false)
        log("divider stops: ${if (on) "30/50/70" else "Android's own"} (from the next split)")
    }

    private fun field(target: Any, name: String): Any? {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            runCatching { return cls.getDeclaredField(name).apply { isAccessible = true }.get(target) }
            cls = cls.superclass
        }
        return null
    }

    private fun setField(target: Any, name: String, value: Any?) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            val done = runCatching { cls.getDeclaredField(name).apply { isAccessible = true }.set(target, value) }.isSuccess
            if (done) return
            cls = cls.superclass
        }
    }
}
