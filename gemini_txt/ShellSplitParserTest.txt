package org.arkikeskus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ShellSplitParser] against `dumpsys activity activities` text. The samples follow the layout the
 * parser reads (task lines `* Task{… #id …}`, split stages marked `stage_a` / `stage_b`, activities
 * `ActivityRecord{… u0 package/Activity tId}`); swap in dumps from the phone when a ROM changes it.
 */
class ShellSplitParserTest {

    private val own = "org.arkikeskus.launcher"

    private fun parse(text: String) = ShellSplitParser.parse(text.trimIndent(), own)

    /** Two apps in split screen, showing, over Folark's home. */
    private val splitShowing = """
        ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
        Display #0 (activities from top to bottom):
          * Task{a1 #301 type=undefined mode=multi-window visible=true}
            * Task{a2 #302 type=undefined stage_a mode=multi-window visible=true}
              * Task{a3 #15520 type=standard A=10234:com.android.chrome U=0 visible=true visibleRequested=true mode=multi-window}
                * Hist  #0: ActivityRecord{9f8e7d u0 com.android.chrome/org.chromium.chrome.browser.ChromeTabbedActivity t15520}
            * Task{a4 #303 type=undefined stage_b mode=multi-window visible=true}
              * Task{a5 #15530 type=standard A=10111:com.google.android.apps.maps U=0 visible=true mode=multi-window}
                * Hist  #0: ActivityRecord{8e7d6c u0 com.google.android.apps.maps/com.google.android.maps.MapsActivity t15530}
          * Task{b1 #1 type=home A=10100:org.arkikeskus.launcher U=0 visible=false mode=fullscreen}
            * Hist  #0: ActivityRecord{7d6c5b u0 org.arkikeskus.launcher/.LauncherActivity t1}
    """

    @Test
    fun `split showing - both apps, in their stages`() {
        val s = parse(splitShowing)
        assertEquals(listOf(15520, 15530), s.splitShowing.map { it.id })
        assertEquals(listOf("stage_a", "stage_b"), s.splitShowing.map { it.stage })
        assertTrue(s.isStaged(15520) && s.isStaged(15530))
        assertFalse(s.isStaged(1))
        assertEquals("com.android.chrome", s.splitShowing[0].packageName)
    }

    @Test
    fun `container tasks are not apps`() {
        val s = parse(splitShowing)
        assertFalse(s.isRunning(301))
        assertFalse(s.isRunning(302))
        assertTrue(s.isRunning(15520))
    }

    @Test
    fun `hidden split - staged but not showing`() {
        val s = parse(splitShowing.replace("visible=true", "visible=false").replace("visibleRequested=true", "visibleRequested=false"))
        assertTrue(s.splitShowing.isEmpty())
        assertEquals(listOf(15520, 15530), s.splitStaged.map { it.id })
    }

    @Test
    fun `a package name must match whole, not as a prefix`() {
        val s = parse(
            """
            Display #0 (activities from top to bottom):
              * Task{c1 #40 type=standard A=10001:com.foo.bar U=0 visible=true mode=fullscreen}
                * Hist  #0: ActivityRecord{1 u0 com.foo.bar/.Main t40}
              * Task{c2 #41 type=standard A=10002:com.foo U=0 visible=false mode=fullscreen}
                * Hist  #0: ActivityRecord{2 u0 com.foo/.Main t41}
            """,
        )
        assertEquals(41, s.taskOf("com.foo")?.id)
        assertEquals(40, s.taskOf("com.foo.bar")?.id)
        assertNull(s.taskOf("com.fo"))
    }

    @Test
    fun `the root activity names the app when the affinity differs`() {
        val s = parse(
            """
            Display #0 (activities from top to bottom):
              * Task{d1 #50 type=standard A=10003:custom.affinity U=0 visible=true mode=fullscreen}
                * Hist  #1: ActivityRecord{3 u0 com.share.sheet/.Chooser t50}
                * Hist  #0: ActivityRecord{4 u0 com.real.app/.Main t50}
            """,
        )
        assertEquals(50, s.taskOf("com.real.app")?.id)
        assertEquals("com.real.app", s.tasks.single().packageName)
    }

    @Test
    fun `front-most task wins`() {
        val s = parse(
            """
            Display #0 (activities from top to bottom):
              * Task{e1 #60 type=standard A=10004:com.app U=0 visible=true mode=fullscreen}
              * Task{e2 #59 type=standard A=10004:com.app U=0 visible=false mode=fullscreen}
            """,
        )
        assertEquals(60, s.taskOf("com.app")?.id)
    }

    @Test
    fun `Folark's picker counts as staged but is no split app`() {
        val s = parse(
            """
            Display #0 (activities from top to bottom):
              * Task{f1 #301 type=undefined mode=multi-window visible=true}
                * Task{f2 #302 type=undefined stage_a mode=multi-window visible=true}
                  * Task{f3 #70 type=standard A=10234:com.android.chrome U=0 visible=true mode=multi-window}
                * Task{f4 #303 type=undefined stage_b mode=multi-window visible=true}
                  * Task{f5 #71 type=standard A=10100:org.arkikeskus.launcher.splitpicker U=0 visible=true mode=multi-window}
                    * Hist  #0: ActivityRecord{5 u0 org.arkikeskus.launcher/.recents.SplitPickerActivity t71}
            """,
        )
        assertTrue(s.isStaged(71))
        assertTrue(s.splitStaged.isEmpty()) // only one app: Chrome
        assertTrue(s.tasks.first { it.id == 71 }.own)
    }

    @Test
    fun `the app in front skips Home and Folark`() {
        val s = parse(
            """
            Display #0 (activities from top to bottom):
              * Task{g1 #1 type=home A=10100:org.arkikeskus.launcher U=0 visible=true mode=fullscreen}
              * Task{g2 #80 type=standard A=10100:org.arkikeskus.launcher.splitpicker U=0 visible=true mode=fullscreen}
              * Task{g3 #81 type=standard A=10005:com.notes U=0 visible=true mode=fullscreen}
            """,
        )
        assertEquals(81, s.frontApp()?.id)
    }

    // ------------------------------------------------------------------ the phone's own dump

    /**
     * `dumpsys activity activities` from the Razr Fold (Android 17, Motorola WM Shell) with two apps
     * in split screen, trimmed to the task and activity lines; third-party package names replaced.
     * Motorola prints the hierarchy twice and keeps an empty stage_c.
     */
    private fun motoDump(): TaskSnapshot {
        val text = requireNotNull(javaClass.classLoader?.getResource("dumps/moto_split_activities.dump")) { "fixture missing" }.readText()
        return ShellSplitParser.parse(text, own)
    }

    @Test
    fun `moto dump - the split and its stages`() {
        val s = motoDump()
        assertEquals(listOf(16523, 16479), s.splitShowing.map { it.id })
        assertEquals(listOf("stage_a", "stage_b"), s.splitShowing.map { it.stage })
        assertEquals(setOf(16523, 16479), s.stagedIds) // not the empty stage_c, not the containers
    }

    @Test
    fun `moto dump - each task once although the hierarchy is printed twice`() {
        val s = motoDump()
        assertEquals(s.tasks.map { it.id }.distinct(), s.tasks.map { it.id })
        assertEquals(10, s.tasks.size)
    }

    @Test
    fun `moto dump - lookups, the app in front and Folark's own task`() {
        val s = motoDump()
        assertEquals(16479, s.taskOf("com.example.chat")?.id)
        assertEquals(16545, s.taskOf("com.android.vending")?.id)
        assertEquals(16523, s.frontApp()?.id)
        val home = s.tasks.single { it.id == 16567 }
        assertTrue(home.own) // Folark's home is type=undefined here, not type=home
        assertFalse(s.isStaged(16567))
    }

    @Test
    fun `the app used last, behind Home, skipping one app`() {
        val s = parse(
            """
            Display #0 (activities from top to bottom):
              * Task{h1 #1 type=home A=10100:org.arkikeskus.launcher U=0 visible=true mode=fullscreen}
              * Task{h2 #90 type=standard A=10006:com.chat U=0 visible=false mode=fullscreen}
              * Task{h3 #91 type=standard A=10007:com.maps U=0 visible=false mode=fullscreen}
            """,
        )
        assertNull(s.frontApp())
        assertEquals(90, s.lastApp()?.id)
        assertEquals(91, s.lastApp(except = "com.chat")?.id)
    }

    @Test
    fun `nothing open`() {
        val s = parse("Display #0 (activities from top to bottom):")
        assertTrue(s.tasks.isEmpty())
        assertNull(s.frontApp())
        assertNull(s.taskOf("com.app"))
    }
}
