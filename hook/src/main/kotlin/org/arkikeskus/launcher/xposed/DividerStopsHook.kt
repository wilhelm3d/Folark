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
    const val EXTRA_LOCK = "lock"
    const val EXTRA_MARKS = "marks"
    const val EXTRA_HAPTICS = "haptics"
    const val EXTRA_KEEP = "keep"
    const val EXTRA_PEEK_FLING = "peek_fling"
    val CHANGED_URI: Uri = Uri.parse("content://${RecentsHook.STATE_AUTHORITY}/divider_stops")

    /**
     * As Folark's setting says (asked when the first split is built, and again when it changes).
     * Until the answer is in it counts as on, the setting's default, so the first split after a
     * restart has the stops too.
     */
    @Volatile private var on = true
    /** The divider stays where it is let go, between [FREE_FROM] and [FREE_TO] of the screen. */
    @Volatile private var free = true
    /** Which dividers are locked: "off", "two", "three" or "all" (S13). */
    @Volatile private var lock = "off"
    /** Let-go near ⅓ / ½ / ⅔ lands on it (S5); a tick when the divider passes one (S6). */
    @Volatile private var marks = true
    @Volatile private var haptics = true
    /** A split keeps its sizes when the phone turns or folds (S7). */
    @Volatile private var keep = true
    /** A fling of peek view's divider towards the strip closes that app (P13). */
    @Volatile private var peekFling = false
    /** A divider is being dragged now (for the marks overlay and the ticks). */
    @Volatile private var dragging = false
    @Volatile private var lastZone = -1

    /**
     * Until when (uptime) Motorola's own divider animation runs (to peek, half and half, closing,
     * its three-app layouts): Folark's sizing stays out of it then. Motorola keeps resizing data
     * from frame to frame (StageUISpecs), and switching layouts under it mid-way crashed System UI.
     */
    @Volatile private var motoUntil = 0L
    /** Set while Folark itself moves the divider (its own snaps don't count as Motorola's). */
    @Volatile private var ours = false

    private fun motoMoving(): Boolean = android.os.SystemClock.uptimeMillis() < motoUntil
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
    /** Motorola's peek states: the strip at the start (left / top) or at the end. */
    private const val STATE_PEEK_START = 23
    private const val STATE_PEEK_END = 22
    /** How fast (px/s) a fling of peek view's divider must be to close the strip's app. */
    private const val FLING_CLOSE_SPEED = 2_500f
    /** When a divider's final place is reported to Folark after it rests. */
    private const val REPORT_AFTER_MS = 700L
    /** How long after Motorola starts a divider animation Folark's sizing keeps out. */
    private const val MOTO_WINDOW_MS = 900L
    /** Snap positions of Folark's layouts: ⅓, ⅔ (two apps) and three apps all on screen. */
    private val FOLARK_SNAPS = setOf(0, 2, 5, 6)
    /** Whether the snapToTarget running on this thread turned [ours] on (and so turns it off). */
    private val snapSetOurs = ThreadLocal<Boolean>()
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
                        if (!free || motoMoving()) return
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
                        if (!free || motoMoving()) return
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
                        dragEnded()
                        if (param.args.size != 3) return
                        val layout = param.thisObject
                        val velocity = (param.args[1] as? Float) ?: 0f
                        var position = param.args[2] as Int
                        runCatching { if (flingClosesPeek(layout, position, velocity)) { param.result = null; return } }
                        if (!free) return
                        runCatching {
                            position = magnet(layout, position)
                            if (stayTwo(layout, position)) param.result = null
                        }.onFailure { log("free divider: Motorola's snap kept ($it)") }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) = reportLayoutSoon()
                }).size
            }.getOrDefault(0)
        }
        runCatching { XposedHelpers.findClass(LAYOUT_3, loader) }.getOrNull()?.let { cls ->
            hooked += runCatching {
                XposedBridge.hookAllMethods(cls, "onDividerFinishMoving", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        dragEnded()
                        if (!free || param.args.size != 3) return
                        runCatching {
                            val layout = param.thisObject
                            if (stayThree(layout, param.args[0] as Int, magnet(layout, param.args[2] as Int, three = true))) param.result = null
                        }.onFailure { log("free divider: Motorola's snap kept ($it)") }
                    }

                    override fun afterHookedMethod(param: MethodHookParam) = reportLayoutSoon()
                }).size
            }.getOrDefault(0)
        }
        // Motorola's own divider animations (any not started by Folark's free divider).
        for (name in listOf(LAYOUT_2, LAYOUT_3)) {
            val cls = runCatching { XposedHelpers.findClass(name, loader) }.getOrNull() ?: continue
            // A snap to one of Folark's layouts (⅓ / ⅔, three apps all on screen) is Folark's own,
            // whoever asked for it (the free divider, the size buttons, 33/33/33).
            hooked += runCatching {
                XposedBridge.hookAllMethods(cls, "snapToTarget", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val targets = param.args.filter { it != null && it !is Int && it !is android.view.animation.Interpolator }
                        val snaps = targets.map { field(it!!, "snapPosition") as? Int }
                        val mine = snaps.isNotEmpty() && snaps.all { it in FOLARK_SNAPS } && !ours
                        snapSetOurs.set(mine)
                        if (mine) ours = true
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (snapSetOurs.get() == true) ours = false
                        snapSetOurs.set(false)
                    }
                }).size
            }.getOrDefault(0)
            val names = cls.declaredMethods.map { it.name }.filter { it.startsWith("flingDividerPosition") }.toSet()
            for (name in names) {
                hooked += runCatching {
                    XposedBridge.hookAllMethods(cls, name, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!ours) motoUntil = android.os.SystemClock.uptimeMillis() + MOTO_WINDOW_MS
                        }
                    }).size
                }.getOrDefault(0)
            }
        }
        hooked += hookDragAndLock(loader)
        if (algorithm != null) log("free divider: hooked $hooked method(s)")
    }

    // ------------------------------------------------------------------ lock, marks, keep size, peek fling

    private fun hookDragAndLock(loader: ClassLoader): Int {
        var hooked = 0
        for (name in listOf(LAYOUT_2, LAYOUT_3)) {
            val cls = runCatching { XposedHelpers.findClass(name, loader) }.getOrNull() ?: continue
            val three = name == LAYOUT_3
            // S13: a locked divider counts as one Motorola doesn't let move (its own check).
            hooked += runCatching {
                XposedBridge.hookAllMethods(cls, "isNotMovableDivider", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (lock == "all" || (lock == "two" && !three) || (lock == "three" && three)) param.result = true
                    }
                }).size
            }.getOrDefault(0)
            // S5 / S6: the drag starts (Folark shows its marks) and passes ⅓, ½, ⅔ (a tick).
            hooked += runCatching {
                XposedBridge.hookAllMethods(cls, "onDividerMoving", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        runCatching { onDragMove(param.thisObject, param.args[0] as Int, param.args[1] as Int, three) }
                    }
                }).size
            }.getOrDefault(0)
            // S7: Motorola puts the divider back on one of its fixed stops after a turn or fold.
            hooked += runCatching {
                XposedBridge.hookAllMethods(cls, "initDividerPositionByRatio", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!keep || !free || motoMoving()) return
                        runCatching {
                            val kept = if (three) keepThree(param.thisObject, param.args[0] as Rect, param.args[1] as Boolean)
                            else keepTwo(param.thisObject, param.args[0] as Rect, param.args[1] as Boolean)
                            if (kept) {
                                param.result = null
                                reportLayoutSoon()
                            }
                        }.onFailure { log("keep size: Motorola's kept ($it)") }
                    }
                }).size
            }.getOrDefault(0)
        }
        return hooked
    }

    /** The marks ⅓, ½, ⅔ the drag ticks at and a let-go lands on (S5, S6). */
    private val MARKS = floatArrayOf(1f / 3, 0.5f, 2f / 3)
    private val MARKS_THREE = floatArrayOf(0.25f, 1f / 3, 0.5f, 2f / 3, 0.75f)
    private const val MAGNET = 0.025f

    private fun onDragMove(layout: Any, index: Int, position: Int, three: Boolean) {
        val algorithm = field(layout, "mDividerSnapAlgorithm") ?: return
        val span = span(algorithm) ?: return
        if (!dragging) {
            dragging = true
            lastZone = -1
            SplitThreeHook.reportDrag(true, if (three) 3 else 2)
        }
        if (!haptics) return
        val f = position.toFloat() / span
        val zone = (if (three) MARKS_THREE else MARKS).count { it < f }
        if (lastZone >= 0 && zone != lastZone) dividerView(layout, index, three)?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        lastZone = zone
    }

    private fun dragEnded() {
        if (!dragging) return
        dragging = false
        SplitThreeHook.reportDrag(false, 0)
    }

    private fun dividerView(layout: Any, index: Int, three: Boolean): android.view.View? = runCatching {
        val manager = if (three) (field(layout, "mSplitWindowManagers") as? Array<*>)?.getOrNull(index) else field(layout, "mSplitWindowManager")
        manager?.let { field(it, "mDividerView") } as? android.view.View
    }.getOrNull()

    /** [position] moved onto a mark when let go close to it (S5); unchanged otherwise. */
    private fun magnet(layout: Any, position: Int, three: Boolean = false): Int {
        if (!marks) return position
        val algorithm = field(layout, "mDividerSnapAlgorithm") ?: return position
        val span = span(algorithm) ?: return position
        val f = position.toFloat() / span
        val mark = (if (three) MARKS_THREE else MARKS).minByOrNull { kotlin.math.abs(it - f) } ?: return position
        if (kotlin.math.abs(mark - f) > MAGNET) return position
        if (!three && mark == 0.5f) {
            (field(field(algorithm, "mMiddleTarget") ?: return position, "position") as? Int)?.let { return it }
        }
        return (mark * span).toInt()
    }

    /**
     * P13: peek view's divider flung hard towards the strip: that app closes (the divider goes to
     * the dismiss stop on its side, as Motorola's own close does).
     */
    private fun flingClosesPeek(layout: Any, position: Int, velocity: Float): Boolean {
        if (!peekFling) return false
        val manager = field(layout, "mSplitLayoutManager") ?: return false
        val state = field(field(manager, "mSplitState") ?: return false, "mState") as? Int ?: return false
        val algorithm = field(layout, "mDividerSnapAlgorithm") ?: return false
        val target = when {
            state == STATE_PEEK_START && velocity < -FLING_CLOSE_SPEED -> field(algorithm, "mDismissStartTarget")
            state == STATE_PEEK_END && velocity > FLING_CLOSE_SPEED -> field(algorithm, "mDismissEndTarget")
            else -> null
        } ?: return false
        val snap = Reflect.methods(layout.javaClass).firstOrNull { it.name == "snapToTarget" && it.parameterTypes.size == 4 } ?: return false
        snap.invoke(layout, position, target, FREE_SNAP_MS * 2, android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
        return true
    }

    /** S7, two apps at a free size: the divider at the same share of the new screen. */
    private fun keepTwo(layout: Any, before: Rect, wasLeftRight: Boolean): Boolean {
        val manager = field(layout, "mSplitLayoutManager") ?: return false
        val state = field(field(manager, "mSplitState") ?: return false, "mState") as? Int ?: return false
        if (state != SNAP_FIRST_THIRD && state != SNAP_SECOND_THIRD) return false
        val leftRight = field(manager, "mIsLeftRightSplit") as? Boolean ?: return false
        if (leftRight != wasLeftRight) return false
        val root = field(layout, "mRootBounds") as? Rect ?: return false
        val oldSpan = if (wasLeftRight) before.width() else before.height()
        val newSpan = if (leftRight) root.width() else root.height()
        val position = field(layout, "mDividerPosition") as? Int ?: return false
        if (oldSpan <= 0 || newSpan <= 0) return false
        val now = (position.toLong() * newSpan / oldSpan).toInt()
        val update = Reflect.methods(layout.javaClass).firstOrNull {
            it.name.startsWith("updateBounds") && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        } ?: return false
        setField(layout, "mDividerPosition", now)
        update.invoke(layout, now)
        return true
    }

    /** S7, three apps all on screen: both dividers at the same shares of the new screen. */
    private fun keepThree(layout: Any, before: Rect, wasLeftRight: Boolean): Boolean {
        val manager = field(layout, "mSplitLayoutManager") ?: return false
        val state = field(field(manager, "mSplitState") ?: return false, "mState") as? Int ?: return false
        if (state != STATE_THREE_EVEN) return false
        val leftRight = field(manager, "mIsLeftRightSplit") as? Boolean ?: return false
        if (leftRight != wasLeftRight) return false
        val root = field(layout, "mRootBounds") as? Rect ?: return false
        val oldSpan = if (wasLeftRight) before.width() else before.height()
        val newSpan = if (leftRight) root.width() else root.height()
        val positions = field(layout, "mDividerPositions") as? IntArray ?: return false
        if (oldSpan <= 0 || newSpan <= 0 || positions.size != 2) return false
        val update = Reflect.methods(layout.javaClass).firstOrNull {
            it.name.startsWith("updateBounds") && it.parameterTypes.size == 2 &&
                it.parameterTypes.all { t -> t == Int::class.javaPrimitiveType }
        } ?: return false
        positions[0] = (positions[0].toLong() * newSpan / oldSpan).toInt()
        positions[1] = (positions[1].toLong() * newSpan / oldSpan).toInt()
        update.invoke(layout, positions[0], positions[1])
        return true
    }

    /** After a divider rests: its place goes to Folark (S1: each set of apps remembers its sizes). */
    private fun reportLayoutSoon() {
        worker.postDelayed({ runCatching { SplitThreeHook.reportLayout() } }, REPORT_AFTER_MS)
    }

    /**
     * Two apps: the divider to [position] (S1 / S4 / S11) — a free place in the middle range, the
     * nearest of Motorola's own stops outside it (peek), or half and half at the middle.
     */
    fun placeTwo(layout: Any, position: Int): Boolean {
        val algorithm = field(layout, "mDividerSnapAlgorithm") ?: return false
        val span = span(algorithm) ?: return false
        val f = position.toFloat() / span
        val middle = field(algorithm, "mMiddleTarget") ?: return false
        val target = when {
            f in FREE_FROM..FREE_TO -> {
                val state = freeState(algorithm, position) ?: return false
                if (state == 1) middle else newTarget(middle, position, state)
            }
            else -> {
                val targets = (field(algorithm, "mTargets") as? List<*>).orEmpty().filterNotNull()
                val inner = if (targets.size >= 3) targets.subList(1, targets.size - 1) else targets
                inner.minByOrNull { kotlin.math.abs(((field(it, "position") as? Int) ?: Int.MAX_VALUE / 2) - position) }
            }
        } ?: return false
        val from = field(layout, "mDividerPosition") as? Int ?: return false
        val snap = Reflect.methods(layout.javaClass).firstOrNull { it.name == "snapToTarget" && it.parameterTypes.size == 4 } ?: return false
        snap.invoke(layout, from, target, FREE_SNAP_MS * 3, android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
        reportLayoutSoon()
        return true
    }

    /** Three apps: the dividers to [first] and [second] (S1 / S4 / S11); Motorola's layout for narrow panes. */
    fun placeThree(layout: Any, first: Int, second: Int): Boolean {
        val algorithm = field(layout, "mDividerSnapAlgorithm") ?: return false
        val like = field(algorithm, "mMiddleTarget") ?: return false
        // All on screen: Folark's targets (state 5); a narrow strip: Motorola's (its peek / carousel).
        val free = threeFree(algorithm, first, second)
        val t0 = newTarget(like, first, if (free) 5 else 11) ?: return false
        val t1 = newTarget(like, second, if (free) 6 else 12) ?: return false
        val now = field(layout, "mDividerPositions") as? IntArray ?: return false
        val snap = Reflect.methods(layout.javaClass).firstOrNull { it.name == "snapToTarget" && it.parameterTypes.size == 6 } ?: return false
        clearSpecs(field(layout, "mSplitLayoutManager"))
        snap.invoke(layout, now[0], t0, now[1], t1, FREE_SNAP_MS * 3, android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f))
        reportLayoutSoon()
        return true
    }

    /**
     * Three apps in the all-on-screen layout (state 5, even or as let go): args[4..6] the apps
     * left to right (or top to bottom), args[7] / args[8] the dividers, at the divider positions
     * args[1] / args[2] — as Motorola's own code does when the dividers rest (its resizing branch
     * kept the old widths, so a moved divider left apps cut off).
     */
    private fun plainBoundsThree(manager: Any, args: Array<Any?>): Boolean {
        val layout = field(manager, "mSplitLayout") ?: return false
        if (!layout.javaClass.name.endsWith("SplitLayoutCarousel3Apps")) return false
        val p0 = args[1] as? Int ?: return false
        val p1 = args[2] as? Int ?: return false
        val rects = (4..8).map { args[it] as? Rect ?: return false }
        val root = field(manager, "mRootBounds") as? Rect ?: return false
        val leftRight = field(manager, "mIsLeftRightSplit") as? Boolean ?: return false
        val size = field(manager, "mDividerSize") as? Int ?: return false
        val dividerBounds = Reflect.methods(manager.javaClass)
            .firstOrNull { it.name == "calculateDividerBounds" && it.parameterTypes.size == 2 }
            ?.apply { isAccessible = true } ?: return false
        if (args[9] != true) clearSpecs(manager)
        val first = minOf(p0, p1)
        val second = maxOf(p0, p1)
        rects.forEach { it.set(root) }
        val (a, b, c, d0, d1) = rects
        if (leftRight) {
            val at0 = first + root.left
            val at1 = second + root.left
            dividerBounds.invoke(manager, at0, d0)
            dividerBounds.invoke(manager, at1, d1)
            a.right = at0
            b.left = at0 + size
            b.right = at1
            c.left = at1 + size
        } else {
            val at0 = first + root.top
            val at1 = second + root.top
            dividerBounds.invoke(manager, at0, d0)
            dividerBounds.invoke(manager, at1, d1)
            a.bottom = at0
            b.top = at0 + size
            b.bottom = at1
            c.top = at1 + size
        }
        return true
    }

    /** Motorola's per-frame resizing data (StageUISpecs) cleared, so surfaces follow the bounds. */
    private fun clearSpecs(manager: Any?) {
        manager ?: return
        runCatching { (field(field(manager, "mImmersiveSurfaceEffectPolicy")!!, "mStageUISpecs") as? MutableCollection<*>)?.clear() }
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
        val snap = Reflect.methods(layout.javaClass)
            .firstOrNull { it.name == "snapToTarget" && it.parameterTypes.size == 4 } ?: return false
        snap.isAccessible = true
        ours = true
        try {
            snap.invoke(layout, position, target, FREE_SNAP_MS, android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
        } finally {
            ours = false
        }
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
        val snap = Reflect.methods(layout.javaClass)
            .firstOrNull { it.name == "snapToTarget" && it.parameterTypes.size == 6 } ?: return false
        snap.isAccessible = true
        val from0 = if (index == 0) position else now[0]
        val from1 = if (index == 1) position else now[1]
        // Motorola's resizing effect from the drag off: the three apps take the dividers' places.
        clearSpecs(field(layout, "mSplitLayoutManager"))
        ours = true
        try {
            snap.invoke(layout, minOf(from0, from1), t0, maxOf(from0, from1), t1, FREE_SNAP_MS, android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f))
        } finally {
            ours = false
        }
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
                    if ((!on && !free) || motoMoving()) return
                    val state = param.args.getOrNull(0) as? Int ?: return
                    runCatching {
                        when {
                            param.args.size == 7 && (state == SNAP_FIRST_THIRD || state == SNAP_SECOND_THIRD) ->
                                if (plainBounds(param.thisObject, param.args)) param.result = null
                            param.args.size == 10 && state == STATE_THREE_EVEN ->
                                if (plainBoundsThree(param.thisObject, param.args)) param.result = null
                        }
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
        val dividerBounds = Reflect.methods(manager.javaClass)
            .firstOrNull { it.name == "calculateDividerBounds" && it.parameterTypes.size == 2 }
            ?.apply { isAccessible = true } ?: return false
        // Motorola's resizing effect (scaled / pushed-out surfaces) off for this layout, once the
        // divider rests (while it moves Motorola keeps using it, as its own code does).
        if (args[6] != true) clearSpecs(manager)
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
        val constructor = Reflect.constructors(like.javaClass)
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
        lock = answer.getString(EXTRA_LOCK) ?: "off"
        marks = answer.getBoolean(EXTRA_MARKS, true)
        haptics = answer.getBoolean(EXTRA_HAPTICS, true)
        keep = answer.getBoolean(EXTRA_KEEP, true)
        peekFling = answer.getBoolean(EXTRA_PEEK_FLING, false)
        log("divider stops: ${if (on) "33/50/67" else "Android's own"}, ${if (free) "stays where let go" else "snaps"} (from the next split)")
    }

    private fun field(target: Any, name: String): Any? = Reflect.get(target, name)

    private fun setField(target: Any, name: String, value: Any?) {
        Reflect.set(target, name, value)
    }
}
