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

    // The shapes Folark's real root scripts have (RootTasks, SystemTaskbar, ModuleTools): they
    // must run as written, not as the name of a program (a `timeout <command>` prefix broke them).
    @Test fun `a command may start with a variable or a command substitution`() {
        assertThat(RootShell.run("f=\$(echo hi); [ -n \"\$f\" ] && echo \"got \$f\"")).isEqualTo("got hi\n")
        assertThat(RootShell.run("CLASSPATH=/x sh -c 'echo \$CLASSPATH'")).isEqualTo("/x\n")
    }

    @Test fun `a command may start with if or for`() {
        assertThat(RootShell.run("if true; then echo yes; fi")).isEqualTo("yes\n")
        assertThat(RootShell.run("for i in 1 2; do echo n\$i; done")).isEqualTo("n1\nn2\n")
        assertThat(RootShell.run("echo still")).isEqualTo("still\n")
    }

    @Test fun `a multi-line script runs whole`() {
        val script = """
            U=${'$'}(echo 7 | tr -cd '0-9')
            [ -n "${'$'}U" ] || U=0
            echo "user=${'$'}U"
        """.trimIndent()
        assertThat(RootShell.run(script)).isEqualTo("user=7\n")
    }

    @Test fun `switched off it never starts a shell`() {
        RootShell.enabled = false
        assertThat(RootShell.run("echo hi")).isNull()
        RootShell.enabled = true
    }
}
