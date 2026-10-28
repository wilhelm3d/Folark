package org.arkikeskus.launcher.data.root

import android.content.Context

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
        return RootShell.runBytes(snapshotCommand(taskId, userId))?.takeIf { it.size > MIN_JPEG_BYTES }
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
        if ((apk + path).any { it == '\'' || it == '"' || it == '`' || it == '$' || it == ' ' }) return emptyMap()
        // What the helper says when something fails is kept for Settings ▸ Recents ▸ Check screenshots.
        RootShell.run(
            "CLASSPATH='$apk' app_process /system/bin ${RootMain::class.java.name} snapshots '$path' ${ids.joinToString(" ")} 2>'$path/helper.log'; chmod 644 '$path/helper.log'",
            SNAPSHOTS_TIMEOUT_MS,
        ) ?: return emptyMap()
        val out = HashMap<Int, ByteArray>()
        for (id in ids) {
            val file = java.io.File(dir, "$id.jpg")
            runCatching { if (file.length() > MIN_JPEG_BYTES) out[id] = file.readBytes() }
            file.delete()
        }
        return out
    }

    /**
     * Settings ▸ Recents ▸ Check screenshots: every step of getting an app's screenshot, and what
     * each one answered, as text to read or copy. Blocking: call off the main thread.
     */
    fun diagnose(context: Context): String = buildString {
        appendLine("Folark screenshot check")
        appendLine("Root switch (Settings ▸ Root): " + if (RootShell.enabled) "on" else "OFF")
        val rootOk = RootShell.available(recheck = true)
        appendLine("1. Root: " + if (rootOk) "yes" else "NO (su refused or missing)")
        appendLine("   id: " + (RootShell.run("id")?.trim() ?: "-"))
        val tasks = org.arkikeskus.launcher.data.OpenTasks.list(context)
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
        val file = snapshot(first.taskId, first.userId)
        appendLine("4. File for #${first.taskId}: " + (file?.let { "${it.size} bytes" } ?: "none"))
        val fromSystem = snapshotsFromSystem(context, listOf(first.taskId))
        appendLine("5. From the task manager: " + (fromSystem[first.taskId]?.let { "${it.size} bytes" } ?: "none"))
        val log = runCatching { java.io.File(java.io.File(context.cacheDir, "recents_shots"), "helper.log").readText() }.getOrNull()
        if (!log.isNullOrBlank()) appendLine("   helper said:\n" + log.trim().lines().take(12).joinToString("\n") { "   $it" })
        val decoded = (file ?: fromSystem[first.taskId])?.let {
            android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
        }
        appendLine("6. Picture: " + (decoded?.let { "${it.width}×${it.height}" } ?: "can't be read"))
    }

    /**
     * Removes task [taskId] from Recents, as swiping its card away would: a small helper in Folark's
     * own package runs as root and asks Android's task manager directly ([RootMain]).
     */
    fun removeTask(context: Context, taskId: Int): Boolean {
        if (taskId < 0) return false
        val apk = context.applicationInfo.sourceDir ?: return false
        if (apk.any { it == '\'' || it == '"' || it == '`' || it == '$' }) return false
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
        if (apk.any { it == '\'' || it == '"' || it == '`' || it == '$' }) return false
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
        val safe = packages.filter { PACKAGE.matches(it) }
        if (safe.isEmpty()) return emptySet()
        val script = safe.joinToString("; ") { p ->
            "if ! cmd deviceidle whitelist | grep -q \",$p,\"; then cmd deviceidle whitelist +$p >/dev/null 2>&1 && echo added:$p; fi; " +
                "am set-standby-bucket $p active >/dev/null 2>&1; " +
                "for pid in \$(pidof $p); do echo $KEEP_ALIVE_ADJ > /proc/\$pid/oom_score_adj; done"
        }
        val out = RootShell.run(script, PROTECT_TIMEOUT_MS) ?: return emptySet()
        return out.lineSequence().filter { it.startsWith("added:") }.map { it.removePrefix("added:").trim() }.toSet()
    }

    /** Takes back what [protect] did for [packageName]; [exempted]: Folark added its exemption. */
    fun unprotect(packageName: String, exempted: Boolean) {
        if (!PACKAGE.matches(packageName)) return
        val script = buildString {
            if (exempted) append("cmd deviceidle whitelist -$packageName >/dev/null 2>&1; ")
            append("for pid in \$(pidof $packageName); do echo 0 > /proc/\$pid/oom_score_adj; done")
        }
        RootShell.run(script)
    }

    /** Stops [packageName] completely (its tasks stay listed until Android tidies them up). */
    /** Uses am start to launch two packages into split screen (ROOT). */
    fun launchSplitPair(pkg1: String, pkg2: String): Boolean {
        if (!PACKAGE.matches(pkg1) || !PACKAGE.matches(pkg2)) return false
        // 3 = WINDOWING_MODE_SPLIT_SCREEN_PRIMARY
        // 4 = WINDOWING_MODE_SPLIT_SCREEN_SECONDARY
        val script = "am start -n $pkg1 --windowingMode 3; sleep 0.3; am start -n $pkg2 --windowingMode 4"
        return RootShell.run(script) != null
    }

    fun forceStop(packageName: String): Boolean {
        if (!PACKAGE.matches(packageName)) return false
        return RootShell.run("am force-stop $packageName") != null
    }

    private val PACKAGE = Regex("^[A-Za-z0-9_.]+$")
    private const val MIN_JPEG_BYTES = 64
    private const val HELPER_TIMEOUT_MS = 10_000L
    private const val SNAPSHOTS_TIMEOUT_MS = 20_000L
    private const val PROTECT_TIMEOUT_MS = 8_000L
    /** The priority Android gives its own persistent services: far from the first to be closed. */
    private const val KEEP_ALIVE_ADJ = -800
}
