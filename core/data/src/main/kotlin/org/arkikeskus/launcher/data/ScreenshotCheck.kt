package org.arkikeskus.launcher.data

import android.content.Context
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.data.root.RootTasks
import org.arkikeskus.launcher.model.catching

/**
 * Settings ▸ Recents ▸ Check screenshots: every step of getting an app's screenshot, and what each
 * one answered, as text to read or copy. It lives here, not with [RootTasks], because it also asks
 * for the open apps ([OpenTasks]).
 */
object ScreenshotCheck {

    /** The report. Blocking: call off the main thread. */
    fun run(context: Context): String = buildString {
        appendLine("Folark screenshot check")
        appendLine("Root switch (Settings ▸ Root): " + if (RootShell.enabled) "on" else "OFF")
        val rootOk = RootShell.available(recheck = true)
        appendLine("1. Root: " + if (rootOk) "yes" else "NO (su refused or missing)")
        appendLine("   id: " + (RootShell.run("id")?.trim() ?: "-"))
        val tasks = OpenTasks.list(context)
        if (tasks == null) {
            appendLine("2. Open apps: CAN'T READ (no DUMP grant)")
            return@buildString
        }
        appendLine("2. Open apps: ${tasks.size}")
        tasks.take(4).forEach { appendLine("   #${it.taskId} ${it.packageName} user ${it.userId}") }
        if (!rootOk) return@buildString
        val first = tasks.firstOrNull() ?: return@buildString
        val dir = "/data/system_ce/${first.userId}/snapshots"
        appendLine("3. Folder $dir:")
        val listing = RootShell.run("ls -la $dir 2>&1 | head -8; echo '   inside:'; find $dir -mindepth 2 -maxdepth 3 2>&1 | head -12; echo \"   files for #${first.taskId}:\"; find $dir -name '${first.taskId}*' 2>&1 | head -6")
        appendLine(listing?.trimEnd()?.prependIndent("   ") ?: "   (no answer)")
        val file = RootTasks.snapshot(first.taskId, first.userId)
        appendLine("4. File for #${first.taskId}: " + (file?.let { "${it.size} bytes" } ?: "none"))
        val fromSystem = RootTasks.snapshotsFromSystem(context, listOf(first.taskId))
        appendLine("5. From the task manager: " + (fromSystem[first.taskId]?.let { "${it.size} bytes" } ?: "none"))
        val log = catching { java.io.File(java.io.File(context.cacheDir, "recents_shots"), "helper.log").readText() }.getOrNull()
        if (!log.isNullOrBlank()) appendLine("   helper said:\n" + log.trim().lines().take(12).joinToString("\n") { "   $it" })
        val decoded = (file ?: fromSystem[first.taskId])?.let {
            android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
        }
        appendLine("6. Picture: " + (decoded?.let { "${it.width}×${it.height}" } ?: "can't be read"))
    }
}
