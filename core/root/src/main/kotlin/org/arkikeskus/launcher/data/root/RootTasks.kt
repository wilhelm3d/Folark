package org.arkikeskus.launcher.data.root

import android.content.Context
import org.arkikeskus.launcher.model.catching

/**
 * What root adds to Folark's Recents: Android's own saved screenshot of each open app (the one the
 * system Recents shows), and closing a single app the way swiping its card away does.
 */
object RootTasks {

    /**
     * The saved screenshot of task [taskId], or null. Android has kept them as `<id>_reduced.jpg`
     * and `<id>.jpg` (sometimes .png or .webp) in the user's snapshots folder, and on Android 17 in
     * a folder inside it: whichever is there, the smaller copy first.
     */
    fun snapshot(taskId: Int, userId: Int): ByteArray? {
        if (taskId < 0 || userId < 0) return null
        return RootShell.runBytes(snapshotCommand(taskId, userId), bulk = true)?.takeIf { it.size > MIN_JPEG_BYTES }
    }

    /**
     * The saved screenshots of [tasks] (task id to user id) in one root call (E12): each found
     * picture (the smaller copy first) is copied into Folark's cache, read and deleted. Tasks
     * without a picture are left out.
     */
    // One at a time: Recents and the taskbar share the folder the pictures are copied to.
    @Synchronized
    fun snapshots(context: Context, tasks: Map<Int, Int>): Map<Int, ByteArray> {
        val wanted = tasks.filter { (id, user) -> id >= 0 && user >= 0 }
        if (wanted.isEmpty()) return emptyMap()
        val dir = java.io.File(context.cacheDir, "recents_files").apply { mkdirs() }
        val out = dir.absolutePath
        if (!ShellSafe.isQuotable(out)) return emptyMap()
        dir.listFiles()?.forEach { it.delete() }
        val script = wanted.entries.groupBy({ it.value }, { it.key }).entries.joinToString(" ; ") { (user, ids) ->
            "find /data/system_ce/$user/snapshots -maxdepth 3 -type f 2>/dev/null | " +
                "grep -E '/(${ids.joinToString("|")})(_reduced)?\\.(jpg|png|webp)\$' | sort -r | " +
                "while read f; do b=\${f##*/}; id=\${b%%[._]*}; [ -e '$out'/\$id ] || cp \"\$f\" '$out'/\$id; done"
        } + " ; chmod 644 '$out'/* 2>/dev/null; echo done"
        RootShell.run(script, SNAPSHOTS_TIMEOUT_MS) ?: return emptyMap()
        val result = HashMap<Int, ByteArray>()
        for (id in wanted.keys) {
            val file = java.io.File(dir, id.toString())
            catching { if (file.length() > MIN_JPEG_BYTES) result[id] = file.readBytes() }
            file.delete()
        }
        return result
    }

    private fun snapshotCommand(taskId: Int, userId: Int): String {
        val dir = "/data/system_ce/$userId/snapshots"
        return "f=\$(find $dir -maxdepth 3 -type f 2>/dev/null | grep -E '/${taskId}(_reduced)?\\.(jpg|png|webp)\$' | sort -r | head -1); " +
            "[ -n \"\$f\" ] && cat \"\$f\""
    }

    /**
     * Android's screenshots of [taskIds], asked from Android's task manager (memory or disk, the
     * same pictures the system Recents shows) by the root helper in one go. Saved by the helper in
     * Folark's cache, read here and deleted at once.
     */
    fun snapshotsFromSystem(context: Context, taskIds: List<Int>): Map<Int, ByteArray> {
        val ids = taskIds.filter { it >= 0 }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val apk = context.applicationInfo.sourceDir ?: return emptyMap()
        val dir = java.io.File(context.cacheDir, "recents_shots").apply { mkdirs() }
        val path = dir.absolutePath
        if (!ShellSafe.isQuotable(apk + path)) return emptyMap()
        // What the helper says when something fails is kept for Settings ▸ Recents ▸ Check screenshots.
        RootShell.run(
            "CLASSPATH='$apk' app_process /system/bin ${RootMain::class.java.name} snapshots '$path' ${ids.joinToString(" ")} 2>'$path/helper.log'; chmod 644 '$path/helper.log'",
            SNAPSHOTS_TIMEOUT_MS,
        ) ?: return emptyMap()
        val out = HashMap<Int, ByteArray>()
        for (id in ids) {
            val file = java.io.File(dir, "$id.jpg")
            catching { if (file.length() > MIN_JPEG_BYTES) out[id] = file.readBytes() }
            file.delete()
        }
        return out
    }

    /**
     * Removes task [taskId] from Recents, as swiping its card away would: a small helper in Folark's
     * own package runs as root and asks Android's task manager directly ([RootMain]).
     */
    fun removeTask(context: Context, taskId: Int): Boolean {
        if (taskId < 0) return false
        val apk = context.applicationInfo.sourceDir ?: return false
        if (!ShellSafe.isQuotable(apk, allowSpaces = true)) return false
        val out = RootShell.run(
            "CLASSPATH='$apk' app_process /system/bin ${RootMain::class.java.name} remove-task $taskId",
            HELPER_TIMEOUT_MS,
        )
        return out?.contains(RootMain.OK) == true
    }

    /** Closes several tasks in one go (one helper start instead of one per task). */
    fun removeTasks(context: Context, taskIds: List<Int>): Boolean {
        val ids = taskIds.filter { it >= 0 }
        if (ids.isEmpty()) return true
        val apk = context.applicationInfo.sourceDir ?: return false
        if (!ShellSafe.isQuotable(apk, allowSpaces = true)) return false
        val out = RootShell.run(
            "CLASSPATH='$apk' app_process /system/bin ${RootMain::class.java.name} remove-tasks ${ids.joinToString(" ")}",
            HELPER_TIMEOUT_MS,
        )
        return out?.contains(RootMain.OK) == true
    }

    /**
     * Keeps [packages] in memory as far as Android allows: exempt from battery optimisation (only
     * where they weren't already; those are returned so the exemption can be taken back on unpin),
     * in the "active" standby bucket, and their running processes given a high priority (Android
     * resets that when the app changes state, so it is given again each time Recents opens).
     */
    fun protect(packages: Collection<String>): Set<String> {
        val safe = packages.filter { ShellSafe.isName(it) }
        if (safe.isEmpty()) return emptySet()
        val script = safe.joinToString("; ") { p ->
            "if ! cmd deviceidle whitelist | grep -q \",$p,\"; then cmd deviceidle whitelist +$p >/dev/null 2>&1 && echo added:$p; fi; " +
                "am set-standby-bucket $p active >/dev/null 2>&1; " +
                "for pid in \$(pidof $p); do f=/proc/\$pid/oom_score_adj; [ \"\$(cat \$f 2>/dev/null)\" -gt $KEEP_ALIVE_ADJ ] 2>/dev/null && echo $KEEP_ALIVE_ADJ > \$f || true; done"
        }
        val out = RootShell.run(script, PROTECT_TIMEOUT_MS) ?: return emptySet()
        return out.lineSequence().filter { it.startsWith("added:") }.map { it.removePrefix("added:").trim() }.toSet()
    }

    /** Takes back what [protect] did for [packageName]; [exempted]: Folark added its exemption. */
    fun unprotect(packageName: String, exempted: Boolean) {
        if (!ShellSafe.isName(packageName)) return
        val script = buildString {
            if (exempted) append("cmd deviceidle whitelist -$packageName >/dev/null 2>&1; ")
            // The priority itself is not written back: 0 would rank the app as if it were on screen.
            // Android works it out again at the app's next state change.
            append("true")
        }
        RootShell.run(script)
    }

    /**
     * How much memory (resident, KB) each of [packages] is using, all its processes together
     * ("pkg" and "pkg:service"), from one `ps` call. Empty without root.
     */
    fun memoryKb(packages: Collection<String>): Map<String, Long> {
        val wanted = packages.filter { ShellSafe.isName(it) }.toSet()
        if (wanted.isEmpty()) return emptyMap()
        val out = RootShell.run("ps -A -o RSS,NAME") ?: return emptyMap()
        return parseMemory(out, wanted)
    }

    /** [memoryKb]'s parsing: "RSS NAME" lines, summed per package in [wanted]. */
    internal fun parseMemory(psOutput: String, wanted: Set<String>): Map<String, Long> {
        val totals = HashMap<String, Long>()
        for (line in psOutput.lineSequence()) {
            val parts = line.trim().split(WHITESPACE, limit = 2)
            if (parts.size < 2) continue
            val kb = parts[0].toLongOrNull() ?: continue
            val pkg = parts[1].substringBefore(':')
            if (pkg in wanted) totals[pkg] = (totals[pkg] ?: 0L) + kb
        }
        return totals
    }

    /** Stops [packageName] completely (its tasks stay listed until Android tidies them up). */
    fun forceStop(packageName: String): Boolean {
        if (!ShellSafe.isName(packageName)) return false
        return RootShell.run("am force-stop $packageName") != null
    }

    private val WHITESPACE = Regex("\\s+")
    private const val MIN_JPEG_BYTES = 64
    private const val HELPER_TIMEOUT_MS = 10_000L
    private const val SNAPSHOTS_TIMEOUT_MS = 20_000L
    private const val PROTECT_TIMEOUT_MS = 8_000L
    /**
     * Android's "perceptible" priority (what a playing music app gets): pinned apps are closed only
     * after every cached and background app, but never before the app you are using. The old -800
     * (persistent system services) ranked them above the foreground app, so under memory pressure
     * Android closed what was on screen first.
     */
    private const val KEEP_ALIVE_ADJ = 200
}
