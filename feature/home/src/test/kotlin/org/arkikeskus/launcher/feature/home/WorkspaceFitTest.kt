package org.arkikeskus.launcher.feature.home

import com.google.common.truth.Truth.assertThat
import org.arkikeskus.launcher.feature.home.workspace.canPlaceWidget
import org.arkikeskus.launcher.feature.home.workspace.isRectFree
import org.arkikeskus.launcher.feature.home.workspace.stackTargetFor
import org.arkikeskus.launcher.model.WidgetPlacement
import org.junit.Test

class WorkspaceFitTest {

    private val none = Long.MIN_VALUE

    private fun widget(row: Long, page: Int, x: Int, y: Int, spanX: Int, spanY: Int) =
        PlacedBuiltin(row, "type", page, x, y, spanX, spanY)

    private fun folder(id: Long, page: Int, x: Int, y: Int) = PlacedFolder(id, "folder", emptyList(), page, x, y)

    // ----------------------------------------------------------------------------- isRectFree

    @Test
    fun cellsOutsideTheGridAreNeverFree() {
        val empty = emptyList<HomeEntry>()
        assertThat(isRectFree(empty, 4, 5, none, 0, -1, 0, 1, 1)).isFalse()
        assertThat(isRectFree(empty, 4, 5, none, 0, 0, -1, 1, 1)).isFalse()
        assertThat(isRectFree(empty, 4, 5, none, 0, 3, 0, 2, 1)).isFalse()
        assertThat(isRectFree(empty, 4, 5, none, 0, 0, 4, 1, 2)).isFalse()
        // The bottom-right corner, exactly filling it, is inside.
        assertThat(isRectFree(empty, 4, 5, none, 0, 2, 3, 2, 2)).isTrue()
    }

    @Test
    fun aWidgetBlocksItsWholeFootprintOnItsOwnPageOnly() {
        val entries = listOf(widget(1, page = 0, x = 1, y = 1, spanX = 2, spanY = 2))
        assertThat(isRectFree(entries, 4, 5, none, 0, 2, 2, 1, 1)).isFalse() // inside its far corner
        assertThat(isRectFree(entries, 4, 5, none, 0, 3, 1, 1, 1)).isTrue() // just beside it
        assertThat(isRectFree(entries, 4, 5, none, 1, 2, 2, 1, 1)).isTrue() // the same cell, next page
        assertThat(isRectFree(entries, 4, 5, none, 0, 0, 0, 2, 2)).isFalse() // a rectangle that clips it
    }

    @Test
    fun theWidgetBeingMovedDoesNotBlockItself() {
        val entries = listOf(widget(1, 0, 1, 1, 2, 2))
        assertThat(isRectFree(entries, 4, 5, excludeRowId = 1, page = 0, x = 1, y = 1, spanX = 2, spanY = 2)).isTrue()
        assertThat(isRectFree(entries, 4, 5, excludeRowId = 2, page = 0, x = 1, y = 1, spanX = 2, spanY = 2)).isFalse()
    }

    @Test
    fun anythingElseOnTheGridTakesOneCell() {
        val entries = listOf(folder(7, page = 0, x = 2, y = 2))
        assertThat(isRectFree(entries, 4, 5, none, 0, 2, 2, 1, 1)).isFalse()
        assertThat(isRectFree(entries, 4, 5, none, 0, 3, 2, 1, 1)).isTrue()
        // A widget being moved is the only thing "exclude" lets through: a folder stays in the way.
        assertThat(isRectFree(entries, 4, 5, excludeRowId = 7, page = 0, x = 2, y = 2, spanX = 1, spanY = 1)).isFalse()
    }

    // --------------------------------------------------------------------------- stackTargetFor

    @Test
    fun aWidgetDroppedSquarelyOnAnotherStacksOntoIt() {
        val target = widget(2, 0, 0, 0, 2, 2)
        val entries = listOf(target, folder(9, 0, 3, 3))
        assertThat(stackTargetFor(entries, draggedRowId = 5, p = WidgetPlacement(0, 0, 0, 2, 2))).isEqualTo(target)
    }

    @Test
    fun halfAnOverlapOrLessIsAMoveNotAStack() {
        val entries = listOf(widget(2, 0, 0, 0, 2, 2))
        // Half of the two footprints (2 of 4 cells): not MORE than half, so no stack.
        assertThat(stackTargetFor(entries, 5, WidgetPlacement(0, 1, 0, 2, 2))).isNull()
        // A corner touching (1 of 4 cells).
        assertThat(stackTargetFor(entries, 5, WidgetPlacement(0, 1, 1, 2, 2))).isNull()
        // Side by side, no overlap at all.
        assertThat(stackTargetFor(entries, 5, WidgetPlacement(0, 2, 0, 2, 2))).isNull()
    }

    @Test
    fun theSmallerFootprintDecidesWhatCountsAsMoreThanHalf() {
        val big = widget(2, 0, 0, 0, 3, 2) // 6 cells
        // A 2x2 sitting on it covers 4 cells, more than half of the smaller (4) footprint.
        assertThat(stackTargetFor(listOf(big), 5, WidgetPlacement(0, 0, 0, 2, 2))).isEqualTo(big)
        // Pushed right so only 2 cells overlap: half of the smaller footprint, no stack.
        assertThat(stackTargetFor(listOf(big), 5, WidgetPlacement(0, 2, 0, 2, 2))).isNull()
    }

    @Test
    fun aWidgetNeverStacksOntoItself() {
        val itself = widget(5, 0, 0, 0, 2, 2)
        assertThat(stackTargetFor(listOf(itself), draggedRowId = 5, p = WidgetPlacement(0, 0, 0, 2, 2))).isNull()
    }

    @Test
    fun onlyWidgetsOnTheSamePageCanBeStackedOnto() {
        val onOtherPage = widget(2, page = 1, x = 0, y = 0, spanX = 2, spanY = 2)
        assertThat(stackTargetFor(listOf(onOtherPage), 5, WidgetPlacement(0, 0, 0, 2, 2))).isNull()
        // Folders and apps are not stacked onto either.
        assertThat(stackTargetFor(listOf(folder(9, 0, 0, 0)), 5, WidgetPlacement(0, 0, 0, 1, 1))).isNull()
    }

    @Test
    fun theWidgetItCoversMostIsTheOneStackedOnto() {
        val left = widget(2, 0, 0, 0, 2, 2)
        val right = widget(3, 0, 2, 0, 2, 2)
        // A 3x2 over both: all 4 cells of the left one, 2 cells of the right one.
        assertThat(stackTargetFor(listOf(right, left), 5, WidgetPlacement(0, 0, 0, 3, 2))).isEqualTo(left)
        // Moved right, it covers the right one most.
        assertThat(stackTargetFor(listOf(left, right), 5, WidgetPlacement(0, 1, 0, 3, 2))).isEqualTo(right)
    }

    @Test
    fun aStackCanBeStackedOnto() {
        val stack = PlacedWidgetStack(4, emptyList(), 0, 0, 0, 2, 2)
        assertThat(stackTargetFor(listOf(stack), 5, WidgetPlacement(0, 0, 0, 2, 2))).isEqualTo(stack)
    }

    // ------------------------------------------------------------------------- canPlaceWidget

    @Test
    fun aWidgetFitsWhereTheGridHasRoomAndNotOutsideIt() {
        val empty = emptyList<HomeEntry>()
        assertThat(canPlaceWidget(empty, 4, 5, WidgetPlacement(0, 0, 0, 2, 2))).isTrue()
        assertThat(canPlaceWidget(empty, 4, 5, WidgetPlacement(0, 3, 0, 2, 2))).isFalse()
        assertThat(canPlaceWidget(empty, 4, 5, WidgetPlacement(0, 0, 4, 2, 2))).isFalse()
    }

    @Test
    fun aWidgetMayBeDroppedOnItsOwnPlace() {
        val entries = listOf(widget(1, 0, 0, 0, 4, 5))
        assertThat(canPlaceWidget(entries, 4, 5, WidgetPlacement(0, 0, 0, 4, 5), rowId = 1)).isTrue()
    }

    @Test
    fun aFullPageLeavesNoRoomForAnotherWidget() {
        // One widget fills the whole page; a new one has nowhere to push it to.
        val entries = listOf(widget(1, 0, 0, 0, 4, 5))
        assertThat(canPlaceWidget(entries, 4, 5, WidgetPlacement(0, 0, 0, 2, 2))).isFalse()
    }
}
