/*
 * Copyright 2022-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.sudoku.ui.board

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.customview.widget.ExploreByTouchHelper
import de.lemke.sudoku.R
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.toSudokuDigitChar

private const val NO_VIEW = ExploreByTouchHelper.INVALID_ID

/** Gives TalkBack one entry per cell of [view], each reading its row, column, value and notes. */
class BoardAccessibilityHelper(private val view: SudokuBoardView) : ExploreByTouchHelper(view) {
    override fun getVirtualViewAt(
        x: Float,
        y: Float,
    ): Int = view.cellAt(x, y).takeIf { it >= 0 } ?: NO_VIEW

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        val board = view.render?.board ?: return
        virtualViewIds.addAll(0 until board.size.cellCount)
    }

    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat,
    ) {
        val board = checkNotNull(view.render).board
        val geometry = checkNotNull(view.geometry)
        val position = board.position(virtualViewId)
        val box = geometry.cellBox(virtualViewId)
        node.contentDescription = describe(board[virtualViewId], position.row + 1, position.column + 1)
        node.setBoundsInParent(Rect(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt()))
        node.addAction(AccessibilityActionCompat.ACTION_CLICK)
        node.addAction(AccessibilityActionCompat.ACTION_LONG_CLICK)
        node.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(position.row, 1, position.column, 1, false))
    }

    override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
        val size =
            view.render
                ?.board
                ?.size
                ?.value ?: return
        node.setCollectionInfo(AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(size, size, false))
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?,
    ): Boolean =
        when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> {
                view.onCellClick?.invoke(virtualViewId)
                true
            }

            AccessibilityNodeInfo.ACTION_LONG_CLICK -> {
                view.onCellLongClick?.invoke(virtualViewId)
                true
            }

            else -> {
                false
            }
        }

    private fun describe(
        cell: Cell,
        row: Int,
        column: Int,
    ): String {
        val resources = view.resources
        val content =
            when {
                cell.value == null -> resources.getString(R.string.cell_empty)
                cell.origin == CellOrigin.GIVEN -> resources.getString(R.string.cell_given, cell.value.toSudokuDigitChar())
                cell.origin == CellOrigin.HINT -> resources.getString(R.string.cell_hint, cell.value.toSudokuDigitChar())
                cell.error -> resources.getString(R.string.cell_wrong, cell.value.toSudokuDigitChar())
                else -> cell.value.toSudokuDigitChar().toString()
            }
        val withNotes =
            if (cell.value == null && !cell.notes.isEmpty()) {
                resources.getString(
                    R.string.cell_notes,
                    content,
                    cell.notes.digits.joinToString(", ") { it.toSudokuDigitChar().toString() },
                )
            } else {
                content
            }
        return resources.getString(R.string.cell_description, row, column, withNotes)
    }
}
