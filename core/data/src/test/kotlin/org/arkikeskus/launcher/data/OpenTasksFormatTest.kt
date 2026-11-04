package org.arkikeskus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** [OpenTasks.formatProblem]: telling "nothing open" apart from "Android printed it differently". */
class OpenTasksFormatTest {

    private val own = "org.arkikeskus.launcher"

    private fun check(text: String): Pair<List<OpenTask>, String?> {
        val tasks = OpenTasks.parse(text.trimIndent(), own)
        return tasks to OpenTasks.formatProblem(text.trimIndent(), tasks)
    }

    @Test
    fun `a readable list has no problem`() {
        val (tasks, problem) = check(
            """
            RECENT TASKS (dumpsys activity recents)
              * Recent #0: Task{a1 #15520 type=standard A=10234:com.android.chrome U=0 visible=true mode=fullscreen}
                mActivityComponent=com.android.chrome/org.chromium.chrome.browser.ChromeTabbedActivity
            """,
        )
        assertEquals(1, tasks.size)
        assertNull(problem)
    }

    @Test
    fun `nothing open is not a problem`() {
        val (tasks, problem) = check(
            """
            RECENT TASKS (dumpsys activity recents)
              * Recent #0: Task{b1 #2 type=home A=10050:com.motorola.launcher3 U=0 mode=fullscreen}
            """,
        )
        assertEquals(0, tasks.size)
        assertNull(problem)
    }

    @Test
    fun `entries without a readable id or type are a problem`() {
        val (tasks, problem) = check(
            """
            RECENT TASKS (dumpsys activity recents)
              * Recent #0: TaskRecord[id:15520 pkg:com.android.chrome]
            """,
        )
        assertEquals(0, tasks.size)
        assertNotNull(problem)
    }

    @Test
    fun `tasks without Recent entries are a problem`() {
        val (_, problem) = check(
            """
            RECENT TASKS (dumpsys activity recents)
              recentTask taskId=15520 userId=0 cmp=com.android.chrome/.Main
            """,
        )
        assertNotNull(problem)
    }
}
