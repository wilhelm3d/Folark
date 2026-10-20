package org.arkikeskus.launcher.data.root

import android.content.Context

/**
 * What root adds to Folark's Recents: Android's own saved screenshot of each open app (the one the
 * system Recents shows), and closing a single app the way swiping its card away does.
 */
object RootTasks {

    /** The saved screenshot of task [taskId] (JPEG bytes, the smaller copy first), or null. */
    fun snapshot(taskId: Int, userId: Int): ByteArray? {
        if (taskId < 0 || userId < 0) return null
        val dir = "/data/system_ce/$userId/snapshots"
        return RootShell.runBytes("cat $dir/${taskId}_reduced.jpg 2>/dev/null || cat $dir/$taskId.jpg")
            ?.takeIf { it.size > MIN_JPEG_BYTES }
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

    /** Stops [packageName] completely (its tasks stay listed until Android tidies them up). */
    fun forceStop(packageName: String): Boolean {
        if (!PACKAGE.matches(packageName)) return false
        return RootShell.run("am force-stop $packageName") != null
    }

    private val PACKAGE = Regex("^[A-Za-z0-9_.]+$")
    private const val MIN_JPEG_BYTES = 64
    private const val HELPER_TIMEOUT_MS = 10_000L
}
