package org.arkikeskus.launcher.data.root

import org.junit.Assert.assertEquals
import org.junit.Test

class RootTasksMemoryTest {

    private val ps = """
           RSS NAME
        123456 com.example.mail
         20480 com.example.mail:sync
          9000 com.example.maps
           512 com.other
        garbage line
             0 [kthreadd]
    """.trimIndent()

    @Test fun sumsEveryProcessOfAPackage() {
        val m = RootTasks.parseMemory(ps, setOf("com.example.mail", "com.example.maps"))
        assertEquals(123456L + 20480L, m["com.example.mail"])
        assertEquals(9000L, m["com.example.maps"])
    }

    @Test fun leavesOutPackagesNotAskedFor() {
        val m = RootTasks.parseMemory(ps, setOf("com.example.maps"))
        assertEquals(setOf("com.example.maps"), m.keys)
    }

    @Test fun aPrefixIsNotTheSamePackage() {
        // "com.example.mail" must not count for "com.example.ma".
        val m = RootTasks.parseMemory(ps, setOf("com.example.ma"))
        assertEquals(emptyMap<String, Long>(), m)
    }
}
