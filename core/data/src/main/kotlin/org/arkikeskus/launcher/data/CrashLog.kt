package org.arkikeskus.launcher.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.arkikeskus.launcher.model.catching

/**
 * A local, on-device crash log: an uncaught-exception handler appends to a small file, and
 * Settings ▸ System ▸ Crash log shows it (and clears it). Nothing leaves the phone — it replaces
 * the "adb logcat -b crash into a txt file" dance for the user's own debugging.
 */
object CrashLog {
    private const val FILE = "crash_log.txt"
    private const val MAX_BYTES = 48 * 1024
    private const val MAX_ENTRIES = 10

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            catching { append(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The log, oldest first, or an empty string when nothing has crashed. */
    fun read(context: Context): String = file(context).takeIf { it.exists() }?.readText().orEmpty()

    /** Empties the log. */
    fun clear(context: Context) {
        catching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun append(context: Context, thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = buildString {
            appendLine("— $stamp  (${thread.name})")
            appendLine(throwable.stackTraceToString().lineSequence().take(40).joinToString("\n"))
            appendLine()
        }
        val f = file(context)
        val kept = f.takeIf { it.exists() }?.readText().orEmpty()
        // Keep the newest [MAX_ENTRIES] entries by dropping from the (oldest) front.
        var lines = (kept + entry).split("— ")
        while (lines.size > MAX_ENTRIES + 1) lines = lines.drop(1)
        f.writeText(lines.joinToString("— ").takeLast(MAX_BYTES))
    }
}
