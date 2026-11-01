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
 * WM Shell throws on its own thread (System UI restarts) when `moveToSideStage` gets a task that is
 * already in split screen or isn't running, so [moveToSide] checks both first. Every blocking
 * function here runs `dumpsys`: call them off the main thread.
 */
object ShellSplit {

    // Built once: the parsers below run over thousands of `dumpsys` lines.
    private val TASK_ID = Regex("""Task\{\S+ #(\d+)""")
    private val TASK_ID_EQ = Regex("""taskId=(\d+)""")
    private val ID_EQ = Regex("""\bid=(\d+)""")
    private val STAGE = Regex("""stage_[abc]""")
    private val PKG_INTENT = Regex("""\bI=([\w.]+)/""")
    private val PKG_AFFINITY = Regex("""\bA=\d+:([\w.]+)""")
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
            val file = File(context.cacheDir, "shell_split_${Thread.currentThread().id}.txt")
            val ok = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
            ).use { fd -> Debug.dumpService("activity", fd.fileDescriptor, args) }
            val text = if (ok) file.readText() else null
            file.delete()
            text
        }.getOrNull()
    }

    /**
     * The newest task of [packageName] (from Recents, newest first). The name must stand on its own
     * (`com.foo` never matches `com.foo.bar`). The task may no longer be running: [moveToSide] checks.
     */
    fun taskIdOf(context: Context, packageName: String, root: Boolean = false): Int? {
        if (packageName.isEmpty()) return null
        val text = dump(context, arrayOf("recents"), root) ?: return null
        val name = Regex("""(?<![\w.])${Regex.escape(packageName)}(?![\w.])""")
        var block: StringBuilder? = null
        fun check(b: StringBuilder?): Int? {
            val body = b?.toString() ?: return null
            if (body.contains("type=home") || !name.containsMatchIn(body)) return null
            return TASK_ID_EQ.find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: TASK_ID.find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: ID_EQ.find(body)?.groupValues?.get(1)?.toIntOrNull()
        }
        for (line in text.lineSequence()) {
            if (line.contains("Recent #")) {
                check(block)?.let { return it }
                block = StringBuilder()
            }
            block?.appendLine(line)
        }
        return check(block)
    }

    data class SplitTask(val taskId: Int, val packageName: String, val visible: Boolean = true)

    /**
     * One read of the window hierarchy: every running task, every task in a split stage (any app,
     * Folark's picker included), and one app per stage for [splitTasks] (Folark's own tasks left out).
     */
    private class Tasks(val running: Set<Int>, val stagedIds: Set<Int>, val staged: List<SplitTask>)

    private fun readTasks(context: Context, root: Boolean, visibleOnly: Boolean = false): Tasks? {
        val text = dump(context, arrayOf("activities"), root) ?: return null
        val running = HashSet<Int>()
        val stagedIds = HashSet<Int>()
        val perStage = LinkedHashMap<String, SplitTask>()
        var stageIndent = -1
        var currentStage = ""
        for (line in text.lineSequence()) {
            if (line.startsWith("Display ") || line.contains("TaskDisplayArea") || line.startsWith("RootHomeTask")) {
                stageIndent = -1
                currentStage = ""
            }
            val star = line.indexOf("* Task{")
            if (star < 0) continue
            val id = TASK_ID.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val stage = STAGE.find(line)?.value
            if (stage != null) {
                stageIndent = star
                currentStage = stage
                continue
            }
            val pkg = PKG_INTENT.find(line)?.groupValues?.get(1)
                ?: PKG_AFFINITY.find(line)?.groupValues?.get(1)
                ?: ""
            if (pkg.isNotEmpty()) running += id
            if (stageIndent < 0) continue
            if (star <= stageIndent) {
                stageIndent = -1
                currentStage = ""
                continue
            }
            if (star > stageIndent + 4) continue
            if (line.contains("type=home") || line.contains("type=recents") || line.contains("mode=fullscreen")) continue
            stagedIds += id
            val isVis = (line.contains("visible=true") || line.contains("visibleRequested=true")) &&
                !line.contains("visible=false")
            if (visibleOnly && !isVis) continue
            // Folark's own tasks (its split picker's affinity is "<package>.splitpicker") are no split app.
            if (pkg.isNotEmpty() && !pkg.startsWith(context.packageName)) {
                perStage.putIfAbsent(currentStage, SplitTask(id, pkg, isVis))
            }
        }
        return Tasks(running, stagedIds, perStage.values.toList())
    }

    /** The two (or three) apps of the split screen showing now; empty when there is none. */
    fun splitTasks(context: Context, root: Boolean = false): List<SplitTask> {
        val visible = readTasks(context, root, visibleOnly = true)?.staged.orEmpty()
        return if (visible.size >= 2) visible else emptyList()
    }

    /** Like [splitTasks], also while the split is hidden (behind Recents or Home). */
    fun stagedSplitTasks(context: Context, root: Boolean = false): List<SplitTask> {
        val staged = readTasks(context, root)?.staged.orEmpty()
        return if (staged.size >= 2) staged else emptyList()
    }

    /** Every task in a split stage, any app (Folark's picker included). */
    fun allStagedTaskIds(context: Context, root: Boolean = false): Set<Int> =
        readTasks(context, root)?.stagedIds.orEmpty()

    /** True when [taskId] is running and could be moved into split screen right now. */
    fun isRunning(context: Context, taskId: Int, root: Boolean = false): Boolean =
        taskId > 0 && readTasks(context, root)?.running?.contains(taskId) == true

    fun swapSides(context: Context, root: Boolean = false): Boolean =
        wmShell(context, root, "splitscreen", "switchSplitPosition")

    /**
     * Closes any split screen (staged or showing), keeping [keepTaskId] in front (-1: none), and
     * waits until WM Shell has really let the tasks go, so a [moveToSide] right after doesn't meet
     * them still in their stage. Does nothing when no split is staged.
     */
    fun exitSplit(context: Context, keepTaskId: Int, root: Boolean = false): Boolean {
        if (readTasks(context, root)?.stagedIds.isNullOrEmpty()) return true
        if (!wmShell(context, root, "splitscreen", "exitSplitScreen", keepTaskId.toString())) return false
        val until = System.currentTimeMillis() + EXIT_WAIT_MS
        while (System.currentTimeMillis() < until) {
            Thread.sleep(EXIT_POLL_MS)
            if (readTasks(context, root)?.stagedIds.isNullOrEmpty()) return true
        }
        return false
    }

    fun clearAllRecents(context: Context): Boolean = wmShell(context, false, "recents", "clearAll")

    /**
     * Moves the running task [taskId] into split screen, on the right ([right]) or left; the app
     * behind it fills the other half. Never sends a task WM Shell would throw on: one already in a
     * split (true, it is in split screen already) or one that isn't running (false).
     */
    fun moveToSide(context: Context, taskId: Int, right: Boolean, root: Boolean = false): Boolean {
        if (taskId <= 0) return false
        val tasks = readTasks(context, root) ?: return false
        if (taskId in tasks.stagedIds) return true
        if (taskId !in tasks.running) return false
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
