package org.arkikeskus.launcher.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.arkikeskus.launcher.data.di.LAUNCHER_DB_MIGRATIONS
import org.arkikeskus.launcher.data.di.MIGRATION_5_6
import org.arkikeskus.launcher.data.di.MIGRATION_6_7
import org.arkikeskus.launcher.data.di.MIGRATION_7_8
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.data.local.LauncherDatabase
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * launcher.db from every shipped version (5…8) up to the current one: each step leaves exactly the
 * schema Room exported for that version (core/data/schemas, copied into androidTest assets), and
 * the home layout — apps, folders, their places — comes through unchanged.
 */
@RunWith(AndroidJUnit4::class)
class LauncherDatabaseMigrationTest {

    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LauncherDatabase::class.java)

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(name)
    }

    /** A v5 home: an app on page 0, a folder and an app inside it, and an app in the dock. */
    private fun createV5() {
        helper.createDatabase(name, 5).apply {
            execSQL(
                "INSERT INTO home_items (id, containerId, folderName, packageName, className, userSerial, shortcutId, page, cellX, cellY) " +
                    "VALUES (1, ${HomeItemEntity.HOME}, NULL, 'com.android.chrome', 'com.google.android.apps.chrome.Main', 0, NULL, 0, 1, 2)",
            )
            execSQL(
                "INSERT INTO home_items (id, containerId, folderName, packageName, className, userSerial, shortcutId, page, cellX, cellY) " +
                    "VALUES (2, ${HomeItemEntity.HOME}, 'Tools', '', '', 0, NULL, 1, 0, 0)",
            )
            execSQL(
                "INSERT INTO home_items (id, containerId, folderName, packageName, className, userSerial, shortcutId, page, cellX, cellY) " +
                    "VALUES (3, 2, NULL, 'com.android.calculator2', 'com.android.calculator2.Calculator', 0, NULL, 0, 0, 0)",
            )
            execSQL(
                "INSERT INTO home_items (id, containerId, folderName, packageName, className, userSerial, shortcutId, page, cellX, cellY) " +
                    "VALUES (4, -2, NULL, 'com.google.android.apps.maps', 'com.google.android.maps.MapsActivity', 10, NULL, 0, 3, 0)",
            )
            close()
        }
    }

    @Test
    fun step_5_to_6() {
        createV5()
        helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6).use { db ->
            db.query("SELECT spanX, spanY, appWidgetId, widgetProvider FROM home_items WHERE id = 1").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(1)
                assertThat(c.getInt(1)).isEqualTo(1)
                assertThat(c.isNull(2)).isTrue()
                assertThat(c.isNull(3)).isTrue()
            }
        }
    }

    @Test
    fun step_6_to_7() {
        createV5()
        helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6).close()
        helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7).use { db ->
            db.query("SELECT builtinType FROM home_items WHERE id = 1").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.isNull(0)).isTrue()
            }
        }
    }

    @Test
    fun step_7_to_8_puts_everything_on_the_outer_screen() {
        createV5()
        helper.runMigrationsAndValidate(name, 7, true, MIGRATION_5_6, MIGRATION_6_7).close()
        helper.runMigrationsAndValidate(name, 8, true, MIGRATION_7_8).use { db ->
            db.query("SELECT DISTINCT screenType FROM home_items").use { c ->
                assertThat(c.count).isEqualTo(1)
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("OUTER")
            }
            // The new unique index (per screen) is there, the old one gone.
            db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'home_items'").use { c ->
                val names = buildList { while (c.moveToNext()) add(c.getString(0)) }
                assertThat(names).contains("index_home_items_screenType_containerId_page_cellX_cellY")
                assertThat(names).doesNotContain("index_home_items_containerId_page_cellX_cellY")
            }
        }
    }

    @Test
    fun all_the_way_from_5_keeps_the_home_layout() = runTest {
        createV5()
        helper.runMigrationsAndValidate(name, 8, true, *LAUNCHER_DB_MIGRATIONS).close()

        // Opened the way the app opens it (DataModule), the layout reads back as it was.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, LauncherDatabase::class.java, name)
            .addMigrations(*LAUNCHER_DB_MIGRATIONS)
            .build()
        try {
            val items = db.homeItemDao().getAll().associateBy { it.id }
            assertThat(items.keys).containsExactly(1L, 2L, 3L, 4L)
            with(items.getValue(1L)) {
                assertThat(packageName).isEqualTo("com.android.chrome")
                assertThat(listOf(page, cellX, cellY)).containsExactly(0, 1, 2).inOrder()
            }
            assertThat(items.getValue(2L).folderName).isEqualTo("Tools")
            assertThat(items.getValue(3L).containerId).isEqualTo(2L)
            assertThat(items.getValue(4L).userSerial).isEqualTo(10L)
        } finally {
            db.close()
        }
    }
}
