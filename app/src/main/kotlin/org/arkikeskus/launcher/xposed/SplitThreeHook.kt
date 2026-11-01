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
import org.arkikeskus.launcher.data.SplitThree
import java.lang.reflect.Field
import java.util.concurrent.Executor

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
 * - and it tells Folark whether that worked, so Folark can fall back to its other ways.
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

    @Volatile private var controller: Any? = null
    @Volatile private var listening = false
    private var log: (String) -> Unit = {}

    fun install(loader: ClassLoader, log: (String) -> Unit) {
        this.log = log
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
        Thread {
            runCatching {
                context.contentResolver.call(Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitThree.METHOD_READY, null, null)
            }
        }.start()
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

        // A layout change for the split showing now (Android's peek view, or half and half).
        val snap = intent.getIntExtra(SplitThree.EXTRA_SNAP, -1)
        if (snap >= 0) {
            executor.execute { snapTo(context, c, request, snap, 0) }
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
     * target (the first and last ones), which would close the split instead.
     */
    private fun snapTo(context: Context, c: Any, request: String, snap: Int, tries: Int) {
        val visible = runCatching { c.javaClass.getMethod("isSplitScreenVisible").invoke(c) as Boolean }.getOrDefault(false)
        if (!visible) {
            if (tries < WAIT_TRIES) {
                Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({ snapTo(context, c, request, snap, tries + 1) }, WAIT_STEP_MS)
            } else {
                reply(context, request, false)
            }
            return
        }
        val ok = runCatching {
            val stage = field(c, "mStageCoordinator") ?: error("no StageCoordinator")
            val manager = field(stage, "mSplitLayoutManager") ?: error("no SplitLayoutManager")
            val layout = field(manager, "mSplitLayout") ?: error("no SplitLayout")
            val algorithm = field(layout, "mDividerSnapAlgorithm") ?: error("no DividerSnapAlgorithm")
            val targets = (field(algorithm, "mTargets") as? List<*>).orEmpty().filterNotNull()

            // Never pick index 0 (dismiss start) or last index (dismiss end):
            // Match exact snapPosition first, then fall back to DividerSnapAlgorithm's non-dismiss split targets.
            val nonDismiss = if (targets.size >= 3) targets.subList(1, targets.size - 1) else targets
            val target = nonDismiss.firstOrNull { field(it, "snapPosition") == snap }
                ?: when (snap) {
                    SplitThree.SNAP_PEEK_LEFT -> field(algorithm, "mFirstSplitTarget") ?: nonDismiss.firstOrNull()
                    SplitThree.SNAP_PEEK_RIGHT -> field(algorithm, "mLastSplitTarget") ?: nonDismiss.lastOrNull()
                    else -> field(algorithm, "mMiddleTarget") ?: nonDismiss.getOrNull(nonDismiss.size / 2)
                }
                ?: error("no valid split snap target for $snap")

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
     * On WM Shell's thread: [task1] and [task2] into split screen in one step, [task1] on
     * [position] (0 left / 1 right) and [task2] on the other side, divided as [snapPosition].
     * `startTasks(int, Bundle, int, Bundle, int position, int snapPosition, RemoteTransition,
     * InstanceId)`. Refuses unless both tasks are running: WM Shell opens a lone task full screen
     * (task 2 = -1) and throws on a task it doesn't know.
     */
    private fun startSplitTasksNative(
        context: Context, c: Any, request: String, task1: Int, task2: Int, position: Int, snapPosition: Int,
    ) {
        val ok = runCatching {
            if (task1 <= 0 || task2 <= 0 || task1 == task2) error("two different tasks needed ($task1, $task2)")
            if (!isRunningTask(c, task1) || !isRunningTask(c, task2)) error("a task isn't running ($task1, $task2)")
            val int = Int::class.javaPrimitiveType
            val m = methods(c.javaClass).firstOrNull {
                val p = it.parameterTypes
                it.name == "startTasks" && p.size == 8 && p[0] == int && p[1] == Bundle::class.java &&
                    p[2] == int && p[3] == Bundle::class.java && p[4] == int && p[5] == int
            } ?: error("startTasks(int, Bundle, int, Bundle, int, int, …) not found")
            m.isAccessible = true
            m.invoke(c, task1, Bundle(), task2, Bundle(), position, snapPosition, null, null)
            true
        }.getOrElse {
            log("split: startTasks failed: $it")
            false
        }
        reply(context, request, ok)
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

    private fun methods(cls: Class<*>): List<java.lang.reflect.Method> = generateSequence(cls) { it.superclass }
        .flatMap { it.declaredMethods.asSequence() }.toList()

    private fun reply(context: Context, request: String, ok: Boolean) {
        Thread {
            runCatching {
                context.contentResolver.call(
                    Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitThree.METHOD_RESULT, request,
                    Bundle().apply { putBoolean(SplitThree.EXTRA_OK, ok) },
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
