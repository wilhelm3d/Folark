package org.arkikeskus.launcher.data

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.arkikeskus.launcher.model.AppItem

/**
 * The split-select picker: Folark's own app drawer, opened in the half of the screen beside the app
 * that was dragged to an edge (Recents or the taskbar). The dragged ("first") app and the side it
 * was dropped on travel along as extras; the picker then puts the chosen app beside it.
 */
object SplitPicker {
    private const val PICKER = "org.arkikeskus.launcher.recents.SplitPickerActivity"
    const val EXTRA_FIRST_PACKAGE = "first_package"
    /** [AppItem.key] of the first app (it tells profiles apart), when known. */
    const val EXTRA_FIRST_KEY = "first_key"
    /** The first app's running task, when known (-1: not known). */
    const val EXTRA_FIRST_TASK = "first_task"
    const val EXTRA_RIGHT = "right"
    const val EXTRA_SELF_DOCK_RIGHT = "self_dock_right"
    const val EXTRA_PEEK = "peek"

    /** The picker's first frame goes up before the first app is put beside it. */
    private const val PICKER_START_MS = 150L
    /** The app brought to the front settles before it is moved into its half. */
    private const val FRONT_SETTLE_MS = 160L
    private const val FIND_TRIES = 8
    private const val FIND_RETRY_MS = 60L

    fun componentName(context: Context): ComponentName = ComponentName(context.packageName, PICKER)

    fun intent(
        context: Context,
        firstPackage: String,
        right: Boolean,
        selfDockRight: Boolean = false,
        peek: Boolean = false,
        firstKey: String? = null,
        firstTaskId: Int? = null,
    ): Intent = Intent().apply {
        component = componentName(context)
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION,
        )
        putExtra(EXTRA_FIRST_PACKAGE, firstPackage)
        putExtra(EXTRA_FIRST_KEY, firstKey)
        putExtra(EXTRA_FIRST_TASK, firstTaskId ?: -1)
        putExtra(EXTRA_RIGHT, right)
        putExtra(EXTRA_SELF_DOCK_RIGHT, selfDockRight)
        putExtra(EXTRA_PEEK, peek)
    }

    /**
     * Opens the picker beside the first app: the first app on [firstOnRight]'s side, the picker in
     * the other half. Whichever comes up last is moved into its half, and the one behind it fills
     * the other (WM Shell's own way). The first app comes forward by its task ([firstTaskId]) when
     * it is running, else it is opened. A first app still inside an older split is taken out of it
     * first (WM Shell can't move a task that is already in a split). Without the DUMP grant or root
     * the picker opens full screen and builds the split once an app is picked.
     */
    suspend fun openBesideApp(
        context: Context,
        appRepository: AppRepository,
        firstApp: AppItem?,
        firstPackage: String,
        firstTaskId: Int?,
        firstOnRight: Boolean,
        peek: Boolean = false,
    ) {
        val noAnim = ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
        fun picker(selfDockRight: Boolean) = intent(
            context, firstPackage, right = firstOnRight, selfDockRight = selfDockRight, peek = peek,
            firstKey = firstApp?.key, firstTaskId = firstTaskId,
        )

        val canRun = withContext(Dispatchers.IO) { ShellSplit.canRun(context) }
        if (!canRun) {
            withContext(Dispatchers.Main) { runCatching { context.startActivity(picker(false), noAnim) } }
            return
        }
        val root = withContext(Dispatchers.IO) { ShellSplit.preferRoot(context) }
        withContext(Dispatchers.IO) { ShellSplit.exitSplit(context, keepTaskId = firstTaskId ?: -1, root = root) }

        if (firstOnRight) {
            // Picker first (it ends up on the left), then the first app, which is moved to the right.
            withContext(Dispatchers.Main) { runCatching { context.startActivity(picker(false), noAnim) } }
            delay(PICKER_START_MS)
            val id = bringToFront(context, appRepository, firstApp, firstPackage, firstTaskId, root)
            if (id != null) {
                delay(FRONT_SETTLE_MS)
                withContext(Dispatchers.IO) { ShellSplit.moveToSide(context, id, right = true, root = root) }
            }
        } else {
            // First app first (it ends up on the left), then the picker, which moves itself right.
            bringToFront(context, appRepository, firstApp, firstPackage, firstTaskId, root)
            delay(FRONT_SETTLE_MS)
            withContext(Dispatchers.Main) { runCatching { context.startActivity(picker(true), noAnim) } }
        }
    }

    /**
     * Brings the first app to the front: its running task [taskId] if there is one, else it opens
     * [app]. Returns the task once it is running (null when it never showed up).
     */
    suspend fun bringToFront(
        context: Context,
        appRepository: AppRepository,
        app: AppItem?,
        packageName: String,
        taskId: Int?,
        root: Boolean,
    ): Int? {
        val running = taskId != null && withContext(Dispatchers.IO) { ShellSplit.isRunning(context, taskId, root) }
        val moved = running && withContext(Dispatchers.Main) {
            runCatching { context.getSystemService(ActivityManager::class.java).moveTaskToFront(taskId!!, 0) }.isSuccess
        }
        if (!moved) {
            if (app == null) return null
            withContext(Dispatchers.Main) { appRepository.launch(app) }
        }
        return waitForTask(context, packageName, if (moved) taskId else null, root)
    }

    /** [known] once it is running, else the newest running task of [packageName]; polled briefly. */
    suspend fun waitForTask(context: Context, packageName: String, known: Int?, root: Boolean): Int? =
        withContext(Dispatchers.IO) {
            repeat(FIND_TRIES) {
                val id = known ?: ShellSplit.taskIdOf(context, packageName, root)
                if (id != null && ShellSplit.isRunning(context, id, root)) return@withContext id
                delay(FIND_RETRY_MS)
            }
            null
        }
}
