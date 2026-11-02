package org.arkikeskus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The exported Room schemas (core/data/schemas) and their copies in androidTest assets, which
 * LauncherDatabaseMigrationTest reads: every copy must equal its schema, and every schema must
 * really be its own version (a version re-exported over an older file once made 7.json equal 8.json,
 * and a migration test against it would have checked the wrong thing).
 */
class SchemaCopiesTest {

    private val db = "org.arkikeskus.launcher.data.local.LauncherDatabase"
    private val exported = File("schemas/$db")
    private val copies = File("src/androidTest/assets/$db")

    private fun schemas(): List<File> =
        exported.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.nameWithoutExtension.toInt() }

    private fun text(f: File) = f.readText().replace("\r\n", "\n")

    @Test
    fun `every exported schema has its copy in androidTest assets`() {
        val all = schemas()
        assertTrue("no schemas found in ${exported.absolutePath}", all.isNotEmpty())
        all.forEach { f ->
            val copy = File(copies, f.name)
            assertTrue("missing ${copy.path}: copy ${f.name} there", copy.isFile)
            assertEquals("${copy.path} differs from ${f.path}", text(f), text(copy))
        }
    }

    @Test
    fun `each schema is its own version`() {
        val version = Regex(""""version":\s*(\d+)""")
        val hash = Regex(""""identityHash":\s*"([0-9a-f]+)"""")
        val hashes = schemas().map { f ->
            val t = text(f)
            assertEquals("${f.name} says another version", f.nameWithoutExtension, version.find(t)?.groupValues?.get(1))
            hash.find(t)?.groupValues?.get(1).orEmpty()
        }
        hashes.zipWithNext().forEach { (a, b) -> assertNotEquals("two versions share one schema", a, b) }
    }
}
