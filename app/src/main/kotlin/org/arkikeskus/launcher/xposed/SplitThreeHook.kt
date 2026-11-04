package org.arkikeskus.launcher.xposed

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.PathInterpolator
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.arkikeskus.launcher.data.SplitEvents
import org.arkikeskus.launcher.data.SplitThree
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * The SystemUI half of Folark's native split screen. Android 17's split screen (WM Shell, inside
 * SystemUI) can tile two or three apps, but nothing on the phone offers that to a launcher, so this
 * hook lets Folark ask for it:
 *
 * - it keeps the live `SplitScreenController` (caught when it starts, or the first time it is used),
 * - it listens for Folark's requests, accepted only when Android confirms the sender is Folark
 *   (`getSentFromPackage`, Android 14+), so no other app can start tasks through it,
 * - it runs each request on WM Shell's own thread: two running tasks into a split ([startSplitTasksNative]),
 *   a third app into the third place ([attempt]), a divider snap ([snapTo]) or Motorola's own
 *   split-select ([selectNative]),
 * - it tells Folark whether that worked, so Folark can fall back to its other ways,
 * - and it adds a listener to WM Shell's split screen ([attachListener]) that passes every change on
 *   to Folark (an app entering or leaving a split stage, the split showing or hiding), so Folark
 *   waits for the change instead of reading Android's window list again and again.
 *
 * Every step is wrapped: if anything is missing or different on another phone, it just answers
 * "no" and Folark uses the old way.
 */
internal object SplitThreeHook {
    private const val CONTROLLER = "com.android.wm.shell.splitscreen.SplitScreenController"
    private const val FOLARK = "org.arkikeskus.launcher"

    /** WM Shell's split positions (SplitScreenConstants): the new app goes on the right / bottom. */
    private const val SPLIT_POSITION_BOTTOM_OR_RIGHT = 1

    /** The third place of a split (0 and 1 are the two halves). */
    private const val THIRD_INDEX = 2

    private const val WAIT_STEP_MS = 100L
    private const val WAIT_TRIES = 25

    /** How long the divider takes to glide to its new place (as when dropped by a finger). */
    private const val SNAP_MS = 350

    /** How far (of the divider's whole travel) a stop may be from a third and still count as one. */
    private const val THIRD_REACH = 0.08f

    private const val LISTENER = "com.android.wm.shell.splitscreen.SplitScreen\$SplitScreenListener"

    @Volatile private var controller: Any? = null
    @Volatile private var listening = false
    @Volatile private var loader: ClassLoader? = null
    @Volatile private var splitListener: Any? = null
    @Volatile private var appContext: Context? = null
    /** Everything sent to Folark goes out here, in order and off WM Shell's thread. */
    private val outbox = Executors.newSingleThreadExecutor()
    private var log: (String) -> Unit = {}

    fun install(loader: ClassLoader, log: (String) -> Unit) {
        this.log = log
        this.loader = loader
        val cls = runCatching { XposedHelpers.findClass(CONTROLLER, loader) }.getOrNull()
        if (cls == null) {
            log("no $CONTROLLER in SystemUI; split hook stays off")
            return
        }
        val capture = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching { keep(param.thisObject) }
            }
        }
        val hooked = runCatching {
            XposedBridge.hookAllMethods(cls, "onInit", capture).size +
                XposedBridge.hookAllMethods(cls, "isSplitScreenVisible", capture).size
        }.getOrDefault(0)
        log("split hook: hooked $hooked SystemUI method(s)")
    }

    private fun keep(found: Any?) {
        if (found == null || controller != null) return
        controller = found
        listen()
    }

    private fun listen() {
        if (listening) return
        val c = controller ?: return
        val context = (field(c, "mContext") as? Context) ?: appContext() ?: return
        listening = true
        runCatching {
            context.registerReceiver(receiver, IntentFilter(SplitThree.ACTION), Context.RECEIVER_EXPORTED)
        }.onFailure {
            listening = false
            log("split hook: can't listen: $it")
            return
        }
        log("split hook: ready (SplitScreenController captured)")
        appContext = context
        // "Ready" first, then the listener's state: Folark resets what it knows on "ready".
        outbox.execute {
            runCatching {
                context.contentResolver.call(Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitThree.METHOD_READY, null, null)
            }
        }
        mainExecutor(c)?.execute { attachListener(context, c) }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runCatching { handle(context, intent) }.onFailure { log("split hook failed: $it") }
        }
    }

    private fun BroadcastReceiver.handle(context: Context, intent: Intent) {
        val request = intent.getStringExtra(SplitThree.EXTRA_REQUEST) ?: return
        // Only Folark may ask (Android itself vouches for the sender since Android 14).
        if (Build.VERSION.SDK_INT < 34 || sentFromPackage != FOLARK) {
            log("split hook: refused request from ${if (Build.VERSION.SDK_INT >= 34) sentFromPackage else "?"}")
            return
        }
        val c = controller ?: return reply(context, request, false)
        val executor = mainExecutor(c) ?: return reply(context, request, false)

        // Folark wants the split state now (it started after System UI, or lost track).
        if (intent.getBooleanExtra(SplitThree.EXTRA_EVENTS, false)) {
            executor.execute { reply(context, request, attachListener(context, c)) }
            return
        }

        // Where the divider is now, seen from one of the split's apps (a scene saved from Recents).
        if (intent.getBooleanExtra(SplitThree.EXTRA_QUERY_SNAP, false)) {
            val forTask = intent.getIntExtra(SplitThree.EXTRA_SNAP_FOR_TASK, -1)
            executor.execute { querySnap(context, c, request, forTask) }
            return
        }

        // A layout change for the split showing now (peek, a third, half and half…).
        val snap = intent.getIntExtra(SplitThree.EXTRA_SNAP, -1)
        if (snap >= 0) {
            val forTask = intent.getIntExtra(SplitThree.EXTRA_SNAP_FOR_TASK, -1)
            executor.execute { snapTo(context, c, request, snap, 0, forTask) }
            return
        }

        // A notification's intent beside a running app, in one step.
        if (intent.getBooleanExtra(SplitThree.EXTRA_INTENT_AND_TASK, false)) {
            @Suppress("DEPRECATION")
            val pending = intent.getParcelableExtra<PendingIntent>(SplitThree.EXTRA_INTENT)
            val userId = intent.getIntExtra(SplitThree.EXTRA_USER_ID, 0)
            val taskId = intent.getIntExtra(SplitThree.EXTRA_TASK, -1)
            val position = intent.getIntExtra(SplitThree.EXTRA_SPLIT_POSITION, SPLIT_POSITION_BOTTOM_OR_RIGHT)
            val snapPosition = intent.getIntExtra(SplitThree.EXTRA_SNAP_POSITION, SplitThree.SNAP_EVEN)
            if (pending == null || taskId <= 0) return reply(context, request, false)
            executor.execute { startIntentAndTaskNative(context, c, request, pending, userId, taskId, position, snapPosition) }
            return
        }

        if (intent.getBooleanExtra(SplitThree.EXTRA_SPLIT_TASKS, false)) {
            val t1 = intent.getIntExtra(SplitThree.EXTRA_TASK_1, -1)
            val t2 = intent.getIntExtra(SplitThree.EXTRA_TASK_2, -1)
            val position = intent.getIntExtra(SplitThree.EXTRA_SPLIT_POSITION, SPLIT_POSITION_BOTTOM_OR_RIGHT)
            val snapPosition = intent.getIntExtra(SplitThree.EXTRA_SNAP_POSITION, SplitThree.SNAP_EVEN)
            executor.execute { startSplitTasksNative(context, c, request, t1, t2, position, snapPosition) }
            return
        }

        // Motorola's own split-select (its system app picker), mirroring SplitScreenGestureManager.
        val select = intent.getIntExtra(SplitThree.EXTRA_SELECT, 0)
        if (select > 0) {
            val pos = intent.getIntExtra(SplitThree.EXTRA_POS, 0)
            val selTask = intent.getIntExtra(SplitThree.EXTRA_TASK, -1)
            executor.execute { selectNative(context, c, request, select, selTask, pos) }
            return
        }

        val task = intent.getIntExtra(SplitThree.EXTRA_TASK, -1)
        @Suppress("DEPRECATION")
        val launch = intent.getParcelableExtra<PendingIntent>(SplitThree.EXTRA_INTENT)
        val userId = intent.getIntExtra(SplitThree.EXTRA_USER_ID, 0)
        if (task <= 0 && launch == null) return reply(context, request, false)
        executor.execute { attempt(context, c, request, task, launch, userId, 0) }
    }

    /** On WM Shell's thread: once two apps are in split screen, start the third into the third place. */
    private fun attempt(context: Context, c: Any, request: String, task: Int, launch: PendingIntent?, userId: Int, tries: Int) {
        val visible = runCatching { c.javaClass.getMethod("isSplitScreenVisible").invoke(c) as Boolean }.getOrDefault(false)
        if (!visible) {
            if (tries < WAIT_TRIES) {
                Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed(
                    { attempt(context, c, request, task, launch, userId, tries + 1) }, WAIT_STEP_MS,
                )
            } else {
                reply(context, request, false)
            }
            return
        }
        val ok = runCatching {
            if (task > 0) {
                // startTask(int taskId, int position, Bundle options, WindowContainerToken, int index)
                val m = c.javaClass.methods.first {
                    it.name == "startTask" && it.parameterTypes.size == 5 &&
                        it.parameterTypes[4] == Int::class.javaPrimitiveType
                }
                m.invoke(c, task, SPLIT_POSITION_BOTTOM_OR_RIGHT, null, null, THIRD_INDEX)
            } else {
                // startIntent(PendingIntent, int userId, Intent fillIn, int position, Bundle options,
                //             WindowContainerToken, int index)
                val m = c.javaClass.methods.first {
                    it.name == "startIntent" && it.parameterTypes.size == 7 &&
                        it.parameterTypes[0] == PendingIntent::class.java &&
                        it.parameterTypes[6] == Int::class.javaPrimitiveType
                }
                m.invoke(c, launch, userId, null, SPLIT_POSITION_BOTTOM_OR_RIGHT, null, null, THIRD_INDEX)
            }
            true
        }.getOrElse {
            log("three-app split: start failed: $it")
            false
        }
        reply(context, request, ok)
    }

    /**
     * On WM Shell's thread: moves the split's divider to the snap target [snap], exactly where a
     * finger dragging the divider ends (SplitLayout.snapToTarget), so Android sets up the layout the
     * same way, peek strip included. Waits for the split to be showing first. Never picks a dismiss
     * target (the first and last ones), which would close the split instead. With [forTask] the snap
     * is meant for that task as if it sat left / on top: turned round when it sits on the other side.
     * A third or two thirds the divider has no stop for is refused (no silent half and half).
     */
    private fun snapTo(context: Context, c: Any, request: String, snap: Int, tries: Int, forTask: Int = -1) {
        val visible = runCatching { c.javaClass.getMethod("isSplitScreenVisible").invoke(c) as Boolean }.getOrDefault(false)
        if (!visible) {
            if (tries < WAIT_TRIES) {
                Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({ snapTo(context, c, request, snap, tries + 1, forTask) }, WAIT_STEP_MS)
            } else {
                reply(context, request, false)
            }
            return
        }
        val ok = runCatching {
            val wanted = if (forTask > 0 && splitPosition(c, forTask) == SPLIT_POSITION_BOTTOM_OR_RIGHT) mirrored(snap) else snap
            val layout = splitLayout(c)
            val algorithm = field(layout, "mDividerSnapAlgorithm") ?: error("no DividerSnapAlgorithm")
            val targets = (field(algorithm, "mTargets") as? List<*>).orEmpty().filterNotNull()

            // Never pick index 0 (dismiss start) or last index (dismiss end):
            // Match exact snapPosition first, then fall back to DividerSnapAlgorithm's non-dismiss split targets.
            val nonDismiss = if (targets.size >= 3) targets.subList(1, targets.size - 1) else targets
            val target = nonDismiss.firstOrNull { field(it, "snapPosition") == wanted }
                ?: when (wanted) {
                    SplitThree.SNAP_PEEK_LEFT -> field(algorithm, "mFirstSplitTarget") ?: nonDismiss.firstOrNull()
                    SplitThree.SNAP_PEEK_RIGHT -> field(algorithm, "mLastSplitTarget") ?: nonDismiss.lastOrNull()
                    SplitThree.SNAP_ONE_THIRD -> nearest(targets, nonDismiss, 1f / 3)
                    SplitThree.SNAP_TWO_THIRDS -> nearest(targets, nonDismiss, 2f / 3)
                    else -> field(algorithm, "mMiddleTarget") ?: nonDismiss.getOrNull(nonDismiss.size / 2)
                }
                ?: error("no valid split snap target for $wanted")

            val position = field(layout, "mDividerPosition") as? Int ?: error("no divider position")
            val method = methods(layout.javaClass).first { it.name == "snapToTarget" && it.parameterTypes.size == 4 }
            method.isAccessible = true
            method.invoke(layout, position, target, SNAP_MS, PathInterpolator(0.2f, 0f, 0f, 1f))
            true
        }.getOrElse {
            log("split layout: can't snap to $snap: $it")
            false
        }
        reply(context, request, ok)
    }

    /**
     * On WM Shell's thread: the snap position the divider is at now (the nearest stop), seen from
     * [forTask] as if it sat left / on top. Refused when [forTask] isn't in the split.
     */
    private fun querySnap(context: Context, c: Any, request: String, forTask: Int) {
        val value = runCatching {
            val side = splitPosition(c, forTask)
            if (side != 0 && side != SPLIT_POSITION_BOTTOM_OR_RIGHT) error("task $forTask isn't in the split")
            val layout = splitLayout(c)
            val algorithm = field(layout, "mDividerSnapAlgorithm") ?: error("no DividerSnapAlgorithm")
            val targets = (field(algorithm, "mTargets") as? List<*>).orEmpty().filterNotNull()
            val position = field(layout, "mDividerPosition") as? Int ?: error("no divider position")
            val closest = targets.minByOrNull { kotlin.math.abs(((field(it, "position") as? Int) ?: Int.MAX_VALUE / 2) - position) }
                ?: error("no snap targets")
            val snap = field(closest, "snapPosition") as? Int ?: error("no snapPosition")
            if (side == SPLIT_POSITION_BOTTOM_OR_RIGHT) mirrored(snap) else snap
        }.getOrElse {
            log("split layout: can't read the divider: $it")
            null
        }
        reply(context, request, value != null, value)
    }

    /** The SplitLayout of the split screen (the divider and its stops). */
    private fun splitLayout(c: Any): Any {
        val stage = field(c, "mStageCoordinator") ?: error("no StageCoordinator")
        field(stage, "mSplitLayout")?.let { return it }
        val manager = field(stage, "mSplitLayoutManager") ?: error("no SplitLayoutManager")
        return field(manager, "mSplitLayout") ?: error("no SplitLayout")
    }

    /** Which side [taskId] sits on: 0 left / top, 1 right / bottom, -1 not in the split (or unknown). */
    private fun splitPosition(c: Any, taskId: Int): Int = runCatching {
        val m = c.javaClass.methods.firstOrNull {
            it.name == "getSplitPosition" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        } ?: return@runCatching -1
        m.invoke(c, taskId) as Int
    }.getOrDefault(-1)

    /** The same layout seen from the other side (a third ↔ two thirds, peek left ↔ peek right). */
    private fun mirrored(snap: Int): Int = when (snap) {
        SplitThree.SNAP_ONE_THIRD -> SplitThree.SNAP_TWO_THIRDS
        SplitThree.SNAP_TWO_THIRDS -> SplitThree.SNAP_ONE_THIRD
        SplitThree.SNAP_PEEK_LEFT -> SplitThree.SNAP_PEEK_RIGHT
        SplitThree.SNAP_PEEK_RIGHT -> SplitThree.SNAP_PEEK_LEFT
        else -> snap
    }

    /**
     * The stop among [candidates] nearest to [fraction] of the way across (from the dismiss stops at
     * both ends of [all]), or null when none is within reach of it: then the phone has no such stop
     * (the middle one or a peek strip is not a third).
     */
    private fun nearest(all: List<Any>, candidates: List<Any>, fraction: Float): Any? {
        val start = all.firstOrNull()?.let { field(it, "position") as? Int } ?: return null
        val end = all.lastOrNull()?.let { field(it, "position") as? Int } ?: return null
        val span = (end - start).toFloat()
        if (span <= 0f) return null
        val goal = start + span * fraction
        val best = candidates.minByOrNull { kotlin.math.abs(((field(it, "position") as? Int) ?: return@minByOrNull Float.MAX_VALUE) - goal) }
            ?: return null
        val at = field(best, "position") as? Int ?: return null
        return best.takeIf { kotlin.math.abs(at - goal) <= span * THIRD_REACH }
    }

    /**
     * On WM Shell's thread: [pending] (a notification's tap action) into split screen beside the
     * running task [taskId], in one step. `startIntentAndTask(PendingIntent, int userId, Bundle,
     * int taskId, Bundle, int position, int snapPosition, RemoteTransition, InstanceId)`. Refuses
     * unless [taskId] is running (WM Shell throws on a task it doesn't know).
     */
    private fun startIntentAndTaskNative(
        context: Context, c: Any, request: String, pending: PendingIntent, userId: Int, taskId: Int, position: Int, snapPosition: Int,
    ) {
        val int = Int::class.javaPrimitiveType!!
        val failure = runCatching {
            if (!isRunningTask(c, taskId)) error("task $taskId isn't running")
            invokeLeading(
                c, "startIntentAndTask",
                listOf(
                    PendingIntent::class.java to pending, int to userId, Bundle::class.java to Bundle(),
                    int to taskId, Bundle::class.java to Bundle(), int to position, int to snapPosition,
                ),
            )
        }.exceptionOrNull()
        if (failure != null) log("split hook: intent beside task $taskId failed: ${reason(failure)}")
        reply(context, request, failure == null, detail = failure?.let { reason(it) })
    }

    /**
     * On WM Shell's thread: [task1] and [task2] into split screen in one step, [task1] on
     * [position] (0 left / 1 right) and [task2] on the other side, divided as [snapPosition].
     * `startTasks(int, Bundle, int, Bundle, int position, int snapPosition, RemoteTransition,
     * InstanceId)`. Refuses unless both tasks are running: WM Shell opens a lone task full screen
     * (task 2 = -1) and throws on a task it doesn't know.
     */
    private fun startSplitTasksNative(
        context: Context, c: Any, request: String, task1: Int, task2: Int, position: Int, snapPosition: Int,
    ) {
        val int = Int::class.javaPrimitiveType!!
        val failure = runCatching {
            if (task1 <= 0 || task2 <= 0 || task1 == task2) error("two different tasks needed ($task1, $task2)")
            if (!isRunningTask(c, task1) || !isRunningTask(c, task2)) error("a task isn't running ($task1, $task2)")
            invokeLeading(
                c, "startTasks",
                listOf(
                    int to task1, Bundle::class.java to Bundle(), int to task2, Bundle::class.java to Bundle(),
                    int to position, int to snapPosition,
                ),
            )
        }.exceptionOrNull()
        if (failure != null) log("split: startTasks failed: ${reason(failure)}")
        reply(context, request, failure == null, detail = failure?.let { reason(it) })
    }

    /**
     * Calls WM Shell's [name] whose parameters START with [leading] (type to value). Android builds
     * add parameters at the end (a RemoteTransition, an InstanceId, flags for flexible split…), so
     * an exact count breaks on the next release; the rest are filled with neutral values: null,
     * 0 / false, and a real InstanceId (the split logger reads it). The shortest match wins. When
     * none matches, the error lists what the class does have, for Check split screen.
     */
    private fun invokeLeading(c: Any, name: String, leading: List<Pair<Class<*>, Any?>>) {
        val candidates = methods(c.javaClass).filter { it.name == name }
        val m = candidates.filter { method ->
            val p = method.parameterTypes
            p.size >= leading.size && leading.indices.all { i -> p[i] == leading[i].first || p[i].isAssignableFrom(leading[i].first) }
        }.minByOrNull { it.parameterTypes.size }
            ?: error("no $name(${leading.joinToString { it.first.simpleName }}, …); found: " +
                candidates.joinToString(" | ") { method -> method.parameterTypes.joinToString(",") { it.simpleName } }.ifEmpty { "none" })
        val args = m.parameterTypes.mapIndexed { i, type -> if (i < leading.size) leading[i].second else neutral(type) }
        m.isAccessible = true
        m.invoke(c, *args.toTypedArray())
    }

    /** A harmless value for a trailing parameter of [type]. */
    private fun neutral(type: Class<*>): Any? = when {
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        type == Boolean::class.javaPrimitiveType -> false
        type == Float::class.javaPrimitiveType -> 0f
        type.name == "com.android.internal.logging.InstanceId" -> runCatching {
            type.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }
                .newInstance((System.nanoTime() and 0xFFFFF).toInt())
        }.getOrNull()
        else -> null
    }

    /** What went wrong, without reflection's wrapper. */
    private fun reason(t: Throwable): String {
        val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        // The first frame is often a JDK check (Preconditions, Objects); WM Shell's own frames say
        // where it happened, so up to three of those follow it.
        val frames = cause.stackTrace
        val shell = frames.filter { it.className.startsWith("com.android.wm.shell") }.take(3)
        val shown = (listOfNotNull(frames.firstOrNull()) + shell).distinct()
        val at = shown.joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        return (cause.javaClass.simpleName + ": " + (cause.message ?: "")).take(200) + if (at.isEmpty()) "" else " ($at)"
    }

    /** WM Shell knows [taskId] as a running task (unknown ones make it throw); true when it can't tell. */
    private fun isRunningTask(c: Any, taskId: Int): Boolean = runCatching {
        val organizer = field(c, "mTaskOrganizer") ?: return true
        val get = methods(organizer.javaClass).firstOrNull {
            it.name == "getRunningTaskInfo" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        } ?: return true
        get.isAccessible = true
        get.invoke(organizer, taskId) != null
    }.getOrDefault(true)

    /**
     * On WM Shell's thread: hand off to Motorola's own split-select (its system app picker), exactly
     * as `SplitScreenGestureManager` does. [mode] 1 = two-app: `startTaskWithSelectRequest(taskId,
     * options, index, bounds, leftRight)` on the controller; 2 = three-app:
     * `requestEnterThreeAppsSplitSelectByGesture(position)` on the controller's `mImpl`, only while a
     * split is showing. Everything is wrapped, so a signature that differs just answers "no".
     */
    private fun selectNative(context: Context, c: Any, request: String, mode: Int, task: Int, pos: Int) {
        val ok = runCatching {
            if (mode == 2) {
                val visible = runCatching { c.javaClass.getMethod("isSplitScreenVisible").invoke(c) as Boolean }.getOrDefault(false)
                if (!visible) error("no split visible for three-app select")
                val impl = field(c, "mImpl") ?: error("no mImpl")
                val m = methods(impl.javaClass).first { it.name == "requestEnterThreeAppsSplitSelectByGesture" && it.parameterTypes.size == 1 }
                m.isAccessible = true
                m.invoke(impl, pos)
            } else {
                if (task <= 0) error("no task")
                // startTaskWithSelectRequest(int taskId, Bundle options, int index, Rect bounds, boolean leftRight)
                val m = methods(c.javaClass).first { it.name == "startTaskWithSelectRequest" && it.parameterTypes.size == 5 }
                m.isAccessible = true
                m.invoke(c, task, Bundle(), pos, null, true)
            }
            true
        }.getOrElse {
            log("split select ($mode) failed: $it")
            false
        }
        reply(context, request, ok)
    }

    /**
     * On WM Shell's thread: adds Folark's listener to the split screen's listeners (once), then has
     * WM Shell send it the state now, as it does for every new listener (`sendStatusToListener`).
     */
    private fun attachListener(context: Context, c: Any): Boolean = runCatching {
        val stage = field(c, "mStageCoordinator") ?: error("no StageCoordinator")
        @Suppress("UNCHECKED_CAST")
        val listeners = field(stage, "mListeners") as? MutableList<Any> ?: error("no listener list")
        val listener = splitListener ?: newListener()?.also { splitListener = it } ?: error("no $LISTENER")
        if (listeners.none { it === listener }) listeners.add(listener)
        methods(stage.javaClass).firstOrNull { it.name == "sendStatusToListener" && it.parameterTypes.size == 1 }
            ?.apply { isAccessible = true }
            ?.invoke(stage, listener)
        send(context, Bundle().apply { putInt(SplitEvents.EXTRA_KIND, SplitEvents.KIND_ATTACHED) })
        true
    }.getOrElse {
        log("split events: can't listen: $it")
        false
    }

    /**
     * A `SplitScreen.SplitScreenListener` made at run time (the interface lives in System UI). It
     * passes the changes on and never throws: it runs on WM Shell's own thread.
     */
    private fun newListener(): Any? = runCatching {
        val cl = loader ?: return null
        val type = Class.forName(LISTENER, false, cl)
        Proxy.newProxyInstance(cl, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FolarkSplitListener"
                else -> {
                    runCatching { onListenerCall(method.name, args) }
                    defaultFor(method.returnType)
                }
            }
        }
    }.getOrNull()

    private fun onListenerCall(name: String, args: Array<out Any?>?) {
        val context = appContext ?: return
        when (name) {
            "onTaskStageChanged" -> send(context, Bundle().apply {
                putInt(SplitEvents.EXTRA_KIND, SplitEvents.KIND_STAGE)
                putInt(SplitEvents.EXTRA_TASK, args?.getOrNull(0) as? Int ?: return)
                putInt(SplitEvents.EXTRA_STAGE, args.getOrNull(1) as? Int ?: SplitEvents.STAGE_NONE)
                putBoolean(SplitEvents.EXTRA_VISIBLE, args.getOrNull(2) as? Boolean ?: false)
            })
            "onSplitVisibilityChanged" -> send(context, Bundle().apply {
                putInt(SplitEvents.EXTRA_KIND, SplitEvents.KIND_VISIBLE)
                putBoolean(SplitEvents.EXTRA_VISIBLE, args?.getOrNull(0) as? Boolean ?: false)
            })
        }
    }

    /** What a listener method gives back: nothing for the void ones (all today), a zero otherwise. */
    private fun defaultFor(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        else -> null
    }

    /** Passes a split change on to Folark (its RecentsHookState), off WM Shell's thread. */
    private fun send(context: Context, extras: Bundle) {
        outbox.execute {
            runCatching {
                context.contentResolver.call(Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitEvents.METHOD_EVENT, null, extras)
            }
        }
    }

    private fun methods(cls: Class<*>): List<java.lang.reflect.Method> = generateSequence(cls) { it.superclass }
        .flatMap { it.declaredMethods.asSequence() }.toList()

    private fun reply(context: Context, request: String, ok: Boolean, value: Int? = null, detail: String? = null) {
        Thread {
            runCatching {
                context.contentResolver.call(
                    Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitThree.METHOD_RESULT, request,
                    Bundle().apply {
                        putBoolean(SplitThree.EXTRA_OK, ok)
                        if (value != null) putInt(SplitThree.EXTRA_VALUE, value)
                        if (detail != null) putString(SplitThree.EXTRA_DETAIL, detail)
                    },
                )
            }
        }.start()
    }

    /** WM Shell's main executor: the controller's `mMainExecutor`, or any Executor field it has. */
    private fun mainExecutor(c: Any): Executor? =
        (field(c, "mMainExecutor") as? Executor)
            ?: allFields(c.javaClass).firstOrNull { Executor::class.java.isAssignableFrom(it.type) }
                ?.let { runCatching { it.isAccessible = true; it.get(c) as? Executor }.getOrNull() }

    private fun field(target: Any, name: String): Any? =
        allFields(target.javaClass).firstOrNull { it.name == name }
            ?.let { runCatching { it.isAccessible = true; it.get(target) }.getOrNull() }

    private fun allFields(cls: Class<*>): List<Field> = generateSequence(cls) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }.toList()

    private fun appContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()
}
