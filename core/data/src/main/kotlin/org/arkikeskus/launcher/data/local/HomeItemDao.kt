package org.arkikeskus.launcher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeItemDao {
    /** Every row (home items, folders, and folder children) — the ViewModel partitions by container. */
    @Query("SELECT * FROM home_items WHERE screenType = :screenType ORDER BY containerId ASC, page ASC, cellY ASC, cellX ASC")
    fun observeAll(screenType: String): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items WHERE containerId = :containerId AND screenType = :screenType ORDER BY page ASC, cellY ASC, cellX ASC")
    suspend fun getContainerOrdered(containerId: Long, screenType: String): List<HomeItemEntity>

    @Query("SELECT * FROM home_items WHERE containerId = :containerId AND screenType = :screenType")
    suspend fun getContainer(containerId: Long, screenType: String): List<HomeItemEntity>

    /** Every row in both containers, for the ghost-row sweep. */
    @Query("SELECT * FROM home_items")
    suspend fun getAll(): List<HomeItemEntity>

    @Query("SELECT * FROM home_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HomeItemEntity?

    @Query(
        "SELECT * FROM home_items WHERE containerId = :containerId AND packageName = :pkg " +
            "AND className = :cls AND userSerial = :user AND screenType = :screenType LIMIT 1",
    )
    suspend fun getByKey(containerId: Long, pkg: String, cls: String, user: Long, screenType: String): HomeItemEntity?

    @Query(
        "SELECT * FROM home_items WHERE containerId = :containerId AND page = :page " +
            "AND cellX = :cellX AND cellY = :cellY AND screenType = :screenType LIMIT 1",
    )
    suspend fun getAt(containerId: Long, page: Int, cellX: Int, cellY: Int, screenType: String): HomeItemEntity?

    @Insert
    suspend fun insert(item: HomeItemEntity): Long

    @Query("DELETE FROM home_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM home_items WHERE containerId = :containerId AND packageName = :pkg " +
            "AND className = :cls AND userSerial = :user AND screenType = :screenType",
    )
    suspend fun deleteByKey(containerId: Long, pkg: String, cls: String, user: Long, screenType: String)

    @Query("DELETE FROM home_items WHERE screenType = :screenType")
    suspend fun clear(screenType: String)

    @Query(
        "SELECT COUNT(*) FROM home_items WHERE containerId = :containerId AND packageName = :pkg " +
            "AND className = :cls AND userSerial = :user AND screenType = :screenType",
    )
    suspend fun count(containerId: Long, pkg: String, cls: String, user: Long, screenType: String): Int

    @Query("SELECT COUNT(*) FROM home_items WHERE containerId = :containerId AND screenType = :screenType")
    suspend fun childCount(containerId: Long, screenType: String): Int

    @Query(
        "UPDATE home_items SET containerId = :containerId, page = :page, cellX = :cellX, cellY = :cellY " +
            "WHERE id = :id",
    )
    suspend fun moveById(id: Long, containerId: Long, page: Int, cellX: Int, cellY: Int)

    /** Shifts the page of every row in [containerId] at or after [from] by [offset]. */
    @Query("UPDATE home_items SET page = page + :offset WHERE containerId = :containerId AND page >= :from AND screenType = :screenType")
    suspend fun offsetPages(containerId: Long, from: Int, offset: Int, screenType: String)

    @Query("SELECT COUNT(*) FROM home_items WHERE containerId = :containerId AND page = :page AND screenType = :screenType")
    suspend fun countOnPage(containerId: Long, page: Int, screenType: String): Int

    /** Ids of the folder rows on [page] of [containerId]; their children live under those ids. */
    @Query("SELECT id FROM home_items WHERE containerId = :containerId AND page = :page AND folderName IS NOT NULL AND screenType = :screenType")
    suspend fun folderIdsOnPage(containerId: Long, page: Int, screenType: String): List<Long>

    @Query("DELETE FROM home_items WHERE containerId IN (:containerIds) AND screenType = :screenType")
    suspend fun deleteByContainers(containerIds: List<Long>, screenType: String)

    @Query("DELETE FROM home_items WHERE containerId = :containerId AND page = :page AND screenType = :screenType")
    suspend fun deleteOnPage(containerId: Long, page: Int, screenType: String)

    @Query("UPDATE home_items SET folderName = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE home_items SET spanX = :spanX, spanY = :spanY WHERE id = :id")
    suspend fun updateSpans(id: Long, spanX: Int, spanY: Int)

    /** Binds a restored (placeholder) widget row to its freshly allocated device-local [appWidgetId]. */
    @Query("UPDATE home_items SET appWidgetId = :appWidgetId WHERE id = :id")
    suspend fun updateWidgetId(id: Long, appWidgetId: Int)

    /** Unbinds a widget row (stale id from another device's restore) back into a placeholder. */
    @Query("UPDATE home_items SET appWidgetId = NULL WHERE id = :id")
    suspend fun clearWidgetId(id: Long)

    /** The device-local ids of every currently-bound widget, to reconcile against the AppWidgetHost. */
    @Query("SELECT appWidgetId FROM home_items WHERE appWidgetId IS NOT NULL")
    suspend fun boundWidgetIds(): List<Int>

    @Query("SELECT * FROM home_items WHERE screenType = :screenType")
    suspend fun getAllOnce(screenType: String): List<HomeItemEntity>

    /** Atomically replaces the entire layout for BOTH screens. */
    @Transaction
    suspend fun replaceAllLayouts(items: List<HomeItemEntity>) {
        clearAll()
        for (item in items) insert(item)
    }

    @Query("DELETE FROM home_items")
    suspend fun clearAll()

    /** Atomically replaces the layout for a single screen type. */
    @Transaction
    suspend fun replaceLayout(items: List<HomeItemEntity>, screenType: String) {
        clear(screenType)
        for (item in items) insert(item)
    }
}
