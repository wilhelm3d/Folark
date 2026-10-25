package org.arkikeskus.launcher.data.root

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Root through the phone's `su` (KernelSU, Magisk and the like), for the few things only root can
 * do (Settings ▸ Recents and Settings ▸ Root). Root may be there only until the next reboot, so it
 * is checked again from time to time instead of being assumed.
 *
 * One `su` shell is kept open while Folark is using root and commands are written into it, so a
 * command costs a few milliseconds instead of starting `su` again (50–200 ms each). The shell is
 * closed after [IDLE_CLOSE_MS] without commands. Each command runs in its own subshell (so nothing
 * it does can change the next one), with no input, and its output ends at a random marker line
 * carrying its exit code.
 *
 * Every command is fixed text built inside Folark from numbers and package names it read itself;
 * nothing a person types ever reaches a root shell. Blocking: call off the main thread.
 */
object RootShell {
    @Volatile private var known: Boolean? = null
    @Volatile private var checkedAt = 0L

    /** Settings ▸ Root ▸ Use root: off, Folark never runs `su` at all (and closes an open shell). */
    @Volatile var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) close()
        }

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

    /** Runs [command] as root and returns what it printed, or null when root refused, it failed or timed out. */
    fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? =
        runBytes(command, timeoutMs)?.toString(Charsets.UTF_8)

    /** Like [run], for binary output (a picture). */
    fun runBytes(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ByteArray? {
        if (!enabled) return null
        synchronized(lock) {
            val shell = session ?: Session.start()?.also { session = it } ?: return null
            val result = shell.exec(command, timeoutMs)
            if (!shell.alive) {
                shell.destroy()
                session = null
            }
            scheduleIdleClose()
            return result
        }
    }

    /** The shell started for root. Tests on a PC set it to `sh` to run the same code without root. */
    @Volatile internal var binary: String = "su"

    /** Closes the shell (it is opened again by the next command). */
    fun close() {
        synchronized(lock) {
            session?.destroy()
            session = null
        }
    }

    private val lock = Any()
    private var session: Session? = null

    private val timer = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "folark-root-idle").apply { isDaemon = true }
    }
    private var idleClose: ScheduledFuture<*>? = null

    private fun scheduleIdleClose() {
        idleClose?.cancel(false)
        idleClose = timer.schedule({ close() }, IDLE_CLOSE_MS, TimeUnit.MILLISECONDS)
    }

    /** One open `su` shell: its input, and everything it prints collected by a reader thread. */
    private class Session(private val process: Process) {
        private val input: OutputStream = process.outputStream
        private val buffer = Output()
        private val signal = Object()
        @Volatile var alive = true
            private set

        init {
            Thread({
                val chunk = ByteArray(64 * 1024)
                runCatching {
                    process.inputStream.use { stream ->
                        while (true) {
                            val n = stream.read(chunk)
                            if (n < 0) break
                            synchronized(signal) {
                                buffer.write(chunk, 0, n)
                                signal.notifyAll()
                            }
                        }
                    }
                }
                synchronized(signal) {
                    alive = false
                    signal.notifyAll()
                }
            }, "folark-root-out").apply { isDaemon = true }.start()
            // Nothing reads Android's warnings; drain them so the shell never stalls on a full pipe.
            Thread({ runCatching { process.errorStream.use { it.copyTo(NullOutput) } } }, "folark-root-err")
                .apply { isDaemon = true }.start()
        }

        /**
         * Writes [command] and waits for its marker line. Null when it failed (exit code not 0),
         * timed out (the shell is then thrown away) or the shell ended.
         */
        fun exec(command: String, timeoutMs: Long): ByteArray? {
            val marker = "__folark_" + UUID.randomUUID().toString().replace("-", "") + "__"
            synchronized(signal) { buffer.reset() }
            val script = "( $command\n) </dev/null; __rc=\$?; echo; echo \"$marker:\$__rc\"\n"
            try {
                input.write(script.toByteArray())
                input.flush()
            } catch (e: IOException) {
                alive = false
                return null
            }
            val needle = "\n$marker:".toByteArray()
            val deadline = System.currentTimeMillis() + timeoutMs
            synchronized(signal) {
                while (true) {
                    // Looks only at the end of what has come in (no copy of a big output per chunk).
                    val bytes = buffer.raw()
                    val size = buffer.size()
                    val at = lastIndexOf(bytes, size, needle)
                    if (at >= 0) {
                        var end = at + needle.size
                        while (end < size && bytes[end] != NEWLINE) end++
                        if (end < size) {
                            val code = String(bytes, at + needle.size, end - at - needle.size).trim().toIntOrNull()
                            // The output, without the blank line echoed before the marker.
                            val out = if (code == 0) bytes.copyOfRange(0, at) else null
                            buffer.reset()
                            return out
                        }
                    }
                    if (!alive) return null
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0) {
                        alive = false
                        return null
                    }
                    signal.wait(left)
                }
            }
        }

        fun destroy() {
            alive = false
            runCatching { input.close() }
            process.destroy()
        }

        companion object {
            fun start(): Session? = runCatching {
                Session(ProcessBuilder(binary).redirectErrorStream(false).start())
            }.getOrNull()

            private fun lastIndexOf(bytes: ByteArray, size: Int, needle: ByteArray): Int {
                // The marker is at the very end: look back from there only.
                val from = size - needle.size
                val stop = maxOf(0, size - needle.size - MARKER_TAIL)
                var i = from
                while (i >= stop) {
                    var match = true
                    for (j in needle.indices) if (bytes[i + j] != needle[j]) { match = false; break }
                    if (match) return i
                    i--
                }
                return -1
            }

            private const val MARKER_TAIL = 32
            private const val NEWLINE = '\n'.code.toByte()
        }
    }

    /** The shell's output so far; [raw] is the live array (valid up to [size]), not a copy. */
    private class Output : ByteArrayOutputStream(64 * 1024) {
        fun raw(): ByteArray = buf
    }

    private object NullOutput : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private const val RECHECK_MS = 60_000L
    private const val CHECK_TIMEOUT_MS = 8_000L
    private const val DEFAULT_TIMEOUT_MS = 5_000L
    private const val IDLE_CLOSE_MS = 30_000L
}
