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
 * In System UI (WM Shell): the split divider's stops at ⅓, ½ and ⅔ (Settings ▸ Recents & split
 * screen ▸ Divider stops).
 *
 * How the Razr's WM Shell works (read from its SystemUI): with Motorola's three-app split on,
 * DividerSnapAlgorithm runs in snap mode 6, and on the inner screen side by side its two-app stops
 * are 5/90 (14), 50/50 (1) and 90/5 (13) only. After every move the layout takes the split state
 * of the stop nearest the divider (getSplitStateByDividerPositions: stop 0 → state 0, 2 → 2) and
 * sizes the apps from SplitState's table, which has ⅓ (33 %) and ⅔ (67 %) for states 0 and 2.
 * updateTargets() rebuilds the stops on every rotation, bounds or state change, so the stops are
 * added after each rebuild (not once), at 33 % and 67 % to match that table exactly. Middle,
 * first, last and the Moto 5/90 stops stay Android's; the second / second-last stops become ours,
 * as updateTargets itself picks them for a five-stop list.
 *
 * Lists with three-app stops (Motorola's 7 to 12) are left alone, and anything unexpected (a
 * renamed field, a target it can't build) leaves the stops untouched.
 */
internal object DividerStopsHook {
    const val METHOD = "divider_stops"
    const val EXTRA_ON = "on"
    val CHANGED_URI: Uri = Uri.parse("content://${RecentsHook.STATE_AUTHORITY}/divider_stops")

    /**
     * As Folark's setting says (asked when the first split is built, and again when it changes).
     * Until the answer is in it counts as on, the setting's default, so the first split after a
     * restart has the stops too.
     */
    @Volatile private var on = true
    @Volatile private var asked = false
    private var log: (String) -> Unit = {}
    private val worker by lazy { Handler(HandlerThread("folark-divider-stops").apply { start() }.looper) }

    private val CLASSES = listOf(
        "com.android.wm.shell.common.split.DividerSnapAlgorithm",
        "com.android.wm.shell.shared.split.DividerSnapAlgorithm",
    )

    private const val SNAP_FIRST_THIRD = 0
    private const val SNAP_SECOND_THIRD = 2
    /** Inner stops of a two-app list: ⅓, ½, ⅔, 90/10, 10/90 and Motorola's 90/5, 5/90. */
    private val TWO_APP_SNAPS = setOf(0, 1, 2, 3, 4, 13, 14)
    private const val FIRST = 0.33f
    private const val SECOND = 0.67f

    fun install(loader: ClassLoader, log: (String) -> Unit) {
        this.log = log
        var hooked = 0
        for (name in CLASSES) {
            val cls = runCatching { XposedHelpers.findClass(name, loader) }.getOrNull() ?: continue
            // After every rebuild of the stops (the constructor calls it too).
            hooked += runCatching {
                XposedBridge.hookAllMethods(cls, "updateTargets", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        ask()
                        if (on) runCatching { addStops(param.thisObject) }.onFailure { log("divider stops: left as they are ($it)") }
                    }
                }).size
            }.getOrDefault(0)
        }
        log(if (hooked > 0) "divider stops: hooked updateTargets ($hooked)" else "divider stops: no DividerSnapAlgorithm.updateTargets here")
    }

    /** Replaces the ⅓ / ⅔ stops of [algorithm] with 33 % / 67 % ones (adds them where missing). */
    private fun addStops(algorithm: Any) {
        @Suppress("UNCHECKED_CAST")
        val targets = field(algorithm, "mTargets") as? MutableList<Any?> ?: error("no mTargets")
        if (targets.size < 3) error("only ${targets.size} targets")
        // Only a two-app list (its inner stops all two-app ones) gets the sizes; three-app layouts
        // (Motorola's stops 5 to 12) keep theirs.
        val innerSnaps = targets.subList(1, targets.size - 1).map { field(it ?: return, "snapPosition") as? Int ?: return }
        if (innerSnaps.any { it !in TWO_APP_SNAPS }) return
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
        // insets), 33 % and 67 % sit the same way.
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
        // As updateTargets picks them: the outermost stops stay (Motorola's 5/90 and 90/5 when
        // there), the second and second-last are the sizes. Older builds' names too.
        val all = targets.filterNotNull()
        setField(algorithm, "mMiddleTarget", all[all.size / 2])
        setField(algorithm, "mFirstDividerTarget", all[1])
        setField(algorithm, "mLastDividerTarget", all[all.size - 2])
        if (all.size >= 5) {
            setField(algorithm, "mSecondDividerTarget", all[2])
            setField(algorithm, "mSecondLastDividerTarget", all[all.size - 3])
        }
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
        log("divider stops: ${if (on) "33/50/67" else "Android's own"} (from the next split)")
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
