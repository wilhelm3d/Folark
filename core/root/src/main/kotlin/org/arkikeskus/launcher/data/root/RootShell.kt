package org.arkikeskus.launcher.data.root

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.arkikeskus.launcher.model.catching

/**
 * Root through the phone's `su` (KernelSU, Magisk and the like), for the few things only root can
 * do (Settings ▸ Recents and Settings ▸ Root). Root may be there only until the next reboot, so it
 * is checked again from time to time instead of being assumed.
 *
 * Two `su` shells are kept open while Folark is using root and commands are written into them, so
 * a command costs a few milliseconds instead of starting `su` again (50–200 ms each): one for quick
 * commands, one for slow jobs, so a 20-second screenshot job never holds up locking the screen. The shell is
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

    /**
     * Whether root answers right now (the root manager may ask the first time). [prompt]: a person
     * just tapped something that needs root and the root manager's question may be on screen, so
     * it is given [PROMPT_TIMEOUT_MS] to be answered (a timeout closes the shell, and with it the
     * question: allowing root after that would do nothing).
     */
    fun available(recheck: Boolean = false, prompt: Boolean = false): Boolean {
        if (!enabled) return false
        val now = System.currentTimeMillis()
        val cached = known
        if (!recheck && !prompt && cached != null && now - checkedAt < RECHECK_MS) return cached
        val timeout = if (prompt) PROMPT_TIMEOUT_MS else CHECK_TIMEOUT_MS
        val ok = catching { run("id", timeout)?.contains("uid=0") == true }.getOrDefault(false)
        known = ok
        checkedAt = now
        return ok
    }

    /** The last answer of [available] without asking `su` again; null before the first check. */
    val lastKnown: Boolean? get() = known

    /**
     * Runs [command] as root and returns what it printed, or null when root refused, it failed or
     * timed out. [bulk]: a slow job (screenshots, the task helper, a root prompt) that runs in a
     * shell of its own, so quick commands (lock, recents, a grant) never queue behind it; by default
     * every command allowed more than [QUICK_MAX_MS] counts as one.
     */
    fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS, bulk: Boolean = timeoutMs > QUICK_MAX_MS): String? =
        runBytes(command, timeoutMs, bulk)?.toString(Charsets.UTF_8)

    /** Like [run], for binary output (a picture). */
    fun runBytes(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS, bulk: Boolean = timeoutMs > QUICK_MAX_MS): ByteArray? {
        if (!enabled) return null
        return (if (bulk) bulkLane else quickLane).run(command, timeoutMs)
    }

    /** The shell started for root. Tests on a PC set it to `sh` to run the same code without root. */
    @Volatile internal var binary: String = "su"

    /** Closes the shells (they are opened again by the next command). */
    fun close() {
        quickLane.close()
        bulkLane.close()
    }

    private val timer = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "folark-root-idle").apply { isDaemon = true }
    }

    /** One `su` shell and the commands queued for it, one at a time. */
    private class Lane {
        private val lock = Any()
        private var session: Session? = null
        private var idleClose: ScheduledFuture<*>? = null

        fun run(command: String, timeoutMs: Long): ByteArray? = synchronized(lock) {
            val shell = session ?: Session.start()?.also { session = it } ?: return null
            val result = shell.exec(command, timeoutMs)
            if (!shell.alive) {
                shell.destroy()
                session = null
            }
            idleClose?.cancel(false)
            idleClose = timer.schedule({ close() }, IDLE_CLOSE_MS, TimeUnit.MILLISECONDS)
            result
        }

        fun close() {
            synchronized(lock) {
                session?.destroy()
                session = null
            }
        }
    }

    private val quickLane = Lane()
    private val bulkLane = Lane()

    /** One open `su` shell: its input, and everything it prints collected by a reader thread. */
    private class Session(private val process: Process) {
        private val input: OutputStream = process.outputStream
        private val buffer = Output()
        // A plain monitor for wait()/notify(), which Kotlin's Any does not offer.
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private val signal = Object()
        @Volatile var alive = true
            private set

        init {
            Thread({
                val chunk = ByteArray(64 * 1024)
                catching {
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
            Thread({ catching { process.errorStream.use { it.copyTo(NullOutput) } } }, "folark-root-err")
                .apply { isDaemon = true }.start()
        }

        /**
         * Writes [command] and waits for its marker line. Null when it failed (exit code not 0),
         * timed out (the shell is then thrown away) or the shell ended.
         */
        fun exec(command: String, timeoutMs: Long): ByteArray? {
            val marker = "__folark_" + UUID.randomUUID().toString().replace("-", "") + "__"
            synchronized(signal) { buffer.reset() }
            val script = wrap(command, marker, timeoutMs)
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
            // Close the pipe first: a command still writing dies of a broken pipe on its own.
            catching { input.close() }
            // SIGTERM, then SIGKILL after a grace: SIGKILL cannot be ignored by userspace, so the
            // only process that survives it is one the kernel can't stop either — and every
            // command is `</dev/null` under a watchdog anyway (see [wrap]), so nothing is left
            // holding Folark's pipe.
            catching { process.destroy() }
            val killer = Thread({
                catching {
                    Thread.sleep(500)
                    process.destroyForcibly()
                }
            }, "folark-root-kill")
            killer.isDaemon = true
            killer.start()
        }

        companion object {
            /**
             * The shell text that runs [command] and then prints [marker] with its exit code.
             *
             * The command runs in a subshell of its own, in the background, with a watchdog that
             * kills it (and the programs it started) once [timeoutMs] is over: even if this
             * session lingers, nothing keeps Folark's pipe. Not `timeout <command>`: `timeout`
             * runs one program, so commands starting with `if`, `for`, `X=…` or `f=$(…)` failed,
             * and a syntax error took the whole shell down. A killed command ends with a non-zero
             * code, which reads as any failure.
             */
            internal fun wrap(command: String, marker: String, timeoutMs: Long): String {
                val seconds = ((timeoutMs + 999) / 1000).coerceAtLeast(1)
                return "( $command\n) </dev/null & __fp=\$!; " +
                    "( sleep $seconds; pkill -9 -P \$__fp; kill -9 \$__fp ) </dev/null >/dev/null 2>&1 & __fw=\$!; " +
                    "wait \$__fp; __rc=\$?; pkill -P \$__fw 2>/dev/null; kill \$__fw 2>/dev/null; echo; echo \"$marker:\$__rc\"\n"
            }

            fun start(): Session? = catching {
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
    private const val PROMPT_TIMEOUT_MS = 60_000L
    private const val DEFAULT_TIMEOUT_MS = 5_000L
    private const val IDLE_CLOSE_MS = 30_000L
    /** Commands allowed longer than this run in the bulk shell (see [run]). */
    private const val QUICK_MAX_MS = 8_000L
}
