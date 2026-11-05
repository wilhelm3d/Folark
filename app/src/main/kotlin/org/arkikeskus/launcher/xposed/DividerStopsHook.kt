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
    /** The divider stays where it is let go (Settings ▸ Recents & split screen ▸ Free divider). */
    const val EXTRA_FREE = "free"
    val CHANGED_URI: Uri = Uri.parse("content://${RecentsHook.STATE_AUTHORITY}/divider_stops")

    /**
     * As Folark's setting says (asked when the first split is built, and again when it changes).
     * Until the answer is in it counts as on, the setting's default, so the first split after a
     * restart has the stops too.
     */
    @Volatile private var on = true
    /** The divider stays where it is let go, between [FREE_FROM] and [FREE_TO] of the screen. */
    @Volatile private var free = true
    @Volatile private var asked = false
    private var log: (String) -> Unit = {}
    private val worker by lazy { Handler(HandlerThread("folark-divider-stops").apply { start() }.looper) }

    private val CLASSES = listOf(
        "com.android.wm.shell.common.split.DividerSnapAlgorithm",
        "com.android.wm.shell.shared.split.DividerSnapAlgorithm",
    )

    private const val LAYOUT_MANAGER = "com.android.wm.shell.common.split.SplitLayoutManager"
    private const val SNAP_FIRST_THIRD = 0
    private const val SNAP_SECOND_THIRD = 2
    /** Inner stops of a two-app list: ⅓, ½, ⅔, 90/10, 10/90 and Motorola's 90/5, 5/90. */
    private val TWO_APP_SNAPS = setOf(0, 1, 2, 3, 4, 13, 14)
    /** Where a let-go divider stays put (of the screen): nearer an edge, Motorola's peek / close. */
    private const val FREE_FROM = 0.15f
    private const val FREE_TO = 0.85f
    /** How near the middle (of the screen) still counts as half and half. */
    private const val MIDDLE_REACH = 0.025f
    /** Three apps stay as let go while each has at least this much of the screen. */
    private const val FREE_MIN_PANE = 0.15f
    private const val STATE_THREE_EVEN = 5
    private const val LAYOUT_2 = "com.android.wm.shell.common.split.SplitLayout2Apps"
    private const val LAYOUT_3 = "com.android.wm.shell.common.split.SplitLayoutCarousel3Apps"
    /** How long a let-go divider takes to settle (it is already where it stays). */
    private const val FREE_SNAP_MS = 120
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
        hookTwoAppBounds(loader)
        hookFree(loader)
    }

    /**
     * The divider stays where it is let go (two apps and three), instead of Motorola's choice
     * between half and half, peek and closing:
     *
     * - two apps: let go between [FREE_FROM] and [FREE_TO] of the screen (not at the middle), the
     *   divider stays there (SplitLayout2Apps.onDividerFinishMoving → snapToTarget at that very
     *   place), and the layout is a third / two thirds state (0 / 2) by side
     *   (getSplitStateByDividerPositions), which [plainBounds] lays out at the real divider;
     * - three apps: let go with every app at least [FREE_MIN_PANE] wide, both dividers stay, and
     *   the layout is Motorola's all-on-screen state 5 (calculateNearestSplitSnapPosition), whose
     *   bounds follow the dividers.
     *
     * Nearer an edge, Motorola's own peek and close still work.
     */
    private fun hookFree(loader: ClassLoader) {
        var hooked = 0
        val algorithm = CLASSES.firstNotNullOfOrNull { runCatching { XposedHelpers.findClass(it, loader) }.getOrNull() }
        if (algorithm != null) {
            hooked += runCatching {
                XposedBridge.hookAllMethods(algorithm, "getSplitStateByDividerPositions", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!free) return
                        runCatching {
                            val result = param.result as? Int ?: return
                            if (result == -1) return
                            val position = param.args.getOrNull(0) as? Int ?: return
                            freeState(param.thisObject, position)?.let { param.result = it }
                        }
                    }
                }).size
            }.getOrDefault(0)
            hooked += runCatching {
                XposedBridge.hookAllMethods(algorithm, "calculateNearestSplitSnapPosition", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!free) return
                        runCatching {
                            val result = param.result as? Int ?: return
                            if (result != 6 && result != 7 && result != 21) return
                            val a = param.args.getOrNull(0) as? Int ?: return
                            val b = param.args.getOrNull(1) as? Int ?: return
                            if (threeFree(param.thisObject, minOf(a, b), maxOf(a, b))) param.result = STATE_THREE_EVEN
                        }
                    }
                }).size
            }.getOrDefault(0)
        }
        runCatching { XposedHelpers.findClass(LAYOUT_2, loader) }.getOrNull()?.let { cls ->
            hooked += runCatching {
                XposedBridge.hookAllMethods(cls, "onDividerFinishMoving", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!free || param.args.size != 3) return
                        runCatching { if (stayTwo(param.thisObject, param.args[2] as Int)) param.result = null }
                            .onFailure { log("free divider: Motorola's snap kept ($it)") }
                    }
                }).size
            }.getOrDefault(0)
        }
        runCatching { XposedHelpers.findClass(LAYOUT_3, loader) }.getOrNull()?.let { cls ->
            hooked += runCatching {
                XposedBridge.hookAllMethods(cls, "onDividerFinishMoving", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!free || param.args.size != 3) return
                        runCatching { if (stayThree(param.thisObject, param.args[0] as Int, param.args[2] as Int)) param.result = null }
                            .onFailure { log("free divider: Motorola's snap kept ($it)") }
                    }
                }).size
            }.getOrDefault(0)
        }
        if (algorithm != null) log("free divider: hooked $hooked method(s)")
    }

    /** The screen's length along the split (width side by side, else height), or null. */
    private fun span(algorithm: Any): Int? {
        val leftRight = field(algorithm, "mIsLeftRightSplit") as? Boolean ?: true
        return (field(algorithm, if (leftRight) "mDisplayWidth" else "mDisplayHeight") as? Int)?.takeIf { it > 0 }
    }

    /** The two-app state for a divider at [position] in the free range: 1 at the middle, else 0 / 2 by side. */
    private fun freeState(algorithm: Any, position: Int): Int? {
        val span = span(algorithm) ?: return null
        val f = position.toFloat() / span
        if (f < FREE_FROM || f > FREE_TO) return null
        val middle = (field(field(algorithm, "mMiddleTarget") ?: return null, "position") as? Int) ?: return null
        return when {
            kotlin.math.abs(position - middle) <= span * MIDDLE_REACH -> 1
            position < middle -> SNAP_FIRST_THIRD
            else -> SNAP_SECOND_THIRD
        }
    }

    /** Two apps let go at [position]: the divider stays there (true), or Motorola decides (false). */
    private fun stayTwo(layout: Any, position: Int): Boolean {
        val algorithm = field(layout, "mDividerSnapAlgorithm") ?: return false
        val state = freeState(algorithm, position) ?: return false
        if (state == 1) return false // half and half: Motorola's own
        val target = newTarget(field(algorithm, "mMiddleTarget") ?: return false, position, state) ?: return false
        val snap = generateSequence<Class<*>>(layout.javaClass) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == "snapToTarget" && it.parameterTypes.size == 4 } ?: return false
        snap.isAccessible = true
        snap.invoke(layout, position, target, FREE_SNAP_MS, android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
        return true
    }

    /** Three apps with dividers at [first] and [second]: each app at least [FREE_MIN_PANE] wide. */
    private fun threeFree(algorithm: Any, first: Int, second: Int): Boolean {
        val span = span(algorithm) ?: return false
        val min = span * FREE_MIN_PANE
        return first >= min && second - first >= min && span - second >= min
    }

    /** Three apps, divider [index] let go at [position]: both dividers stay (true), or Motorola decides. */
    private fun stayThree(layout: Any, index: Int, position: Int): Boolean {
        if (index !in 0..1) return false
        val algorithm = field(layout, "mDividerSnapAlgorithm") ?: return false
        val positions = (field(layout, "mDividerPositions") as? IntArray)?.copyOf() ?: return false
        if (positions.size != 2) return false
        positions[index] = position
        val first = minOf(positions[0], positions[1])
        val second = maxOf(positions[0], positions[1])
        if (!threeFree(algorithm, first, second)) return false
        val like = field(algorithm, "mMiddleTarget") ?: return false
        val t0 = newTarget(like, first, 5) ?: return false
        val t1 = newTarget(like, second, 6) ?: return false
        val now = field(layout, "mDividerPositions") as IntArray
        val snap = generateSequence<Class<*>>(layout.javaClass) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == "snapToTarget" && it.parameterTypes.size == 6 } ?: return false
        snap.isAccessible = true
        val from0 = if (index == 0) position else now[0]
        val from1 = if (index == 1) position else now[1]
        snap.invoke(layout, minOf(from0, from1), t0, maxOf(from0, from1), t1, FREE_SNAP_MS, android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
        return true
    }

    /**
     * Motorola sizes two apps itself (SplitLayoutManager.updateBoundsWithSnapPositionStageUISpec,
     * its "immersive view" policy) and only knows 50/50 and its 5/90 layouts there: for ⅓ and ⅔
     * (split states 0 and 2) it logs "unsupported" and keeps the small app as wide as the big one,
     * pushed out under the other, so a third looked cut off and wasn't usable. For those two states
     * the two apps are laid out side by side at the divider instead, as plain Android does
     * (SplitLayout2Apps without the policy): each app really that wide. Three apps, 50/50 and
     * Motorola's own layouts stay Motorola's.
     */
    private fun hookTwoAppBounds(loader: ClassLoader) {
        val cls = runCatching { XposedHelpers.findClass(LAYOUT_MANAGER, loader) }.getOrNull() ?: return
        val hooked = runCatching {
            XposedBridge.hookAllMethods(cls, "updateBoundsWithSnapPositionStageUISpec", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if ((!on && !free) || param.args.size != 7) return
                    val state = param.args[0] as? Int ?: return
                    if (state != SNAP_FIRST_THIRD && state != SNAP_SECOND_THIRD) return
                    runCatching {
                        if (plainBounds(param.thisObject, param.args)) param.result = null
                    }.onFailure { log("divider stops: Motorola's sizing kept ($it)") }
                }
            }).size
        }.getOrDefault(0)
        log(if (hooked > 0) "divider stops: two-app sizes laid out ($hooked)" else "divider stops: no two-app sizing here")
    }

    /**
     * The two-app bounds for divider position args[1] into args[3] (left / top), args[4] (right /
     * bottom) and args[5] (the divider), as AOSP's SplitLayout does. False (Motorola's way then)
     * when this isn't a two-app layout or a field is missing.
     */
    private fun plainBounds(manager: Any, args: Array<Any?>): Boolean {
        val layout = field(manager, "mSplitLayout") ?: return false
        if (!layout.javaClass.name.endsWith("SplitLayout2Apps")) return false
        val position = args[1] as? Int ?: return false
        val first = args[3] as? Rect ?: return false
        val second = args[4] as? Rect ?: return false
        val divider = args[5] as? Rect ?: return false
        val root = field(manager, "mRootBounds") as? Rect ?: return false
        val leftRight = field(manager, "mIsLeftRightSplit") as? Boolean ?: return false
        val size = field(manager, "mDividerSize") as? Int ?: return false
        val dividerBounds = generateSequence<Class<*>>(manager.javaClass) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == "calculateDividerBounds" && it.parameterTypes.size == 2 }
            ?.apply { isAccessible = true } ?: return false
        // Motorola's resizing effect (scaled / pushed-out surfaces) off for this layout.
        runCatching { (field(field(manager, "mImmersiveSurfaceEffectPolicy")!!, "mStageUISpecs") as? MutableCollection<*>)?.clear() }
        divider.set(root)
        first.set(root)
        second.set(root)
        if (leftRight) {
            val at = position + root.left
            dividerBounds.invoke(manager, at, divider)
            first.right = at
            second.left = at + size
        } else {
            val at = position + root.top
            dividerBounds.invoke(manager, at, divider)
            first.bottom = at
            second.top = at + size
        }
        return true
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
        free = answer.getBoolean(EXTRA_FREE, true)
        log("divider stops: ${if (on) "33/50/67" else "Android's own"}, ${if (free) "stays where let go" else "snaps"} (from the next split)")
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
