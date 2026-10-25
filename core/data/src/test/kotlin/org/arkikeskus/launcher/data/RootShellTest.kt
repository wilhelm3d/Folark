package org.arkikeskus.launcher.data

import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.data.root.RootShell
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * [RootShell]'s session logic (markers, big outputs, failures, timeouts) run against a plain `sh`
 * instead of `su`, so it needs no root. Skipped where there is no `/bin/sh` (a Windows PC without
 * Git Bash / WSL).
 */
class RootShellTest {

    @Before fun useSh() {
        assumeTrue(File("/bin/sh").canExecute())
        RootShell.close()
        RootShell.binary = "/bin/sh"
        RootShell.enabled = true
    }

    @After fun back() {
        RootShell.close()
        RootShell.binary = "su"
    }

    @Test fun `a big output comes back whole and the next command is clean`() {
        val big = RootShell.runBytes("head -c 524288 /dev/zero | tr '\\0' 'A'")
        assertThat(big).isNotNull()
        assertThat(big!!.size).isEqualTo(512 * 1024)
        assertThat(big.all { it == 'A'.code.toByte() }).isTrue()
        assertThat(RootShell.run("echo clean_pipe")).isEqualTo("clean_pipe\n")
    }

    @Test fun `the end marker is found wherever the pipe splits it`() {
        // Sizes around the 64 KB read size, so the marker lands across two reads in some of them.
        for (size in 65_500..65_560 step 3) {
            val out = RootShell.runBytes("head -c $size /dev/zero | tr '\\0' 'B'")
            assertThat(out?.size).isEqualTo(size)
        }
    }

    @Test fun `a failing command gives null and the shell keeps working`() {
        assertThat(RootShell.run("exit 3")).isNull()
        assertThat(RootShell.run("false")).isNull()
        assertThat(RootShell.run("echo ok")).isEqualTo("ok\n")
    }

    @Test fun `a command that hangs times out and the next one gets a fresh shell`() {
        val started = System.currentTimeMillis()
        assertThat(RootShell.run("sleep 5; echo late", timeoutMs = 300)).isNull()
        assertThat(System.currentTimeMillis() - started).isLessThan(2_000L)
        // Nothing of the old command leaks into this one.
        assertThat(RootShell.run("echo next")).isEqualTo("next\n")
    }

    @Test fun `a command cannot change the next one`() {
        assertThat(RootShell.run("cd /; X=1; echo \$X")).isEqualTo("1\n")
        assertThat(RootShell.run("echo \"[\$X]\"")).isEqualTo("[]\n")
    }

    @Test fun `switched off it never starts a shell`() {
        RootShell.enabled = false
        assertThat(RootShell.run("echo hi")).isNull()
        RootShell.enabled = true
    }
}
