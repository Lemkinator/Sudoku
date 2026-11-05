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

import android.app.Application
import android.graphics.Rect
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View.MeasureSpec
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import androidx.test.core.app.ApplicationProvider
import de.lemke.sudoku.domain.model.Board
import de.lemke.sudoku.domain.model.Cell
import de.lemke.sudoku.domain.model.CellOrigin
import de.lemke.sudoku.domain.model.NoteMask
import de.lemke.sudoku.domain.model.SudokuSize
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val SIDE = 400
private val SOLUTION_4 = listOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 3, 4, 1, 4, 1, 2, 3)

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class BoardAccessibilityHelperTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var view: SudokuBoardView
    private lateinit var helper: BoardAccessibilityHelper
    private var clicked: Int? = null
    private var longClicked: Int? = null

    @Before
    fun setup() {
        view = SudokuBoardView(context)
        view.onCellClick = { clicked = it }
        view.onCellLongClick = { longClicked = it }
        view.render = BoardRender(golden4())
        view.measure(MeasureSpec.makeMeasureSpec(SIDE, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(SIDE, MeasureSpec.EXACTLY))
        view.layout(0, 0, SIDE, SIDE)
        helper = BoardAccessibilityHelper(view)
    }

    private fun golden4(): Board {
        val board = Board.of(SudokuSize.FOUR, SOLUTION_4, setOf(0, 3, 6, 9, 12, 15))
        return board
            .with(1, Cell(2, notes = NoteMask.of(2, 3)))
            .with(2, Cell(3, value = 4))
            .with(4, Cell(3, value = 3, origin = CellOrigin.HINT))
            .with(5, Cell(4, value = 4))
    }

    private fun node(id: Int): AccessibilityNodeInfoCompat =
        checkNotNull(checkNotNull(helper.getAccessibilityNodeProvider(view)).createAccessibilityNodeInfo(id))

    private fun description(id: Int) = node(id).contentDescription

    @Test
    fun `a given cell reads its value`() {
        description(0) shouldBe "Row 1, column 1, 1, given"
    }

    @Test
    fun `an empty cell reads its notes`() {
        description(1) shouldBe "Row 1, column 2, empty, notes 2, 3"
    }

    @Test
    fun `a wrong value reads wrong`() {
        description(2) shouldBe "Row 1, column 3, 4, wrong"
    }

    @Test
    fun `a hint cell reads hint`() {
        description(4) shouldBe "Row 2, column 1, 3, hint"
    }

    @Test
    fun `a correct player value reads plain`() {
        description(5) shouldBe "Row 2, column 2, 4"
    }

    @Test
    fun `an empty cell without notes reads empty`() {
        description(8) shouldBe "Row 3, column 1, empty"
    }

    @Test
    fun `a 16x16 note reads as a letter`() {
        val solution = List(256) { (4 * (it / 16 % 4) + it / 16 / 4 + it % 16) % 16 + 1 }
        val board = Board.of(SudokuSize.SIXTEEN, solution, emptySet())
        view.render = BoardRender(board.with(5, Cell(solution[5], notes = NoteMask.of(1, 10, 16))))
        view.measure(MeasureSpec.makeMeasureSpec(SIDE, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(SIDE, MeasureSpec.EXACTLY))
        view.layout(0, 0, SIDE, SIDE)

        description(5) shouldBe "Row 1, column 6, empty, notes 1, A, G"
    }

    @Test
    fun `the click action selects the cell`() {
        helper.getAccessibilityNodeProvider(view)!!.performAction(2, AccessibilityNodeInfo.ACTION_CLICK, null).shouldBeTrue()

        clicked shouldBe 2
    }

    @Test
    fun `the long click action erases the cell`() {
        helper.getAccessibilityNodeProvider(view)!!.performAction(2, AccessibilityNodeInfo.ACTION_LONG_CLICK, null).shouldBeTrue()

        longClicked shouldBe 2
    }

    @Test
    fun `an unknown action is refused`() {
        helper.getAccessibilityNodeProvider(view)!!.performAction(2, AccessibilityNodeInfo.ACTION_FOCUS, null).shouldBeFalse()

        clicked shouldBe null
    }

    @Test
    fun `a view without callbacks still accepts the actions`() {
        view.onCellClick = null
        view.onCellLongClick = null
        val provider = helper.getAccessibilityNodeProvider(view)!!

        provider.performAction(2, AccessibilityNodeInfo.ACTION_CLICK, null).shouldBeTrue()
        provider.performAction(2, AccessibilityNodeInfo.ACTION_LONG_CLICK, null).shouldBeTrue()
    }

    @Test
    fun `the host reports a 4 by 4 grid`() {
        val info = node(ExploreByTouchHelper.HOST_ID).collectionInfo

        info.rowCount to info.columnCount shouldBe (4 to 4)
    }

    @Test
    fun `a cell reports its grid position and bounds`() {
        val node = node(6)
        val bounds = Rect().also(node::getBoundsInParent)

        node.collectionItemInfo.rowIndex to node.collectionItemInfo.columnIndex shouldBe (1 to 2)
        bounds shouldBe Rect(200, 100, 300, 200)
    }

    @Test
    fun `hovering a cell finds its virtual view and hovering the cut corner finds none`() {
        val manager = shadowOf(context.getSystemService(AccessibilityManager::class.java))
        manager.setEnabled(true)
        manager.setTouchExplorationEnabled(true)

        view.dispatchGenericMotionEvent(hover(150f, 50f)).shouldBeTrue()
        view.dispatchGenericMotionEvent(hover(1f, 1f)).shouldBeFalse()
    }

    @Test
    fun `without a board the host has no children`() {
        view.render = null

        node(ExploreByTouchHelper.HOST_ID).childCount shouldBe 0
    }

    @Test
    fun `every cell is a child of the host`() {
        node(ExploreByTouchHelper.HOST_ID).childCount shouldBe 16
    }

    private fun hover(
        x: Float,
        y: Float,
    ): MotionEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, x, y, 0).also { it.source = InputDevice.SOURCE_TOUCHSCREEN }
}
