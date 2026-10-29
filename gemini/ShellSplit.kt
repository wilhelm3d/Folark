package org.arkikeskus.launcher.data

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File

object ShellSplit {

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

    fun grantCommand(packageName: String): String =
        "adb shell \"pm grant $packageName android.permission.DUMP; pm grant $packageName android.permission.PACKAGE_USAGE_STATS\""

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

    /** Finds the taskId of SplitPickerActivity specifically (never matching MainActivity / type=home). */
    fun pickerTaskIdOf(context: Context): Int? {
        val activities = dump(context, arrayOf("activities"))
        if (activities != null) {
            for (line in activities.lineSequence()) {
                if (line.contains("SplitPickerActivity") && line.contains("Task{")) {
                    val id = Regex("""Task\{\S+ #(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
                    if (id != null) return id
                }
            }
            var currentTaskId: Int? = null
            for (line in activities.lineSequence()) {
                if (line.contains("* Task{")) {
                    currentTaskId = if (line.contains("type=home")) null
                    else Regex("""Task\{\S+ #(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
                } else if (currentTaskId != null && line.contains("SplitPickerActivity")) {
                    return currentTaskId
                }
            }
        }
        return taskIdOf(context, "SplitPickerActivity")
    }

    fun taskIdOf(context: Context, packageName: String): Int? {
        val text = dump(context, arrayOf("recents")) ?: return null
        val blocks = mutableListOf<StringBuilder>()
        for (line in text.lineSequence()) {
            if (line.contains("Recent #")) blocks += StringBuilder()
            blocks.lastOrNull()?.appendLine(line)
        }
        val needles = listOf("$packageName/", ":$packageName", "{$packageName", "=$packageName", " $packageName", packageName)
        for (block in blocks) {
            val body = block.toString()
            if (body.contains("type=home")) continue
            if (needles.none { body.contains(it) }) continue
            val id = Regex("""taskId=(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""Task\{\S+ #(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""\bid=(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
            if (id != null) return id
        }
        return null
    }

    data class SplitTask(val taskId: Int, val packageName: String, val visible: Boolean = true)

    private fun queryStagedTasks(context: Context, visibleOnly: Boolean): List<SplitTask> {
        val text = dump(context, arrayOf("activities")) ?: return emptyList()
        val found = LinkedHashMap<String, SplitTask>()
        var stageIndent = -1
        var currentStage = ""
        for (line in text.lineSequence()) {
            if (line.startsWith("Display ") || line.contains("TaskDisplayArea") || line.startsWith("RootHomeTask")) {
                stageIndent = -1
                currentStage = ""
            }
            val star = line.indexOf("* Task{")
            if (star < 0) continue
            val stageMatch = Regex("""stage_[abc]""").find(line)?.value
            if (stageMatch != null) {
                stageIndent = star
                currentStage = stageMatch
                continue
            }
            if (stageIndent < 0) continue
            if (star <= stageIndent) {
                stageIndent = -1
                currentStage = ""
                continue
            }
            if (star > stageIndent + 4) continue
            if (line.contains("type=home") || line.contains("type=recents")) continue
            if (line.contains("mode=fullscreen")) continue

            val isVis = (line.contains("visible=true") || line.contains("visibleRequested=true")) &&
                !line.contains("visible=false")
            if (visibleOnly && !isVis) continue
            val id = Regex("""Task\{\S+ #(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val pkg = Regex("""\bA=\d+:([\w.]+)""").find(line)?.groupValues?.get(1)
                ?: Regex("""\bI=([\w.]+)/""").find(line)?.groupValues?.get(1)
                ?: ""
            if (pkg.isNotEmpty() && pkg != context.packageName) {
                // Keep the top-most task per stage (stage_a, stage_b, stage_c)
                found.putIfAbsent(currentStage, SplitTask(id, pkg, isVis))
            }
        }
        return found.values.toList()
    }

    /** Tasks visibly in split screen right now. */
    fun splitTasks(context: Context): List<SplitTask> {
        val visible = queryStagedTasks(context, visibleOnly = true)
        return if (visible.size >= 2) visible else emptyList()
    }

    /**
     * Tasks currently paired in split stages (even when backgrounded behind Home or Folark Recents),
     * so Recents can display them grouped together as a single split card.
     */
    fun stagedSplitTasks(context: Context): List<SplitTask> {
        val staged = queryStagedTasks(context, visibleOnly = false)
        return if (staged.size >= 2) staged else emptyList()
    }

    fun allStagedTaskIds(context: Context): Set<Int> =
        queryStagedTasks(context, visibleOnly = false).map { it.taskId }.toSet()

    fun swapSides(context: Context): Boolean = wmShell(context, "splitscreen", "switchSplitPosition")

    fun exitSplit(context: Context, keepTaskId: Int): Boolean =
        wmShell(context, "splitscreen", "exitSplitScreen", keepTaskId.toString())

    fun clearAllRecents(context: Context): Boolean = wmShell(context, "recents", "clearAll")

    private fun wmShell(context: Context, vararg command: String): Boolean =
        dump(context, arrayOf("service", "SystemUIService", "WMShell", *command)) != null

    /** Direct, zero-overhead WMShell moveToSideStage when the caller already cleared old split state. */
    fun moveToSideFast(context: Context, taskId: Int, right: Boolean): Boolean {
        if (taskId <= 0) return false
        val sideStr = if (right) "1" else "0"
        return dump(
            context,
            arrayOf("service", "SystemUIService", "WMShell", "splitscreen", "moveToSideStage", taskId.toString(), sideStr),
        ) != null
    }

    fun moveToSide(context: Context, taskId: Int, right: Boolean): Boolean {
        if (taskId <= 0) return false
        val sideStr = if (right) "1" else "0"
        val res = dump(
            context,
            arrayOf("service", "SystemUIService", "WMShell", "splitscreen", "moveToSideStage", taskId.toString(), sideStr),
        )
        return res != null
    }
}
