package org.arkikeskus.launcher.data

import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Real three-app split screen, asked of Folark's Vector module in SystemUI (its SplitThreeHook):
 * with two apps in split screen, a third joins them as a third pane. Folark sends a request to
 * SystemUI (Android tells SystemUI it came from Folark), SystemUI answers through Folark's
 * RecentsHookState provider, and [addThird] returns whether it worked — false whenever the module
 * isn't there, so callers fall back to a floating window.
 */
object SplitThree {
    const val ACTION = "org.arkikeskus.launcher.action.SPLIT_THIRD"
    const val EXTRA_REQUEST = "request"
    const val EXTRA_TASK = "task"
    const val EXTRA_INTENT = "intent"
    const val EXTRA_USER_ID = "user"
    const val EXTRA_OK = "ok"
    /** A split layout to switch to, by WM Shell's snap position (see the SNAP_* values). */
    const val EXTRA_SNAP = "snap"
    /** Native split-select: 1 = two-app picker (startTaskWithSelectRequest), 2 = three-app select. */
    const val EXTRA_SELECT = "select"
    const val EXTRA_SPLIT_TASKS = "split_tasks"
    /** The split index / position hint that goes with a select request. */
    const val EXTRA_POS = "pos"

    /** Half and half. */
    const val SNAP_EVEN = 1
    /** Android's peek view: the left app 90%, the right one peeking in from the edge (SNAP_TO_2_90_10). */
    const val SNAP_PEEK_RIGHT = 3
    /** Android's peek view: the right app 90%, the left one peeking in from the edge (SNAP_TO_2_10_90). */
    const val SNAP_PEEK_LEFT = 4

    /** Provider methods SystemUI calls on Folark: "ready" when it starts, "result" per request. */
    const val METHOD_READY = "split3_ready"
    const val METHOD_RESULT = "split3_result"

    private const val SYSTEMUI = "com.android.systemui"
    private const val PREFS = "folark_recents_hook"
    private const val READY_AT = "split3_ready_at"
    private const val READY_FOR_MS = 14L * 24 * 3600 * 1000
    private const val TIMEOUT_MS = 4_500L

    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    /** SystemUI says the three-app split can be asked for (the module is running there). */
    fun onReady(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, 0).edit().putLong(READY_AT, System.currentTimeMillis()).apply()
        }
    }

    /** SystemUI's answer to request [request]. */
    fun onResult(request: String?, ok: Boolean) {
        request ?: return
        waiting.remove(request)?.complete(ok)
    }

    /** The module has said it is ready in SystemUI (since SystemUI last started, give or take). */
    fun ready(context: Context): Boolean = Build.VERSION.SDK_INT >= 34 && runCatching {
        val at = context.getSharedPreferences(PREFS, 0).getLong(READY_AT, 0L)
        at > 0L && System.currentTimeMillis() - at < READY_FOR_MS
    }.getOrDefault(false)

    /**
     * With two apps in split screen (or about to be), adds a third as the third pane: the open task
     * [taskId], or else the app [component] (opened in the third pane). Only apps of this profile can
     * be opened by [component]. Suspends until SystemUI answers (a few seconds at most).
     */
    suspend fun addThird(
        context: Context,
        taskId: Int? = null,
        component: ComponentName? = null,
        user: UserHandle? = null,
    ): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        if (taskId == null && component == null) return false
        if (taskId == null && user != null && user != Process.myUserHandle()) return false
        return send(context) { request ->
            if (taskId != null) {
                putExtra(EXTRA_TASK, taskId)
            } else if (component != null) {
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val pending = PendingIntent.getActivity(
                    context, request.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                putExtra(EXTRA_INTENT, pending).putExtra(EXTRA_USER_ID, Process.myUid() / PER_USER_RANGE)
            }
        }
    }

    /**
     * Switches the two-app split showing now (or about to) to [snap]: Android's own peek view
     * ([SNAP_PEEK_LEFT] / [SNAP_PEEK_RIGHT], one app pushed off the edge with a strip to tap) or
     * half and half ([SNAP_EVEN]) — as if the divider had been dragged there. False when the
     * module isn't running in SystemUI or the phone has no such layout.
     */
    suspend fun snap(context: Context, snap: Int): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return send(context) { putExtra(EXTRA_SNAP, snap) }
    }

    /**
     * Motorola's native two-app split-select: put [taskId] in split and let the system's own app
     * picker choose the other app. [index] is the side (0 left / 1 right). Returns false where the
     * module or the method isn't there.
     */
    suspend fun splitSelect(context: Context, taskId: Int, index: Int): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return send(context) { putExtra(EXTRA_SELECT, 1); putExtra(EXTRA_TASK, taskId); putExtra(EXTRA_POS, index) }
    }

    /**
     * Motorola's native three-app split-select (only while a split is showing): the system shows
     * its picker for the third app. [position] is the place hint (as Moto's gesture manager passes).
     */
    suspend fun startSplitTasks(context: Context, taskId1: Int, taskId2: Int, snapPosition: Int): Boolean {
        return send(context) { putExtra(EXTRA_SPLIT_TASKS, true); putExtra("taskId1", taskId1); putExtra("taskId2", taskId2); putExtra("snapPosition", snapPosition) }
    }

    suspend fun selectThird(context: Context, position: Int): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return send(context) { putExtra(EXTRA_SELECT, 2); putExtra(EXTRA_POS, position) }
    }

    /** Sends one request to SystemUI (Android shares Folark's identity with it) and waits for the answer. */
    private suspend fun send(context: Context, fill: Intent.(request: String) -> Unit): Boolean {
        val request = UUID.randomUUID().toString()
        val answer = CompletableDeferred<Boolean>()
        waiting[request] = answer
        val intent = Intent(ACTION).setPackage(SYSTEMUI).putExtra(EXTRA_REQUEST, request)
        intent.fill(request)
        val sent = runCatching {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            context.sendBroadcast(intent, null, options)
        }.isSuccess
        if (!sent) {
            waiting.remove(request)
            return false
        }
        val ok = withTimeoutOrNull(TIMEOUT_MS) { answer.await() }
        waiting.remove(request)
        return ok == true
    }

    /** UserHandle.PER_USER_RANGE: a uid's user is uid / 100000. */
    private const val PER_USER_RANGE = 100_000
}
