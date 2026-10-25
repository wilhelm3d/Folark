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
 * The SystemUI half of Folark's real three-app split. Android 17's split screen (WM Shell, inside
 * SystemUI) can already tile three apps: when two are in split screen and a task is started into
 * the third place, `StageCoordinator.startTask` switches to a three-way layout. Nothing on the phone
 * offers that from a launcher, so this hook lets Folark ask for it:
 *
 * - it keeps the live `SplitScreenController` (caught when it starts, or the first time it is used),
 * - it listens for Folark's request, accepted only when Android confirms the sender is Folark
 *   (`getSentFromPackage`, Android 14+), so no other app can start tasks through it,
 * - on WM Shell's own thread it waits (up to a few seconds) for the two-app split to be showing,
 *   then starts the third app into the third place: an open app by its task, or an app that is not
 *   open by its launch PendingIntent (made by Folark, so it starts as Folark would start it),
 * - and it tells Folark whether that worked, so Folark can fall back to a floating window.
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

    private const val WAIT_STEP_MS = 120L
    private const val WAIT_TRIES = 30

    /** How long the divider takes to glide to its new place (as when dropped by a finger). */
    private const val SNAP_MS = 450

    @Volatile private var controller: Any? = null
    @Volatile private var listening = false
    private var log: (String) -> Unit = {}

    fun install(loader: ClassLoader, log: (String) -> Unit) {
        this.log = log
        val cls = runCatching { XposedHelpers.findClass(CONTROLLER, loader) }.getOrNull()
        if (cls == null) {
            log("no $CONTROLLER in SystemUI; three-app split stays off")
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
        log("three-app split: hooked $hooked SystemUI method(s)")
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
            log("three-app split: can't listen: $it")
            return
        }
        log("three-app split: ready (split screen found)")
        // Tell Folark the three-app split can be asked for (shown in its settings).
        Thread {
            runCatching {
                context.contentResolver.call(Uri.parse("content://${RecentsHook.STATE_AUTHORITY}"), SplitThree.METHOD_READY, null, null)
            }
        }.start()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runCatching { handle(context, intent) }.onFailure { log("three-app split failed: $it") }
        }
    }

    private fun BroadcastReceiver.handle(context: Context, intent: Intent) {
        val request = intent.getStringExtra(SplitThree.EXTRA_REQUEST) ?: return
        // Only Folark may ask (Android itself vouches for the sender since Android 14).
        if (Build.VERSION.SDK_INT < 34 || sentFromPackage != FOLARK) {
            log("three-app split: refused a request from ${if (Build.VERSION.SDK_INT >= 34) sentFromPackage else "?"}")
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
     * On WM Shell's thread: moves the split's divider to the snap target [snap] — exactly where a
     * finger dragging the divider ends (SplitLayout.snapToTarget), so Android sets up the layout the
     * same way, peek strip included. Waits for the split to be showing first.
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
            val targets = (field(algorithm, "mTargets") as? List<*>).orEmpty()
            val target = targets.firstOrNull { it != null && field(it, "snapPosition") == snap }
                ?: error("no snap target $snap among ${targets.size}")
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
