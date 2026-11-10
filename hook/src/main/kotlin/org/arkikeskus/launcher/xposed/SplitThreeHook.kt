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
import org.arkikeskus.launcher.model.catching

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
    /** How long after the snap animation the divider is checked, and how far off it may be. */
    private const val SNAP_SETTLE_MS = 450L
    private const val SNAP_SLACK_PX = 24
    private const val THIRD_INDEX = 2

    private const val WAIT_STEP_MS = 100L
    /** How long startTasks waits for a just-started task to reach WM Shell (10 x 100 ms). */
    private const val TASK_WAIT_TRIES = 10
    private const val TASK_WAIT_STEP_MS = 100L
    private const val WAIT_TRIES = 25

    /** How long the divider takes to glide to its new place (as when dropped by a finger). */
    private const val SNAP_MS = 350

    /** How far (of the divider's whole travel) a stop may be from a third and still count as one. */
    private const val THIRD_REACH = 0.08f

    /** Motorola's split states (SplitState): three apps even, its carousel both ways, immersive. */
    private const val SNAP_ALGORITHM = "com.android.wm.shell.common.split.DividerSnapAlgorithm"
    private const val STATE_THREE_EVEN = 5
    private const val STATE_CAROUSEL_RIGHT = 6
    private const val STATE_CAROUSEL_LEFT = 7
    private const val STATE_IMMERSIVE_3 = 21
    /** How far (of the screen) a divider may be from an even stop and still count (2 %). */
    private const val THREE_EVEN_REACH = 0.02f
    /** How long the three-app layout gets to appear after a third app joined. */
    private const val THREE_WAIT_TRIES = 30
    /** When the split state is logged after a third app was sent. */
    private const val THIRD_CHECK_MS = 1_500L
    /** How long split events gather before they go to Folark together. */
    private const val EVENT_BATCH_MS = 24L

    private const val STAGE_COORDINATOR = "com.android.wm.shell.splitscreen.StageCoordinator"
    /** Whether Folark's listener stepped out for the setSideStagePosition running on this thread. */
    private val stepOut = ThreadLocal<Boolean>()

    private const val LISTENER = "com.android.wm.shell.splitscreen.SplitScreen\$SplitScreenListener"

    @Volatile private var controller: Any? = null
    @Volatile private var listening = false
    @Volatile private var loader: ClassLoader? = null
    @Volatile private var splitListener: Any? = null
    @Volatile private var appContext: Context? = null
    /** Everything sent to Folark goes out here, in order and off WM Shell's thread. */
    private val outbox = Executors.newSingleThreadScheduledExecutor()
    /** Split events waiting to go to Folark together (E4: a three-app start fires about six). */
    private val pendingEvents = ArrayList<Bundle>()
    private var flushQueued = false
    private var log: (String) -> Unit = {}

    fun install(loader: ClassLoader, log: (String) -> Unit) {
        this.log = log
        this.loader = loader
        val cls = catching { XposedHelpers.findClass(CONTROLLER, loader) }.getOrNull()
        if (cls == null) {
            log("no $CONTROLLER in SystemUI; split hook stays off")
            return
        }
        val capture = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                catching { keep(param.thisObject) }
            }
        }
        val hooked = catching {
            XposedBridge.hookAllMethods(cls, "onInit", capture).size +
                XposedBridge.hookAllMethods(cls, "isSplitScreenVisible", capture).size
        }.getOrDefault(0)
        log("split hook: hooked $hooked SystemUI method(s)")
        hookThreeEven(loader)
        guardSideStagePosition(loader)
    }

    /**
     * Motorola's StageCoordinator.setSideStagePosition tells each split listener about the stages
     * with the listener's own place in the list as the stage index (`activeStages.get(size)`), so
     * with more listeners than apps in the split it throws IndexOutOfBounds on WM Shell's thread
     * and System UI dies — and Folark's listener is one more than Motorola's own. That happened on
     * every swap (tapping the app peeking in) and some split starts. Folark's listener steps out of
     * the list while that method runs and back in after (it needs nothing from that call).
     */
    private fun guardSideStagePosition(loader: ClassLoader) {
        val cls = catching { XposedHelpers.findClass(STAGE_COORDINATOR, loader) }.getOrNull() ?: return
        val hooked = catching {
            XposedBridge.hookAllMethods(cls, "setSideStagePosition", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    stepOut.set(false)
                    val listener = splitListener ?: return
                    catching {
                        @Suppress("UNCHECKED_CAST")
                        val listeners = field(param.thisObject, "mListeners") as? MutableList<Any> ?: return
                        if (listeners.removeAll { it === listener }) stepOut.set(true)
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (stepOut.get() != true) return
                    stepOut.set(false)
                    val listener = splitListener ?: return
                    catching {
                        @Suppress("UNCHECKED_CAST")
                        val listeners = field(param.thisObject, "mListeners") as? MutableList<Any> ?: return
                        if (listeners.none { it === listener }) listeners.add(listener)
                    }
                }
            }).size
        }.getOrDefault(0)
        log("split hook: side-stage guard ${if (hooked > 0) "on" else "not found"}")
    }

    /**
     * Motorola's three-app split has an even layout (state 5: dividers at 33 % and 66 %, all three
     * apps on screen), but on the Razr Fold it works out the layout from the two divider positions
     * (DividerSnapAlgorithm.calculateNearestSplitSnapPosition) and only ever answers its carousel
     * (6 / 7: one app a 10 % strip) or immersive view (21). With both dividers at the even stops it
     * now answers 5, so the even layout Motorola already has is used, and kept on rotation (the
     * dividers are put back at state 5's stops). Anywhere else nothing changes.
     */
    private fun hookThreeEven(loader: ClassLoader) {
        val cls = catching { XposedHelpers.findClass(SNAP_ALGORITHM, loader) }.getOrNull() ?: return
        val hooked = catching {
            XposedBridge.hookAllMethods(cls, "calculateNearestSplitSnapPosition", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    catching {
                        val result = param.result as? Int ?: return
                        if (result != STATE_CAROUSEL_LEFT && result != STATE_CAROUSEL_RIGHT && result != STATE_IMMERSIVE_3) return
                        val p0 = param.args.getOrNull(0) as? Int ?: return
                        val p1 = param.args.getOrNull(1) as? Int ?: return
                        if (atThreeEven(param.thisObject, minOf(p0, p1), maxOf(p0, p1))) param.result = STATE_THREE_EVEN
                    }
                }
            }).size
        }.getOrDefault(0)
        log("split hook: three even ${if (hooked > 0) "on" else "not found"}")
    }

    /** Whether divider positions [first] and [second] sit at state 5's stops (33 % / 66 %). */
    private fun atThreeEven(algorithm: Any, first: Int, second: Int): Boolean {
        val (at0, at1) = threeEvenStops(algorithm) ?: return false
        val leftRight = field(algorithm, "mIsLeftRightSplit") as? Boolean ?: true
        val span = (field(algorithm, if (leftRight) "mDisplayWidth" else "mDisplayHeight") as? Int) ?: 0
        val slack = maxOf(SNAP_SLACK_PX, (span * THREE_EVEN_REACH).toInt())
        return kotlin.math.abs(first - at0) <= slack && kotlin.math.abs(second - at1) <= slack
    }

    /** State 5's two divider stops, as Motorola works them out (getDividerSnapTarget(5, 0 / 1)). */
    private fun threeEvenStops(algorithm: Any): Pair<Int, Int>? = catching {
        val get = methods(algorithm.javaClass).first {
            it.name == "getDividerSnapTarget" && it.parameterTypes.size == 2 &&
                it.parameterTypes.all { t -> t == Int::class.javaPrimitiveType }
        }
        get.isAccessible = true
        val first = get.invoke(algorithm, STATE_THREE_EVEN, 0) ?: return@catching null
        val second = get.invoke(algorithm, STATE_THREE_EVEN, 1) ?: return@catching null
        val a = field(first, "position") as Int
        val b = field(second, "position") as Int
        minOf(a, b) to maxOf(a, b)
    }.getOrNull()

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
        catching {
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
            catching {
                context.contentResolver.call(Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitThree.METHOD_READY, null, null)
            }
        }
        mainExecutor(c)?.execute { attachListener(context, c) }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            catching { handle(context, intent) }.onFailure { log("split hook failed: $it") }
        }
    }

    private fun BroadcastReceiver.handle(context: Context, intent: Intent) {
        val request = intent.getStringExtra(SplitThree.EXTRA_REQUEST) ?: return
        val sender = if (Build.VERSION.SDK_INT >= 34) sentFromPackage else null
        log("split hook: request ${intent.extras?.keySet()?.filter { it != SplitThree.EXTRA_REQUEST }?.sorted()} from $sender")
        // Only Folark may ask (Android itself vouches for the sender since Android 14). The answer
        // goes to Folark's own provider only, so saying why is safe and shows in its split check.
        if (Build.VERSION.SDK_INT < 34 || sender != FOLARK) {
            log("split hook: refused request from $sender")
            return reply(context, request, false, detail = "refused: System UI can't tell the sender (it says $sender)")
        }
        val c = controller ?: return reply(context, request, false, detail = "WM Shell's split controller not caught yet")
        val executor = mainExecutor(c) ?: return reply(context, request, false, detail = "no WM Shell thread")

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
        intent.getIntArrayExtra(SplitThree.EXTRA_SNAP_FRACTIONS)?.let { fractions ->
            executor.execute { snapFractions(context, c, request, fractions, 0) }
            return
        }
        if (snap == SplitThree.SNAP_THREE_EVEN || snap == SplitThree.SNAP_THREE_PEEK) {
            executor.execute { snapThree(context, c, request, snap, 0) }
            return
        }
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
        val visible = catching { c.javaClass.getMethod("isSplitScreenVisible").invoke(c) as Boolean }.getOrDefault(false)
        // Not while a split is still forming: Motorola adds the third app to whatever split shows
        // at that moment (often the one before), and only as a third when two apps are settled.
        if (!visible || splitBusy(c)) {
            if (tries < WAIT_TRIES) {
                Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed(
                    { attempt(context, c, request, task, launch, userId, tries + 1) }, WAIT_STEP_MS,
                )
            } else {
                reply(context, request, false)
            }
            return
        }
        val int = Int::class.java
        val failure = catching {
            if (task > 0) {
                // startTask(int taskId, int position, Bundle options, WindowContainerToken, int index, …)
                invokeWithIndex(
                    c, "startTask",
                    listOf(int to task, int to SPLIT_POSITION_BOTTOM_OR_RIGHT, Bundle::class.java to null),
                    THIRD_INDEX,
                )
            } else {
                // startIntent(PendingIntent, int userId, Intent fillIn, int position, Bundle options,
                //             WindowContainerToken, int index, …)
                invokeWithIndex(
                    c, "startIntent",
                    listOf(
                        PendingIntent::class.java to launch, int to userId, Intent::class.java to null,
                        int to SPLIT_POSITION_BOTTOM_OR_RIGHT, Bundle::class.java to null,
                    ),
                    THIRD_INDEX,
                )
            }
        }.exceptionOrNull()
        if (failure != null) log("three-app split: start failed: ${reason(failure)}")
        else log("three-app split: third ${if (task > 0) "task $task" else "app"} sent at split state ${splitState(c)} (after ${tries * WAIT_STEP_MS} ms)")
        reply(context, request, failure == null, detail = failure?.let { reason(it) })
        // For the log: what Motorola made of it.
        if (failure == null) {
            Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed(
                { log("three-app split: split state ${splitState(c)} after the third (${activeStages(c)} app(s))") }, THIRD_CHECK_MS,
            )
        }
    }

    /** A split transition is under way (entering, resizing or animating): WM Shell's state is in flux. */
    private fun splitBusy(c: Any): Boolean = catching {
        val stage = field(c, "mStageCoordinator") ?: return false
        val transitions = field(stage, "mSplitTransitions") ?: return false
        field(transitions, "mPendingEnter") != null || field(transitions, "mPendingResize") != null ||
            field(transitions, "mAnimatingTransition") != null ||
            activeStages(c).let { it in 0..1 }
    }.getOrDefault(false)

    /** Motorola's split state now (SplitState.mState), or null. */
    private fun splitState(c: Any): Int? = catching {
        val split = field(splitManager(c), "mSplitState") ?: return@catching null
        field(split, "mState") as Int
    }.getOrNull()

    /** How many apps Motorola's split state holds (2 or 3; 0 for none), or -1 when unknown. */
    private fun activeStages(c: Any): Int = catching {
        val state = field(splitManager(c), "mSplitState") ?: return@catching -1
        val m = methods(state.javaClass).first { it.name == "getActiveStageCountBySplitState" && it.parameterTypes.isEmpty() }
        m.isAccessible = true
        m.invoke(state) as Int
    }.getOrDefault(-1)

    /**
     * On WM Shell's thread: moves the split's divider to the snap target [snap], exactly where a
     * finger dragging the divider ends (SplitLayout.snapToTarget), so Android sets up the layout the
     * same way, peek strip included. Waits for the split to be showing first. Never picks a dismiss
     * target (the first and last ones), which would close the split instead. With [forTask] the snap
     * is meant for that task as if it sat left / on top: turned round when it sits on the other side.
     * A third or two thirds the divider has no stop for is refused (no silent half and half).
     */
    private fun snapTo(context: Context, c: Any, request: String, snap: Int, tries: Int, forTask: Int = -1) {
        val visible = catching { c.javaClass.getMethod("isSplitScreenVisible").invoke(c) as Boolean }.getOrDefault(false)
        if (!visible) {
            if (tries < WAIT_TRIES) {
                Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({ snapTo(context, c, request, snap, tries + 1, forTask) }, WAIT_STEP_MS)
            } else {
                reply(context, request, false)
            }
            return
        }
        var check: (() -> Unit)? = null
        val ok = catching {
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
            // Once the divider has settled: did it stay there? (A system that only keeps 50/50
            // moves it back.) Answered then, so Folark learns whether sizes hold on this phone.
            val wantedAt = field(target, "position") as? Int
            check = {
                val now = field(layout, "mDividerPosition") as? Int
                val held = wantedAt == null || now == null || kotlin.math.abs(now - wantedAt) <= SNAP_SLACK_PX
                if (held) reply(context, request, true)
                else reply(context, request, false, detail = "the divider went back ($now, sent to $wantedAt)")
            }
            true
        }.getOrElse {
            log("split layout: can't snap to $snap: $it")
            false
        }
        val verify = check
        if (ok && verify != null) {
            Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({ catching(verify) }, SNAP_MS + SNAP_SETTLE_MS)
            return
        }
        reply(context, request, ok)
    }

    /**
     * On WM Shell's thread: the three-app split showing now (or about to, after a third app joined)
     * to three equal thirds, both dividers gliding to Motorola's own even stops
     * (SplitLayoutCarousel3Apps.snapToTarget, as when dropped by a finger). Its end works out the
     * layout from the dividers, which [hookThreeEven] answers with the even one. Answered once it
     * settled: ok only when Motorola's split state is the even one.
     */
    private fun snapThree(context: Context, c: Any, request: String, wanted: Int, tries: Int) {
        val manager = catching { splitManager(c) }.getOrNull()
        val layout = manager?.let { field(it, "mSplitLayout") }
        val three = layout != null && layout.javaClass.name.endsWith("Carousel3Apps") &&
            (field(layout, "mDividerPositions") as? IntArray)?.size == 2
        if (!three) {
            if (tries < THREE_WAIT_TRIES) {
                Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({ snapThree(context, c, request, wanted, tries + 1) }, WAIT_STEP_MS)
            } else {
                reply(context, request, false, detail = "no three-app split showing")
            }
            return
        }
        val failure = catching {
            val algorithm = field(layout, "mDividerSnapAlgorithm") ?: error("no DividerSnapAlgorithm")
            val get = methods(algorithm.javaClass).first {
                it.name == "getDividerSnapTarget" && it.parameterTypes.size == 2 &&
                    it.parameterTypes.all { t -> t == Int::class.javaPrimitiveType }
            }
            get.isAccessible = true
            val first = get.invoke(algorithm, wanted, 0) ?: error("no stop for $wanted")
            val second = get.invoke(algorithm, wanted, 1) ?: error("no stop for $wanted")
            val positions = field(layout, "mDividerPositions") as IntArray
            val method = methods(layout.javaClass).first { it.name == "snapToTarget" && it.parameterTypes.size == 6 }
            method.isAccessible = true
            method.invoke(layout, positions[0], first, positions[1], second, SNAP_MS, PathInterpolator(0.2f, 0f, 0f, 1f))
        }.exceptionOrNull()
        if (failure != null) {
            log("split layout: can't make three apps $wanted: ${reason(failure)}")
            return reply(context, request, false, detail = reason(failure))
        }
        Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({
            val state = catching {
                val split = field(manager, "mSplitState") ?: return@catching null
                field(split, "mState") as Int
            }.getOrNull()
            if (state == wanted) reply(context, request, true)
            else reply(context, request, false, detail = "the dividers went back (split state $state)")
        }, SNAP_MS + SNAP_SETTLE_MS)
    }

    /**
     * On WM Shell's thread: the split showing (or about to) to the divider places [fractions]
     * (thousandths): one for two apps, two for three. Waits for a layout with that many apps.
     */
    private fun snapFractions(context: Context, c: Any, request: String, fractions: IntArray, tries: Int) {
        val layout = catching { field(splitManager(c), "mSplitLayout") }.getOrNull()
        val three = layout?.javaClass?.name?.endsWith("Carousel3Apps") == true
        val fits = layout != null && !splitBusy(c) && (if (fractions.size == 2) three else !three)
        if (!fits) {
            if (tries < THREE_WAIT_TRIES) {
                Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed({ snapFractions(context, c, request, fractions, tries + 1) }, WAIT_STEP_MS)
            } else {
                reply(context, request, false, detail = "no split with ${fractions.size + 1} apps showing")
            }
            return
        }
        // Known to be there: [fits] above needs a layout.
        val active: Any = layout
        val ok = catching {
            val algorithm = field(active, "mDividerSnapAlgorithm") ?: error("no DividerSnapAlgorithm")
            val leftRight = field(algorithm, "mIsLeftRightSplit") as? Boolean ?: true
            val span = field(algorithm, if (leftRight) "mDisplayWidth" else "mDisplayHeight") as Int
            val at = fractions.map { (it.coerceIn(1, 999).toLong() * span / 1000).toInt() }.sorted()
            if (three) DividerStopsHook.placeThree(active, at[0], at[1]) else DividerStopsHook.placeTwo(active, at[0])
        }.getOrElse { log("split layout: can't place the divider: ${reason(it)}"); false }
        reply(context, request, ok)
    }

    /** S5: a divider started / stopped being dragged, for Folark's marks overlay. */
    fun reportDrag(on: Boolean, panes: Int) {
        val context = appContext ?: return
        send(context, Bundle().apply {
            putInt(SplitEvents.EXTRA_KIND, SplitEvents.KIND_DRAG)
            putBoolean(SplitEvents.EXTRA_VISIBLE, on)
            putInt(SplitEvents.EXTRA_PANES, panes)
        })
    }

    /**
     * S1: the split's apps (left to right, by WM Shell's stage order) and divider places in
     * thousandths, once a divider rests: Folark keeps them per set of apps.
     */
    fun reportLayout() {
        val c = controller ?: return
        val context = appContext ?: return
        val executor = mainExecutor(c) ?: return
        executor.execute {
            catching {
                val stage = field(c, "mStageCoordinator") ?: return@catching
                val layout = field(splitManager(c), "mSplitLayout") ?: return@catching
                val algorithm = field(layout, "mDividerSnapAlgorithm") ?: return@catching
                val leftRight = field(algorithm, "mIsLeftRightSplit") as? Boolean ?: true
                val span = (field(algorithm, if (leftRight) "mDisplayWidth" else "mDisplayHeight") as? Int)?.takeIf { it > 0 } ?: return@catching
                val positions = (field(layout, "mDividerPositions") as? IntArray)?.sorted()
                    ?: listOfNotNull(field(layout, "mDividerPosition") as? Int)
                if (positions.isEmpty()) return@catching
                val getTask = methods(stage.javaClass).first { it.name == "getTaskId" && it.parameterTypes.size == 1 }
                val tasks = (0..positions.size).map { getTask.invoke(stage, it) as Int }
                if (tasks.any { it <= 0 }) return@catching
                send(context, Bundle().apply {
                    putInt(SplitEvents.EXTRA_KIND, SplitEvents.KIND_LAYOUT)
                    putIntArray(SplitEvents.EXTRA_TASKS, tasks.toIntArray())
                    putIntArray(SplitEvents.EXTRA_FRACTIONS, positions.map { (it.toLong() * 1000 / span).toInt() }.toIntArray())
                })
            }
        }
    }

    /** WM Shell's SplitLayoutManager (Motorola), which holds the current split layout and state. */
    private fun splitManager(c: Any): Any {
        val stage = field(c, "mStageCoordinator") ?: error("no StageCoordinator")
        return field(stage, "mSplitLayoutManager") ?: error("no SplitLayoutManager")
    }

    /**
     * On WM Shell's thread: the snap position the divider is at now (the nearest stop), seen from
     * [forTask] as if it sat left / on top. Refused when [forTask] isn't in the split.
     */
    private fun querySnap(context: Context, c: Any, request: String, forTask: Int) {
        val value = catching {
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
    private fun splitPosition(c: Any, taskId: Int): Int = catching {
        val m = c.javaClass.methods.firstOrNull {
            it.name == "getSplitPosition" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        } ?: return@catching -1
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
        val int = Int::class.java
        val failure = catching {
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
        context: Context, c: Any, request: String, task1: Int, task2: Int, position: Int, snapPosition: Int, tries: Int = 0,
    ) {
        val int = Int::class.java
        // A task started a moment ago (the split picker docking itself) reaches WM Shell's task list
        // a little later than it exists: wait for it on this thread instead of failing at once.
        if (task1 > 0 && task2 > 0 && task1 != task2 && tries < TASK_WAIT_TRIES &&
            (!isRunningTask(c, task1) || !isRunningTask(c, task2))
        ) {
            Handler(Looper.myLooper() ?: Looper.getMainLooper()).postDelayed(
                { startSplitTasksNative(context, c, request, task1, task2, position, snapPosition, tries + 1) }, TASK_WAIT_STEP_MS,
            )
            return
        }
        val failure = catching {
            if (task1 <= 0 || task2 <= 0 || task1 == task2) error("two different tasks needed ($task1, $task2)")
            if (!isRunningTask(c, task1) || !isRunningTask(c, task2)) {
                error("a task isn't running (${listOf(task1, task2).filterNot { isRunningTask(c, it) }.joinToString()} of $task1, $task2, after ${tries * TASK_WAIT_STEP_MS} ms)")
            }
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

    /**
     * Like [invokeLeading], with the split index (which pane: 2 = the third) in the first number
     * after [leading]; newer builds may add parameters after it.
     */
    private fun invokeWithIndex(c: Any, name: String, leading: List<Pair<Class<*>, Any?>>, index: Int) {
        val int = Int::class.javaPrimitiveType
        val candidates = methods(c.javaClass).filter { it.name == name }
        val m = candidates.filter { method ->
            val p = method.parameterTypes
            p.size > leading.size && leading.indices.all { i -> p[i] == leading[i].first || p[i].isAssignableFrom(leading[i].first) } &&
                (leading.size until p.size).any { p[it] == int }
        }.minByOrNull { it.parameterTypes.size }
            ?: error("no $name(${leading.joinToString { it.first.simpleName }}, …, int index); found: " +
                candidates.joinToString(" | ") { method -> method.parameterTypes.joinToString(",") { it.simpleName } }.ifEmpty { "none" })
        val p = m.parameterTypes
        val indexAt = (leading.size until p.size).first { p[it] == int }
        val args = p.mapIndexed { i, type ->
            when {
                i < leading.size -> leading[i].second
                i == indexAt -> index
                else -> neutral(type)
            }
        }
        m.isAccessible = true
        m.invoke(c, *args.toTypedArray())
    }

    /** A harmless value for a trailing parameter of [type]. */
    private fun neutral(type: Class<*>): Any? = when {
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        type == Boolean::class.javaPrimitiveType -> false
        type == Float::class.javaPrimitiveType -> 0f
        type.name == "com.android.internal.logging.InstanceId" -> catching {
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
    private fun isRunningTask(c: Any, taskId: Int): Boolean = catching {
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
        val ok = catching {
            if (mode == 2) {
                val visible = catching { c.javaClass.getMethod("isSplitScreenVisible").invoke(c) as Boolean }.getOrDefault(false)
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
    private fun attachListener(context: Context, c: Any): Boolean = catching {
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
    private fun newListener(): Any? = catching {
        val cl = loader ?: return null
        val type = Class.forName(LISTENER, false, cl)
        Proxy.newProxyInstance(cl, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FolarkSplitListener"
                else -> {
                    catching { onListenerCall(method.name, args) }
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
        synchronized(pendingEvents) {
            pendingEvents += extras
            if (flushQueued) return
            flushQueued = true
        }
        outbox.schedule({ flush(context) }, EVENT_BATCH_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /** Everything [send] collected in the last [EVENT_BATCH_MS], in order, in one call. */
    private fun flush(context: Context) {
        val batch = synchronized(pendingEvents) {
            flushQueued = false
            ArrayList(pendingEvents).also { pendingEvents.clear() }
        }
        if (batch.isEmpty()) return
        val extras = if (batch.size == 1) batch[0] else Bundle().apply { putParcelableArrayList(SplitEvents.EXTRA_BATCH, batch) }
        catching {
            context.contentResolver.call(Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitEvents.METHOD_EVENT, null, extras)
        }
    }

    private fun methods(cls: Class<*>): List<java.lang.reflect.Method> = Reflect.methods(cls)

    private fun reply(context: Context, request: String, ok: Boolean, value: Int? = null, detail: String? = null) {
        Thread {
            catching {
                context.contentResolver.call(
                    Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitThree.METHOD_RESULT, request,
                    Bundle().apply {
                        putBoolean(SplitThree.EXTRA_OK, ok)
                        if (value != null) putInt(SplitThree.EXTRA_VALUE, value)
                        if (detail != null) putString(SplitThree.EXTRA_DETAIL, detail)
                    },
                )
            }.onFailure { log("split hook: answer for $request didn't reach Folark: $it") }
        }.start()
    }

    /** WM Shell's main executor: the controller's `mMainExecutor`, or any Executor field it has. */
    private fun mainExecutor(c: Any): Executor? =
        (field(c, "mMainExecutor") as? Executor)
            ?: allFields(c.javaClass).firstOrNull { Executor::class.java.isAssignableFrom(it.type) }
                ?.let { catching { it.isAccessible = true; it.get(c) as? Executor }.getOrNull() }

    private fun field(target: Any, name: String): Any? = Reflect.get(target, name)

    private fun allFields(cls: Class<*>): List<Field> = generateSequence(cls) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }.toList()

    private fun appContext(): Context? = catching {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()
}
