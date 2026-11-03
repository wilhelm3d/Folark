package org.arkikeskus.launcher.data

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.Process
import org.arkikeskus.launcher.data.root.RootShell
import java.io.File

/**
 * Split screen through Android's own WM Shell commands (`dumpsys activity service SystemUIService
 * WMShell splitscreen …`). Needs the one-time DUMP grant ([grantCommand]); with [root] the same
 * commands run through `su` instead, so no grant is needed.
 *
 * What is open comes from one read of the window hierarchy, a [TaskSnapshot]: callers that take
 * several steps read once and pass it along ([snapshot]) instead of reading for every question.
 *
 * WM Shell throws on its own thread (System UI restarts) when `moveToSideStage` gets a task that is
 * already in split screen or isn't running, so [moveToSide] checks both first. Every blocking
 * function here runs `dumpsys`: call them off the main thread.
 */
object ShellSplit {

    private val SAFE_ARG = Regex("""^[\w.\-]+$""")

    private const val EXIT_WAIT_MS = 800L
    private const val EXIT_POLL_MS = 80L

    fun available(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED &&
            hasUsageAccess(context)

    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        when (appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }.getOrDefault(false)

    fun hasDump(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    /** True when split commands can run at all: the DUMP grant, or else root (checked once, cached). */
    fun canRun(context: Context): Boolean = hasDump(context) || RootShell.available()

    /** Run the commands through root: only when the DUMP grant is missing and root is there. */
    fun preferRoot(context: Context): Boolean = !hasDump(context) && RootShell.available()

    fun grantCommand(packageName: String): String =
        "adb shell \"pm grant $packageName android.permission.DUMP; pm grant $packageName android.permission.PACKAGE_USAGE_STATS\""

    /** `dumpsys activity <args>`: in-process with the DUMP grant, or through `su` with [root]. */
    @Synchronized
    internal fun dump(context: Context, args: Array<String>, root: Boolean = false): String? {
        if (root) {
            if (args.any { !SAFE_ARG.matches(it) }) return null
            return RootShell.run("dumpsys activity " + args.joinToString(" "))
        }
        return runCatching {
            // A file of its own per read: reads run on several threads at once.
            val file = File.createTempFile("shell_split_", ".txt", context.cacheDir)
            try {
                val ok = ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
                ).use { fd -> Debug.dumpService("activity", fd.fileDescriptor, args) }
                if (ok) file.readText() else null
            } finally {
                file.delete()
            }
        }.getOrNull()
    }

    /** One read of what is open (null when Android wouldn't say). */
    fun snapshot(context: Context, root: Boolean = false): TaskSnapshot? =
        dump(context, arrayOf("activities"), root)?.let { ShellSplitParser.parse(it, context.packageName) }

    /** The front-most running task of [packageName] (the name must match exactly). */
    fun taskIdOf(context: Context, packageName: String, root: Boolean = false, read: TaskSnapshot? = null): Int? =
        (read ?: snapshot(context, root))?.taskOf(packageName)?.id

    data class SplitTask(val taskId: Int, val packageName: String, val visible: Boolean = true)

    private fun TaskSnapshot.Task.asSplitTask() = SplitTask(id, packageName, visible)

    /** The two (or three) apps of the split screen showing now; empty when there is none. */
    fun splitTasks(context: Context, root: Boolean = false, read: TaskSnapshot? = null): List<SplitTask> =
        (read ?: snapshot(context, root))?.splitShowing.orEmpty().map { it.asSplitTask() }

    /** Like [splitTasks], also while the split is hidden (behind Recents or Home). */
    fun stagedSplitTasks(context: Context, root: Boolean = false, read: TaskSnapshot? = null): List<SplitTask> =
        (read ?: snapshot(context, root))?.splitStaged.orEmpty().map { it.asSplitTask() }

    /** Every task in a split stage, any app (Folark's picker included). */
    fun allStagedTaskIds(context: Context, root: Boolean = false, read: TaskSnapshot? = null): Set<Int> =
        (read ?: snapshot(context, root))?.stagedIds.orEmpty()

    /** True when [taskId] is running and could be moved into split screen right now. */
    fun isRunning(context: Context, taskId: Int, root: Boolean = false, read: TaskSnapshot? = null): Boolean =
        taskId > 0 && (read ?: snapshot(context, root))?.isRunning(taskId) == true

    fun swapSides(context: Context, root: Boolean = false): Boolean =
        wmShell(context, root, "splitscreen", "switchSplitPosition")

    /**
     * Closes any split screen (staged or showing), keeping [keepTaskId] in front (-1: none), and
     * (with [wait]) waits until WM Shell has really let the tasks go, so a [moveToSide] right after
     * doesn't meet them still in their stage. Does nothing when no split is staged ([read]: what is
     * open now, if already known).
     */
    fun exitSplit(context: Context, keepTaskId: Int, root: Boolean = false, read: TaskSnapshot? = null, wait: Boolean = true): Boolean {
        val now = read ?: snapshot(context, root)
        if (now?.stagedIds.isNullOrEmpty()) return true
        if (!wmShell(context, root, "splitscreen", "exitSplitScreen", keepTaskId.toString())) return false
        if (!wait) return true
        val until = System.currentTimeMillis() + EXIT_WAIT_MS
        while (System.currentTimeMillis() < until) {
            Thread.sleep(EXIT_POLL_MS)
            if (snapshot(context, root)?.stagedIds.isNullOrEmpty()) return true
        }
        return false
    }

    fun clearAllRecents(context: Context): Boolean = wmShell(context, false, "recents", "clearAll")

    /**
     * Moves the running task [taskId] into split screen, on the right ([right]) or left; the app
     * behind it fills the other half. Never sends a task WM Shell would throw on: one already in a
     * split (true, it is in split screen already) or one that isn't running (false). [read]: what is
     * open now, if just read.
     */
    fun moveToSide(context: Context, taskId: Int, right: Boolean, root: Boolean = false, read: TaskSnapshot? = null): Boolean {
        if (taskId <= 0) return false
        val now = read ?: snapshot(context, root) ?: return false
        if (now.isStaged(taskId)) return true
        if (!now.isRunning(taskId)) return false
        return wmShell(context, root, "splitscreen", "moveToSideStage", taskId.toString(), if (right) "1" else "0")
    }

    /**
     * A WM Shell command. The commands print nothing when they work and an "Error…" / "Invalid
     * command…" line when they don't, so that output (not just whether `dumpsys` ran) is the answer.
     */
    private fun wmShell(context: Context, root: Boolean, vararg command: String): Boolean {
        val out = dump(context, arrayOf("service", "SystemUIService", "WMShell", *command), root) ?: return false
        return out.lineSequence().map { it.trim() }.none {
            it.startsWith("Error", ignoreCase = true) ||
                it.startsWith("Invalid command", ignoreCase = true) ||
                it.startsWith("Unknown command", ignoreCase = true)
        }
    }
}
