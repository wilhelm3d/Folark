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
 * Split screen through Android's own split-screen command (the one `adb shell dumpsys activity
 * service SystemUIService WMShell splitscreen moveToSideStage <task> <side>` runs).
 *
 * An ordinary app's "open next to the current app" request is ignored unless it comes from the app
 * in front, and the accessibility "toggle split screen" action is refused on some phones. This
 * command works from anywhere, but it needs the DUMP permission, which only a one-time
 * `adb shell pm grant <package> android.permission.DUMP` can give. With it Folark reads Android's
 * list of recent tasks to find the task number of the app it just opened, and sends the command.
 * Nothing is kept or sent anywhere.
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
    internal fun dump(context: Context, args: Array<String>): String? = runCatching {
        // A file, not a pipe: the system writes the whole reply before the call returns, and a
        // long reply would fill a pipe and stall both sides.
        val file = File(context.cacheDir, "shell_split.txt")
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
        val text = dump(context, arrayOf("recents"))
        if (text == null) return null
        // Each task starts at a "Recent #n" line; its number appears as "#123" there and/or as
        // "taskId=123" / "id=123" further down. The package shows up in its intent, activity or
        // affinity, in whichever format this Android version prints.
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
    data class SplitTask(val taskId: Int, val packageName: String)

    /**
     * The tasks in split screen now, from Android's window hierarchy: the visible tasks inside the
     * split stages (stage_a, stage_b, and Motorola's stage_c for three apps). Empty when no split
     * screen is open or the dump is refused.
     */
    fun splitTasks(context: Context): List<SplitTask> {
        val text = dump(context, arrayOf("activities")) ?: return emptyList()
        val found = LinkedHashMap<Int, SplitTask>()
        var stageIndent = -1
        for (line in text.lineSequence()) {
            val star = line.indexOf("* Task{")
            if (star < 0) continue
            if (line.contains("name=stage_")) {
                stageIndent = star
                continue
            }
            if (stageIndent < 0) continue
            if (star <= stageIndent) {
                stageIndent = -1
                continue
            }
            if (!line.contains("visible=true") && !line.contains("visibleRequested=true")) continue
            val id = Regex("""Task\{\S+ #(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val pkg = Regex("""\bA=\d+:([\w.]+)""").find(line)?.groupValues?.get(1)
                ?: Regex("""\bI=([\w.]+)/""").find(line)?.groupValues?.get(1)
                ?: continue
            found.putIfAbsent(id, SplitTask(id, pkg))
        }
        return found.values.toList()
    }

    /** Swaps the two sides of the split screen. */
    fun swapSides(context: Context): Boolean = wmShell(context, "splitscreen", "switchSplitPosition")

    /** Ends split screen, leaving task [keepTaskId] full screen. */
    fun exitSplit(context: Context, keepTaskId: Int): Boolean =
        wmShell(context, "splitscreen", "exitSplitScreen", keepTaskId.toString())

    /** Android's own "Clear all" of Recents. */
    fun clearAllRecents(context: Context): Boolean = wmShell(context, "recents", "clearAll")

    private fun wmShell(context: Context, vararg command: String): Boolean =
        dump(context, arrayOf("service", "SystemUIService", "WMShell", *command)) != null

    /** Moves task [taskId] into the side half, [right] or left, beside the app behind it. */
    fun moveToSide(context: Context, taskId: Int, right: Boolean): Boolean {
        // WM Shell's moveToStage throws IllegalArgumentException("taskId is in split<id>") when the
        // task is ALREADY a split member — and it throws on WM Shell's own HandlerThread inside
        // System UI, where Folark cannot catch it, so System UI is killed (drops to the lock screen).
        // Never re-issue the command for a task that is already in the split: it is already placed.
        if (splitTasks(context).any { it.taskId == taskId }) return true
        dump(
            context,
            arrayOf("service", "SystemUIService", "WMShell", "splitscreen", "moveToSideStage", taskId.toString(), if (right) "1" else "0"),
        ) ?: return false
        return true
    }
}
