package org.arkikeskus.launcher.data

import android.content.Context
import org.arkikeskus.launcher.data.root.RootShell
import org.arkikeskus.launcher.model.LauncherSettings
import java.text.DateFormat
import java.util.Date

/**
 * Settings ▸ Recents ▸ Split screen ▸ Check split screen: every part split screen depends on, what
 * it answered, and which way a split would be made right now. Blocking (it reads the window list
 * and may ask `su`): call it off the main thread.
 */
object SplitDiagnostics {

    fun report(context: Context, engine: Int): String = buildString {
        appendLine("Folark split screen check")
        appendLine("Engine: " + engineName(engine))
        val dump = ShellSplit.hasDump(context)
        appendLine("1. ADB grant (DUMP): " + if (dump) "yes" else "NO")
        appendLine("2. Usage access: " + if (ShellSplit.hasUsageAccess(context)) "yes" else "no (only the taskbar's recent apps need it)")
        val root = RootShell.available(recheck = true)
        appendLine("3. Root: " + when {
            !RootShell.enabled -> "off (Settings ▸ Root)"
            root -> "yes"
            else -> "no"
        })
        val readyAt = SplitThree.readyAt(context)
        appendLine("4. Vector module in System UI: " + if (SplitThree.running(context)) "running (System UI started ${time(readyAt)})" else "not seen")
        appendLine("5. Split events from System UI: " + if (SplitEvents.live) {
            "connected ${time(SplitEvents.attachedAt)}, last ${time(SplitEvents.lastEventAt)}"
        } else {
            "not connected (Folark reads the window list instead)"
        })
        val paused = SplitThree.pausedInfo(context)
        appendLine("6. Native split: " + if (paused == null) "allowed" else {
            "PAUSED: System UI restarted ${time(paused.first)}, right after a ${paused.second ?: "split"} request"
        })
        val route = SplitRoute.resolve(context, engine)
        appendLine("7. A split is made now by: " + when {
            route.native && route.commands -> "the module, else the commands" + if (route.root) " (through root)" else ""
            route.native -> "the module"
            route.commands -> "the commands" + if (route.root) " (through root)" else ""
            else -> "NOTHING: give the ADB grant, or allow root"
        })
        val read = if (dump || root) ShellSplit.snapshot(context, root = !dump) else null
        if (read == null) {
            appendLine("8. Window list: can't be read")
        } else {
            appendLine("8. Window list: ${read.tasks.size} app tasks")
            if (dump) OpenTasks.list(context)
            OpenTasks.formatProblem?.let { appendLine("   RECENT TASKS NOT UNDERSTOOD: $it (this Android build prints them differently)") }
            read.frontApp()?.let { appendLine("   in front: ${it.packageName} #${it.id}") }
            val split = read.splitShowing.ifEmpty { read.splitStaged }
            appendLine("   split: " + if (split.isEmpty()) "none" else {
                split.joinToString(" + ") { "${it.packageName} #${it.id} (${it.stage})" } +
                    if (read.splitShowing.isEmpty()) ", hidden" else ", showing"
            })
        }
        val answer = SplitThree.lastAnswer(context)
        appendLine("9. Last System UI answer: " + (answer?.let { a ->
            val at = a.substringAfterLast(" at ").toLongOrNull()
            if (at != null) a.substringBeforeLast(" at ") + " " + time(at) else a
        } ?: "none yet"))
    }

    private fun engineName(engine: Int) = when (engine) {
        LauncherSettings.SPLIT_ENGINE_CLASSIC -> "Commands"
        LauncherSettings.SPLIT_ENGINE_LSPOSED -> "Native"
        LauncherSettings.SPLIT_ENGINE_ROOT -> "Root"
        else -> "Automatic"
    }

    private fun time(ms: Long): String =
        if (ms <= 0L) "-" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(ms))
}
