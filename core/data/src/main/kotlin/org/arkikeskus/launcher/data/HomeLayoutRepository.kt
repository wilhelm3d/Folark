package org.arkikeskus.launcher.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.arkikeskus.launcher.data.local.HomeItemDao
import org.arkikeskus.launcher.data.local.HomeItemEntity
import org.arkikeskus.launcher.data.local.HomeItemEntity.Companion.HOME
import org.arkikeskus.launcher.data.local.LauncherDatabase
import org.arkikeskus.launcher.model.AppItem
import org.arkikeskus.launcher.model.ScreenType
import org.arkikeskus.launcher.model.WidgetPlacement
import javax.inject.Inject
import javax.inject.Singleton

/** The shortcut ids still pinned for a (package, user) after a removal — used to re-pin the set. */
data class RemainingPins(val packageName: String, val userSerial: Long, val shortcutIds: List<String>)

/** One row's target grid position (and sanitized span) in a [HomeLayoutRepository.reflowPlan]. */
data class ReflowPlacement(
    val id: Long, val page: Int, val cellX: Int, val cellY: Int, val spanX: Int, val spanY: Int,
)

/** Where [HomeLayoutRepository.firstRectWithPush] places a new rect, plus the displacements
 *  (rowId → new cell on [page]) that free it. Empty [moved] = the rect was genuinely free. */
data class PushPlacement(val page: Int, val cellX: Int, val cellY: Int, val moved: Map<Long, Pair<Int, Int>>)

/**
 * Persists the home layout (Room): app shortcuts and folders placed at free cells, and the apps
 * inside each folder. Top-level items live in the [HOME] container; a folder's children live in the
 * container identified by the folder row's id. Each container is an independent cell space guarded by
 * the unique (containerId, page, cellX, cellY) index, so the swaps/repacks below stay collision-free.
 */
@Singleton
open class HomeLayoutRepository @Inject constructor(
    private val db: LauncherDatabase,
    private val dao: HomeItemDao,
) {
    /** All rows (home items, folders, folder children); the ViewModel partitions by container. */
    fun homeItems(screenType: ScreenType): Flow<List<HomeItemEntity>> = dao.observeAll(screenType.name)

    suspend fun addToHome(appItem: AppItem, columns: Int, rows: Int, screenType: ScreenType) {
        db.withTransaction {
            if (dao.count(HOME, appItem.packageName, appItem.className, appItem.userSerial, screenType.name) > 0) {
                return@withTransaction
            }
            val (page, cellX, cellY) = firstFreeCell(dao.getContainer(HOME, screenType.name), columns, rows)
            dao.insert(homeApp(appItem, HOME, page, cellX, cellY, screenType))
        }
    }

    suspend fun moveItem(appItem: AppItem, page: Int, cellX: Int, cellY: Int, screenType: ScreenType): Boolean =
        db.withTransaction {
            val source = dao.getByKey(HOME, appItem.packageName, appItem.className, appItem.userSerial, screenType.name)
                ?: return@withTransaction false
            moveOrSwap(source, page, cellX, cellY, screenType)
        }

    suspend fun placeAt(appItem: AppItem, page: Int, cellX: Int, cellY: Int, columns: Int, rows: Int, screenType: ScreenType): Boolean =
        db.withTransaction {
            val existing = dao.getByKey(HOME, appItem.packageName, appItem.className, appItem.userSerial, screenType.name)
            if (existing != null) {
                return@withTransaction moveOrSwap(existing, page, cellX, cellY, screenType)
            }
            val items = dao.getContainer(HOME, screenType.name)
            val (p, x, y) = if (dao.getAt(HOME, page, cellX, cellY, screenType.name) == null &&
                !cellInWidget(items, -1L, page, cellX, cellY)
            ) {
                Triple(page, cellX, cellY)
            } else {
                firstFreeCell(items, columns, rows)
            }
            dao.insert(homeApp(appItem, HOME, p, x, y, screenType))
            true
        }

    suspend fun removeFromHome(appItem: AppItem, screenType: ScreenType) {
        dao.deleteByKey(HOME, appItem.packageName, appItem.className, appItem.userSerial, screenType.name)
    }

    suspend fun moveFolder(folderId: Long, page: Int, cellX: Int, cellY: Int, screenType: ScreenType): Boolean =
        db.withTransaction {
            val source = dao.getById(folderId) ?: return@withTransaction false
            moveOrSwap(source, page, cellX, cellY, screenType)
        }

    suspend fun addShortcut(packageName: String, shortcutId: String, userSerial: Long, columns: Int, rows: Int, screenType: ScreenType) {
        db.withTransaction {
            val present = dao.getContainer(HOME, screenType.name).any {
                it.shortcutId == shortcutId && it.packageName == packageName && it.userSerial == userSerial
            }
            if (present) return@withTransaction
            val (page, x, y) = firstFreeCell(dao.getContainer(HOME, screenType.name), columns, rows)
            dao.insert(
                HomeItemEntity(
                    containerId = HOME,
                    packageName = packageName,
                    userSerial = userSerial,
                    shortcutId = shortcutId,
                    page = page,
                    cellX = x,
                    cellY = y,
                    screenType = screenType.name
                ),
            )
        }
    }

    suspend fun removeShortcut(rowId: Long): RemainingPins? = db.withTransaction {
        val row = dao.getById(rowId) ?: return@withTransaction null
        if (!row.isShortcut) return@withTransaction null
        dao.deleteById(rowId)
        val remaining = dao.getContainer(HOME, row.screenType)
            .filter { it.shortcutId != null && it.packageName == row.packageName && it.userSerial == row.userSerial }
            .mapNotNull { it.shortcutId }
        RemainingPins(row.packageName, row.userSerial, remaining)
    }

    suspend fun reflow(columns: Int, rows: Int, screenType: ScreenType) {
        if (columns <= 0 || rows <= 0) return
        db.withTransaction {
            val items = dao.getContainerOrdered(HOME, screenType.name)
            if (items.isEmpty()) return@withTransaction
            val plan = reflowPlan(items, columns, rows)
            items.forEachIndexed { i, row -> dao.moveById(row.id, HOME, -1, -(i + 1), -1) }
            items.zip(plan).forEach { (row, p) ->
                dao.moveById(p.id, HOME, p.page, p.cellX, p.cellY)
                if (p.spanX != row.spanX || p.spanY != row.spanY) dao.updateSpans(p.id, p.spanX, p.spanY)
            }
        }
    }

    // --- Folders ---

    suspend fun createFolder(target: AppItem, dropped: AppItem, name: String, screenType: ScreenType): Long =
        db.withTransaction {
            val targetRow = dao.getByKey(HOME, target.packageName, target.className, target.userSerial, screenType.name)
                ?: return@withTransaction -1L
            val droppedRow = dao.getByKey(HOME, dropped.packageName, dropped.className, dropped.userSerial, screenType.name)
                ?: return@withTransaction -1L
            if (targetRow.id == droppedRow.id) return@withTransaction -1L
            val page = targetRow.page
            val cellX = targetRow.cellX
            val cellY = targetRow.cellY
            val folderId = dao.insert(
                HomeItemEntity(containerId = HOME, folderName = name, page = -1, cellX = -1, cellY = -1, screenType = screenType.name),
            )
            dao.moveById(targetRow.id, folderId, 0, 0, 0)
            dao.moveById(droppedRow.id, folderId, 0, 1, 0)
            dao.moveById(folderId, HOME, page, cellX, cellY)
            folderId
        }

    suspend fun addToFolder(appItem: AppItem, folderId: Long, screenType: ScreenType) {
        db.withTransaction {
            val row = dao.getByKey(HOME, appItem.packageName, appItem.className, appItem.userSerial, screenType.name)
                ?: return@withTransaction
            if (dao.getByKey(folderId, appItem.packageName, appItem.className, appItem.userSerial, screenType.name) != null) {
                dao.deleteById(row.id)
                return@withTransaction
            }
            val order = dao.childCount(folderId, screenType.name)
            dao.moveById(row.id, folderId, 0, order, 0)
        }
    }

    suspend fun removeFromFolder(appItem: AppItem, folderId: Long, columns: Int, rows: Int, screenType: ScreenType) {
        db.withTransaction {
            val row = dao.getByKey(folderId, appItem.packageName, appItem.className, appItem.userSerial, screenType.name)
                ?: return@withTransaction
            val (p, x, y) = firstFreeCell(dao.getContainer(HOME, screenType.name), columns, rows)
            dao.moveById(row.id, HOME, p, x, y)
            reindexFolder(folderId, screenType)
            dissolveIfNeeded(folderId, screenType)
        }
    }

    suspend fun renameFolder(folderId: Long, name: String) {
        dao.renameFolder(folderId, name)
    }

    private suspend fun reindexFolder(folderId: Long, screenType: ScreenType) {
        val children = dao.getContainerOrdered(folderId, screenType.name)
        children.forEachIndexed { i, child ->
            if (child.cellX != i || child.cellY != 0 || child.page != 0) {
                dao.moveById(child.id, folderId, 0, i, 0)
            }
        }
    }

    private suspend fun dissolveIfNeeded(folderId: Long, screenType: ScreenType) {
        val children = dao.getContainerOrdered(folderId, screenType.name)
        when (children.size) {
            0 -> dao.deleteById(folderId)
            1 -> {
                val folder = dao.getById(folderId) ?: return
                dao.deleteById(folderId)
                dao.moveById(children.first().id, HOME, folder.page, folder.cellX, folder.cellY)
            }
        }
    }

    private suspend fun moveOrSwap(source: HomeItemEntity, page: Int, cellX: Int, cellY: Int, screenType: ScreenType): Boolean {
        if (source.page == page && source.cellX == cellX && source.cellY == cellY) return true
        if (cellInWidget(dao.getContainer(HOME, screenType.name), source.id, page, cellX, cellY)) return false
        val occupant = dao.getAt(HOME, page, cellX, cellY, screenType.name)
        when {
            occupant == null -> dao.moveById(source.id, HOME, page, cellX, cellY)
            occupant.id == source.id -> Unit
            else -> {
                dao.moveById(source.id, HOME, TEMP_SLOT, TEMP_SLOT, TEMP_SLOT)
                dao.moveById(occupant.id, HOME, source.page, source.cellX, source.cellY)
                dao.moveById(source.id, HOME, page, cellX, cellY)
            }
        }
        return true
    }

    private fun homeApp(app: AppItem, container: Long, page: Int, cellX: Int, cellY: Int, screenType: ScreenType) =
        HomeItemEntity(
            containerId = container,
            packageName = app.packageName,
            className = app.className,
            userSerial = app.userSerial,
            page = page,
            cellX = cellX,
            cellY = cellY,
            screenType = screenType.name
        )

    private fun cellInWidget(items: List<HomeItemEntity>, excludeId: Long, page: Int, cellX: Int, cellY: Int): Boolean =
        items.any {
            it.id != excludeId && it.hasFootprint && it.page == page &&
                cellX in it.cellX until (it.cellX + it.spanX) &&
                cellY in it.cellY until (it.cellY + it.spanY)
        }

    private fun firstFreeCell(items: List<HomeItemEntity>, columns: Int, rows: Int): Triple<Int, Int, Int> =
        firstFreeRect(items, columns, 1, 1, rows)

    private suspend fun applyPush(p: PushPlacement) {
        if (p.moved.isEmpty()) return
        var temp = -1
        for (id in p.moved.keys) { dao.moveById(id, HOME, temp, temp, temp); temp-- }
        for ((id, pos) in p.moved) dao.moveById(id, HOME, p.page, pos.first, pos.second)
    }

    suspend fun addWidget(appWidgetId: Int, provider: String, spanX: Int, spanY: Int, columns: Int, rows: Int, screenType: ScreenType) {
        db.withTransaction {
            val placement = firstRectWithPush(dao.getContainer(HOME, screenType.name), columns, spanX, spanY, rows)
            applyPush(placement)
            val (page, x, y) = Triple(placement.page, placement.cellX, placement.cellY)
            dao.insert(
                HomeItemEntity(
                    containerId = HOME,
                    page = page, cellX = x, cellY = y,
                    spanX = spanX.coerceIn(1, columns.coerceAtLeast(1)),
                    spanY = spanY.coerceIn(1, rows.coerceAtLeast(1)),
                    appWidgetId = appWidgetId,
                    widgetProvider = provider,
                    screenType = screenType.name
                ),
            )
        }
    }

    suspend fun addWidgetAt(
        appWidgetId: Int?,
        provider: String?,
        builtinType: String?,
        placement: WidgetPlacement,
        columns: Int,
        rows: Int,
        screenType: ScreenType
    ): Boolean = db.withTransaction {
        val items = dao.getContainer(HOME, screenType.name)
        if (appWidgetId != null && items.any { it.appWidgetId == appWidgetId }) return@withTransaction true
        val p = placement
        if (p.page !in 0..MAX_PAGES) return@withTransaction false
        val plan = ReorderPlanner.planFit(
            items.map { ReorderPlanner.Rect(it.id, it.page, it.cellX, it.cellY, it.spanX, it.spanY) },
            Long.MIN_VALUE, p.page, p.cellX, p.cellY, p.spanX, p.spanY, columns, rows,
        ) ?: return@withTransaction false
        var temp = -1
        for (id in plan.keys) { dao.moveById(id, HOME, temp, temp, temp); temp-- }
        for ((id, pos) in plan) dao.moveById(id, HOME, p.page, pos.first, pos.second)
        dao.insert(HomeItemEntity(
            containerId = HOME, page = p.page, cellX = p.cellX, cellY = p.cellY,
            spanX = p.spanX, spanY = p.spanY, appWidgetId = appWidgetId,
            widgetProvider = provider, builtinType = builtinType,
            screenType = screenType.name
        ))
        true
    }

    suspend fun removeWidget(rowId: Long) {
        dao.deleteById(rowId)
    }

    suspend fun insertPage(at: Int, screenType: ScreenType) {
        db.withTransaction {
            dao.offsetPages(HOME, at, PAGE_SHIFT_HOP, screenType.name)
            dao.offsetPages(HOME, at + PAGE_SHIFT_HOP, 1 - PAGE_SHIFT_HOP, screenType.name)
        }
    }

    suspend fun removeEmptyPage(page: Int, purge: Boolean = false, screenType: ScreenType): Boolean = db.withTransaction {
        if (purge) {
            val folders = dao.folderIdsOnPage(HOME, page, screenType.name)
            if (folders.isNotEmpty()) dao.deleteByContainers(folders, screenType.name)
            dao.deleteOnPage(HOME, page, screenType.name)
        } else if (dao.countOnPage(HOME, page, screenType.name) > 0) {
            return@withTransaction false
        }
        dao.offsetPages(HOME, page + 1, PAGE_SHIFT_HOP, screenType.name)
        dao.offsetPages(HOME, page + 1 + PAGE_SHIFT_HOP, -1 - PAGE_SHIFT_HOP, screenType.name)
        true
    }

    suspend fun addBuiltin(type: String, spanX: Int, spanY: Int, columns: Int, rows: Int, screenType: ScreenType) {
        db.withTransaction {
            val placement = firstRectWithPush(dao.getContainer(HOME, screenType.name), columns, spanX, spanY, rows)
            applyPush(placement)
            val (page, x, y) = Triple(placement.page, placement.cellX, placement.cellY)
            dao.insert(
                HomeItemEntity(
                    containerId = HOME,
                    page = page, cellX = x, cellY = y,
                    spanX = spanX.coerceIn(1, columns.coerceAtLeast(1)),
                    spanY = spanY.coerceIn(1, rows.coerceAtLeast(1)),
                    builtinType = type,
                    screenType = screenType.name
                ),
            )
        }
    }

    suspend fun isHomeEmpty(screenType: ScreenType): Boolean = dao.getContainer(HOME, screenType.name).isEmpty()

    suspend fun unbindStaleWidgets(validIds: Set<Int>, screenType: ScreenType) {
        db.withTransaction {
            dao.getContainer(HOME, screenType.name).forEach { row ->
                val id = row.appWidgetId
                if (id != null && id !in validIds) dao.clearWidgetId(row.id)
            }
        }
    }

    suspend fun bindRestoredWidget(rowId: Long, appWidgetId: Int): Boolean = db.withTransaction {
        val row = dao.getById(rowId) ?: return@withTransaction false
        if (row.widgetProvider == null || row.containerId != HOME) return@withTransaction false
        dao.updateWidgetId(rowId, appWidgetId)
        true
    }

    suspend fun boundWidgetIds(): Set<Int> = dao.boundWidgetIds().toSet()

    suspend fun setWidgetBounds(
        rowId: Long, page: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int, columns: Int, rows: Int, screenType: ScreenType
    ): Boolean = db.withTransaction {
        val row = dao.getById(rowId) ?: return@withTransaction false
        if (!row.isWidget && !row.isBuiltin) return@withTransaction false
        val sx = spanX.coerceAtLeast(1)
        val sy = spanY.coerceAtLeast(1)
        val items = dao.getContainer(HOME, screenType.name)
        if (rectFitsForRow(items, rowId, page, cellX, cellY, sx, sy, columns, rows)) {
            dao.moveById(rowId, HOME, page, cellX, cellY)
            dao.updateSpans(rowId, sx, sy)
            return@withTransaction true
        }
        val cols = columns.coerceAtLeast(1)
        val plan = ReorderPlanner.planFit(
            items.map { ReorderPlanner.Rect(it.id, it.page, it.cellX, it.cellY, it.spanX, it.spanY) },
            rowId, page, cellX, cellY, sx, sy, cols, rows.coerceAtLeast(1),
        ) ?: return@withTransaction false
        var temp = -1
        dao.moveById(rowId, HOME, temp, temp, temp); temp--
        for (id in plan.keys) { dao.moveById(id, HOME, temp, temp, temp); temp-- }
        for ((id, pos) in plan) dao.moveById(id, HOME, page, pos.first, pos.second)
        dao.moveById(rowId, HOME, page, cellX, cellY)
        dao.updateSpans(rowId, sx, sy)
        true
    }

    suspend fun removeStaleAppRows(isInstalled: (packageName: String, userSerial: Long) -> Boolean): Int =
        db.withTransaction {
            val all = dao.getAll()
            val stale = staleAppRowIds(all, isInstalled)
            if (stale.isEmpty()) return@withTransaction 0
            val staleSet = stale.toHashSet()
            val touchedFolders = all
                .filter { it.id in staleSet && it.containerId != HOME }
                .map { it.id to it.containerId } // Need folder id to get screenType
            
            val folderToScreenType = all.filter { it.isFolder }.associate { it.id to it.screenType }
            val touchedFoldersIds = touchedFolders.map { it.second }.toSet()
            stale.forEach { dao.deleteById(it) }
            touchedFoldersIds.forEach { folderId ->
                val type = ScreenType.valueOf(folderToScreenType[folderId] ?: "OUTER")
                reindexFolder(folderId, type)
                dissolveIfNeeded(folderId, type)
            }
            stale.size
        }

    suspend fun removeAppRowsForPackage(packageName: String, userSerial: Long): Int =
        removeStaleAppRows { pkg, serial -> !(pkg == packageName && serial == userSerial) }

    companion object {
        const val ROWS = 6
        const val MAX_PAGES = 50
        private const val PAGE_SHIFT_HOP = 100_000

        fun reflowPlan(rows: List<HomeItemEntity>, columns: Int, gridRows: Int = ROWS): List<ReflowPlacement> {
            val cols = columns.coerceAtLeast(1)
            val rws = gridRows.coerceAtLeast(1)
            val placed = ArrayList<HomeItemEntity>(rows.size)
            val plan = ArrayList<ReflowPlacement>(rows.size)
            for (row in rows) {
                val widget = row.hasFootprint
                val sx = if (widget) row.spanX.coerceIn(1, cols) else 1
                val sy = if (widget) row.spanY.coerceIn(1, rws) else 1
                val (page, x, y) = firstFreeRect(placed, cols, sx, sy, rws)
                plan += ReflowPlacement(row.id, page, x, y, sx, sy)
                placed += HomeItemEntity(page = page, cellX = x, cellY = y, spanX = sx, spanY = sy)
            }
            return plan
        }

        private const val TEMP_SLOT = -1

        fun staleAppRowIds(
            items: List<HomeItemEntity>,
            isInstalled: (packageName: String, userSerial: Long) -> Boolean,
        ): List<Long> {
            val verdict = HashMap<Pair<String, Long>, Boolean>()
            return items.filter { row ->
                row.folderName == null && row.builtinType == null && row.widgetProvider == null &&
                    row.packageName.isNotEmpty() &&
                    !verdict.getOrPut(row.packageName to row.userSerial) {
                        isInstalled(row.packageName, row.userSerial)
                    }
            }.map { it.id }
        }

        fun rectFitsForRow(
            items: List<HomeItemEntity>, excludeRowId: Long,
            page: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int, columns: Int, rows: Int = ROWS,
        ): Boolean {
            val cols = columns.coerceAtLeast(1)
            if (cellX < 0 || cellY < 0 || cellX + spanX > cols || cellY + spanY > rows.coerceAtLeast(1)) return false
            val occupied = HashSet<Triple<Int, Int, Int>>()
            for (e in items) if (e.id != excludeRowId) for (dx in 0 until e.spanX) for (dy in 0 until e.spanY) {
                occupied += Triple(e.page, e.cellX + dx, e.cellY + dy)
            }
            for (dx in 0 until spanX) for (dy in 0 until spanY) {
                if (Triple(page, cellX + dx, cellY + dy) in occupied) return false
            }
            return true
        }

        fun firstRectWithPush(
            items: List<HomeItemEntity>, columns: Int, spanX: Int, spanY: Int, rows: Int = ROWS,
        ): PushPlacement {
            val cols = columns.coerceAtLeast(1)
            val rws = rows.coerceAtLeast(1)
            val sx = spanX.coerceIn(1, cols)
            val sy = spanY.coerceIn(1, rws)
            val free = firstFreeRect(items, cols, sx, sy, rws)
            val maxPage = items.filter { it.page >= 0 }.maxOfOrNull { it.page } ?: -1
            if (free.first <= maxPage) {
                return PushPlacement(free.first, free.second, free.third, emptyMap())
            }
            val rects = items.map { ReorderPlanner.Rect(it.id, it.page, it.cellX, it.cellY, it.spanX, it.spanY) }
            for (page in 0..maxPage) {
                for (y in 0..rws - sy) for (x in 0..cols - sx) {
                    val plan = ReorderPlanner.planFit(rects, NO_ROW, page, x, y, sx, sy, cols, rws)
                    if (plan != null) return PushPlacement(page, x, y, plan)
                }
            }
            return PushPlacement(free.first, free.second, free.third, emptyMap())
        }

        private const val NO_ROW = -1L

        fun firstFreeRect(items: List<HomeItemEntity>, columns: Int, spanX: Int, spanY: Int, rows: Int = ROWS): Triple<Int, Int, Int> {
            val cols = columns.coerceAtLeast(1)
            val rws = rows.coerceAtLeast(1)
            val sx = spanX.coerceIn(1, cols)
            val sy = spanY.coerceIn(1, rws)
            val occupied = HashSet<Triple<Int, Int, Int>>()
            for (e in items) for (dx in 0 until e.spanX) for (dy in 0 until e.spanY) {
                occupied += Triple(e.page, e.cellX + dx, e.cellY + dy)
            }
            var page = 0
            while (true) {
                for (y in 0..rws - sy) for (x in 0..cols - sx) {
                    val fits = (0 until sx).all { dx -> (0 until sy).all { dy -> Triple(page, x + dx, y + dy) !in occupied } }
                    if (fits) return Triple(page, x, y)
                }
                page++
            }
        }
    }
}
