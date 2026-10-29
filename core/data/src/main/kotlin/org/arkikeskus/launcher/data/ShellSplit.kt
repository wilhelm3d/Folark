package org.arkikeskus.launcher.data

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File

/**
 * Split screen through Android's own split-screen command (`dumpsys activity service
 * SystemUIService WMShell splitscreen moveToSideStage <task> <side>`).
 */
object ShellSplit {

    /** Whether both one-time permissions are in place: DUMP, and usage access. */
    fun available(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED &&
            hasUsageAccess(context)

    /** Usage access: the Usage access switch in Android's settings, or the permission granted over ADB. */
    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        when (appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }.getOrDefault(false)

    /** Whether the one-time DUMP grant is there (enough for Folark's Recents and split view). */
    fun hasDump(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    /** The ADB command that gives both permissions, for [packageName]. */
    fun grantCommand(packageName: String): String =
        "adb shell \"pm grant $packageName android.permission.DUMP; pm grant $packageName android.permission.PACKAGE_USAGE_STATS\""

    /** Runs `dumpsys activity <args>` in this process and returns what it printed; null if refused. */
    @Synchronized
    internal fun dump(context: Context, args: Array<String>): String? = runCatching {
        val file = File(context.cacheDir, "shell_split_${Thread.currentThread().id}.txt")
        val ok = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
        ).use { fd -> Debug.dumpService("activity", fd.fileDescriptor, args) }
        val text = if (ok) file.readText() else null
        file.delete()
        text
    }.getOrNull()

    /** The task number of [packageName]'s newest task, from Android's list of recent tasks. */
    fun taskIdOf(context: Context, packageName: String): Int? {
        val text = dump(context, arrayOf("recents")) ?: return null
        val blocks = mutableListOf<StringBuilder>()
        for (line in text.lineSequence()) {
            if (line.contains("Recent #")) blocks += StringBuilder()
            blocks.lastOrNull()?.appendLine(line)
        }
        val needles = listOf("$packageName/", ":$packageName", "{$packageName", "=$packageName", " $packageName")
        for (block in blocks) {
            val body = block.toString()
            if (needles.none { body.contains(it) }) continue
            val id = Regex("""taskId=(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""Task\{\S+ #(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""\bid=(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
            if (id != null) return id
        }
        return null
    }

    /** A task shown in split screen: its number and app. */
    data class SplitTask(val taskId: Int, val packageName: String, val visible: Boolean = true)

    /**
     * All tasks currently parented inside WM Shell's split stages (`stage_a`, `stage_b`, `stage_c`),
     * whether currently visible on screen or in the background.
     */
    private fun queryStagedTasks(context: Context, visibleOnly: Boolean): List<SplitTask> {
        val text = dump(context, arrayOf("activities")) ?: return emptyList()
        val found = LinkedHashMap<Int, SplitTask>()
        var stageIndent = -1
        for (line in text.lineSequence()) {
            val star = line.indexOf("* Task{")
            if (star < 0) continue
            if (line.contains("name=stage_") || line.contains("(stage_")) {
                stageIndent = star
                continue
            }
            if (stageIndent < 0) continue
            if (star <= stageIndent) {
                stageIndent = -1
                continue
            }
            val isVis = line.contains("visible=true") || line.contains("visibleRequested=true")
            if (visibleOnly && !isVis) continue
            val id = Regex("""Task\{\S+ #(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val pkg = Regex("""\bA=\d+:([\w.]+)""").find(line)?.groupValues?.get(1)
                ?: Regex("""\bI=([\w.]+)/""").find(line)?.groupValues?.get(1)
                ?: ""
            found.putIfAbsent(id, SplitTask(id, pkg, isVis))
        }
        return found.values.toList()
    }

    /**
     * The tasks visibly sharing the split screen right now.
     */
    fun splitTasks(context: Context): List<SplitTask> =
        queryStagedTasks(context, visibleOnly = true).filter { it.packageName.isNotEmpty() }

    /**
     * Every task ID currently attached to any split stage (visible or backgrounded).
     * Used to prevent `SplitScreenController.moveToStage` from throwing
     * `IllegalArgumentException: taskId is in split<id>` and crashing SystemUI.
     */
    fun allStagedTaskIds(context: Context): Set<Int> =
        queryStagedTasks(context, visibleOnly = false).map { it.taskId }.toSet()

    /** Swaps the two sides of the split screen. */
    fun swapSides(context: Context): Boolean = wmShell(context, "splitscreen", "switchSplitPosition")

    /** Ends split screen, leaving task [keepTaskId] full screen. */
    fun exitSplit(context: Context, keepTaskId: Int): Boolean =
        wmShell(context, "splitscreen", "exitSplitScreen", keepTaskId.toString())

    /** Android's own "Clear all" of Recents. */
    fun clearAllRecents(context: Context): Boolean = wmShell(context, "recents", "clearAll")

    private fun wmShell(context: Context, vararg command: String): Boolean =
        dump(context, arrayOf("service", "SystemUIService", "WMShell", *command)) != null

    /**
     * Moves task [taskId] into the side half, [right] (1) or left (0), beside the standard task
     * currently in front on screen.
     */
    fun moveToSide(context: Context, taskId: Int, right: Boolean): Boolean {
        val sideStr = if (right) "1" else "0"
        if (taskId <= 0) return false

        val allStaged = allStagedTaskIds(context)
        if (taskId in allStaged) {
            exitSplit(context, taskId)
            Thread.sleep(150L)
        }

        val res = dump(
            context,
            arrayOf("service", "SystemUIService", "WMShell", "splitscreen", "moveToSideStage", taskId.toString(), sideStr),
        )
        return res != null
    }
}
