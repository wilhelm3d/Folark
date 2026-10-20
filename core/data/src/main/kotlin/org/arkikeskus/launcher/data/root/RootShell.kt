package org.arkikeskus.launcher.data.root

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Root through the phone's `su` (KernelSU, Magisk and the like), for the few things only root can
 * do (Settings ▸ Recents: thumbnails and closing single apps). Root may be there only until the
 * next reboot, so it is checked again from time to time instead of being assumed.
 *
 * Every command is fixed text built inside Folark from numbers and package names it read itself;
 * nothing a person types ever reaches a root shell. Blocking: call off the main thread.
 */
object RootShell {
    @Volatile private var known: Boolean? = null
    @Volatile private var checkedAt = 0L

    /** Settings ▸ Root ▸ Use root: off, Folark never runs `su` at all. */
    @Volatile var enabled: Boolean = true

    /** Whether the last "no root" answer is old enough to ask again (root may be back). */
    fun recheckDue(): Boolean = System.currentTimeMillis() - checkedAt >= RECHECK_MS

    /** Whether root answers right now (the root manager may ask the first time). */
    fun available(recheck: Boolean = false): Boolean {
        if (!enabled) return false
        val now = System.currentTimeMillis()
        val cached = known
        if (!recheck && cached != null && now - checkedAt < RECHECK_MS) return cached
        val ok = runCatching { run("id", CHECK_TIMEOUT_MS)?.contains("uid=0") == true }.getOrDefault(false)
        known = ok
        checkedAt = now
        return ok
    }

    /** The last answer of [available] without asking `su` again; null before the first check. */
    val lastKnown: Boolean? get() = known

    /** Runs [command] as root and returns what it printed, or null when root refused or timed out. */
    fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? =
        runBytes(command, timeoutMs)?.toString(Charsets.UTF_8)

    /** Like [run], for binary output (a picture). */
    fun runBytes(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ByteArray? = if (!enabled) null else runCatching {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(false).start()
        val out = ByteArrayOutputStream()
        // Read in the background: a big answer would otherwise fill the pipe and stall both sides.
        val reader = Thread {
            runCatching { process.inputStream.use { it.copyTo(out) } }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            return@runCatching null
        }
        reader.join(READER_JOIN_MS)
        if (process.exitValue() != 0) null else out.toByteArray()
    }.getOrNull()

    private const val RECHECK_MS = 60_000L
    private const val CHECK_TIMEOUT_MS = 8_000L
    private const val DEFAULT_TIMEOUT_MS = 5_000L
    private const val READER_JOIN_MS = 2_000L
}
